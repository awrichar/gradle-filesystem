package com.cisco.gradle.filesystem

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

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
    }

    def "install single executable"() {
        given:
        buildFile << """
            $pluginInit

            model {
                filesystem {
                    prefix '${installFolder.path}'
                    install \$.components.foo, "/"
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
        installFolder.listFiles()*.name == ['foo']
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
}
