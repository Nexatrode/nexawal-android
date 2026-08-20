@file:Suppress("UnstableApiUsage")

import java.util.Properties
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

val cmakeListsPath = file("src/main/cpp/CMakeLists.txt").absolutePath

/**
 * Build the native wallet core from source by default. This keeps local, F-Droid,
 * and Wallet Scrutiny builds on the same auditable path. A prebuilt release asset
 * is still available as an explicit opt-in for quick smoke builds.
 *
 * `NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1` remains accepted for existing recipes and
 * is now redundant. Set `NEXAWAL_USE_PREBUILT=1` to copy an extracted
 * `MoneroWalletCore-android.zip` instead.
 */
val forceSource: Boolean =
    System.getenv("NEXAWAL_BUILD_NATIVE_FROM_SOURCE")
        ?.equals("1", ignoreCase = true) == true
        || System.getenv("NEXAWAL_BUILD_NATIVE_FROM_SOURCE")
            ?.equals("true", ignoreCase = true) == true

val usePrebuilt: Boolean =
    System.getenv("NEXAWAL_USE_PREBUILT")
        ?.equals("1", ignoreCase = true) == true
        || System.getenv("NEXAWAL_USE_PREBUILT")
            ?.equals("true", ignoreCase = true) == true

require(!(forceSource && usePrebuilt)) {
    "NEXAWAL_BUILD_NATIVE_FROM_SOURCE and NEXAWAL_USE_PREBUILT cannot both be enabled"
}

val buildNativeFromSource: Boolean = !usePrebuilt

abstract class BuildMoneroWalletCoreFromSource @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val submoduleRootPath: Property<String>

    @get:Input
    abstract val androidApi: Property<String>

    @get:Input
    abstract val cargoFeatures: Property<String>

    @get:Input
    abstract val abis: Property<String>

    @get:Input
    abstract val nexawalAndroidDir: Property<String>

    @TaskAction
    fun run() {
        val submoduleRoot = project.file(submoduleRootPath.get())
        val script = submoduleRoot.resolve("Scripts/build_android.sh")
        require(script.isFile) {
            "Missing ${script.absolutePath}. Init the MoneroWalletCoreFFI submodule."
        }

        val ndkHome = System.getenv("ANDROID_NDK_HOME")
            ?: System.getenv("ANDROID_NDK_ROOT")
            ?: project.rootProject.file("local.properties").takeIf { it.exists() }?.let { propsFile ->
                Properties().apply { propsFile.inputStream().use { load(it) } }.getProperty("ndk.dir")
            }

        require(!ndkHome.isNullOrBlank()) {
            "NEXAWAL_BUILD_NATIVE_FROM_SOURCE requires ANDROID_NDK_HOME/ANDROID_NDK_ROOT or ndk.dir in local.properties"
        }

        logger.lifecycle(
            "Building libmonerowalletcore.so from source " +
                "(NDK=$ndkHome features=${cargoFeatures.get()} abis=${abis.get()})"
        )

        // Override keys only — do not replace the full process environment (PATH must remain).
        execOperations.exec {
            workingDir = submoduleRoot
            executable = "bash"
            args(script.absolutePath)
            environment("ANDROID_NDK_HOME", ndkHome)
            environment("ANDROID_API", androidApi.get())
            environment("PROFILE", "release")
            environment("CARGO_FEATURES", cargoFeatures.get())
            environment("ABIS", abis.get())
            environment("INSTALL_TO_NEXAWAL_ANDROID", "1")
            environment("NEXAWAL_ANDROID_DIR", nexawalAndroidDir.get())
            isIgnoreExitValue = false
        }
    }
}

val buildMoneroWalletCoreFromSource by tasks.registering(BuildMoneroWalletCoreFromSource::class) {
    group = "walletcore"
    description =
        "Rebuilds libmonerowalletcore.so from the MoneroWalletCoreFFI submodule (Rust + NDK)."
    onlyIf { buildNativeFromSource }
    submoduleRootPath.set(rootProject.file("MoneroWalletCoreFFI").absolutePath)
    androidApi.set(System.getenv("ANDROID_API") ?: "26")
    cargoFeatures.set(System.getenv("CARGO_FEATURES") ?: "compile-time-generators")
    abis.set(System.getenv("ABIS") ?: "arm64-v8a,x86_64")
    nexawalAndroidDir.set(rootProject.projectDir.absolutePath)
}

/**
 * Sync prebuilt libmonerowalletcore.so from the MoneroWalletCoreFFI git submodule.
 *
 * Clone with: git clone --recurse-submodules …
 * Or later:   git submodule update --init --recursive
 * Float tip:  git submodule update --remote MoneroWalletCoreFFI
 *
 * Skipped unless [usePrebuilt] is enabled — source builds install into jniLibs themselves.
 */
