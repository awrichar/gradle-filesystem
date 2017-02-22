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

    private File getBinaryOutputFile(BinarySpec binary) throws IllegalArgumentException {
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

    private Object callWithContext(Closure closure, Object context) {
        closure.delegate = context
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        return closure.call(context)
    }

    private void addFilesystemTasks(Task mainTask, FilesystemHandler filesystemHandler) {
        // Loop through all binaries for all components added to the filesystem
        filesystemHandler.entries.each { FilesystemHandler.Entry item ->
            item.component.binaries.each { BinarySpec binary ->
                // Run the filters (if any)
                FilesystemHandler.EntryDetails details = new FilesystemHandler.EntryDetails(binary, item)
                filesystemHandler.entryFilters.each {
                    callWithContext(it, details)
                }
                if (item.filter) {
                    callWithContext(item.filter, details)
                }
                if (details.exclude) {
                    return
                }

                // Create the task to copy the binary
                binary.tasks.create(binary.tasks.taskName('install'), Copy) {
                    configureCopyTask(it, binary, filesystemHandler.prefix, details.destPath)
                    mainTask.dependsOn it
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

    private void configureCopyTask(Copy task, BinarySpec binary, Object prefix, Object dest) {
        task.into prefix
        task.from(getBinaryOutputFile(binary)) {
            it.into dest
        }
        task.onlyIf { binary.buildable }
        task.mustRunAfter binary.buildTask
    }
}
