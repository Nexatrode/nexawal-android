@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin-only module. The Kotlin plugin is already on the buildscript classpath (via the Android toolchain),
    // so we must apply it without specifying a version to avoid the "unknown version" conflict.
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    // Build with the JDK provided by the isolated builder, while retaining
    // Java 11 bytecode compatibility for consumers.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // Serialization runtime used by walletcore-api (Transfer parsing, fee preview/send/sweep JSON).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // The native runtime (jniLibs + JNI shim) is packaged by the Android library module.
    // This Kotlin-only module provides the public API surface and can be depended on by app code.
    //
    // NOTE: A Kotlin/JVM module cannot depend on an Android library (AAR) at compile time.
    // The expectation is:
    //  - app includes :walletcore (native libs)
    //  - app (or other JVM modules) includes :walletcore-api (Kotlin API)
    //
    // If you want a single dependency for consumers, we’ll need to resolve the Kotlin plugin
    // conflict in :walletcore and move this API surface into the Android library module.
}
