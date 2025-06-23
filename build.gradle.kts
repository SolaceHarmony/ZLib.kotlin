plugins {
    kotlin("multiplatform") version "2.0.21" // Or your desired Kotlin version
}

kotlin {
    // jvm() // JVM target removed
    macosArm64() {
        binaries {
            framework {
                baseName = "ZLib" // Replace with your framework name
            }
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
    }

    linuxX64() {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
        compilations.getByName("main").defaultSourceSet.dependsOn(sourceSets.getByName("commonMain"))
        compilations.getByName("test").defaultSourceSet.dependsOn(sourceSets.getByName("commonTest"))
    }

    linuxArm64() {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
        compilations.getByName("main").defaultSourceSet.dependsOn(sourceSets.getByName("commonMain"))
        compilations.getByName("test").defaultSourceSet.dependsOn(sourceSets.getByName("commonTest"))
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
        // Ensure no jvmMain or jvmTest source sets are defined
        val macosArm64Main by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        // Explicitly define macosArm64Test if not already present by convention,
        // linking it to commonTest.
        val macosArm64Test by getting {
            dependsOn(commonTest)
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += "-Xallocator=mimalloc"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Add any dependencies your project needs here.
}
