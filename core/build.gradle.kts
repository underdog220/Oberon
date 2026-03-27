plugins {
    id("org.jetbrains.kotlin.jvm")
}

version = "0.003"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    }
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    // Ensure compileJava stays aligned with Kotlin jvmTarget=17
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

