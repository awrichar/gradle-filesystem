package com.cisco.gradle.filesystem

import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec

class FilesystemHandler {
    Object prefix = '/'
    List<Entry> entries = []
    List<Closure> entryFilters = []

    static class Entry {
        ComponentSpec component
        Object destPath
        Closure filter

        Entry(ComponentSpec component, Object destPath, Closure filter) {
            this.component = component
            this.destPath = destPath
            this.filter = filter
        }
    }

    static class EntryDetails {
        boolean exclude
        BinarySpec binary
        Object destPath
        List<Object> copyTo = []

        EntryDetails(BinarySpec binary, Entry entry) {
            this.exclude = false
            this.binary = binary
            this.destPath = entry.destPath
        }

        void copyTo(Object path) {
            copyTo << path
        }
    }

    void prefix(Object prefix) {
        this.prefix = prefix
    }

    void install(ComponentSpec component, Object destPath, Closure filter = null) {
        entries << new Entry(component, destPath, filter)
    }

    void eachBinary(Closure closure) {
        entryFilters << closure
    }
}
