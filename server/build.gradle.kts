plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

application {
    mainClass.set("com.terminus.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.logback.classic)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.commons.csv)
}

tasks.register<JavaExec>("importGtfs") {
    group = "application"
    description = "Download/import the MARTA GTFS feed and rebuild the departure index"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.terminus.server.GtfsImporterKt")
}
