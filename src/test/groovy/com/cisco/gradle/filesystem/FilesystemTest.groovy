package com.cisco.gradle.filesystem

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class FilesystemTest extends Specification {
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File installFolder

    static final String pluginInit = """
        plugins {
            id 'cpp'
            id 'com.cisco.filesystem'
        }

        model {
            components {
                foo(NativeExecutableSpec) {
                    sources.cpp.source {
                        srcDir '.'
                        include 'main.cpp'
                    }
                }
            }
        }
    """

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        installFolder = testProjectDir.newFolder()

        testProjectDir.newFile('main.cpp') << """
            int main() {
                return 0;
            }
        """
        testProjectDir.newFile('libfoo.so')
    }

    List<String> folderContents(File folder, String subfolder='') {
        List<String> filenames = new File(folder, subfolder).listFiles()*.name
        Collections.sort(filenames)
        return filenames
    }

    def "install nothing"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == UP_TO_DATE
        folderContents(installFolder) == []
    }

    def "basic install"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/'
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['foo']
    }

    def "install to subfolder"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/bin'
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['bin']
        folderContents(installFolder, 'bin') == ['foo']
    }

    def "install with modified path for one binary"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/bin', {
                        destPath = '/bin2'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['bin2']
        folderContents(installFolder, 'bin2') == ['foo']
    }

    def "install with modified path for each binaries"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/bin'
                    eachBinary {
                        destPath = "/usr/\$destPath"
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['usr']
        folderContents(installFolder, 'usr') == ['bin']
        folderContents(installFolder, 'usr/bin') == ['foo']
    }

    def "install with exclude"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/bin', {
                        exclude = true
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == UP_TO_DATE
        folderContents(installFolder) == []
    }

    def "install with copy"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/bin', {
                        copyTo '/bin2'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['bin', 'bin2']
        folderContents(installFolder, 'bin') == ['foo']
        folderContents(installFolder, 'bin2') == ['foo']
    }

    def "install with symlink"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/', {
                        symlinkAs 'bar'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['bar', 'foo']
    }

    def "null component throws exception"() {
        given:
        buildFile << """
            plugins {
                id 'com.cisco.filesystem'
            }

            model {
                filesystem {
                    install null, "/"
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('filesystem')
                .build()

        then:
        Exception e = thrown()
    }

    def "prebuilt install"() {
        given:
        buildFile << """
            plugins {
                id 'cpp'
                id 'com.cisco.filesystem'
            }

            model {
                repositories {
                    libs(PrebuiltLibraries) {
                        foo {
                            binaries.withType(SharedLibraryBinary) {
                                sharedLibraryFile = file('libfoo.so')
                            }
                        }
                    }
                }

                filesystem {
                    prefix '${installFolder.path}'
                    install(\$.repositories['libs'].resolveLibrary('foo'), '/folder1') {
                        copyTo '/folder2'
                        symlinkAs 'libfoo.so.2'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('filesystem')
                .build()

        then:
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['folder1', 'folder2']
        folderContents(installFolder, 'folder1') == ['libfoo.so', 'libfoo.so.2']
        folderContents(installFolder, 'folder2') == ['libfoo.so']
    }

    def "install with rename"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, '/', {
                        rename { 'bar' }
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build', 'filesystem')
                .build()

        then:
        result.task(":build").outcome == SUCCESS
        result.task(":filesystem").outcome == SUCCESS
        folderContents(installFolder) == ['bar']
    }
}
