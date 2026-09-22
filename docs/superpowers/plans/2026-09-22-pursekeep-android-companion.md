# PurseKeep Android Companion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a signed, installable Android companion app (`app.pursekeep`) that forwards Google Wallet / bank notifications to the existing `POST /api/wallet/capture`, paired by QR from Settings › Devices, built and released by CI — plus the server changes (QR, client version, rename to PurseKeep) and a shared wire contract with tests on both sides.

**Architecture:** Native Kotlin app in `android/` with four isolated units — `CaptureExtractor` (pure), Room outbox + WorkManager flusher, `CaptureApi` (OkHttp + pure response classifier), Compose UI over a ViewModel — talking to the unchanged capture endpoint. Server side adds migration 0005 (`wallet_devices.client_version`), a QR on the Devices page, and contract fixtures shared by vitest and Kotlin unit tests.

**Tech Stack:** Kotlin 2.3.21, AGP 8.13.2, Gradle 8.14.5, JDK 17, Compose BOM 2026.09.00 + Material3, Room 2.8.5 (KSP 2.3.12), WorkManager 2.11.2, DataStore 1.2.1, OkHttp 5.5.0, kotlinx.serialization 1.11.0, play-services-code-scanner 16.1.0, JUnit 4, Robolectric 4.17. Server: Next.js 16 / Drizzle / zod 4 / vitest, `qrcode` 1.5.4.

**Spec:** `docs/superpowers/specs/2026-09-22-pursekeep-android-companion-design.md`

## Global Constraints

- Application id **`app.pursekeep`**, namespace `app.pursekeep`; never change.
- `minSdk = 33`, `targetSdk = 35`, `compileSdk = 35`; JDK 17; never `QUERY_ALL_PACKAGES`.
- Release APKs must be signed with the user's keystore `~/.pursekeep/release.jks` (alias `pursekeep`) locally and via CI secrets; debug fallback only with a loud warning.
- Wire payload for Android is exactly `{kind:"android_notification", app, title, text, postedAt}`; `title` ≤ 2000 chars, `text` ≤ 4000, `app` ≤ 200, `postedAt` ISO-8601 with offset.
- Headers on every capture request: `Authorization: Bearer <token>`, `Content-Type: application/json`, `X-PurseKeep-Client: android/<versionName>+<versionCode>`, `User-Agent: PurseKeep-Android/<versionName>`.
- QR/pairing JSON: `{"v":1,"app":"pursekeep","url":"https://…","token":"…"}`; reject `v≠1`, `app≠"pursekeep"`, non-https, empty token.
- Product name in user-facing web strings becomes **PurseKeep**; repo name, tailnet host, code identifiers, DB names unchanged.
- Git: plain commit messages, **no AI co-author trailer** (repo rule). Lockfile changes must be written by npm 10: `npx npm@10 install --package-lock-only`.
- Local toolchain: `export JAVA_HOME=~/.local/android-toolchain/jdk17 ANDROID_HOME=~/.local/android-toolchain/sdk PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH` before any Gradle command.
- CI for the app runs on `ubuntu-latest` only; the self-hosted runner is for deploys.

---

## File map

**Web (modify):** `src/app/manifest.ts`, `src/app/layout.tsx`, `src/components/app-shell.tsx`, `src/app/(auth)/layout.tsx`, `src/app/join/[code]/page.tsx`, `README.md`, `CHANGELOG.md`, `src/db/schema.ts`, `src/app/api/wallet/capture/route.ts`, `src/lib/actions/wallet.ts`, `src/components/wallet-devices-panel.tsx`, `src/app/(app)/settings/devices/page.tsx`, `src/app/(app)/settings/page.tsx`, `src/app/(app)/wallet/page.tsx`, `.github/workflows/ci.yaml`, `.github/workflows/deploy.yaml`, `.gitignore`, `package.json`.
**Web (create):** `src/db/migrations/0005_client_version.sql` (+ meta via drizzle-kit), `src/lib/wallet/client-version.ts`, `tests/wallet-contract.test.ts`, `tests/wallet-client-version.test.ts`, `docs/wallet-capture-contract.md`, `.github/workflows/android.yaml`.
**Android (create):** everything under `android/` per spec §4, plus `android/contract/fixtures/*.json`, `android/README.md`.
**Docs (replace):** `docs/wallet-android-setup.md`.

---

### Task 1: Rename the product to PurseKeep (web strings only)

**Files:**
- Modify: `src/app/manifest.ts:5-7`, `src/app/layout.tsx:16-19`, `src/components/app-shell.tsx:52`, `src/app/(auth)/layout.tsx:10`, `src/app/join/[code]/page.tsx:19`, `README.md:1-3`, `CHANGELOG.md:3`

**Interfaces:** none.

- [ ] **Step 1: Replace the strings**

```bash
cd /home/martomarzo/code/11_money-maker
sed -i 's/Money Maker/PurseKeep/g' src/components/app-shell.tsx "src/app/(auth)/layout.tsx" "src/app/join/[code]/page.tsx" src/app/layout.tsx CHANGELOG.md
sed -i '1s/.*/# PurseKeep/' README.md
sed -i '3s/.*/Personal and shared finances: track expenses, budgets, accounts and cards across currencies, alone or with a household. Live at a tailnet-only URL for now; Android companion app in `android\/`. Full design in [plan.md](plan.md)./' README.md
```

Then edit `src/app/manifest.ts` so the first three fields read:

```ts
    name: "PurseKeep",
    short_name: "PurseKeep",
    description: "Personal and shared finances",
```

- [ ] **Step 2: Verify no user-facing "Money Maker" remains and the app still builds**

Run: `grep -rn "Money Maker" src README.md CHANGELOG.md; npm run lint && npm run typecheck`
Expected: grep prints nothing; lint and typecheck pass.

- [ ] **Step 3: Commit**

```bash
git add -A src README.md CHANGELOG.md
git commit -m "Rename the product to PurseKeep in user-facing strings"
```

---

### Task 2: Release keystore + Android skeleton that builds a signed APK

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/version.properties`, `android/keystore.properties.example`, `android/local.properties` (gitignored), `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values/themes.xml`, `android/app/src/main/res/values/colors.xml`, `android/app/src/main/res/drawable/ic_launcher_foreground.xml`, `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `android/app/src/main/kotlin/app/pursekeep/MainActivity.kt`, `android/app/src/main/kotlin/app/pursekeep/ui/Theme.kt`
- Modify: `.gitignore`

**Interfaces:**
- Produces: Gradle module `:app` with `BuildConfig.VERSION_NAME` / `VERSION_CODE`; `PurseKeepTheme { }` composable; version catalog aliases used by all later tasks.

- [ ] **Step 1: Generate the keystore (once, user-owned) and the local properties**

```bash
export JAVA_HOME=~/.local/android-toolchain/jdk17 ANDROID_HOME=~/.local/android-toolchain/sdk PATH=~/.local/android-toolchain/jdk17/bin:~/.local/android-toolchain/sdk/platform-tools:$PATH
mkdir -p ~/.pursekeep && chmod 700 ~/.pursekeep
KS_PASS=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
keytool -genkeypair -v -keystore ~/.pursekeep/release.jks -alias pursekeep -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=PurseKeep, O=PurseKeep, C=AR"
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=pursekeep\nkeyPassword=%s\n' "$HOME/.pursekeep/release.jks" "$KS_PASS" "$KS_PASS" > ~/.pursekeep/keystore.properties
chmod 600 ~/.pursekeep/keystore.properties ~/.pursekeep/release.jks
mkdir -p /home/martomarzo/code/11_money-maker/android
cp ~/.pursekeep/keystore.properties /home/martomarzo/code/11_money-maker/android/keystore.properties
printf 'sdk.dir=%s\n' "$HOME/.local/android-toolchain/sdk" > /home/martomarzo/code/11_money-maker/android/local.properties
```

Tell the user: back up `~/.pursekeep/` (keystore + passwords). Losing it means every phone must uninstall/reinstall and a Play listing would need a new package.

- [ ] **Step 2: Add ignore rules**

Append to `.gitignore`:

```
# Android companion (android/)
android/.gradle/
android/build/
android/app/build/
android/local.properties
android/keystore.properties
android/*.jks
android/.kotlin/
android/.idea/
```

- [ ] **Step 3: Write the Gradle project files**

`android/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PurseKeep"
include(":app")
```

`android/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`android/gradle.properties`:
```
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`android/version.properties`:
```
versionName=0.1.0
versionCode=1
```

`android/keystore.properties.example`:
```
storeFile=/absolute/path/to/release.jks
storePassword=change-me
keyAlias=pursekeep
keyPassword=change-me
```

`android/gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.13.2"
kotlin = "2.3.21"
ksp = "2.3.12"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
coreKtx = "1.19.0"
room = "2.8.5"
work = "2.11.2"
datastore = "1.2.1"
okhttp = "5.5.0"
serialization = "1.11.0"
coroutines = "1.11.0"
browser = "1.10.0"
codeScanner = "16.1.0"
junit = "4.13.2"
robolectric = "4.17"
androidxTestCore = "1.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-browser = { group = "androidx.browser", name = "browser", version.ref = "browser" }
play-code-scanner = { group = "com.google.android.gms", name = "play-services-code-scanner", version.ref = "codeScanner" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`android/app/build.gradle.kts`:
```kotlin
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 35

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

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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
}
```

`android/app/proguard-rules.pro`: empty file with the comment `# Minification is off for now (spec §8). Add kotlinx.serialization rules before enabling R8.`

- [ ] **Step 4: Manifest, resources, theme, activity**

`android/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <queries>
        <package android:name="com.google.android.apps.walletnfcrel" />
        <package android:name="com.revolut.revolut" />
        <package android:name="com.transferwise.android" />
    </queries>

    <application
        android:name=".PurseKeepApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.PurseKeep">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".capture.NotificationCatcher"
            android:exported="false"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

(`PurseKeepApp` and `NotificationCatcher` are created in Task 7; until then, Task 2 uses a temporary minimal `PurseKeepApp` — see Step 5 — and the `<service>` element is **added in Task 7**, not now. For Task 2 omit the `<service>` block.)

`android/app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PurseKeep</string>
</resources>
```

`android/app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="brand">#0F766E</color>
    <color name="ic_launcher_background">#0F766E</color>
</resources>
```

`android/app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PurseKeep" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```

