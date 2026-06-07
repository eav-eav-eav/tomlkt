plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlin.kapt) apply false

    alias(libs.plugins.dokka) apply false

    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.jmh) apply false

    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
