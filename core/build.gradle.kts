@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import java.net.URI

// Plugins

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)

    alias(libs.plugins.detekt)

    alias(libs.plugins.maven.publish)
}

// Archives Metadata

val archivesName: String by rootProject
base.archivesName = archivesName

// Kotlin

kotlin {
    explicitApi()
    jvmToolchain(8)

    jvm()

    js {
        browser()
        nodejs()
    }

    mingwX64()
    macosArm64()
    macosX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxArm64()
    linuxX64()
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        applyDefaultHierarchyTemplate()

        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.ExperimentalSubclassOptIn")
                optIn("kotlin.contracts.ExperimentalContracts")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("kotlinx.serialization.InternalSerializationApi")
                optIn("dev.eav.tomlkt.TomlSpecific")

                compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }

        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.serialization.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.api)

                runtimeOnly(libs.junit.jupiter.engine)
            }
        }

        val kotlinxMain by creating {
            dependsOn(commonMain)

            dependencies {
                api(libs.kotlinx.datetime)
            }
        }

        val jsMain by getting {
            dependsOn(kotlinxMain)
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val mingwX64Main by getting {
            dependsOn(kotlinxMain)
        }

        val macosArm64Main by getting {
            dependsOn(kotlinxMain)
        }

        val macosX64Main by getting {
            dependsOn(kotlinxMain)
        }

        val iosX64Main by getting {
            dependsOn(kotlinxMain)
        }

        val iosArm64Main by getting {
            dependsOn(kotlinxMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(kotlinxMain)
        }

        val linuxArm64Main by getting {
            dependsOn(kotlinxMain)
        }

        val linuxX64Main by getting {
            dependsOn(kotlinxMain)
        }

        val wasmJsMain by getting {
            dependsOn(kotlinxMain)
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }
    }
}

// Linter

detekt {
    config.from(files("$rootDir/format/detekt.yml"))
}

tasks {
    withType<Detekt> {
        reports {
            sarif.required = false
            xml.required = false
            html.required = false
            md.required = false
        }
    }
}

// Tests

tasks {
    withType<KotlinJvmTest> {
        useJUnitPlatform()
    }

    check {
        dependsOn(getByName("detektMetadataMain"))
    }
}

// Documentation

val docsDir = rootDir.resolve("docs")

dokka {
    moduleName = "tomlkt"

    dokkaSourceSets {
        commonMain {
            sourceLink {
                localDirectory = file("src/commonMain/kotlin")
                remoteUrl = URI("https://github.com/eav-eav-eav/tomlkt/blob/master/src/commonMain/kotlin")
                remoteLineSuffix = "#L"
            }
        }
    }

    dokkaPublications {
        html {
            outputDirectory = docsDir
        }
    }
}

tasks {
    register<Delete>("deleteOldDocs") {
        group = "documentation"
        delete(docsDir)
    }

    register<Jar>("createJavadocByDokka") {
        group = "documentation"
        dependsOn("deleteOldDocs", "dokkaGenerate")
        archiveClassifier = "javadoc"
        from(docsDir)
    }
}

// Deployment

mavenPublishing {
    coordinates(project.group.toString(), project.rootProject.name, project.version.toString())

    val platform = KotlinMultiplatform(
        javadocJar = JavadocJar.Dokka("dokkaGenerate")
    )
    configure(platform)

    pom {
        name = "tomlkt"
        description = "TOML support for kotlinx.serialization"
        url = "https://github.com/eav-eav-eav/tomlkt"

        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        issueManagement {
            system = "Github"
            url = "https://github.com/eav-eav-eav/tomlkt/issues"
        }

        scm {
            connection = "https://github.com/eav-eav-eav/tomlkt.git"
            url = "https://github.com/eav-eav-eav/tomlkt"
        }

        developers {
            developer {
                name = "Eav"
            }
        }
    }

    publishToMavenCentral()

    signAllPublications()
}
