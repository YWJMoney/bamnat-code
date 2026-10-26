// [PERF-03] 324-5 (검토 A4): how long 밤낮 takes to show its first month, measured on a real phone, and the
// Baseline Profile made from that run instead of the hand-written one. See README.md; CI does not build it.
// [PERF-13] 1.28 AO: the baselineprofile plugin makes this the profile producer for :app, on a Gradle managed
// emulator (pixel6Api34) so it runs without a phone too (the baseline-profile workflow).
import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.bamnat.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // [PERF-13] An emulator Gradle downloads and starts itself. aosp: no Google Play image is needed, and a
    // profile is the same on either.
    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// [PERF-13] `:app:generateBaselineProfile` runs BaselineProfileGenerator on the managed emulator; with
// -Pbamnat.profileOnPhone it runs on the connected phone instead (README).
baselineProfile {
    val onPhone = project.hasProperty("bamnat.profileOnPhone")
    if (!onPhone) managedDevices += "pixel6Api34"
    useConnectedDevices = onPhone
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
