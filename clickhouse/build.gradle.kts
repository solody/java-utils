plugins {
    `java-common-conventions`
}

dependencies {
    testImplementation(testFixtures(project(":testing")))
}