plugins { kotlin("multiplatform") version "2.4.0" }

kotlin {
    jvmToolchain(21)
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "OutboxSDK"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

tasks.wrapper { gradleVersion = "9.5.0" }
