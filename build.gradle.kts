@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform") version "2.2.10"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

kotlin {
    // Standardize on Java 17 toolchain
    jvmToolchain(17)

    // Modern Kotlin compiler options (K2)
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        progressiveMode.set(true)
        allWarningsAsErrors.set(true)
        // expect/actual classes are stable in modern Kotlin; no extra flag required.
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
                implementation("com.squareup.okio:okio:3.10.2")
                implementation("co.touchlab:kermit:2.0.8")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
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
        val linuxX64Main by getting {
            dependsOn(linuxMain)
            // Redundant edge for IDE clarity
            dependsOn(commonMain)
        }

        val linuxX64Test by getting {
            dependsOn(linuxTest)
            // Redundant edge for IDE clarity
            dependsOn(commonTest)
        }

        // Configure platform-specific source sets for macOS
        val macosArm64Main by getting {
            dependsOn(nativeMain)
            // Redundant edge for IDE clarity
            dependsOn(commonMain)
        }

        val macosArm64Test by getting {
            dependsOn(nativeTest)
            // Redundant edge for IDE clarity
            dependsOn(commonTest)
        }
    }
}

dependencies {
    // Add any dependencies your project needs here.
}

// Ktlint configuration
ktlint {
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    // No excludes: cover all source sets, including tests
}

// Detekt configuration
detekt {
    toolVersion = "1.23.8"
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

// Disable running tests during normal builds by default.
// To enable tests, build with: ./gradlew build -PwithTests=true
val withTests: Boolean =
    providers
        .gradleProperty("withTests")
        .map { it.equals("true", ignoreCase = true) }
        .getOrElse(false)

// For Kotlin Multiplatform, disable only actual test execution tasks unless explicitly enabled.
if (!withTests) {
    tasks.withType<Test>().configureEach {
        enabled = false
    }
    tasks.withType<KotlinNativeTest>().configureEach {
        enabled = false
    }
}

// Friendly root `test` task for KMP: prints targets/tasks when tests are disabled,
// or depends on all available test tasks when -PwithTests=true
val kmp = extensions.findByType(KotlinMultiplatformExtension::class.java)

tasks.register("test") {
    group = "verification"
    description =
        if (withTests) {
            "Runs all tests across configured Kotlin Multiplatform targets"
        } else {
            "Displays available test targets and how to enable running tests"
        }

    if (withTests) {
        // Only depend on tasks that are present; Gradle will ignore empty collections
        dependsOn(tasks.withType<Test>())
        dependsOn(tasks.withType<KotlinNativeTest>())
    } else {
        doFirst {
            val targets: List<String> = (kmp?.targets?.map { it.name }?.sorted()) ?: emptyList()
            println("\n[ZLib.kotlin] Tests are disabled by default.")
            println("To run tests, use: ./gradlew -PwithTests=true test\n")
            if (targets.isNotEmpty()) {
                println("Configured Kotlin Multiplatform targets: ${targets.joinToString(", ")}")
            }
            val jvmTests = tasks.withType<Test>().map { it.path }.sorted()
            val nativeTests = tasks.withType<KotlinNativeTest>().map { it.path }.sorted()
            if (jvmTests.isNotEmpty() || nativeTests.isNotEmpty()) {
                println("Available test tasks (may be disabled on this host):")
                if (jvmTests.isNotEmpty()) println(" - JVM: ${jvmTests.joinToString(", ")}")
                if (nativeTests.isNotEmpty()) println(" - Native: ${nativeTests.joinToString(", ")}")
            }
            println()
        }
    }
}