val syncMoneroWalletCoreSo by tasks.registering {
    group = "walletcore"
    description = "Copies prebuilt libmonerowalletcore.so from an extracted WalletCore Android release."
    onlyIf { usePrebuilt }

    val abis = listOf("arm64-v8a", "x86_64")
    val submoduleRoot = rootProject.file("MoneroWalletCoreFFI")
    val prebuiltRoot = System.getenv("NEXAWAL_PREBUILT_DIR")
        ?.takeIf { it.isNotBlank() }
        ?.let { rootProject.file(it) }
        ?: submoduleRoot.resolve(".build/artifacts/android")

    inputs.files(abis.map { prebuiltRoot.resolve("$it/libmonerowalletcore.so") })
    outputs.files(abis.map { layout.projectDirectory.file("src/main/jniLibs/$it/libmonerowalletcore.so") })

    doLast {
        require(submoduleRoot.exists()) {
            "MoneroWalletCoreFFI submodule is missing at ${submoduleRoot.absolutePath}. " +
                "Run: git submodule update --init --recursive"
        }
        abis.forEach { abi ->
            val src = prebuiltRoot.resolve("$abi/libmonerowalletcore.so")
            require(src.isFile) {
                "Missing prebuilt core library: ${src.absolutePath}. " +
                    "Extract MoneroWalletCore-android.zip and set NEXAWAL_PREBUILT_DIR, " +
                    "or run the default from-source build (./gradlew :walletcore:assembleDebug)."
            }
            val dstDir = file("src/main/jniLibs/$abi")
            if (!dstDir.exists()) dstDir.mkdirs()
            val dst = dstDir.resolve("libmonerowalletcore.so")
            src.copyTo(dst, overwrite = true)
            println("Synced libmonerowalletcore.so for $abi from submodule -> ${dst.absolutePath}")
        }
    }
}

/**
 * Copy libc++_shared.so from the configured Android NDK into this module's jniLibs so runtime dlopen can resolve it.
 *
 * Why:
 * - `libmonerowalletcore.so` depends on `libc++_shared.so`
 * - Android does not provide it globally; it must be packaged into the AAR and ultimately the APK/AAB under lib/<abi>/
 *
 * How we find the NDK:
 * - Prefer ANDROID_NDK_HOME / ANDROID_NDK_ROOT env vars
 * - Otherwise read `ndk.dir` from the project's local.properties
 */
val ensureLibcxxShared by tasks.registering {
    group = "walletcore"
    description = "Copies libc++_shared.so from the Android NDK into src/main/jniLibs for supported ABIs."

    doLast {
        val localPropsFile = rootProject.file("local.properties")
        val localProps = Properties().apply {
            if (localPropsFile.exists()) {
                localPropsFile.inputStream().use { load(it) }
            }
        }

        val ndkHome = System.getenv("ANDROID_NDK_HOME")
            ?: System.getenv("ANDROID_NDK_ROOT")
            ?: localProps.getProperty("ndk.dir")

        require(!ndkHome.isNullOrBlank()) {
            "Unable to locate Android NDK. Set ANDROID_NDK_HOME/ANDROID_NDK_ROOT or configure ndk.dir in local.properties."
        }

        val ndkDir = file(ndkHome)
        require(ndkDir.exists()) { "Android NDK dir does not exist: $ndkDir" }

        // Map Android ABI -> NDK sysroot triple
        val abiToTriple = mapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "x86_64" to "x86_64-linux-android",
        )

        val prebuiltRoot = ndkDir.resolve("toolchains/llvm/prebuilt")
        require(prebuiltRoot.exists()) { "NDK LLVM prebuilt dir not found under: ${prebuiltRoot.absolutePath}" }

        // Find host prebuilt folder (darwin-x86_64, darwin-arm64, linux-x86_64, etc.)
        val hostPrebuilt = prebuiltRoot.listFiles()?.firstOrNull { it.isDirectory }
        require(hostPrebuilt != null) {
            "Could not find NDK host prebuilt directory under: ${prebuiltRoot.absolutePath}"
        }

        val sysrootUsrLib = hostPrebuilt.resolve("sysroot/usr/lib")
        require(sysrootUsrLib.exists()) { "NDK sysroot usr lib directory not found: ${sysrootUsrLib.absolutePath}" }

        abiToTriple.forEach { (abi, triple) ->
            val src = sysrootUsrLib.resolve("$triple/libc++_shared.so")
            require(src.exists()) { "Missing libc++_shared.so for ABI=$abi at: ${src.absolutePath}" }

            val dstDir = file("src/main/jniLibs/$abi")
            if (!dstDir.exists()) dstDir.mkdirs()

            val dst = dstDir.resolve("libc++_shared.so")
            src.copyTo(dst, overwrite = true)
            println("Copied libc++_shared.so for $abi -> ${dst.absolutePath}")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildMoneroWalletCoreFromSource)
    dependsOn(syncMoneroWalletCoreSo)
    dependsOn(ensureLibcxxShared)
}

android {
    namespace = "com.nexatrode.nexawal.walletcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        // Ensure we only build/package ABIs we provide in jniLibs.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Prefer NDK flexible page-size support when available (16 KB devices).
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }

        // Native-only module: keep consumer rules file (safe even if empty/minimal).
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file(cmakeListsPath)
        }
    }

    // This module ships native libs via src/main/jniLibs. We also build a JNI shim via CMake.
    packaging {
        jniLibs {
            pickFirsts += setOf(
                "**/libmonerowalletcore.so",
                "**/libwalletcore_jni.so",
                "**/libc++_shared.so",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Not a Compose module; it provides native libs + JNI shim only.
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
}
