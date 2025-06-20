plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.0.20"
}

repositories {
    mavenCentral()
}

kotlin {
    macosArm64 {
        binaries {
            framework {
                baseName = "ZLib"
                isStatic = true
            }

        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("src/commonMain/kotlin")
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val commonTest by getting {
            kotlin.srcDir("src/commonTest/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val macosArm64Main by getting {
            kotlin.srcDir("src/macosArm64Main/kotlin")
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-macosarm64:1.9.0")
            }
        }
    }
}

tasks.named("build") {
    dependsOn("linkDebugFrameworkMacosArm64")
}