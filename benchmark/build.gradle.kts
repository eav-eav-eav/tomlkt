plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.jmh)
}

dependencies {
    jmh(libs.jmh.core)
    kaptJmh(libs.jmh.generator.annprocess)

    // tomlkt
    jmh(project(":core"))
    jmh(libs.kotlinx.serialization.core)
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
}

jmh {
    // Run both the decoding (test.DecodeBenchmark) and encoding
    // (test.EncodeBenchmark) benchmarks. A single regex matching either class is
    // used rather than a list: the jmh plugin intersects multiple include
    // patterns, so listing both classes would match nothing.
    includes.set(listOf("test\\..*Benchmark"))
}

// Always re-run the benchmark: its inputs rarely change, but cached results
// from a different machine state (load, power profile) are misleading.
tasks.named("jmh") {
    outputs.upToDateWhen { false }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.Measurement")
}

kotlin {
    jvmToolchain(17)
}
