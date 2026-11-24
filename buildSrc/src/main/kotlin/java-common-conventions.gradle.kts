plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

group = "com.solody.java-utils"
version = "1.0.0"

val libs = the<VersionCatalogsExtension>().named("libs")
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:slf4j-simple")

    implementation(platform(libs.findLibrary("vertx-stack-depchain").get()))
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-client")
    testImplementation("io.vertx:vertx-junit5")

    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())

    testImplementation("org.mockito:mockito-core:5.+")
    mockitoAgent("org.mockito:mockito-core:5.+") { isTransitive = false }

    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "java-utils"
                description = "A useful Java library."
                url = "https://github.com/solody/java-utils"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "solody"
                        name = "solody"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/solody/java-utils.git"
                    developerConnection = "scm:git:ssh://github.com/solody/java-utils.git"
                    url = "https://github.com/solody/java-utils"
                }
            }
        }
    }
}

// Test-fixture capabilities cannot be represented in Maven POM metadata.
// Keep fixtures for in-repo consumption; do not publish them.
plugins.withId("java-test-fixtures") {
    val javaComponent = components["java"] as AdhocComponentWithVariants
    javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed", "standard_out", "standard_error")
    }
}
