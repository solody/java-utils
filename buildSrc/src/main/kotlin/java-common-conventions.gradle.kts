import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

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
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = project.name
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

    repositories {
        maven {
            name = "central"
            url = uri(
                "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = findProperty("mavenCentralUsername") as String?
                    ?: System.getenv("MAVEN_CENTRAL_USERNAME")
                password = findProperty("mavenCentralPassword") as String?
                    ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
            }
        }
    }
}

signing {
    val signingPassword = findProperty("signingPassword") as String?
        ?: System.getenv("SIGNING_PASSWORD")
        ?: findProperty("signing.password") as String?

    val signingKeyFromProp = findProperty("signingKey") as String?
        ?: System.getenv("SIGNING_KEY")
    val signingKeyFilePath = findProperty("signingKeyFile") as String?
        ?: System.getenv("SIGNING_KEY_FILE")

    val signingKey = when {
        !signingKeyFromProp.isNullOrBlank() ->
            signingKeyFromProp.replace("\\n", "\n")
        !signingKeyFilePath.isNullOrBlank() -> {
            val file = file(signingKeyFilePath)
            require(file.isFile) { "signingKeyFile not found: $signingKeyFilePath" }
            file.readText()
        }
        else -> null
    }

    // Prefer in-memory / file key (CI-friendly); else fall back to keyring props.
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }

    sign(publishing.publications["mavenJava"])
}

// Sign for remote publish, or when a Sign task is requested explicitly.
tasks.withType<Sign>().configureEach {
    onlyIf {
        val names = gradle.taskGraph.allTasks.map { it.name }
        names.any { it.startsWith("sign") } ||
            names.any { it.startsWith("publish") && !it.contains("ToMavenLocal") }
    }
}

// After :publish, upload the staging repo into Central Publisher Portal.
tasks.register("closeCentralDeployment") {
    group = "publishing"
    description =
        "Upload the OSSRH staging deployment to Central Publisher Portal (same IP as publish)."
    doLast {
        val username = findProperty("mavenCentralUsername") as String?
            ?: System.getenv("MAVEN_CENTRAL_USERNAME")
            ?: error("Missing mavenCentralUsername / MAVEN_CENTRAL_USERNAME")
        val password = findProperty("mavenCentralPassword") as String?
            ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
            ?: error("Missing mavenCentralPassword / MAVEN_CENTRAL_PASSWORD")
        val namespace = findProperty("mavenCentralNamespace") as String? ?: "com.solody"
        val publishingType = findProperty("mavenCentralPublishingType") as String? ?: "user_managed"

        val token = Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        val url =
            "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$namespace?publishing_type=$publishingType"

        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 60_000
        connection.readTimeout = 120_000

        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (code !in 200..299) {
            error("closeCentralDeployment failed: HTTP $code\n$body")
        }
        logger.lifecycle("Uploaded staging deployment for $namespace (HTTP $code). Check https://central.sonatype.com/publishing")
        if (body.isNotBlank()) {
            logger.lifecycle(body)
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
