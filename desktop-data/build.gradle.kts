plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