`android/app/src/main/res/drawable/ic_launcher_foreground.xml` (a simple purse: body + clasp):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF"
        android:pathData="M30,46 h48 a8,8 0 0 1 8,8 v18 a8,8 0 0 1 -8,8 h-48 a8,8 0 0 1 -8,-8 v-18 a8,8 0 0 1 8,-8 z" />
    <path android:fillColor="#FFFFFF"
        android:pathData="M38,46 v-6 a16,16 0 0 1 32,0 v6 h-6 v-6 a10,10 0 0 0 -20,0 v6 z" />
    <path android:fillColor="#0F766E"
        android:pathData="M54,56 a5,5 0 1 1 -0.01,0 z" />
</vector>
```

`android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`android/app/src/main/kotlin/app/pursekeep/ui/Theme.kt`:
```kotlin
package app.pursekeep.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0F766E)
private val TealLight = Color(0xFF5EEAD4)

@Composable
fun PurseKeepTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = TealLight) else lightColorScheme(primary = Teal)
    MaterialTheme(colorScheme = scheme, content = content)
}
```

`android/app/src/main/kotlin/app/pursekeep/MainActivity.kt` (Task 2 version; replaced in Task 8):
```kotlin
package app.pursekeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import app.pursekeep.ui.PurseKeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PurseKeepTheme {
                Scaffold { padding ->
                    Text("PurseKeep ${BuildConfig.VERSION_NAME}", modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
```

- [ ] **Step 5: Temporary Application class (replaced in Task 7)**

`android/app/src/main/kotlin/app/pursekeep/PurseKeepApp.kt`:
```kotlin
package app.pursekeep

import android.app.Application

class PurseKeepApp : Application()
```

- [ ] **Step 6: Generate the Gradle wrapper and build**

