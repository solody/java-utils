plugins {
    `java-common-conventions`
    `java-test-fixtures`
}


dependencies {
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesApi("org.jspecify:jspecify")
    testFixturesApi("org.slf4j:slf4j-api:2.0.20")
}