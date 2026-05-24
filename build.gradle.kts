plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // FIX: secrets plugin removed (caused build failure with Gradle 9.x)
}
