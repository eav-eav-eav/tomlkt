import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.kapt)

    alias(libs.plugins.jmh)
}

dependencies {
    jmh(libs.jmh.core)
    kaptJmh(libs.jmh.generator.annprocess)

    // tomlkt
    jmh(project(":core"))
    // toml4j
    jmh(libs.toml4j)
    // ktoml
    jmh(libs.ktoml.core)
    // jackson
    jmh(libs.jackson.dataformat.toml)
    jmh(libs.jackson.module.kotlin)
    jmh(libs.jackson.datatype.jsr310)
    // night config
    jmh(libs.night.config.toml)
    // tomlj
    jmh(libs.tomlj)

    // official JSON
    jmh(libs.kotlinx.serialization.json)
}

jmh {
    includes.set(listOf("test.Benchmark"))
}

allOpen {
    annotation("org.openjdk.jmh.annotations.Measurement")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
