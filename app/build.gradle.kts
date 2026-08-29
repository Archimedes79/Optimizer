plugins {
    alias(libs.plugins.android.application)
}

/*
 * Release signing is optional.
 *
 * With RELEASE_KEYSTORE_FILE and the matching passwords present - from CI
 * secrets or a local gradle.properties - the APK is signed with the real
 * upload key. Without them the build falls back to the debug key, so the
 * artifact is still installable; it just cannot update an install that was
 * signed with a different key. See docs/RELEASING.md.
 */
val keystorePath: String? = System.getenv("RELEASE_KEYSTORE_FILE")
    ?: providers.gradleProperty("RELEASE_KEYSTORE_FILE").orNull
val keystoreFile = keystorePath?.takeIf { it.isNotBlank() }?.let { file(it) }
val hasReleaseKeystore = keystoreFile?.exists() == true

val keystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    ?: providers.gradleProperty("RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
    ?: providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
    ?: providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull

// Overridable from CI so a tagged build carries that tag's version.
val appVersionCode: Int = (System.getenv("appVersionCode")
    ?: providers.gradleProperty("appVersionCode").orNull)?.toIntOrNull() ?: 1
val appVersionName: String = System.getenv("appVersionName")
    ?: providers.gradleProperty("appVersionName").orNull ?: "1.0.0"

android {
    namespace = "de.mm.portfoliooptimizerclassic"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.mm.portfoliooptimizerclassic"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        // Lets plain JUnit tests exercise classes that call android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.gson)
    implementation(libs.commons.math3)
    implementation(libs.mpandroidchart)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
