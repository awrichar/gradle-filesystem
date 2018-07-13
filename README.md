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
            install($.components.bar, '/lib') {
                if (binary in StaticLibraryBinarySpec) {
                    exclude = true
                }
            }

            // Install foo into both /bin and /usr/bin
            install($.components.foo, '/bin') {
                copyTo '/usr/bin'
            }

            // Install foo into /bin and symlink as '/bin/bar'
            install($.components.foo, '/bin') {
                symlinkAs 'bar'
            }

            // Install foo into /bin, but rename it to 'bar' instead
            install($.components.foo, '/bin') {
                rename { 'bar' }
            }

            // Install a PrebuiltLibrary foo into /bin
	    install $.repositories['libs'].resolveLibrary('foo'), '/bin'
        }
    }

## License

Author: Andrew Richardson (andreric@cisco.com)

Created for Cisco and released under the terms of the Apache 2.0 License (contribution #148021642).
