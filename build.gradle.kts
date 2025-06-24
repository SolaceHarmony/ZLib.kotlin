@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform") version "2.1.20" // Updated to latest Kotlin version
}

kotlin {
    // jvm() // JVM target removed
    macosArm64 {
        binaries {
            framework {
                baseName = "ZLib" // Replace with your framework name
            }
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
        compilations.all {
            this@macosArm64.compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    linuxX64 {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
        compilations.all {
            this@linuxX64.compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    linuxArm64 {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
        compilations.all {
            this@linuxArm64.compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Configure platform-specific source sets
        @Suppress("unused")
        val macosArm64Main by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        @Suppress("unused")
        val macosArm64Test by getting {
            dependsOn(commonTest)
        }
        @Suppress("unused")
        val linuxX64Main by getting {
            dependsOn(commonMain)
        }
        @Suppress("unused")
        val linuxX64Test by getting {
            dependsOn(commonTest)
        }
        @Suppress("unused")
        val linuxArm64Main by getting {
            dependsOn(commonMain)
        }
        @Suppress("unused")
        val linuxArm64Test by getting {
            dependsOn(commonTest)
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += listOf("-Xexpect-actual-classes")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Add any dependencies your project needs here.
}
