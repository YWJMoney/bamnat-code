plugins {
    id("com.android.application") version "8.11.1" apply false
    id("com.android.library") version "8.11.1" apply false
    id("com.android.test") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
    // [PERF-13] 1.28 AO: :benchmark makes the Baseline Profile and startup profile that :app takes in (benchmark/README.md).
    id("androidx.baselineprofile") version "1.3.4" apply false
}
