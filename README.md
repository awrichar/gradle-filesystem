# gradle-filesystem

Plugin for installing binaries on the filesystem.

Run with "gradle filesystem".

## Example Usage

    model {
        filesystem {
            // Optional prefix for all destination paths (default is '/')
            prefix '/usr'

            // Install all binaries from foo into /bin
            install $.components.foo, '/bin'

            // Add a rule to modify the details for all binaries
            eachBinary {
                destPath = "${binary.targetPlatform.name}/${destPath}"
            }

            // Install binaries from bar into /lib, but exclude static libraries
            install($.components.foo, '/lib') {
                if (binary in StaticLibraryBinarySpec) {
                    exclude = true
                }
            }
        }
    }
