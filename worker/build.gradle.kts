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
    mainClass.set("com.terminus.worker.WorkerKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":server"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.firebase.admin)
    implementation(libs.logback.classic)
}
