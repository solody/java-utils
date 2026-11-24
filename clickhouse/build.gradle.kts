plugins {
    `java-common-conventions`
    `java-test-fixtures`
}

dependencies {
    testImplementation(testFixtures(project(":testing")))
}