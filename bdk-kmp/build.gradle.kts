plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ubique.uniffi)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.bcv)
}

// ---------------------------------------------------------------------------
// bdk-ffi lives in the git submodule at ../bdk-ffi, pinned to an upstream tag.
// Upstream pins UniFFI 0.31.2; the KMP bindgen needs 0.32.0. The patch in
// ../patches is the whole delta and is applied to the submodule before cargo
// runs, so the submodule stays on the canonical upstream commit.
// ---------------------------------------------------------------------------
val bdkFfiCrate = rootProject.layout.projectDirectory.dir("bdk-ffi/bdk-ffi")
val uniffiPatch = rootProject.layout.projectDirectory.file("patches/0001-uniffi-0.32-rust-1.93.patch")

val applyBdkFfiPatch = tasks.register("applyBdkFfiPatch") {
    description = "Applies the UniFFI 0.32 patch to the bdk-ffi submodule if it is not applied yet."
    val submoduleDir = rootProject.layout.projectDirectory.dir("bdk-ffi").asFile
    val cargoToml = bdkFfiCrate.file("Cargo.toml").asFile
    val patchFile = uniffiPatch.asFile
    inputs.file(patchFile)
    outputs.upToDateWhen { cargoToml.readText().contains("=0.32.0") }
    doLast {
        if (!cargoToml.readText().contains("=0.32.0")) {
            val exit = ProcessBuilder("git", "apply", patchFile.absolutePath)
                .directory(submoduleDir)
                .inheritIO()
                .start()
                .waitFor()
            check(exit == 0) { "git apply ${patchFile.name} failed with exit code $exit" }
        }
    }
}

cargo {
    packageDirectory.set(bdkFfiCrate)
    targetDirectory.set(rootProject.layout.projectDirectory.dir("cargo-build"))
}

uniffi {
    generateFromLibrary {
        packageName = "org.bitcoindevkit"
    }
}

// The Kotlin API is generated into build/ at compile time, so the committed dumps under api/
// are the only place the public surface is visible in the repository. apiCheck runs in CI.
@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    klib {
        enabled = true
    }
}

tasks.matching { it.name.startsWith("cargoBuild") || it.name == "buildLibraryForBindings" }
    .configureEach { dependsOn(applyBdkFfiPatch) }

kotlin {
    jvmToolchain(17)

    jvm()

    android {
        namespace = "org.bitcoindevkit.kmp"
        compileSdk = 36
        minSdk = 24
        // Device tests join the "test" source-set tree, so commonTest runs on the emulator too.
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Only the regtest tests talk to bitcoind's JSON-RPC; see src/commonTest/.../regtest.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
        // Native libraries for the JVM platforms this machine cannot compile are dropped here by
        // the publish workflow (linux-x86-64/, win32-x86-64/) and ride along in the jar.
        jvmMain {
            resources.srcDir(rootProject.layout.projectDirectory.dir("jvm-natives"))
        }
    }
}
