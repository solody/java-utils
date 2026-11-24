plugins {
    `java-common-conventions`
    `java-test-fixtures`
}

// Internal test helpers only — do not publish to Maven Central.
tasks.withType<PublishToMavenRepository>().configureEach { enabled = false }
tasks.withType<PublishToMavenLocal>().configureEach { enabled = false }
tasks.withType<Sign>().configureEach { enabled = false }

dependencies {
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesApi("org.jspecify:jspecify")
    testFixturesApi("org.slf4j:slf4j-api:2.0.20")
}