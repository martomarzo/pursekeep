import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? =
    keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signing("storeFile", "ANDROID_KEYSTORE_FILE")
val releaseStorePassword = signing("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signing("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signing("keyPassword", "ANDROID_KEY_PASSWORD")
val hasReleaseKey = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "app.pursekeep"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pursekeep"
        minSdk = 33
        targetSdk = 35
        versionCode = versionProps.getProperty("versionCode").trim().toInt()
        versionName = versionProps.getProperty("versionName").trim()
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        } else {
            logger.warn("PurseKeep: NO RELEASE KEYSTORE configured (android/keystore.properties or ANDROID_KEYSTORE_* env). Release APK will be DEBUG-signed and cannot upgrade a release-signed install.")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

android {
    kotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.browser)
    implementation(libs.play.code.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
