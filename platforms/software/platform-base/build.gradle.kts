plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.logging)
    api(projects.modelCore)

    api(libs.guava)
    api(libs.jspecify)
    api(libs.jsr305)

    implementation(projects.dependencyManagement)
    implementation(projects.execution)
    implementation(projects.modelReflect)

    implementation(libs.commonsLang)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(projects.native)
    testImplementation(projects.snapshots)
    testImplementation(projects.processServices)

    testFixturesApi(projects.fileCollections)
    testFixturesApi(testFixtures(projects.modelCore))

    testFixturesImplementation(libs.guava)

    testRuntimeOnly(projects.distributionsCore) {
        because("RuntimeShadedJarCreatorTest requires a distribution to access the ...-relocated.txt metadata")
    }
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

description = """Provides general purpose base types and interfaces for modeling projects, and provides runtime and language support."""
tasks.isolatedProjectsIntegTest {
    enabled = false
}
