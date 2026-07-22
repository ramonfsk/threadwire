import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Produces a real combined .xcframework (not just per-target .framework bundles) at
    // build/XCFrameworks/<debug|release>/ThreadwireCore.xcframework - the standard KMP
    // Gradle task this registers is `assembleThreadwireCoreXCFramework` (plus
    // per-configuration variants). sample-app-ios's existing Run Script embed
    // (`embedAndSignAppleFrameworkForXcode`) is untouched and keeps working as before;
    // this XCFramework is specifically what `:ui-ios`'s Package.swift binaryTarget
    // references, since SPM needs a real .xcframework, not a bare .framework slice.
    val xcframework = XCFramework("ThreadwireCore")

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ThreadwireCore"
            isStatic = true
            xcframework.add(this)
        }
    }

    androidLibrary {
       namespace = "com.fsk.threadwire.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // No typed `androidHostTest` accessor is generated for this source set (unlike
        // commonMain/commonTest/androidMain/iosMain above), so it's looked up by name.
        getByName("androidHostTest").dependencies {
            // JVM-only: real embedded server for SseChatTransport tests. MockEngine
            // can't produce a proper SSESession (that adaptation is implemented by each
            // concrete client engine, not by MockEngine), so this exercises the real
            // protocol path over loopback instead - see SseChatTransportTest's KDoc.
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
    }
}