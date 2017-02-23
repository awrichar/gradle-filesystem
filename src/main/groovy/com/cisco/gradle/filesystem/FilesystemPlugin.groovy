package com.cisco.gradle.filesystem

import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.jvm.JarBinarySpec
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.platform.base.BinarySpec

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

    static private File getBinaryOutputFile(BinarySpec binary) throws IllegalArgumentException {
        if (binary in SharedLibraryBinarySpec) {
            return binary.sharedLibraryFile
        } else if (binary in StaticLibraryBinarySpec) {
            return binary.staticLibraryFile
        } else if (binary in NativeExecutableBinarySpec) {
            return binary.executable.file
        } else if (binary in JarBinarySpec) {
            return binary.jarFile
        }

        throw new IllegalArgumentException("Unsupported binary type: ${binary.class.name}")
    }

    static private void addFilesystemTasks(Task mainTask, FilesystemHandler filesystemHandler) {
        // Loop through all binaries for all components added to the filesystem
        filesystemHandler.entries.each { FilesystemHandler.Entry item ->
            item.component.binaries.each { BinarySpec binary ->
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
                binary.tasks.create(binary.tasks.taskName('install'), Copy) { Copy copyTask ->
                    configureCopyTask(copyTask, binary, filesystemHandler.prefix, details.destPath)
                    mainTask.dependsOn copyTask

                    File outputFile = getBinaryOutputFile(binary)
                    File basePath = copyTask.project.file(filesystemHandler.prefix)
                    File destPath = new File(basePath, String.valueOf(details.destPath))
                    File destFile = new File(destPath, outputFile.name)

                    // Create tasks for symlinks (if any)
                    int i = 1
                    details.symlinkAs.each { String path ->
                        String taskName = binary.tasks.taskName('installLink', String.valueOf(i++))
                        binary.tasks.create(taskName, Task) {
                            configureLinkTask(it, binary, destFile, path)
                            mainTask.dependsOn it
                            it.dependsOn copyTask
                        }
                    }
                }

                // Create tasks for additional copies (if any)
                int i = 1
                details.copyTo.each { Object dest ->
                    String taskName = binary.tasks.taskName('installCopy', String.valueOf(i++))
                    binary.tasks.create(taskName, Copy) {
                        configureCopyTask(it, binary, filesystemHandler.prefix, dest)
                        mainTask.dependsOn it
                    }
                }
            }
        }
    }

    static private void configureCopyTask(Copy task, BinarySpec binary, Object prefix, Object dest) {
        task.into prefix
        task.from(getBinaryOutputFile(binary)) {
            it.into dest
        }
        task.onlyIf { binary.buildable }
        task.mustRunAfter binary.buildTask
    }

    static private void configureLinkTask(Task task, BinarySpec binary, File target, String path) {
        Path targetPath = target.toPath()
        Path linkPath = targetPath.parent.resolve(path)

        task.doLast {
            if (Files.isSymbolicLink(linkPath)) {
                Files.delete(linkPath)
            }
            Files.createDirectories(linkPath.parent)
            Files.createSymbolicLink(linkPath, linkPath.parent.relativize(targetPath))
        }
        task.onlyIf { binary.buildable }
        task.mustRunAfter binary.buildTask
    }
}
