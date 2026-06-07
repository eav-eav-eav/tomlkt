// A throwaway harness that adapts tomlkt to the toml-test compliance suite
// (https://github.com/toml-lang/toml-test). Not part of the published library.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.serialization.json)
}

application {
    // First arg selects direction: "decode" (default) or "encode".
    mainClass = "harness.MainKt"
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("dev.eav.tomlkt.TomlSpecific")
    }
}

// ---- toml-test compliance runner (toml-test v2) -----------------------------
//
// `./gradlew :compliance:complianceTest` builds the harness and runs the whole
// toml-test suite (decoder + encoder) against it; `complianceDecode` runs only
// the decoder side for faster iteration. v2 runs the encoder cases alongside
// the decoder in a single pass, so there is no standalone encoder task.
// Configure with:
//   -PtomlVersion=1.0   TOML version to test, "1.0" or "1.1" (default 1.1)
//   -PtomlTest=/path/to/toml-test   the runner (else $TOML_TEST, ~/go/bin, PATH)
// Install toml-test (v2) with:
//   go install github.com/toml-lang/toml-test/v2/cmd/toml-test@v2.2.0

// The `application` plugin's installDist produces this launcher.
val harnessLauncher = layout.buildDirectory.file("install/compliance/bin/compliance")

val tomlVersion = (findProperty("tomlVersion") as String?) ?: "1.1"

fun resolveTomlTest(): String {
    (findProperty("tomlTest") as String?)?.let { return it }
    System.getenv("TOML_TEST")?.let { return it }
    val home = System.getProperty("user.home")
    val goBin = file("$home/go/bin/toml-test")
    if (goBin.exists()) {
        return goBin.absolutePath
    }
    // Assume it is on PATH.
    return "toml-test"
}

// Resolve the runner and fail with install instructions when an explicit path
// does not exist. A bare "toml-test" is left for the OS to resolve on PATH.
fun requireTomlTest(): String {
    val tomlTest = resolveTomlTest()
    if (tomlTest.contains('/') && !file(tomlTest).exists()) {
        throw GradleException(
            "toml-test runner not found at '$tomlTest'. Install it (v2) with:\n" +
                "  go install github.com/toml-lang/toml-test/v2/cmd/toml-test@v2.2.0\n" +
                "or point at it with -PtomlTest=/path/to/toml-test (or \$TOML_TEST).",
        )
    }
    return tomlTest
}

// Shared `toml-test test` argument list. The decoder is always supplied (v2
// requires it, even for encoder runs); pass encode = true to also run the
// encoder cases.
fun testCommand(withEncoder: Boolean): List<String> {
    val launcher = harnessLauncher.get().asFile.absolutePath
    return buildList {
        add(requireTomlTest())
        add("test")
        add("-decoder"); add("$launcher decode")
        if (withEncoder) {
            add("-encoder"); add("$launcher encode")
        }
        add("-toml"); add(tomlVersion)
    }
}

val complianceDecode by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run the toml-test decoder suite against the harness."
    dependsOn(tasks.named("installDist"))
    doFirst { commandLine(testCommand(withEncoder = false)) }
}

tasks.register<Exec>("complianceTest") {
    group = "verification"
    description = "Build the harness and run the full toml-test suite (decoder + encoder)."
    dependsOn(tasks.named("installDist"))
    doFirst { commandLine(testCommand(withEncoder = true)) }
}
