plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

group = "com.solody"
version = "1.0-SNAPSHOT"

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

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed", "standard_out", "standard_error")
    }
}
