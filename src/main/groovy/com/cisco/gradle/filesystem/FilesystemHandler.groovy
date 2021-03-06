package com.cisco.gradle.filesystem

import org.gradle.api.Task
import org.gradle.nativeplatform.NativeBinary
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec

class FilesystemHandler {
    Object prefix = '/'
    List<Entry> entries = []
    List<Closure> entryFilters = []
    List<Closure> installedFileHandlers = []

    static class Entry {
        Object component
        Object destPath
        Closure filter

        Entry(ComponentSpec component, Object destPath, Closure filter) {
            this.component = component
            this.destPath = destPath
            this.filter = filter
        }

        Entry(PrebuiltLibrary component, Object destPath, Closure filter) {
            this.component = component
            this.destPath = destPath
            this.filter = filter
        }
    }

    static class EntryDetails {
        boolean exclude
        Object binary
        Object destPath
        Closure renameAction
        List<Object> copyTo = []
        List<String> symlinkAs = []

        EntryDetails(BinarySpec binary, Entry entry) {
            this.exclude = false
            this.binary = binary
            this.destPath = entry.destPath
        }

        EntryDetails(NativeBinary binary, Entry entry) {
            this.exclude = false
            this.binary = binary
            this.destPath = entry.destPath
        }

        void configure(Closure closure) {
            closure.delegate = this
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.call(this)
        }

        void rename(Closure closure) {
            renameAction = closure
        }

        void copyTo(Object path) {
            copyTo << path
        }

        void symlinkAs(String relativePath) {
            symlinkAs << relativePath
        }
    }

    static class InstalledFileDetails {
        File installedFile
        Task installTask
        boolean isSymlink
    }

    void prefix(Object prefix) {
        this.prefix = prefix
    }

    void install(ComponentSpec component, Object destPath, Closure filter = null) {
        entries << new Entry(component, destPath, filter)
    }

    void install(PrebuiltLibrary prebuilt, Object destPath, Closure filter = null) {
        entries << new Entry(prebuilt, destPath, filter)
    }

    void eachBinary(Closure closure) {
        entryFilters << closure
    }

    void eachInstalledFile(Closure closure) {
        installedFileHandlers << closure
    }
}
