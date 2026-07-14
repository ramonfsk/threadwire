import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ThreadwireCore"
            isStatic = true
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