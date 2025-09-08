@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform") version "2.1.20" // Updated to latest Kotlin version
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Only include Linux x64 for now to avoid network dependency issues
    linuxX64 {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
    }

    // Restore macOS target so tests can run locally on macOS hosts
    macosArm64 {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
            // test binary is created by default
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("com.squareup.okio:okio:3.9.0")
                implementation("co.touchlab:kermit:2.0.4")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Linux source sets
        val linuxMain by creating {
            dependsOn(nativeMain)
        }
        val linuxTest by creating {
            dependsOn(nativeTest)
        }

        // Configure platform-specific source sets
        @Suppress("unused")
        val linuxX64Main by getting {
            dependsOn(linuxMain)
            // Redundant edge for IDE clarity
            dependsOn(commonMain)
        }

        @Suppress("unused")
        val linuxX64Test by getting {
            dependsOn(linuxTest)
            // Redundant edge for IDE clarity
            dependsOn(commonTest)
        }

        // Configure platform-specific source sets for macOS
        @Suppress("unused")
        val macosArm64Main by getting {
            dependsOn(nativeMain)
            // Redundant edge for IDE clarity
            dependsOn(commonMain)
        }

        @Suppress("unused")
        val macosArm64Test by getting {
            dependsOn(nativeTest)
            // Redundant edge for IDE clarity
            dependsOn(commonTest)
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Add any dependencies your project needs here.
}

// Ktlint configuration
ktlint {
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false) // Fail the build on violations
    // No excludes: cover all source sets, including tests
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt.yml"))
    source.setFrom(
        files(
            // Main
            "src/commonMain/kotlin",
            "src/nativeMain/kotlin",
            "src/linuxMain/kotlin",
            "src/macosArm64Main/kotlin",
            // Tests
            "src/commonTest/kotlin",
            "src/nativeTest/kotlin",
            "src/linuxTest/kotlin",
            "src/macosArm64Test/kotlin",
        ),
    )
    ignoreFailures = false // Fail the build on violations
}

tasks.named("check").configure {
    dependsOn("ktlintCheck", "detekt")
}