```bash
cd /home/martomarzo/code/11_money-maker/android
export JAVA_HOME=~/.local/android-toolchain/jdk17 ANDROID_HOME=~/.local/android-toolchain/sdk PATH=~/.local/android-toolchain/jdk17/bin:~/.local/android-toolchain/sdk/platform-tools:$PATH
# one-off: obtain a gradle distribution to create the wrapper
curl -sSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.14.5-bin.zip && mkdir -p ~/.local/android-toolchain/gradle && unzip -q -o /tmp/gradle.zip -d ~/.local/android-toolchain/gradle
~/.local/android-toolchain/gradle/gradle-8.14.5/bin/gradle wrapper --gradle-version 8.14.5 --distribution-type bin
./gradlew --no-daemon :app:assembleRelease
```
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/release/app-release.apk`; no "NO RELEASE KEYSTORE" warning.

- [ ] **Step 7: Verify the signature is the release key**

Run: `$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | head -3`
Expected: a line containing `CN=PurseKeep`.

- [ ] **Step 8: Commit** (wrapper jar included; keystore/local properties excluded by .gitignore)

```bash
cd /home/martomarzo/code/11_money-maker
git status --short | grep -E "keystore.properties$|local.properties$|\.jks$" && echo "STOP: secret file staged" || true
git add .gitignore android
git commit -m "Add Android companion skeleton (app.pursekeep) with release signing"
```

---

### Task 3: Wire model, extractor and dedupe hash

**Files:**
- Create: `android/app/src/main/kotlin/app/pursekeep/capture/AndroidCapture.kt`, `android/app/src/main/kotlin/app/pursekeep/capture/KnownApps.kt`, `android/app/src/main/kotlin/app/pursekeep/capture/CaptureExtractor.kt`
- Test: `android/app/src/test/kotlin/app/pursekeep/capture/CaptureExtractorTest.kt`

**Interfaces:**
- Produces: `data class AndroidCapture(kind="android_notification", app, title, text, postedAt)` (kotlinx `@Serializable`); `data class NotificationInput(packageName, postTimeMillis, title?, text?, bigText?, isGroupSummary, isOngoing)`; `CaptureExtractor.extract(input, enabledPackages, zone): AndroidCapture?`; `CaptureExtractor.hash(capture): String` (sha256 hex); `CaptureExtractor.nowIso(zone)`; `KnownApps.GOOGLE_WALLET`, `KnownApps.all`, `KnownApps.defaultEnabled`.

- [ ] **Step 1: Write the failing tests**

`android/app/src/test/kotlin/app/pursekeep/capture/CaptureExtractorTest.kt`:
```kotlin
package app.pursekeep.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CaptureExtractorTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val enabled = setOf(KnownApps.GOOGLE_WALLET)
    private fun input(
        pkg: String = KnownApps.GOOGLE_WALLET,
        title: String? = "€12,40 with Visa ••1234",
        text: String? = "MERCADONA",
        bigText: String? = null,
        summary: Boolean = false,
        ongoing: Boolean = false,
    ) = NotificationInput(pkg, 1_790_000_000_000L, title, text, bigText, summary, ongoing)

    @Test fun `extracts wallet notification with offset timestamp`() {
        val c = CaptureExtractor.extract(input(), enabled, zone)!!
        assertEquals("android_notification", c.kind)
        assertEquals(KnownApps.GOOGLE_WALLET, c.app)
        assertEquals("€12,40 with Visa ••1234", c.title)
        assertEquals("MERCADONA", c.text)
        assertEquals("2026-09-21T15:33:20+02:00", c.postedAt)
    }

    @Test fun `skips disabled package`() = assertNull(CaptureExtractor.extract(input(pkg = "com.example"), enabled, zone))
    @Test fun `skips group summary`() = assertNull(CaptureExtractor.extract(input(summary = true), enabled, zone))
    @Test fun `skips ongoing`() = assertNull(CaptureExtractor.extract(input(ongoing = true), enabled, zone))
    @Test fun `skips blank`() = assertNull(CaptureExtractor.extract(input(title = " ", text = null), enabled, zone))

    @Test fun `prefers bigText over text`() {
        val c = CaptureExtractor.extract(input(text = "short", bigText = "the long version"), enabled, zone)!!
        assertEquals("the long version", c.text)
    }

    @Test fun `truncates to contract limits`() {
        val c = CaptureExtractor.extract(input(title = "t".repeat(3000), text = "x".repeat(5000)), enabled, zone)!!
        assertEquals(2000, c.title.length)
        assertEquals(4000, c.text.length)
    }

    @Test fun `hash is stable and sensitive to text`() {
        val a = CaptureExtractor.extract(input(), enabled, zone)!!
        val b = CaptureExtractor.extract(input(text = "LIDL"), enabled, zone)!!
        assertEquals(64, CaptureExtractor.hash(a).length)
        assertEquals(CaptureExtractor.hash(a), CaptureExtractor.hash(a.copy()))
        assert(CaptureExtractor.hash(a) != CaptureExtractor.hash(b))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run (from `android/`, env exported as in Global Constraints): `./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.pursekeep.capture.*'`
Expected: compilation error, `NotificationInput`/`CaptureExtractor` unresolved.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/app/pursekeep/capture/AndroidCapture.kt`:
```kotlin
package app.pursekeep.capture

import kotlinx.serialization.Serializable

/** Wire payload for POST /api/wallet/capture — see docs/wallet-capture-contract.md (v1). */
@Serializable
data class AndroidCapture(
    val kind: String = "android_notification",
    val app: String,
    val title: String,
    val text: String,
    val postedAt: String,
)
```

`android/app/src/main/kotlin/app/pursekeep/capture/KnownApps.kt`:
```kotlin
package app.pursekeep.capture

data class KnownApp(val packageName: String, val label: String)

object KnownApps {
    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    val all: List<KnownApp> = listOf(
        KnownApp(GOOGLE_WALLET, "Google Wallet"),
        KnownApp("com.revolut.revolut", "Revolut"),
        KnownApp("com.transferwise.android", "Wise"),
    )
    val defaultEnabled: Set<String> = setOf(GOOGLE_WALLET)
}
```

`android/app/src/main/kotlin/app/pursekeep/capture/CaptureExtractor.kt`:
```kotlin
package app.pursekeep.capture

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NotificationInput(
    val packageName: String,
    val postTimeMillis: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
)

object CaptureExtractor {
    const val MAX_TITLE = 2000
    const val MAX_TEXT = 4000
    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    fun extract(input: NotificationInput, enabledPackages: Set<String>, zone: ZoneId = ZoneId.systemDefault()): AndroidCapture? {
        if (input.packageName !in enabledPackages) return null
        if (input.isGroupSummary || input.isOngoing) return null
        val title = input.title.orEmpty().trim().take(MAX_TITLE)
        val body = input.bigText?.takeIf { it.isNotBlank() } ?: input.text.orEmpty()
        val text = body.trim().take(MAX_TEXT)
        if (title.isBlank() && text.isBlank()) return null
        return AndroidCapture(
            app = input.packageName.take(200),
            title = title,
            text = text,
            postedAt = isoAt(input.postTimeMillis, zone),
        )
    }

    fun isoAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(ISO)

    fun nowIso(zone: ZoneId = ZoneId.systemDefault()): String = isoAt(System.currentTimeMillis(), zone)

    fun hash(c: AndroidCapture): String = sha256("${c.app}|${c.postedAt}|${c.title}|${c.text}")

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.pursekeep.capture.*'`
Expected: `BUILD SUCCESSFUL`, 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "Android: capture wire model, notification extractor and dedupe hash"
```

---

### Task 4: Pairing payload parser

**Files:**
- Create: `android/app/src/main/kotlin/app/pursekeep/pairing/PairingPayload.kt`
- Test: `android/app/src/test/kotlin/app/pursekeep/pairing/PairingParserTest.kt`

**Interfaces:**
- Produces: `@Serializable data class PairingPayload(v: Int, app: String, url: String, token: String)`; `PairingParser.parse(raw: String): Result<PairingPayload>`; `PairingParser.manual(url: String, token: String): Result<PairingPayload>`.

- [ ] **Step 1: Failing tests**

```kotlin
package app.pursekeep.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingParserTest {
    private val ok = """{"v":1,"app":"pursekeep","url":"https://money-maker.peacock-snapper.ts.net/","token":"abc123"}"""

    @Test fun `parses valid payload and trims trailing slash`() {
        val p = PairingParser.parse(ok).getOrThrow()
        assertEquals("https://money-maker.peacock-snapper.ts.net", p.url)
        assertEquals("abc123", p.token)
    }
    @Test fun `rejects wrong version`() =
        assertTrue(PairingParser.parse(ok.replace("\"v\":1", "\"v\":2")).exceptionOrNull()!!.message!!.contains("version"))
    @Test fun `rejects other app`() =
        assertTrue(PairingParser.parse(ok.replace("pursekeep", "other")).isFailure)
    @Test fun `rejects http`() =
        assertTrue(PairingParser.parse(ok.replace("https://", "http://")).exceptionOrNull()!!.message!!.contains("https"))
    @Test fun `rejects empty token`() =
        assertTrue(PairingParser.parse(ok.replace("abc123", " ")).isFailure)
    @Test fun `rejects garbage`() = assertTrue(PairingParser.parse("hello").isFailure)
    @Test fun `manual entry validates the same way`() {
        assertEquals("https://x.example", PairingParser.manual(" https://x.example/ ", "t").getOrThrow().url)
        assertTrue(PairingParser.manual("x.example", "t").isFailure)
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.pursekeep.pairing.*'` → unresolved reference.

- [ ] **Step 3: Implement**

```kotlin
package app.pursekeep.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PairingPayload(val v: Int, val app: String, val url: String, val token: String)

object PairingParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): Result<PairingPayload> = runCatching {
        val p = try { json.decodeFromString<PairingPayload>(raw) } catch (e: Exception) {
            throw IllegalArgumentException("Not a PurseKeep pairing code")
        }
        require(p.v == 1) { "Unsupported pairing version ${p.v} — update the app" }
        require(p.app == "pursekeep") { "Not a PurseKeep pairing code" }
        validated(p.url, p.token)
    }

    fun manual(url: String, token: String): Result<PairingPayload> = runCatching { validated(url, token) }

    private fun validated(url: String, token: String): PairingPayload {
        val u = url.trim().trimEnd('/')
        require(u.startsWith("https://") && u.length > "https://".length) { "Server URL must start with https://" }
        require(token.isNotBlank()) { "Missing token" }
        return PairingPayload(1, "pursekeep", u, token.trim())
    }
}
```

- [ ] **Step 4: Run tests** → 7 pass.
- [ ] **Step 5: Commit** — `git add android/app/src && git commit -m "Android: pairing payload parser"`

---

### Task 5: Response classifier, CaptureApi, update version compare

**Files:**
- Create: `android/app/src/main/kotlin/app/pursekeep/net/SendResult.kt`, `android/app/src/main/kotlin/app/pursekeep/net/CaptureApi.kt`, `android/app/src/main/kotlin/app/pursekeep/net/UpdateChecker.kt`
- Test: `android/app/src/test/kotlin/app/pursekeep/net/ResponseClassifierTest.kt`, `android/app/src/test/kotlin/app/pursekeep/net/VersionCompareTest.kt`

**Interfaces:**
- Produces: `sealed class SendResult { Sent(serverStatus), Unauthorized, Rejected(error), Retry(reason) }`; `ResponseClassifier.classify(code: Int, body: String): SendResult`; `class CaptureApi(clientVersionName: String, clientVersionCode: Int, client: OkHttpClient)` with `fun send(serverUrl: String, token: String, payloadJson: String): SendResult` (blocking; call on IO); `UpdateChecker.isNewer(tag: String, current: String): Boolean`; `class UpdateChecker(client)` with `fun fetchLatestTag(): String?` (blocking).

- [ ] **Step 1: Failing tests**

`ResponseClassifierTest.kt`:
```kotlin
package app.pursekeep.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseClassifierTest {
    @Test fun `201 booked`() = assertEquals(SendResult.Sent("booked"), ResponseClassifier.classify(201, """{"id":"x","status":"booked"}"""))
    @Test fun `201 needs_account`() = assertEquals(SendResult.Sent("needs_account"), ResponseClassifier.classify(201, """{"id":"x","status":"needs_account"}"""))
    @Test fun `200 duplicate`() = assertEquals(SendResult.Sent("duplicate"), ResponseClassifier.classify(200, """{"duplicate":true}"""))
    @Test fun `401`() = assertEquals(SendResult.Unauthorized, ResponseClassifier.classify(401, """{"error":"unauthorized"}"""))
    @Test fun `400 never retried`() = assertEquals(SendResult.Rejected("empty body"), ResponseClassifier.classify(400, """{"error":"empty body"}"""))
    @Test fun `500 retries`() = assertTrue(ResponseClassifier.classify(500, "boom") is SendResult.Retry)
    @Test fun `unparseable success body still counts as sent`() = assertEquals(SendResult.Sent("ok"), ResponseClassifier.classify(201, "<html>"))
}
```

`VersionCompareTest.kt`:
```kotlin
package app.pursekeep.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newer() = assertTrue(UpdateChecker.isNewer("android-v0.2.0", "0.1.0"))
    @Test fun patch() = assertTrue(UpdateChecker.isNewer("android-v0.1.1", "0.1.0"))
    @Test fun same() = assertFalse(UpdateChecker.isNewer("android-v0.1.0", "0.1.0"))
    @Test fun older() = assertFalse(UpdateChecker.isNewer("android-v0.0.9", "0.1.0"))
    @Test fun otherTag() = assertFalse(UpdateChecker.isNewer("v1.0.0", "0.1.0"))
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.pursekeep.net.*'` → unresolved.

- [ ] **Step 3: Implement**

`SendResult.kt`:
```kotlin
package app.pursekeep.net

sealed class SendResult {
    /** 2xx: serverStatus is booked | needs_account | unparsed | duplicate | ok */
    data class Sent(val serverStatus: String) : SendResult()
    /** 401: token revoked or unknown — stop and mark unpaired */
    data object Unauthorized : SendResult()
    /** 4xx other than 401: permanent, never retried */
    data class Rejected(val error: String) : SendResult()
    /** 5xx / network: keep pending, retry with backoff */
    data class Retry(val reason: String) : SendResult()
}
```

`CaptureApi.kt`:
```kotlin
package app.pursekeep.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ResponseClassifier {
    fun classify(code: Int, body: String): SendResult = when {
        code == 200 || code == 201 -> SendResult.Sent(parseStatus(body))
        code == 401 -> SendResult.Unauthorized
        code in 400..499 -> SendResult.Rejected(parseError(body) ?: "HTTP $code")
        else -> SendResult.Retry("HTTP $code")
    }

    private fun parseStatus(body: String): String = runCatching {
        val obj = Json.parseToJsonElement(body).jsonObject
        if (obj["duplicate"]?.jsonPrimitive?.booleanOrNull == true) "duplicate"
        else obj["status"]?.jsonPrimitive?.contentOrNull ?: "ok"
    }.getOrDefault("ok")

    private fun parseError(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}

class CaptureApi(
    private val clientVersionName: String,
    private val clientVersionCode: Int,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Blocking; call from Dispatchers.IO. */
    fun send(serverUrl: String, token: String, payloadJson: String): SendResult {
        val request = Request.Builder()
            .url(serverUrl.trimEnd('/') + "/api/wallet/capture")
            .header("Authorization", "Bearer $token")
            .header("X-PurseKeep-Client", "android/$clientVersionName+$clientVersionCode")
            .header("User-Agent", "PurseKeep-Android/$clientVersionName")
            .post(payloadJson.toRequestBody(jsonType))
            .build()
        return try {
            client.newCall(request).execute().use { r -> ResponseClassifier.classify(r.code, r.body.string()) }
        } catch (e: IOException) {
            SendResult.Retry(e.message ?: e.javaClass.simpleName)
        }
    }
}
```

`UpdateChecker.kt`:
```kotlin
package app.pursekeep.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    /** Blocking. Returns the latest release tag (e.g. "android-v0.2.0") or null on any failure. */
    fun fetchLatestTag(): String? = runCatching {
        val req = Request.Builder().url(LATEST_URL).header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            Json.parseToJsonElement(r.body.string()).jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/martomarzo/money-maker/releases/latest"
        const val RELEASES_PAGE = "https://github.com/martomarzo/money-maker/releases/latest"
        private const val PREFIX = "android-v"

        fun isNewer(tag: String, current: String): Boolean {
            if (!tag.startsWith(PREFIX)) return false
            val a = parse(tag.removePrefix(PREFIX)) ?: return false
            val b = parse(current) ?: return false
            for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
            return false
        }

        private fun parse(v: String): List<Int>? =
            v.split(".").map { it.toIntOrNull() ?: return null }.takeIf { it.size == 3 }
    }
}
```

- [ ] **Step 4: Run tests** → 12 pass.
- [ ] **Step 5: Commit** — `git add android/app/src && git commit -m "Android: capture API client, response classification, release version compare"`

---

### Task 6: Room outbox + event log

**Files:**
- Create: `android/app/src/main/kotlin/app/pursekeep/outbox/OutboxEntry.kt`, `.../outbox/EventEntry.kt`, `.../outbox/OutboxDao.kt`, `.../outbox/EventDao.kt`, `.../outbox/OutboxDb.kt`
- Test: `android/app/src/test/kotlin/app/pursekeep/outbox/OutboxDaoTest.kt`

**Interfaces:**
- Produces: `OutboxEntry(id, payloadJson, hash, createdAt, attempts, status, lastError, serverStatus)` with `OutboxEntry.PENDING/SENT/FAILED`; `OutboxDao.insert(entry): Long` (−1 on duplicate hash), `pending(): List<OutboxEntry>`, `pendingCount(): Flow<Int>`, `update(entry)`, `pruneSent(before: Long)`; `EventEntry(id, at, kind, message)`; `EventDao.insert`, `latest(): Flow<List<EventEntry>>`, `trim()`; `OutboxDb.build(context)`.

- [ ] **Step 1: Failing test (Robolectric, in-memory Room)**

```kotlin
package app.pursekeep.outbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboxDaoTest {
    private lateinit var db: OutboxDb

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OutboxDb::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun entry(hash: String, at: Long) = OutboxEntry(payloadJson = "{}", hash = hash, createdAt = at)

    @Test fun `duplicate hash is ignored`() = runTest {
        assertEquals(1L, db.outbox().insert(entry("h1", 10)))
        assertEquals(-1L, db.outbox().insert(entry("h1", 11)))
        assertEquals(1, db.outbox().pendingCount().first())
    }

    @Test fun `pending is oldest first and excludes sent`() = runTest {
        db.outbox().insert(entry("b", 20)); db.outbox().insert(entry("a", 10)); db.outbox().insert(entry("c", 30))
        val c = db.outbox().pending().first { it.hash == "c" }
        db.outbox().update(c.copy(status = OutboxEntry.SENT))
        assertEquals(listOf("a", "b"), db.outbox().pending().map { it.hash })
    }

    @Test fun `event log keeps newest 50`() = runTest {
        repeat(60) { db.events().insert(EventEntry(at = it.toLong(), kind = "k", message = "m$it")) }
        db.events().trim()
        val latest = db.events().latest().first()
        assertEquals(50, latest.size)
        assertEquals("m59", latest.first().message)
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.pursekeep.outbox.*'` → unresolved.

- [ ] **Step 3: Implement**

`OutboxEntry.kt`:
```kotlin
package app.pursekeep.outbox

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "outbox", indices = [Index(value = ["hash"], unique = true)])
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val hash: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val status: String = PENDING,
    val lastError: String? = null,
    val serverStatus: String? = null,
) {
    companion object {
        const val PENDING = "pending"
        const val SENT = "sent"
        const val FAILED = "failed"
    }
}
```

`EventEntry.kt`:
```kotlin
package app.pursekeep.outbox

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,     // captured | sent | retry | rejected | unpaired | skip | info
    val message: String,
)
```

`OutboxDao.kt`:
```kotlin
package app.pursekeep.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: OutboxEntry): Long

    @Query("SELECT * FROM outbox WHERE status = 'pending' ORDER BY createdAt ASC, id ASC")
    suspend fun pending(): List<OutboxEntry>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'pending'")
    fun pendingCount(): Flow<Int>

    @Update
    suspend fun update(entry: OutboxEntry)

    @Query("DELETE FROM outbox WHERE status = 'sent' AND createdAt < :before")
    suspend fun pruneSent(before: Long)
}
```

`EventDao.kt`:
```kotlin
package app.pursekeep.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert suspend fun insert(event: EventEntry)

    @Query("SELECT * FROM events ORDER BY at DESC, id DESC LIMIT 50")
    fun latest(): Flow<List<EventEntry>>

    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY at DESC, id DESC LIMIT 50)")
    suspend fun trim()
}
```

`OutboxDb.kt`:
```kotlin
package app.pursekeep.outbox

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OutboxEntry::class, EventEntry::class], version = 1, exportSchema = false)
abstract class OutboxDb : RoomDatabase() {
    abstract fun outbox(): OutboxDao
    abstract fun events(): EventDao

    companion object {
        fun build(context: Context): OutboxDb =
            Room.databaseBuilder(context.applicationContext, OutboxDb::class.java, "pursekeep.db").build()
    }
}
```

- [ ] **Step 4: Run tests** → 3 pass (first run downloads the Robolectric android-all jar; allow a few minutes).
- [ ] **Step 5: Commit** — `git add android/app/src && git commit -m "Android: Room outbox and event log"`

---

### Task 7: Settings, repository, flush worker, notification listener, Application

**Files:**
- Create: `android/app/src/main/kotlin/app/pursekeep/settings/Settings.kt`, `.../outbox/CaptureRepository.kt`, `.../outbox/OutboxFlushWorker.kt`, `.../capture/NotificationCatcher.kt`, `.../Notifications.kt`
- Modify: `android/app/src/main/kotlin/app/pursekeep/PurseKeepApp.kt`, `android/app/src/main/AndroidManifest.xml` (add the `<service>` block from Task 2 Step 4)

**Interfaces:**
- Consumes: Tasks 3–6.
- Produces: `data class Pairing(serverUrl, token, deviceName)`; `Settings(context)` with flows `pairing: Flow<Pairing?>`, `pairingBroken: Flow<Boolean>`, `serverUrl: Flow<String?>`, `enabledPackages: Flow<Set<String>>`, `customPackages: Flow<Set<String>>`, `lastSentAt: Flow<Long?>`, `latestKnownTag: Flow<String?>` and suspend fns `pair(url, token, name)`, `unpair()`, `markUnpaired()`, `setPackageEnabled(pkg, on)`, `addCustomPackage(pkg)`, `removeCustomPackage(pkg)`, `setLastSentAt(ms)`, `recordUpdateCheck(tag, atMs)`, `lastUpdateCheckAt(): Long`; `CaptureRepository.onNotification(input)`, `enqueue(capture, source)`, `sendTest()`, `log(kind, message)`, `testCapture(postedAt): AndroidCapture`; `OutboxFlushWorker.enqueue(context)`; `Notifications.ensureChannel(ctx)`, `Notifications.showUnpaired(ctx)`; `PurseKeepApp.db/settings/api/captures/updates`.

- [ ] **Step 1: Settings (DataStore)**

```kotlin
package app.pursekeep.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pursekeep.capture.KnownApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class Pairing(val serverUrl: String, val token: String, val deviceName: String)

class Settings(private val context: Context) {
    private object K {
        val serverUrl = stringPreferencesKey("serverUrl")
        val token = stringPreferencesKey("token")
        val deviceName = stringPreferencesKey("deviceName")
        val paired = booleanPreferencesKey("paired")
        val broken = booleanPreferencesKey("pairingBroken")
        val enabled = stringSetPreferencesKey("enabledPackages")
        val custom = stringSetPreferencesKey("customPackages")
        val lastSentAt = longPreferencesKey("lastSentAt")
        val lastUpdateCheck = longPreferencesKey("lastUpdateCheck")
        val latestTag = stringPreferencesKey("latestTag")
    }

    private val data get() = context.store.data

    val pairing: Flow<Pairing?> = data.map { p ->
        val url = p[K.serverUrl]; val tok = p[K.token]
        if (url != null && tok != null && p[K.paired] == true) Pairing(url, tok, p[K.deviceName].orEmpty()) else null
    }
    val pairingBroken: Flow<Boolean> = data.map { it[K.broken] == true }
    val serverUrl: Flow<String?> = data.map { it[K.serverUrl] }
    val enabledPackages: Flow<Set<String>> = data.map { it[K.enabled] ?: KnownApps.defaultEnabled }
    val customPackages: Flow<Set<String>> = data.map { it[K.custom] ?: emptySet() }
    val lastSentAt: Flow<Long?> = data.map { it[K.lastSentAt] }
    val latestKnownTag: Flow<String?> = data.map { it[K.latestTag] }

    suspend fun pair(url: String, token: String, name: String) = context.store.edit {
        it[K.serverUrl] = url; it[K.token] = token; it[K.deviceName] = name; it[K.paired] = true; it[K.broken] = false
    }
    suspend fun unpair() = context.store.edit { it.remove(K.token); it[K.paired] = false; it[K.broken] = false }
    /** Server said 401: keep URL/name for display, drop the token. */
    suspend fun markUnpaired() = context.store.edit { it.remove(K.token); it[K.paired] = false; it[K.broken] = true }

    suspend fun setPackageEnabled(pkg: String, on: Boolean) = context.store.edit {
        val cur = it[K.enabled] ?: KnownApps.defaultEnabled
        it[K.enabled] = if (on) cur + pkg else cur - pkg
    }
    suspend fun addCustomPackage(pkg: String) = context.store.edit {
        it[K.custom] = (it[K.custom] ?: emptySet()) + pkg
        it[K.enabled] = (it[K.enabled] ?: KnownApps.defaultEnabled) + pkg
    }
    suspend fun removeCustomPackage(pkg: String) = context.store.edit {
        it[K.custom] = (it[K.custom] ?: emptySet()) - pkg
        it[K.enabled] = (it[K.enabled] ?: KnownApps.defaultEnabled) - pkg
    }
    suspend fun setLastSentAt(ms: Long) = context.store.edit { it[K.lastSentAt] = ms }
    suspend fun recordUpdateCheck(tag: String?, atMs: Long) = context.store.edit {
        it[K.lastUpdateCheck] = atMs; if (tag != null) it[K.latestTag] = tag
    }
    suspend fun lastUpdateCheckAt(): Long = data.first()[K.lastUpdateCheck] ?: 0L
}
```

- [ ] **Step 2: Notifications helper**

`Notifications.kt`:
```kotlin
package app.pursekeep

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifications {
    const val CHANNEL = "status"
    private const val ID_UNPAIRED = 1

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "PurseKeep status", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun showUnpaired(ctx: Context) {
        if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("PurseKeep is no longer paired")
            .setContentText("The server rejected this phone's token. Open the app to pair again.")
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(ID_UNPAIRED, n)
    }
}
```

- [ ] **Step 3: Repository**

`CaptureRepository.kt`:
```kotlin
package app.pursekeep.outbox

import android.content.Context
import app.pursekeep.capture.AndroidCapture
import app.pursekeep.capture.CaptureExtractor
import app.pursekeep.capture.KnownApps
import app.pursekeep.capture.NotificationInput
import app.pursekeep.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class CaptureRepository(private val context: Context, private val db: OutboxDb, private val settings: Settings) {
    private val json = Json { encodeDefaults = true }

    suspend fun onNotification(input: NotificationInput) {
        val enabled = settings.enabledPackages.first()
        val capture = CaptureExtractor.extract(input, enabled) ?: return
        enqueue(capture, source = "notification")
    }

    suspend fun enqueue(capture: AndroidCapture, source: String) {
        val entry = OutboxEntry(
            payloadJson = json.encodeToString(AndroidCapture.serializer(), capture),
            hash = CaptureExtractor.hash(capture),
            createdAt = System.currentTimeMillis(),
        )
        val id = db.outbox().insert(entry)
        if (id == -1L) { log("skip", "Duplicate ignored: ${capture.title}"); return }
        log("captured", "[$source] ${capture.title} — ${capture.text.take(60)}")
        OutboxFlushWorker.enqueue(context)
    }

    suspend fun sendTest() = enqueue(testCapture(CaptureExtractor.nowIso()), source = "test")

    suspend fun log(kind: String, message: String) {
        db.events().insert(EventEntry(at = System.currentTimeMillis(), kind = kind, message = message))
        db.events().trim()
    }

    companion object {
        /** Must stay byte-for-byte equal to android/contract/fixtures/android-wallet-test.json (minus postedAt). */
        fun testCapture(postedAt: String) = AndroidCapture(
            app = KnownApps.GOOGLE_WALLET,
            title = "€0.01 with Visa ••0000",
            text = "PurseKeep test",
            postedAt = postedAt,
        )
    }
}
```

- [ ] **Step 4: Worker**

`OutboxFlushWorker.kt`:
```kotlin
package app.pursekeep.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pursekeep.Notifications
import app.pursekeep.PurseKeepApp
import app.pursekeep.net.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OutboxFlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PurseKeepApp
        val pairing = app.settings.pairing.first() ?: return@withContext Result.success()
        val dao = app.db.outbox()
        val now = System.currentTimeMillis()
        var retry = false
        for (entry in dao.pending()) {
            if (now - entry.createdAt > MAX_AGE_MS) {
                dao.update(entry.copy(status = OutboxEntry.FAILED, lastError = "expired"))
                app.captures.log("rejected", "Expired after 30 days: ${entry.hash.take(8)}")
                continue
            }
            when (val r = app.api.send(pairing.serverUrl, pairing.token, entry.payloadJson)) {
                is SendResult.Sent -> {
                    dao.update(entry.copy(status = OutboxEntry.SENT, serverStatus = r.serverStatus, attempts = entry.attempts + 1, lastError = null))
                    app.settings.setLastSentAt(System.currentTimeMillis())
                    app.captures.log("sent", "→ ${r.serverStatus}")
                }
                SendResult.Unauthorized -> {
                    app.settings.markUnpaired()
                    Notifications.showUnpaired(applicationContext)
                    app.captures.log("unpaired", "Server rejected the token (401)")
                    return@withContext Result.success()
                }
                is SendResult.Rejected -> {
                    dao.update(entry.copy(status = OutboxEntry.FAILED, lastError = r.error, attempts = entry.attempts + 1))
                    app.captures.log("rejected", r.error)
                }
                is SendResult.Retry -> {
                    dao.update(entry.copy(attempts = entry.attempts + 1, lastError = r.reason))
                    app.captures.log("retry", r.reason)
                    retry = true
                    break
                }
            }
        }
        dao.pruneSent(before = now - MAX_AGE_MS)
        if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val NAME = "flush-outbox"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // APPEND_OR_REPLACE: if a flush is running, run another pass after it (a row may have
            // been inserted after the running pass read its list).
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
```

- [ ] **Step 5: Listener service**

`NotificationCatcher.kt`:
```kotlin
package app.pursekeep.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.pursekeep.PurseKeepApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCatcher : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras
        val input = NotificationInput(
            packageName = sbn.packageName,
            postTimeMillis = sbn.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
        val app = applicationContext as PurseKeepApp
        scope.launch { app.captures.onNotification(input) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 6: Application wiring**

Replace `PurseKeepApp.kt`:
```kotlin
package app.pursekeep

import android.app.Application
import app.pursekeep.net.CaptureApi
import app.pursekeep.net.UpdateChecker
import app.pursekeep.outbox.CaptureRepository
import app.pursekeep.outbox.OutboxDb
import app.pursekeep.settings.Settings

class PurseKeepApp : Application() {
    lateinit var db: OutboxDb; private set
    lateinit var settings: Settings; private set
    lateinit var api: CaptureApi; private set
    lateinit var captures: CaptureRepository; private set
    lateinit var updates: UpdateChecker; private set

    override fun onCreate() {
        super.onCreate()
        db = OutboxDb.build(this)
        settings = Settings(this)
        api = CaptureApi(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        captures = CaptureRepository(this, db, settings)
        updates = UpdateChecker()
        Notifications.ensureChannel(this)
    }
}
```

Add the `<service>` element (Task 2 Step 4 listing) inside `<application>` in the manifest.

- [ ] **Step 7: Build and run all unit tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests from Tasks 3–6 still pass.

- [ ] **Step 8: Commit** — `git add android/app/src && git commit -m "Android: settings store, outbox flush worker, notification listener"`

---

### Task 8: UI (ViewModel, Home / Pairing / Apps screens) → first installable APK

**Files:**
- Create: `android/app/src/main/kotlin/app/pursekeep/ui/MainViewModel.kt`, `.../ui/HomeScreen.kt`, `.../ui/PairingScreen.kt`, `.../ui/AppsScreen.kt`, `.../ui/SystemActions.kt`
- Modify: `android/app/src/main/kotlin/app/pursekeep/MainActivity.kt`

**Interfaces:**
- Consumes: Task 7 (`PurseKeepApp`, `Settings`, `CaptureRepository`), Task 4 (`PairingParser`), Task 5 (`UpdateChecker`).
- Produces: `MainViewModel(app)` exposing `state: StateFlow<UiState>` and intents; `Screen` enum; `SystemActions` helpers. (QR scanning is added in Task 10; Task 8 pairing is manual entry only.)

- [ ] **Step 1: System actions**

`SystemActions.kt`:
```kotlin
package app.pursekeep.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationManagerCompat

object SystemActions {
    fun hasNotificationAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun isBatteryExempt(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun openNotificationAccess(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun requestBatteryExemption(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openUrl(ctx: Context, url: String) = CustomTabsIntent.Builder().build().launchUrl(ctx, Uri.parse(url))
}
```

- [ ] **Step 2: ViewModel**

`MainViewModel.kt`:
```kotlin
package app.pursekeep.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pursekeep.BuildConfig
import app.pursekeep.PurseKeepApp
import app.pursekeep.capture.KnownApps
import app.pursekeep.net.UpdateChecker
import app.pursekeep.outbox.EventEntry
import app.pursekeep.outbox.OutboxFlushWorker
import app.pursekeep.pairing.PairingParser
import app.pursekeep.settings.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Pairing, Apps }

data class UiState(
    val pairing: Pairing? = null,
    val pairingBroken: Boolean = false,
    val serverUrl: String? = null,
    val enabled: Set<String> = KnownApps.defaultEnabled,
    val custom: Set<String> = emptySet(),
    val pending: Int = 0,
    val events: List<EventEntry> = emptyList(),
    val lastSentAt: Long? = null,
    val notificationAccess: Boolean = false,
    val batteryExempt: Boolean = false,
    val updateTag: String? = null,
    val pairingError: String? = null,
    val screen: Screen = Screen.Home,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PurseKeepApp
    private val system = MutableStateFlow(Triple(false, false, null as String?)) // access, battery, updateTag
    private val local = MutableStateFlow(Pair(Screen.Home, null as String?))     // screen, pairingError

    val state: StateFlow<UiState> = combine(
        app.settings.pairing, app.settings.pairingBroken, app.settings.serverUrl,
        app.settings.enabledPackages, app.settings.customPackages,
        app.db.outbox().pendingCount(), app.db.events().latest(), app.settings.lastSentAt,
        system, local,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val sys = values[8] as Triple<Boolean, Boolean, String?>
        val loc = values[9] as Pair<Screen, String?>
        UiState(
            pairing = values[0] as Pairing?, pairingBroken = values[1] as Boolean, serverUrl = values[2] as String?,
            enabled = values[3] as Set<String>, custom = values[4] as Set<String>,
            pending = values[5] as Int, events = values[6] as List<EventEntry>, lastSentAt = values[7] as Long?,
            notificationAccess = sys.first, batteryExempt = sys.second, updateTag = sys.third,
            pairingError = loc.second, screen = loc.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun refreshSystemState() {
        val ctx = getApplication<Application>()
        system.value = system.value.copy(first = SystemActions.hasNotificationAccess(ctx), second = SystemActions.isBatteryExempt(ctx))
        OutboxFlushWorker.enqueue(ctx)
        checkUpdates()
    }

    fun go(screen: Screen) { local.value = Pair(screen, null) }

    fun pairManual(url: String, token: String) = pairWith(PairingParser.manual(url, token))
    fun pairScanned(raw: String) = pairWith(PairingParser.parse(raw))

    private fun pairWith(result: Result<app.pursekeep.pairing.PairingPayload>) {
        result.onFailure { local.value = Pair(Screen.Pairing, it.message ?: "Invalid pairing code") }
        result.onSuccess { p ->
            viewModelScope.launch {
                app.settings.pair(p.url, p.token, android.os.Build.MODEL)
                app.captures.log("info", "Paired with ${p.url}")
                app.captures.sendTest()
                local.value = Pair(Screen.Home, null)
            }
        }
    }

    fun unpair() = viewModelScope.launch { app.settings.unpair(); app.captures.log("info", "Unpaired") }
    fun sendTest() = viewModelScope.launch { app.captures.sendTest() }
    fun setPackageEnabled(pkg: String, on: Boolean) = viewModelScope.launch { app.settings.setPackageEnabled(pkg, on) }
    fun addCustomPackage(pkg: String) = viewModelScope.launch { if (pkg.isNotBlank()) app.settings.addCustomPackage(pkg.trim()) }
    fun removeCustomPackage(pkg: String) = viewModelScope.launch { app.settings.removeCustomPackage(pkg) }

    private fun checkUpdates() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (now - app.settings.lastUpdateCheckAt() < 24L * 60 * 60 * 1000) {
            applyTag(app.settings.latestKnownTag.let { flow -> kotlinx.coroutines.flow.first(flow) })
            return@launch
        }
        val tag = withContext(Dispatchers.IO) { app.updates.fetchLatestTag() }
        app.settings.recordUpdateCheck(tag, now)
        applyTag(tag)
    }

    private fun applyTag(tag: String?) {
        val newer = tag?.takeIf { UpdateChecker.isNewer(it, BuildConfig.VERSION_NAME) }
        system.value = system.value.copy(third = newer)
    }
}
```
(Kotlin's `combine` with more than five flows takes an `Array<Any?>`; the casts above are the price of one `combine`.)

- [ ] **Step 3: Home screen**

`HomeScreen.kt`:
```kotlin
package app.pursekeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pursekeep.BuildConfig
import app.pursekeep.net.UpdateChecker
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("PurseKeep", style = MaterialTheme.typography.headlineMedium)
            Text("Companion v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
        state.updateTag?.let { tag ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Update available: $tag", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { SystemActions.openUrl(ctx, UpdateChecker.RELEASES_PAGE) }) { Text("Download") }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pairing", style = MaterialTheme.typography.titleMedium)
                    when {
                        state.pairing != null -> {
                            Text("Paired with ${state.pairing.serverUrl.removePrefix("https://")}")
                            Text("Device: ${state.pairing.deviceName}", style = MaterialTheme.typography.bodySmall)
                            state.lastSentAt?.let { Text("Last sent: ${DateFormat.getDateTimeInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { SystemActions.openUrl(ctx, state.pairing.serverUrl) }) { Text("Open PurseKeep") }
                                TextButton(onClick = { vm.unpair() }) { Text("Unpair") }
                            }
                        }
                        state.pairingBroken -> {
                            Text("The server rejected this phone's token. Pair again from Settings › Devices.", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { vm.go(Screen.Pairing) }) { Text("Pair again") }
                        }
                        else -> {
                            Text("Not paired. Create a device on the web app (Settings › Devices) and scan its QR code.")
                            Button(onClick = { vm.go(Screen.Pairing) }) { Text("Pair this phone") }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notification access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "PurseKeep reads the payment notifications of the apps you enable (Google Wallet by default) " +
                            "and sends their text to your own server. Nothing else is read or stored.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.notificationAccess) Text("Granted ✓") else Button(onClick = { SystemActions.openNotificationAccess(ctx) }) { Text("Grant notification access") }
                    if (state.batteryExempt) Text("Battery optimisation: exempt ✓", style = MaterialTheme.typography.bodySmall)
                    else TextButton(onClick = { SystemActions.requestBatteryExemption(ctx) }) { Text("Exempt from battery optimisation") }
                    TextButton(onClick = { vm.go(Screen.Apps) }) { Text("Apps to listen to (${state.enabled.size})") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.sendTest() }, enabled = state.pairing != null) { Text("Send test") }
                Spacer(Modifier.width(4.dp))
                Text("Pending: ${state.pending}", modifier = Modifier.padding(top = 12.dp))
            }
        }
        item { Text("Recent events", style = MaterialTheme.typography.titleMedium) }
        items(state.events, key = { it.id }) { e ->
            Column {
                Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.at))} · ${e.kind}", style = MaterialTheme.typography.labelSmall)
                Text(e.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Step 4: Pairing screen (manual entry now; scan button wired in Task 10)**

`PairingScreen.kt`:
```kotlin
package app.pursekeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(state: UiState, vm: MainViewModel, onScan: (() -> Unit)?) {
    var url by rememberSaveable { mutableStateOf(state.serverUrl ?: "https://") }
    var token by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pair this phone", style = MaterialTheme.typography.headlineSmall)
        Text("On the web app open Settings › Devices, create a device and scan the QR code it shows.")
        if (onScan != null) Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code") }
        Text("Or enter the details by hand:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Device token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        state.pairingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.pairManual(url, token) }, modifier = Modifier.fillMaxWidth()) { Text("Pair") }
        TextButton(onClick = { vm.go(Screen.Home) }) { Text("Cancel") }
    }
}
```

- [ ] **Step 5: Apps screen**

`AppsScreen.kt`:
```kotlin
package app.pursekeep.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pursekeep.capture.KnownApps

@Composable
fun AppsScreen(state: UiState, vm: MainViewModel) {
    val pm = LocalContext.current.packageManager
    fun installed(pkg: String) = try { pm.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    var custom by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Apps to listen to", style = MaterialTheme.typography.headlineSmall)
        KnownApps.all.forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(app.label)
                    Text(if (installed(app.packageName)) "installed" else "not installed", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = app.packageName in state.enabled, onCheckedChange = { vm.setPackageEnabled(app.packageName, it) })
            }
        }
        state.custom.forEach { pkg ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(pkg); TextButton(onClick = { vm.removeCustomPackage(pkg) }) { Text("Remove") } }
                Switch(checked = pkg in state.enabled, onCheckedChange = { vm.setPackageEnabled(pkg, it) })
            }
        }
        OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Add package name (e.g. com.bank.app)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addCustomPackage(custom); custom = "" }, enabled = custom.contains('.')) { Text("Add") }
        TextButton(onClick = { vm.go(Screen.Home) }) { Text("Back") }
    }
}
```

- [ ] **Step 6: Activity**

Replace `MainActivity.kt`:
```kotlin
package app.pursekeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pursekeep.ui.AppsScreen
import app.pursekeep.ui.HomeScreen
import app.pursekeep.ui.MainViewModel
import app.pursekeep.ui.PairingScreen
import app.pursekeep.ui.PurseKeepTheme
import app.pursekeep.ui.Screen
import androidx.compose.foundation.layout.Box

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            PurseKeepTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) { vm.refreshSystemState(); onPauseOrDispose { } }
                BackHandler(enabled = state.screen != Screen.Home) { vm.go(Screen.Home) }
                Scaffold { padding ->
                    Box(Modifier.padding(padding)) {
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state, vm)
                            Screen.Pairing -> PairingScreen(state, vm, onScan = scanAction())
                            Screen.Apps -> AppsScreen(state, vm)
                        }
                    }
                }
            }
        }
    }

    /** QR scanning is wired in Task 10; until then manual entry only. */
    private fun scanAction(): (() -> Unit)? = null
}
```

- [ ] **Step 7: Build, install on the phone, smoke test**

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | head -1
cp app/build/outputs/apk/release/app-release.apk /tmp/pursekeep-0.1.0-dev.apk
```
Hand the APK to the user (`adb install -r` with USB debugging, or copy the file to the phone and open it). Manual check: app opens, "Pair this phone" → paste the token from the (current) Devices page + server URL → Home shows "Paired", events show `[test]` captured then `sent → needs_account`; on the web, `/wallet` shows the test capture. Grant notification access, tap a card, see a real capture. Report exact results.

- [ ] **Step 8: Commit** — `git add android/app/src && git commit -m "Android: home, pairing and apps screens; first installable companion"`

---

### Task 9: Server — client version column, QR on Devices page, banners off, links

**Files:**
- Modify: `src/db/schema.ts:361-374` (walletDevices), `src/app/api/wallet/capture/route.ts:121-124`, `src/lib/actions/wallet.ts:20-58`, `src/components/wallet-devices-panel.tsx`, `src/app/(app)/settings/devices/page.tsx`, `src/app/(app)/settings/page.tsx:26`, `src/app/(app)/wallet/page.tsx:19-23`, `src/lib/queries.ts` (listWalletDevices select — verify it returns `clientVersion`; it selects whole rows, so nothing to change unless it projects columns), `package.json`
- Create: `src/lib/wallet/client-version.ts`, `src/lib/wallet/pairing-qr.ts`, `src/db/migrations/0005_client_version.sql` (+ `meta/` via drizzle-kit), `tests/wallet-client-version.test.ts`

**Interfaces:**
- Produces: `walletDevices.clientVersion: text | null`; `parseClientVersion(header: string | null): string | null` (returns e.g. `"android/0.1.0+1"`, max 100 chars, or null); `pairingQrSvg(token: string): Promise<{ svg: string; json: string }>` reading the origin from `process.env.AUTH_URL`; `CreateDeviceResult` gains `qrSvg?: string`.

- [ ] **Step 1: Failing test for the header parser**

`tests/wallet-client-version.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { parseClientVersion } from "@/lib/wallet/client-version";

describe("parseClientVersion", () => {
  it("keeps a well-formed android header", () => {
    expect(parseClientVersion("android/0.1.0+1")).toBe("android/0.1.0+1");
  });
  it("returns null for missing or blank", () => {
    expect(parseClientVersion(null)).toBeNull();
    expect(parseClientVersion("   ")).toBeNull();
  });
  it("rejects junk and truncates long values", () => {
    expect(parseClientVersion("<script>")).toBeNull();
    expect(parseClientVersion("android/" + "9".repeat(200))).toHaveLength(100);
  });
});
```

- [ ] **Step 2: Run** — `npx vitest run tests/wallet-client-version.test.ts` → fails to resolve module.

- [ ] **Step 3: Implement parser + schema + migration**

`src/lib/wallet/client-version.ts`:
```ts
/** Value of the `X-PurseKeep-Client` header, e.g. "android/0.1.0+1". Only
 *  [A-Za-z0-9._+/-] is kept; anything else means "unknown client". */
export function parseClientVersion(header: string | null): string | null {
  const v = (header ?? "").trim();
  if (!v || !/^[A-Za-z0-9._+/-]+$/.test(v)) return null;
  return v.slice(0, 100);
}
```

In `src/db/schema.ts`, inside `walletDevices` after `lastSeenAt`:
```ts
  // e.g. "android/0.1.0+1" from the X-PurseKeep-Client header; null for
  // automations/unknown clients.
  clientVersion: text("client_version"),
```

Generate the migration: `DATABASE_URL=postgres://x:y@localhost/z npx drizzle-kit generate --name client_version` (no DB connection is made for `generate`). Expected new file `src/db/migrations/0005_client_version.sql` containing:
```sql
ALTER TABLE "wallet_devices" ADD COLUMN "client_version" text;
```
plus updated `meta/_journal.json` and a new snapshot. Commit all three.

In `route.ts` replace the `lastSeenAt` update:
```ts
  await db
    .update(walletDevices)
    .set({
      lastSeenAt: new Date(),
      clientVersion: parseClientVersion(req.headers.get("x-pursekeep-client")),
    })
    .where(eq(walletDevices.id, device.id));
```
and add `import { parseClientVersion } from "@/lib/wallet/client-version";`.

- [ ] **Step 4: QR generation**

```bash
npm install qrcode && npm install -D @types/qrcode && npx npm@10 install --package-lock-only
```

`src/lib/wallet/pairing-qr.ts`:
```ts
import QRCode from "qrcode";

/** Origin the phone should talk to — AUTH_URL is already the public origin
 *  of this deployment (tailnet today, pursekeep.app later). */
export function serverOrigin(): string {
  const raw = process.env.AUTH_URL ?? "";
  return raw.replace(/\/+$/, "");
}

export type PairingJson = { v: 1; app: "pursekeep"; url: string; token: string };

export function pairingJson(token: string): PairingJson {
  return { v: 1, app: "pursekeep", url: serverOrigin(), token };
}

export async function pairingQrSvg(token: string): Promise<{ svg: string; json: string }> {
  const json = JSON.stringify(pairingJson(token));
  const svg = await QRCode.toString(json, { type: "svg", margin: 1, width: 240, errorCorrectionLevel: "M" });
  return { svg, json };
}
```

In `src/lib/actions/wallet.ts`: extend the result type and action:
```ts
export type CreateDeviceResult = {
  ok: boolean;
  error?: string;
  token?: string;
  deviceName?: string;
  qrSvg?: string;
};
```
and in `createWalletDevice`, after the insert:
```ts
  const { svg } = await pairingQrSvg(token);
  revalidatePath("/settings/devices");
  return { ok: true, token, deviceName: name, qrSvg: svg };
```
with `import { pairingQrSvg } from "@/lib/wallet/pairing-qr";`.

- [ ] **Step 5: Devices panel + pages**

In `wallet-devices-panel.tsx`: add `clientVersion: string | null` to `Device`; replace the token block with:
```tsx
        {result?.ok && result.token && (
          <div className="rounded-xl border border-border bg-surface p-3 text-sm">
            <p className="font-medium">
              Pair &ldquo;{result.deviceName}&rdquo; now — this code is shown once:
            </p>
            {result.qrSvg && (
              <div
                className="mx-auto my-3 w-60 rounded bg-white p-2"
                aria-label="Pairing QR code"
                dangerouslySetInnerHTML={{ __html: result.qrSvg }}
              />
            )}
            <p className="text-xs text-muted">
              In the PurseKeep Android app tap <strong>Pair this phone → Scan QR code</strong>. Or
              enter the token by hand:
            </p>
            <code className="mt-1 block break-all rounded bg-surface-muted p-2 text-xs">
              {result.token}
            </code>
          </div>
        )}
```
In the device list, after `last seen …` add `{d.clientVersion ? ` · ${d.clientVersion}` : ""}`. Change the "Add device" heading's helper: add under `<CardTitle>Add device</CardTitle>`:
```tsx
        <p className="text-xs text-muted">
          Install the PurseKeep Android app from the{" "}
          <a className="underline" href="https://github.com/martomarzo/money-maker/releases/latest">latest release</a>, then create a device here and scan its QR code.
        </p>
```
In `settings/devices/page.tsx`: remove the "Experimental" paragraph; pass `clientVersion: d.clientVersion ?? null`; description → `"Phones running the PurseKeep app. Each device gets a token, shown once as a QR code."`; add below the header: `<ButtonLink href="/wallet" variant="secondary" size="sm">Captured payments</ButtonLink>` (import `ButtonLink` from `@/components/ui`).
In `wallet/page.tsx`: remove the "Experimental" paragraph; description → `"Payments captured from your phone. Booked ones are already in your transactions — fix up the rest here."`.
In `settings/page.tsx`: change the label to `{ href: "/settings/devices", label: "Phone app & captured payments" }`.

- [ ] **Step 6: Verify**

Run: `npm run lint && npm run typecheck && npm run test`
Expected: all pass (existing 80+ tests plus 3 new).

- [ ] **Step 7: Commit**

```bash
git add package.json package-lock.json src tests
git commit -m "Devices page pairs the PurseKeep app by QR; store client version per device"
```

---

### Task 10: QR scanning, update banner wiring, unpaired flow in the app

**Files:**
- Modify: `android/app/src/main/kotlin/app/pursekeep/MainActivity.kt`

**Interfaces:**
- Consumes: `MainViewModel.pairScanned(raw)` (Task 8), `play-services-code-scanner` (Task 2 deps).

- [ ] **Step 1: Wire the scanner**

Replace `scanAction()` in `MainActivity.kt` with:
```kotlin
    private fun scanAction(): (() -> Unit) = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let { vm.pairScanned(it) } }
            .addOnFailureListener { e -> vm.pairFailed(e.message ?: "Scanner unavailable — enter the token by hand") }
    }
```
with imports `com.google.mlkit.vision.barcode.common.Barcode`, `com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions`, `com.google.mlkit.vision.codescanner.GmsBarcodeScanning`.

Add to `MainViewModel`:
```kotlin
    fun pairFailed(message: String) { local.value = Pair(Screen.Pairing, message) }
```

- [ ] **Step 2: Build and verify on the phone**

`./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease`; install; Pair this phone → Scan QR code → scan the QR from Settings › Devices (Task 9 deployed) → Home shows Paired and the test capture lands in `/wallet`. Then revoke the device on the web, tap "Send test": Home flips to the "rejected token" state and a system notification appears. Pair again → works. Report results.

- [ ] **Step 3: Commit** — `git add android && git commit -m "Android: QR pairing via Google code scanner; unpaired recovery"`

---

### Task 11: Wire contract doc, shared fixtures, tests on both sides

**Files:**
- Create: `docs/wallet-capture-contract.md`, `android/contract/fixtures/android-wallet.json`, `android/contract/fixtures/android-wallet-test.json`, `android/contract/fixtures/android-bank.json`, `android/contract/fixtures/ios-transaction.json`, `android/contract/fixtures/qr-pairing.json`, `tests/wallet-contract.test.ts`, `android/app/src/test/kotlin/app/pursekeep/ContractTest.kt`
- Modify: `src/lib/wallet/types.ts:1-4` (comment: MacroDroid → PurseKeep app)

**Interfaces:** fixtures are the interface.

- [ ] **Step 1: Fixtures**

`android-wallet.json`:
```json
{"kind":"android_notification","app":"com.google.android.apps.walletnfcrel","title":"€12,40 with Visa ••1234","text":"MERCADONA","postedAt":"2026-09-22T13:05:00+02:00"}
```
`android-wallet-test.json`:
```json
{"kind":"android_notification","app":"com.google.android.apps.walletnfcrel","title":"€0.01 with Visa ••0000","text":"PurseKeep test","postedAt":"2026-09-22T13:05:00+02:00"}
```
`android-bank.json`:
```json
{"kind":"android_notification","app":"com.revolut.revolut","title":"Paid €8.50 at \"La Farola\"","text":"Card ending in 4321","postedAt":"2026-09-22T13:06:30+02:00"}
```
`ios-transaction.json`:
```json
{"kind":"ios_transaction","merchant":"Carrefour","amount":"23,10","currency":"EUR","cardName":"Visa Revolut","postedAt":"2026-09-22T13:07:00+02:00"}
```
`qr-pairing.json`:
```json
{"v":1,"app":"pursekeep","url":"https://money-maker.peacock-snapper.ts.net","token":"AbCdEf0123456789AbCdEf0123456789AbCdEf0123-_"}
```

- [ ] **Step 2: Server contract test**

`tests/wallet-contract.test.ts`:
```ts
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import { amountToMinor, captureHash, parseCapture } from "@/lib/wallet/engine";
import { capturePayloadSchema } from "@/lib/wallet/types";

const dir = path.resolve(__dirname, "..", "android", "contract", "fixtures");
const load = (name: string) => JSON.parse(readFileSync(path.join(dir, name), "utf8"));

const pairingSchema = z.object({
  v: z.literal(1),
  app: z.literal("pursekeep"),
  url: z.string().url().startsWith("https://"),
  token: z.string().min(1),
});

describe("wallet capture contract v1 (fixtures shared with the Android app)", () => {
  for (const f of ["android-wallet.json", "android-wallet-test.json", "android-bank.json", "ios-transaction.json"]) {
    it(`${f} parses with capturePayloadSchema`, () => {
      expect(capturePayloadSchema.safeParse(load(f)).success).toBe(true);
    });
  }

  it("the app's test capture parses to 1 cent EUR, card 0000, merchant 'PurseKeep test'", () => {
    const payload = capturePayloadSchema.parse(load("android-wallet-test.json"));
    const parsed = parseCapture(payload)!;
    expect(parsed.currency).toBe("EUR");
    expect(amountToMinor(parsed.amountRaw, "EUR")).toBe(1);
    expect(parsed.cardKey).toBe("0000");
    expect(parsed.merchant).toBe("PurseKeep test");
  });

  it("dedupe hash of the wallet fixture is stable", () => {
    const payload = capturePayloadSchema.parse(load("android-wallet.json"));
    // Recorded on first run; a change here means every phone would re-send duplicates.
    expect(captureHash("00000000-0000-0000-0000-000000000000", payload)).toBe("0a79ef62074b3f27befc664b3287c3e01de49fd2db72b98caaf181c9bb48b044");
  });

  it("qr-pairing.json matches the pairing schema", () => {
    expect(pairingSchema.safeParse(load("qr-pairing.json")).success).toBe(true);
  });
});
```
Run: `npx vitest run tests/wallet-contract.test.ts` → all green (the hash constant and the parsed shape `{amountRaw:"0.01",currency:"EUR",merchant:"PurseKeep test",cardKey:"0000"}` were computed with the real engine on 2026-09-22).

- [ ] **Step 3: Android contract test**

`ContractTest.kt`:
```kotlin
package app.pursekeep

import app.pursekeep.capture.AndroidCapture
import app.pursekeep.outbox.CaptureRepository
import app.pursekeep.pairing.PairingParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ContractTest {
    private val json = Json { encodeDefaults = true }
    private fun fixture(name: String) = File("../contract/fixtures/$name").readText()
    private fun same(expectedJson: String, actualJson: String) =
        assertEquals(Json.parseToJsonElement(expectedJson), Json.parseToJsonElement(actualJson))

    @Test fun `wallet fixture serialises identically`() = same(
        fixture("android-wallet.json"),
        json.encodeToString(AndroidCapture.serializer(), AndroidCapture(app = "com.google.android.apps.walletnfcrel", title = "€12,40 with Visa ••1234", text = "MERCADONA", postedAt = "2026-09-22T13:05:00+02:00")),
    )

    @Test fun `test capture matches its fixture`() = same(
        fixture("android-wallet-test.json"),
        json.encodeToString(AndroidCapture.serializer(), CaptureRepository.testCapture("2026-09-22T13:05:00+02:00")),
    )

    @Test fun `bank fixture with quotes round-trips`() {
        val decoded = json.decodeFromString(AndroidCapture.serializer(), fixture("android-bank.json"))
        assertEquals("Paid €8.50 at \"La Farola\"", decoded.title)
        same(fixture("android-bank.json"), json.encodeToString(AndroidCapture.serializer(), decoded))
    }

    @Test fun `pairing fixture is accepted`() {
        val p = PairingParser.parse(fixture("qr-pairing.json")).getOrThrow()
        assertEquals("https://money-maker.peacock-snapper.ts.net", p.url)
    }
}
```
Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.pursekeep.ContractTest'` → 4 pass.

- [ ] **Step 4: Contract document**

`docs/wallet-capture-contract.md`:
```markdown
# Wallet capture wire contract — v1

Shared by the web server (`src/lib/wallet/types.ts`) and the Android app
(`android/app/src/main/kotlin/app/pursekeep/capture/AndroidCapture.kt`).
Fixtures: `android/contract/fixtures/`. Tests: `tests/wallet-contract.test.ts`
(server) and `android/app/src/test/kotlin/app/pursekeep/ContractTest.kt` (app).

## Endpoint
`POST {origin}/api/wallet/capture`

Headers: `Authorization: Bearer <device token>` · `Content-Type: application/json` ·
`X-PurseKeep-Client: android/<versionName>+<versionCode>` (stored on the device row) ·
`User-Agent: PurseKeep-Android/<versionName>`.

## Payloads
- `{"kind":"android_notification","app":"<package>","title":"…","text":"…","postedAt":"<ISO-8601>"}`
  — `app` 1–200 chars, `title` ≤ 2000, `text` ≤ 4000, `postedAt` `YYYY-MM-DDTHH:MM…` with offset.
- `{"kind":"ios_transaction","merchant":"…","amount":"…","currency":"EUR"?,"cardName":"…","postedAt":"…"}`.
- A body that is not valid JSON is stored raw as an *unparsed* capture (never 4xx).

## Responses
- `201 {"id":"<uuid>","status":"booked"|"needs_account"|"unparsed"}`
- `200 {"duplicate":true}` — same device already sent this payload
- `400 {"error":"empty body"}` — permanent, do not retry
- `401 {"error":"unauthorized"}` — token missing/revoked: the app marks itself unpaired
- `5xx` / network errors — retry with backoff; the app gives up after 30 days

## Pairing QR
`{"v":1,"app":"pursekeep","url":"https://<origin>","token":"<device token>"}` — the app rejects
`v≠1`, `app≠"pursekeep"`, non-https URLs and blank tokens.

## Evolution
Adding optional fields: no version bump. Renaming/removing fields or changing a response shape:
bump `v` and the `kind` schemas' version, keep accepting the previous version for at least one
app release, update fixtures + both tests + `CHANGELOG.md` ("Android app" lines).
```

Update the comment at the top of `src/lib/wallet/types.ts`: replace "forwarded by MacroDroid" with "forwarded by the PurseKeep Android app (see docs/wallet-capture-contract.md)".

- [ ] **Step 5: Run everything** — `npm run test` and `./gradlew --no-daemon :app:testDebugUnitTest` → green.
- [ ] **Step 6: Commit** — `git add docs/wallet-capture-contract.md android/contract tests android/app/src src/lib/wallet/types.ts && git commit -m "Shared wallet capture contract with fixtures tested by server and Android app"`

---

### Task 12: CI workflow, path filters, secrets, first release

**Files:**
- Create: `.github/workflows/android.yaml`
- Modify: `.github/workflows/ci.yaml:3-5`, `.github/workflows/deploy.yaml:7-9`

- [ ] **Step 1: Android workflow**

`.github/workflows/android.yaml`:
```yaml
name: Android

on:
  push:
    branches: [main]
    tags: ["android-v*"]
    paths: ["android/**", ".github/workflows/android.yaml"]
  pull_request:
    paths: ["android/**", ".github/workflows/android.yaml"]

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4

      - name: Restore release keystore from secrets
        env:
          KEYSTORE_B64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        run: |
          if [ -n "$KEYSTORE_B64" ]; then
            echo "$KEYSTORE_B64" | base64 -d > "$RUNNER_TEMP/release.jks"
            echo "ANDROID_KEYSTORE_FILE=$RUNNER_TEMP/release.jks" >> "$GITHUB_ENV"
          else
            echo "::warning::No ANDROID_KEYSTORE_BASE64 secret — APK will be debug-signed"
          fi

      - name: Read version
        id: v
        run: echo "name=$(grep '^versionName=' version.properties | cut -d= -f2 | tr -d '[:space:]')" >> "$GITHUB_OUTPUT"

      - name: Tag must match version.properties
        if: startsWith(github.ref, 'refs/tags/android-v')
        run: test "${GITHUB_REF_NAME}" = "android-v${{ steps.v.outputs.name }}"

      - name: Unit tests + release APK
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease

      - name: Name the APK
        run: cp app/build/outputs/apk/release/app-release.apk "pursekeep-${{ steps.v.outputs.name }}.apk"

      - uses: actions/upload-artifact@v4
        with:
          name: pursekeep-apk
          path: android/pursekeep-${{ steps.v.outputs.name }}.apk

      - name: GitHub Release
        if: startsWith(github.ref, 'refs/tags/android-v')
        uses: softprops/action-gh-release@v2
        with:
          files: android/pursekeep-${{ steps.v.outputs.name }}.apk
          generate_release_notes: true
```

- [ ] **Step 2: Path filters on server workflows**

`ci.yaml` `on:` becomes:
```yaml
on:
  push:
    paths-ignore: ["android/**", ".github/workflows/android.yaml"]
  pull_request:
    paths-ignore: ["android/**", ".github/workflows/android.yaml"]
```
`deploy.yaml` `on:` becomes:
```yaml
on:
  push:
    branches: [main]
    paths-ignore: ["android/**", ".github/workflows/android.yaml"]
```

- [ ] **Step 3: Secrets — hand to the user as `!` one-liners** (the assistant must not read the passwords):

```
! cd ~/.pursekeep && gh secret set ANDROID_KEYSTORE_BASE64 --repo martomarzo/money-maker --body "$(base64 -w0 release.jks)"
! cd ~/.pursekeep && gh secret set ANDROID_KEYSTORE_PASSWORD --repo martomarzo/money-maker --body "$(grep '^storePassword=' keystore.properties | cut -d= -f2-)"
! gh secret set ANDROID_KEY_ALIAS --repo martomarzo/money-maker --body pursekeep
! cd ~/.pursekeep && gh secret set ANDROID_KEY_PASSWORD --repo martomarzo/money-maker --body "$(grep '^keyPassword=' keystore.properties | cut -d= -f2-)"
```
Verify: `gh secret list --repo martomarzo/money-maker` shows the four names.

- [ ] **Step 4: Commit, push, watch, tag**

```bash
git add .github/workflows
git commit -m "CI: build, test and release the Android app; skip server CI/deploy for app-only changes"
git push
gh run watch --exit-status $(gh run list --workflow Android --limit 1 --json databaseId -q '.[0].databaseId')
```
Expected: green; artifact `pursekeep-apk` downloadable. Then:
```bash
git tag android-v0.1.0 && git push origin android-v0.1.0
```
Expected: a release `android-v0.1.0` with `pursekeep-0.1.0.apk` attached; installing it over the locally built APK upgrades in place (same key). If the workflow ran before secrets existed, re-run it after Step 3.

---

### Task 13: Docs, changelog, plan/status, memory

**Files:**
- Create: `android/README.md`
- Replace: `docs/wallet-android-setup.md`
- Modify: `CHANGELOG.md` (new 2026-09-22 section at top), `plan.md` (§6 Phase 1.7 → replaced by the companion; backlog: public hosting on pursekeep.app with a security pass), `CLAUDE.md` (session status + "keeping both apps in step" rule), memory `project-status.md`

- [ ] **Step 1: `android/README.md`**

```markdown
# PurseKeep Android companion

Native shell for PurseKeep (application id `app.pursekeep`). Today it forwards
payment notifications (Google Wallet by default) to your PurseKeep server;
later it adds a biometric lock and embeds the web app (see the design spec in
`docs/superpowers/specs/2026-09-22-pursekeep-android-companion-design.md`).

## Build locally
```bash
export JAVA_HOME=~/.local/android-toolchain/jdk17 ANDROID_HOME=~/.local/android-toolchain/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
cp keystore.properties.example keystore.properties   # point at your release keystore
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```
No keystore → the release APK is debug-signed (warning printed) and cannot upgrade a release install.

## Install on a phone
`adb install -r app/build/outputs/apk/release/app-release.apk`, or copy the APK to the phone and open it
(allow installs from this source once). Official builds: GitHub Releases, tags `android-v*`.

## Release
1. Bump `versionName` (semver) and `versionCode` (+1) in `version.properties`; add an "Android app" line to `CHANGELOG.md`.
2. Commit, push, then `git tag android-vX.Y.Z && git push origin android-vX.Y.Z` — CI attaches `pursekeep-X.Y.Z.apk` to a GitHub Release.
3. Phones show an "Update available" banner on next open.

## Wire contract
`docs/wallet-capture-contract.md`; fixtures in `contract/fixtures/` are tested by both the server (`tests/wallet-contract.test.ts`) and `ContractTest.kt`.
```

- [ ] **Step 2: Replace `docs/wallet-android-setup.md`**

```markdown
# Android setup — PurseKeep companion app

1. On the phone, download `pursekeep-<version>.apk` from
   https://github.com/martomarzo/money-maker/releases/latest and open it
   (allow installing from your browser once). The phone must reach your
   server (today: Tailscale connected).
2. In the web app open **Settings › Phone app & captured payments**, create a
   device (e.g. "Pixel") — a QR code appears.
3. In the app tap **Pair this phone → Scan QR code**. The app immediately
   sends a test capture; it shows up under **Captured payments** as
   *Needs account* (dismiss it).
4. Tap **Grant notification access** and enable PurseKeep in the system list.
   Optionally exempt it from battery optimisation.
5. Pay with a card. Within seconds the purchase is in your transactions (if
   the card is mapped) or under Captured payments as *Needs account* — assign
   the account there and tick "remember card".

Under **Apps to listen to** you can add Revolut, Wise or any app by package
name. Offline taps are queued and sent when the phone is back online.
If the web app says a device is revoked, the app shows "no longer paired" —
create a new device and scan again.
```

- [ ] **Step 3: CHANGELOG, plan.md, CLAUDE.md, memory**

Prepend to `CHANGELOG.md` after the intro:
```markdown
## 2026-09-22

### The app is now called PurseKeep
- New name everywhere you see it (web app title, home-screen icon label). Nothing
  about your data or your login changed. Domain reserved: pursekeep.app.

### Android app: automatic payment capture
- **PurseKeep for Android** (v0.1.0, from GitHub Releases) reads Google Wallet
  payment notifications (Revolut, Wise or any app you add) and sends them to
  your PurseKeep server, even if you were offline when you paid.
- Pair the phone by scanning a **QR code** from Settings › Phone app & captured
  payments. A "Send test" button proves the link without spending anything.
- Devices show which app version they run; revoking a device makes the phone
  tell you it needs pairing again.
- The old "experimental" notes and the MacroDroid instructions are gone.

### Android app (versions)
- 0.1.0 — first release: notification capture, offline queue, QR pairing, update banner.
```

`plan.md` §6: under the Phase 1.7 note add: "**Replaced 2026-09-22 by the native PurseKeep Android companion** (spec `docs/superpowers/specs/2026-09-22-pursekeep-android-companion-design.md`, code in `android/`). Roadmap: biometric lock + embedded web app (Play Store candidate) → native screens only if needed." Add a backlog bullet under Phase 4: "**Public hosting on pursekeep.app** (Render/Vercel/…): needs a security pass before leaving the tailnet — rate limiting on login/register, password policy, passkeys, session/cookie hardening, invite-only registration, backups. Separate design session."

`CLAUDE.md` session status: add a bullet "**PurseKeep Android companion — DONE 2026-09-22** (`android/`, app id `app.pursekeep`, releases via tags `android-v*`, contract `docs/wallet-capture-contract.md`). Rule: any change to `src/lib/wallet/types.ts` or the capture route's responses = update `android/contract/fixtures`, both contract tests, and the Android sender; add an 'Android app' line to CHANGELOG with the app version. Keystore is user-owned at `~/.pursekeep/` — never commit. Local toolchain `~/.local/android-toolchain`." Update the NEXT list: "Phase 2 companion step 2 (biometric lock + WebView), public hosting on pursekeep.app + security pass, offline PWA, household budgets, needs-review filter, settle-up."

Memory: update `project-status.md` description and body with the same facts (name, companion done, next steps).

- [ ] **Step 4: Final verification and single stopping-point commit**

```bash
npm run lint && npm run typecheck && npm run test
cd android && ./gradlew --no-daemon :app:testDebugUnitTest && cd ..
git add android/README.md docs/wallet-android-setup.md CHANGELOG.md plan.md CLAUDE.md
git commit -m "Docs and changelog for the PurseKeep Android companion; roadmap and backlog updates"
git push
```
