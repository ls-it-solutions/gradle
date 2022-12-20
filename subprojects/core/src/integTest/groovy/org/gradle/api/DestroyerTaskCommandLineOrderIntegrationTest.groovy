/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api


import org.gradle.integtests.fixtures.executer.TaskOrderSpecs
import spock.lang.IgnoreRest
import spock.lang.Issue

class DestroyerTaskCommandLineOrderIntegrationTest extends AbstractCommandLineOrderTaskIntegrationTest {
    def "destroyer task with a dependency in another project will run before producer tasks when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "a producer task will not run before a task in another project that destroys what it produces (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = foo.task('cleanBar').destroys('../bar/build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "destroyer task with a dependency in another build will run before producer tasks when ordered first (type: #type)"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    def "allows explicit task dependencies to override command line order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        // conflicts with command line order
        cleanBar.dependsOn(generateBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(generateBar.fullPath, cleanBar.fullPath)
        }
    }

    def "allows command line order to override shouldRunAfter relationship"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        // conflicts with command line order
        cleanBar.shouldRunAfter(generateBar)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath)
        }
    }

    def "destroyer task with a dependency in another project followed by a producer task followed by a destroyer task are run in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def cleanFooLocal = foo.task('cleanFooLocalState').destroys('build/foo-local')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path, cleanFooLocal.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanFooLocal.fullPath))
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "destroyer task with a dependency in another build followed by a producer task followed by a destroyer task are run in the correct order"() {
        def foo = includedBuild('child').subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def cleanFooLocal = foo.task('cleanFooLocalState').destroys('build/foo-local')
        def cleanLocal = rootBuild.task('cleanLocal').dependsOn(cleanFooLocal)
        def generateFoo = foo.task('generateFoo').outputs('build/foo').localState('build/foo-local')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        // TODO - does not work with CC yet. cleanFoo starts, which blocks generateFoo. However, cleanFooLocalState can start and so runs before generateFoo
        // However, the cleanFooLocalState should block because it conflicts with generateFoo
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path, cleanLocal.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanFooLocal.fullPath))
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "multiple destroyer tasks listed on the command line followed by producers can run concurrently and are executed in the correct order"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').shouldBlock()
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def cleanBarLocal = bar.task('cleanBarLocalState').destroys('build/bar-local').shouldBlock()
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').localState('build/bar-local')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(cleanFoo.path, cleanBarLocal.path)

            args '--parallel', '--max-workers=2'
            succeeds(clean.path, cleanBarLocal.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanBarLocal.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
        }
    }

    def "a destroyer task finalized by a task in another project will run before producer tasks if ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').finalizedBy(cleanBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "a destroyer task finalizing both a destroyer and a producer task will run after producer tasks"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').finalizedBy(cleanBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').finalizedBy(cleanBar)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(generateBar.fullPath, TaskOrderSpecs.any(generate.fullPath, cleanBar.fullPath))
        }
    }

    def "a task that is neither a producer nor a destroyer can run concurrently with destroyers"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').shouldBlock()
        def exec = bar.task('exec').shouldBlock()

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(cleanFoo.path, exec.path)

            args '--parallel', '--max-workers=2'
            succeeds(cleanFoo.path, exec.path)
        }
    }

    def "destroyers and producers in different projects can run concurrently when they have no dependencies"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo').shouldBlock()
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar').shouldBlock()
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        server.start()

        writeAllFiles()

        expect:
        2.times {
            server.expectConcurrent(cleanFoo.path, generateBar.path)

            args '--parallel', '--max-workers=2', '--rerun-tasks' // --rerun-tasks so that tasks are not up-to-date on second invocation
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    def "destroyer task that mustRunAfter a task in another project will run before producer tasks when ordered first"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').mustRunAfter(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').outputs('build/foo')
        def generateBar = bar.task('generateBar').outputs('build/bar')
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/20195")
    def "destroyer task that is a finalizer of a producer task will run after the producer even when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar')
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type).finalizedBy(cleanFoo)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, cleanFoo.fullPath, clean.fullPath)
            result.assertTaskOrder(generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/20195")
    def "destroyer task that is a finalizer of a producer task and also a dependency will run after the producer even when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type).finalizedBy(cleanFoo)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)

            // cleanFoo must run after generateFoo, as cleanFoo finalizes generateFoo
            // cleanFoo must run after generate, as cleanFoo destroys an output produced by generateFoo and consumed by generate
            result.assertTaskOrder(generateFoo.fullPath, generate.fullPath, cleanFoo.fullPath, clean.fullPath)

            // cleanBar depends on cleanFoo, but cleanFoo must run after generate (per above)
            result.assertTaskOrder(generateBar.fullPath, generate.fullPath, cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/20272")
    def "a destroyer task that mustRunAfter a task that does not get executed will run before producer tasks when ordered first (type: #type)"() {
        def foo = subproject(':foo')
        def bar = subproject(':bar')

        def cleanSpecialBar = bar.task('cleanSpecialBar')
        def cleanFoo = foo.task('cleanFoo').destroys('build/foo')
        def cleanBar = bar.task('cleanBar').destroys('build/bar').dependsOn(cleanFoo).mustRunAfter(cleanSpecialBar)
        def clean = rootBuild.task('clean').dependsOn(cleanFoo).dependsOn(cleanBar)
        def generateFoo = foo.task('generateFoo').produces('build/foo', type)
        def generateBar = bar.task('generateBar').produces('build/bar', type)
        def generate = rootBuild.task('generate').dependsOn(generateBar).dependsOn(generateFoo)

        writeAllFiles()

        expect:
        2.times {
            args '--parallel', '--max-workers=2'
            succeeds(clean.path, generate.path)

            result.assertTaskOrder(cleanFoo.fullPath, cleanBar.fullPath, clean.fullPath)
            result.assertTaskOrder(cleanFoo.fullPath, generateFoo.fullPath, generate.fullPath)
            result.assertTaskOrder(cleanBar.fullPath, generateBar.fullPath, generate.fullPath)
        }

        where:
        type << ProductionType.values()
    }

    @IgnoreRest
    def "complicated setup works"() {
        rootBuild.task("clean").destroys("build")

        def deps = [
            ":foo:writeVersionFiles": [
                deps: [":writeVersionFiles"],
                finalizes: [":foo:assemble", ":foo:build", ":foo:distTar", ":foo:distZip", ":foo:jar", ":foo:publishNebulaIvyPublicationToDistIvyRepository", ":foo:sourceJar"]
            ],
            ":foo:compileJava": [
                deps: []
            ],
            ":foo:compileGroovy": [
                deps: [":foo:compileJava"]
            ],
            ":foo:processResources": [
                deps: []
            ],
            ":foo:classes": [
                deps: [":foo:compileJava", ":foo:processResources"]
            ],
            ":foo:deleteIntegrationTestClassesInMain": [
                deps: [],
                finalizes: [":foo:copyIntegrationTestClasses"]
            ],
            ":foo:deleteIntegrationTestSrcFiles": [
                deps: [],
                finalizes: [":foo:copyIntegrationTestSrcFiles"]
            ],
            ":foo:copyIntegrationTestSrcFiles": [
                deps: [],
                finalizes: [":foo:classes"]
            ],
            ":foo:copyIntegrationTestClasses": [
                deps: [],
                finalizes: [":foo:deleteIntegrationTestSrcFiles"]
            ],
            ":foo:jacocoTestReport": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava"],
                mustRunAfter: [":foo:test"]
            ],
            ":foo:copyGeneratedFiles": [
                deps: [],
                finalizes: [":foo:deleteIntegrationTestClassesInMain"]
            ],
            ":devSnapshot": [
                deps: [":devSnapshotSetup", ":postRelease"]
            ],
            ":devSnapshotSetup": [
                deps: [":releaseCheck"]
            ],
            ":releaseCheck": [
                deps: []
            ],
            ":jacocoTestReport": [
                deps: [":classes", ":compileJava"],
                mustRunAfter: [":test"]
            ],
            // More
            ":foo:jar": [
                deps: [":foo:classes", ":foo:compileJava"]
            ],
            ":foo:distTar": [
                deps: [":foo:jar"]
            ],
            ":foo:distZip": [
                deps: [":foo:jar"]
            ],
            ":foo:sourceJar": [
                deps: []
            ],
            ":foo:assemble": [
                deps: [":foo:distTar", ":foo:distZip", ":foo:jar", ":foo:sourceJar"]
            ],
            ":foo:buildInvocationInfo": [
                deps: []
            ],
            ":foo:compileTestJava": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava"]
            ],
            ":foo:compileTestGroovy": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava", ":foo:compileTestJava"]
            ],
            ":foo:processTestResources": [
                deps: []
            ],
            ":foo:testClasses": [
                deps: [":foo:compileTestGroovy", ":foo:compileTestJava", ":foo:processTestResources"]
            ],
            ":foo:compileIntegTestJava": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava", ":foo:compileTestJava", ":foo:compileTestGroovy", ":foo:testClasses"]
            ],
            ":foo:compileIntegTestGroovy": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava", ":foo:compileTestJava", ":foo:compileTestGroovy", ":foo:testClasses", ":foo:compileIntegTestJava"]
            ],
            ":foo:processIntegTestResources": [
                deps: []
            ],
            ":foo:integTestClasses": [
                deps: [":foo:compileIntegTestGroovy", ":foo:compileIntegTestJava", ":foo:processIntegTestResources"]
            ],
            ":foo:integrationTest": [
                deps: [":foo:buildInvocationInfo", ":foo:classes", ":foo:compileGroovy", ":foo:compileJava", ":foo:compileTestJava", ":foo:compileTestGroovy", ":foo:testClasses", ":foo:compileIntegTestJava", ":foo:compileIntegTestGroovy", ":foo:integTestClasses", ":foo:installDist"]
            ],
            ":foo:installDist": [
                deps: [":foo:jar"],
                finalizes: [":foo:compileJava", ":foo:copyGeneratedFiles"]
            ],
            ":foo:spotbugsIntegTest": [
                deps: [":foo:compileIntegTestGroovy", ":foo:compileIntegTestJava", ":foo:integTestClasses"]
            ],
            ":foo:spotbugsMain": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava"]
            ],
            ":foo:spotbugsTest": [
                deps: [":foo:classes", ":foo:compileGroovy", ":foo:compileJava", ":foo:compileTestJava", ":foo:compileTestGroovy", ":foo:testClasses"]
            ],
            ":foo:test": [
                deps: [":foo:buildInvocationInfo", ":foo:classes", ":foo:compileGroovy", ":foo:compileJava", ":foo:compileTestJava", ":foo:compileTestGroovy", ":foo:testClasses"]
            ],
            ":foo:check": [
                deps: [":foo:integrationTest", ":foo:spotbugsIntegTest", ":foo:spotbugsMain", ":foo:spotbugsTest", ":foo:test"]
            ],
            ":foo:build": [
                deps: [":foo:assemble", ":foo:check", ":foo:integTestClasses"]
            ],
            ":foo:generateDescriptorFileForNebulaIvyPublication": [
                deps: []
            ],
            ":foo:generateMetadataFileForNebulaIvyPublication": [
                deps: [":foo:jar", ":foo:sourceJar"]
            ],
            ":foo:publishNebulaIvyPublicationToDistIvyRepository": [
                deps: [":verifyPublicationReport", ":foo:generateDescriptorFileForNebulaIvyPublication", ":foo:generateMetadataFileForNebulaIvyPublication", ":foo:jar", ":foo:sourceJar"]
            ],
            ":foo:generateMetadataFileForNebulaPublication": [
                deps: [":foo:jar", ":foo:sourceJar"]
            ],
            ":foo:generatePomFileForNebulaPublication": [
                deps: []
            ],
            ":preparePublish": [
                deps: [],
                mustRunAfter: [":foo:build"]
            ],
            ":foo:publishNebulaPublicationToLibsSnapshotsLocalPomRepository": [
                deps: [":verifyPublicationReport", ":foo:generateMetadataFileForNebulaPublication", ":foo:generatePomFileForNebulaPublication", ":foo:jar", ":foo:sourceJar"],
                mustRunAfter: [":preparePublish"]
            ],
            ":compileJava": [
                deps: []
            ],
            ":processResources": [
                deps: []
            ],
            ":classes": [
                deps: [":compileJava", ":processResources"]
            ],
            ":createPropertiesFileForJar": [
                deps: []
            ],
            ":writeManifestProperties": [
                deps: []
            ],
            ":jar": [
                deps: [":classes", ":compileJava", ":createPropertiesFileForJar", ":writeManifestProperties"]
            ],
            ":assemble": [
                deps: [":jar"]
            ],
            ":spotbugsMain": [
                deps: [":classes", ":compileJava"]
            ],
            ":compileTestJava": [
                deps: [":classes", ":compileJava"]
            ],
            ":processTestResources": [
                deps: []
            ],
            ":testClasses": [
                deps: [":compileTestJava", ":processTestResources"]
            ],
            ":spotbugsTest": [
                deps: [":classes", ":compileJava", ":compileTestJava", ":testClasses"]
            ],
            ":buildInvocationInfo": [
                deps: []
            ],
            ":test": [
                deps: [":buildInvocationInfo", ":classes", ":compileJava", ":compileTestJava", ":testClasses"]
            ],
            ":check": [
                deps: [":spotbugsMain", ":spotbugsTest", ":test"]
            ],
            ":build": [
                deps: [":assemble", ":check"]
            ],
            ":writeVersionFiles": [
                deps: [],
                finalizes: [":assemble", ":build", ":jar"]
            ],
            ":postPublish": [
                deps: [],
                mustRunAfter: [":foo:publishNebulaIvyPublicationToDistIvyRepository", ":foo:publishNebulaPublicationToLibsSnapshotsLocalPomRepository"]
            ],
            ":confirmPublication": [
                deps: [],
                mustRunAfter: [":postPublish"]
            ],
            ":publishBuildInfoToArtifactory": [
                deps: [":confirmPublication"],
                mustRunAfter: [":postPublish"]
            ],
            ":publish": [
                deps: [":build", ":postPublish", ":preparePublish", ":publishBuildInfoToArtifactory"]
            ],
            ":prepare": [
                deps: []
            ],
            ":release": [
                deps: [":prepare", ":foo:build"]
            ],
            ":foo:publish": [
                deps: [":postPublish", ":preparePublish", ":foo:build", ":foo:publishNebulaIvyPublicationToDistIvyRepository", ":foo:publishNebulaPublicationToLibsSnapshotsLocalPomRepository"]
            ],
            ":postRelease": [
                deps: [":publish", ":release", ":foo:generateDescriptorFileForNebulaIvyPublication", ":foo:generatePomFileForNebulaPublication", ":foo:publish"],
            ],
            ":verifyPublicationReport": [
                deps: []
            ]
        ]

        expect:
        (deps.values()*.deps.flatten() - deps.keySet()).isEmpty()
        (deps.values().collect { it.finalizes ?: [] }.flatten() - deps.keySet()).isEmpty()
        (deps.values().collect { it.mustRunAfter ?: [] }.flatten() - deps.keySet()).isEmpty()

        when:
        ProjectFixture fooProject = subproject(':foo')
        fooProject.task("clean").destroys("build")
        ProjectFixture rootProject = subproject(':')
        Map<String, TaskFixture> tasks = [:]
        deps.keySet().each { taskPath ->
            def project = taskPath.startsWith(':foo:') ? fooProject : rootProject
            def taskName = taskPath.startsWith(':foo:') ? taskPath.substring(5) : taskPath.substring(1)
            tasks[taskPath.toString()] = project.task(taskName)
        }
        deps.each { taskPath, data ->
            TaskFixture currentTask = tasks[taskPath.toString()]
            data.deps.each { currentTask.dependsOn(tasks[it]) }
            data.finalizes?.each { tasks[it].finalizedBy(currentTask) }
            data.mustRunAfter?.each { currentTask.mustRunAfter(tasks[it]) }
            if (currentTask.name.startsWith("compile")) {
                currentTask.outputs("build/${currentTask.name}/classes")
            }
        }
        tasks[':foo:copyIntegrationTestClasses'].outputs("build/integrationTest/classes")
        tasks[':foo:copyIntegrationTestSrcFiles'].outputs("build/test/classes")
        tasks[':foo:deleteIntegrationTestSrcFiles'].destroys("build/compileIntegTestJava/classes")
        tasks[':foo:deleteIntegrationTestClassesInMain'].destroys("build/compileJava/classes")

        writeAllFiles()

        then:
        succeeds("clean", "check", "devSnapshot")

