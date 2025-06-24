plugins {
    kotlin("multiplatform") version "2.0.21" // Or your desired Kotlin version
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
            kotlinOptions.freeCompilerArgs += "-Xexpect-actual-classes"
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
            kotlinOptions.freeCompilerArgs += "-Xexpect-actual-classes"
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
            kotlinOptions.freeCompilerArgs += "-Xexpect-actual-classes"
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
        val macosArm64Main by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val macosArm64Test by getting {
            dependsOn(commonTest)
        }

        val linuxX64Main by getting {
            dependsOn(commonMain)
        }
        val linuxX64Test by getting {
            dependsOn(commonTest)
        }

        val linuxArm64Main by getting {
            dependsOn(commonMain)
        }
        val linuxArm64Test by getting {
            dependsOn(commonTest)
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += listOf("-Xallocator=mimalloc", "-Xexpect-actual-classes")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Add any dependencies your project needs here.
}
