package com.cisco.gradle.filesystem

import org.gradle.api.Buildable
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.JarBinarySpec
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.SharedLibraryBinary
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinary
import org.gradle.nativeplatform.StaticLibraryBinarySpec

import java.nio.file.Files
import java.nio.file.Path

class FilesystemPlugin extends RuleSource {
    @Model
    FilesystemHandler filesystem() {
        return new FilesystemHandler()
    }

    @Mutate
    void createFilesystemTask(ModelMap<Task> tasks, FilesystemHandler filesystemHandler) {
        tasks.create('filesystem', Task) { Task filesystemTask ->
            filesystemTask.description = "Installs binaries on the filesystem."
            addFilesystemTasks(filesystemTask, filesystemHandler)
        }
    }

    static private File getBinaryOutputFile(Object binary) throws IllegalArgumentException {
        if (binary in SharedLibraryBinarySpec || binary in SharedLibraryBinary) {
            return binary.sharedLibraryFile
        } else if (binary in StaticLibraryBinarySpec || binary in StaticLibraryBinary) {
            return binary.staticLibraryFile
        } else if (binary in NativeExecutableBinarySpec) {
            return binary.executable.file
        } else if (binary in JarBinarySpec) {
            return binary.jarFile
        }

        throw new IllegalArgumentException("Unsupported binary type: ${binary.class.name}")
    }

    static private String taskName(String prefix, Object component, Object binary, String suffix=null) {
        String name = prefix + component.name.capitalize() + binary.name.capitalize()
        if (suffix != null) {
            name += suffix.capitalize()
        }
        return name
    }

    static private void addFilesystemTasks(Task mainTask, FilesystemHandler filesystemHandler) {
        // Loop through all binaries for all components added to the filesystem
        filesystemHandler.entries.each { FilesystemHandler.Entry item ->
            item.component.binaries.each { Object binary ->
                File outputFile = getBinaryOutputFile(binary)
                if (outputFile == null) {
                    return
                }

                // Run the filters (if any)
                FilesystemHandler.EntryDetails details = new FilesystemHandler.EntryDetails(binary, item)
                filesystemHandler.entryFilters.each {
                    details.configure(it)
                }
                if (item.filter) {
                    details.configure(item.filter)
                }
                if (details.exclude) {
                    return
                }

                // Create the task to copy the binary
                TaskContainer tasks = mainTask.project.tasks
                String installTask = taskName("installFile", item.component, binary)
                tasks.create(installTask, Copy) { Copy copyTask ->
                    configureCopyTask(copyTask, binary, filesystemHandler.prefix, details.destPath, details.renameAction)
                    mainTask.dependsOn copyTask

                    File basePath = copyTask.project.file(filesystemHandler.prefix)
                    File destPath = new File(basePath, String.valueOf(details.destPath))
                    File destFile
                    if (details.renameAction) {
                        destFile = new File(destPath, details.renameAction(outputFile.name))
                    } else {
                        destFile = new File(destPath, outputFile.name)
                    }

                    // Create tasks for symlinks (if any)
                    int i = 1
                    details.symlinkAs.each { String path ->
                        String symlinkTask = taskName('installLink', item.component, binary, String.valueOf(i++))
                        tasks.create(symlinkTask, Task) {
                            configureLinkTask(it, binary, destFile, path)
                            mainTask.dependsOn it
                            it.dependsOn copyTask
                        }
                    }
                }

                // Create tasks for additional copies (if any)
                int i = 1
                details.copyTo.each { Object dest ->
                    String copyTask = taskName('installCopy', item.component, binary, String.valueOf(i++))
                    tasks.create(copyTask, Copy) {
                        configureCopyTask(it, binary, filesystemHandler.prefix, dest, details.renameAction)
                        mainTask.dependsOn it
                    }
                }
            }
        }
    }

    static private void configureCopyTask(Copy task, Object binary, Object prefix, Object dest, Closure renameAction=null) {
        task.into prefix
        task.from(getBinaryOutputFile(binary)) {
            it.into dest
        }
        if (renameAction) {
            task.rename(renameAction)
        }
        if (binary in Buildable) {
            task.onlyIf { binary.buildable }
            task.mustRunAfter binary.buildTask
        }
    }

    static private void configureLinkTask(Task task, Object binary, File target, String path) {
        Path targetPath = target.toPath()
        Path linkPath = targetPath.parent.resolve(path)

        task.doLast {
            if (Files.isSymbolicLink(linkPath)) {
                Files.delete(linkPath)
            }
            Files.createDirectories(linkPath.parent)
            Files.createSymbolicLink(linkPath, linkPath.parent.relativize(targetPath))
        }
        if (binary in Buildable) {
            task.onlyIf { binary.buildable }
            task.mustRunAfter binary.buildTask
        }
    }
}