//        def installDist = rootBuild.task("installDist").outputs("build/install")
//        def copyClasses = rootBuild.task("copyClasses").outputs("build/copied-classes")
//        def copySources = rootBuild.task("copySources").outputs("build/copied-sources")
//        def deleteClasses = rootBuild.task("deleteClasses").destroys("build/classes")
//        def deleteSources = rootBuild.task("deleteSources").destroys("build/generated/src")
//        def compileJava = rootBuild.task("compileJava")
//            .outputs("build/classes")
//            .outputs("build/generated/src")
//            .finalizedBy(copyClasses)
//        def jar = rootBuild.task("jar").outputs("build/libs").dependsOn(compileJava)
//        copyClasses.finalizedBy(deleteClasses)
//        deleteClasses.finalizedBy(copySources)
//        copySources.finalizedBy(deleteSources)
//        installDist.dependsOn(jar)
//        compileJava.finalizedBy(installDist)
//        copyClasses.finalizedBy(installDist)
//        rootBuild.task("integTest").dependsOn(compileJava)
//
//        rootBuild.task("copyFiles").outputs("build/copied-files")
//
//        writeAllFiles()
//
//        expect:
//        args '--parallel', '--configuration-cache', '--max-workers=2'
//        succeeds "clean", "installDist", "integTest"
//        args '--parallel', '--configuration-cache', '--max-workers=2'
//        succeeds "clean", "installDist", "integTest"
    }
}
