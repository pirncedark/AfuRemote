# AfuRemote v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tek APK: telefonda paylaşılan link/videoyu aynı Wi-Fi'deki Android TV'de açar ve telefonu TV kumandası yapar; uygulama içinden güncellenir.

**Architecture:** Tek Gradle modülü `app`. TV modunda ön plan servisi NanoHTTPD (9870) + NSD duyurusu açar; istekleri saf-Kotlin `TvRouter` karşılar, Android işleri `AndroidTvActions`'ta. Telefon modunda NSD keşfi + OkHttp istemcisi + (yerel video için) NanoHTTPD (9871). Saf mantık (protokol, sınıflandırıcı, router, token, range, güncelleme ayrıştırıcı, telefon denetleyicisi) JVM birim testli; uçtan uca davranış CI emülatöründe tek cihazda iki rolle test edilir.

**Tech Stack:** Kotlin 2.2.10, AGP 9.4.1, Gradle 9.6.0, Compose BOM 2026.09.00 + Material3, Media3 1.11.1, NanoHTTPD 2.3.1, OkHttp 4.12.0, kotlinx-serialization-json 1.9.0, kotlinx-coroutines 1.10.2, WorkManager 2.9.0, GitHub Actions + reactivecircus/android-emulator-runner.

**Spec:** `docs/superpowers/specs/2026-09-23-afuremote-design.md`

## Global Constraints

- Paket / applicationId: `com.afudm.afuremote`; minSdk 26; compileSdk/targetSdk 37; Java/JVM 17.
- Portlar: TV `9870`, telefon medya `9871`; NSD tipi `_afuremote._tcp`; token başlığı `X-Afu-Token`; protokol `v1`.
- Ön plan servisleri **tür belirterek** başlar (`connectedDevice`; WorkManager güncelleme işçisi `dataSync`). Türsüz FGS Android 14+'ta çöker.
- Uygulama açılışı ağa bağlı olamaz; sunucu/keşif arka planda.
- Kullanıcıya görünen tüm metinler Türkçe. Telefon tarafında "İzin ver" ifadesi **kullanılmaz** (e2e bu metni TV onay düğmesi için arar).
- Derleme/test **yalnız GitHub Actions**'ta (bu PC'de JDK/SDK yok). TDD kırmızı adımı: test + kod aynı push'ta gider (CI turu ~6-15 dk); testler somut davranış doğrular, "sadece derlensin" testi yazılmaz.
- Repo public: imza anahtarı, şifreler depoya girmez (yalnız GitHub secrets + `Desktop\afuproject\_gizli`).
- Çalışma dalı `v1`; her görev sonunda push, CI yeşil olmadan sonraki göreve geçilmez. Commit mesajları İngilizce, sonunda `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- "Hazır" yalnız e2e yeşil olunca denir.

## Review Focus

1. Paylaşılan metinde link yazının ortasında ya da sonunda noktalama ile gelir ("Bunu izle: https://x/a.mp4.") → link temiz çıkarılmalı (Task 2 testi).
2. TV yeniden kuruldu/izinler sıfırlandı → telefonun eski token'ı 401 alır → telefon kendiliğinden yeniden eşleşmeli, kullanıcı hata görmemeli (Task 6 `PhoneControllerTest`).
3. Bozuk/eksik JSON, boş gövde, bilinmeyen tuş → TV sunucusu çökmeden 400 dönmeli (Task 4 testleri).
4. Oynatıcı ileri sardığında Range başlığı: `bytes=500-`, `bytes=-500`, dosyadan büyük aralık → doğru 206 / 416 (Task 6 `RangeParserTest`).
5. YouTube link çeşitleri (youtu.be `?si=`, shorts, m./music., embed) → YouTube TV'ye doğru video id ile gitmeli (Task 2 testi).

---

## Dosya haritası

```
settings.gradle.kts, build.gradle.kts, gradle.properties, .gitignore
.github/workflows/build.yml        derleme + birim test + (Task 5'ten itibaren) emülatör e2e
.github/workflows/release.yml      afuremote-v* etiketi → imzalı APK + sha256 release (Task 7)
scripts/ui.py                      uiautomator dump'tan koordinat (AfuTube'dan)
scripts/e2e.sh                     emülatör uçtan uca testi (Task 5, 6)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/{values/strings.xml, drawable/ic_launcher.xml, drawable/banner.xml, drawable/ic_stat_remote.xml, xml/accessibility_service.xml, xml/file_paths.xml}
app/src/main/java/com/afudm/afuremote/
  AfuRemoteApp.kt, MainActivity.kt, AppVisibility.kt
  mode/AppMode.kt, mode/ModeStore.kt
  ui/Theme.kt, ui/ModeSection.kt
  protocol/Protocol.kt
  classify/LinkClassifier.kt
  pairing/TokenRegistry.kt, pairing/PairingStore.kt
  net/LocalIp.kt
  tv/TvRouter.kt, tv/TvHttpServer.kt, tv/AfuTvService.kt, tv/NsdAdvertiser.kt, tv/AndroidTvActions.kt,
  tv/PairBroker.kt, tv/PairPromptActivity.kt, tv/PlayerActivity.kt, tv/PlayerRegistry.kt, tv/WebActivity.kt,
  tv/RemoteAccessibilityService.kt, tv/BootReceiver.kt, tv/TvHomeScreen.kt
  phone/TvDevice.kt, phone/Messages.kt, phone/TvApi.kt, phone/TvClient.kt, phone/TvDiscovery.kt, phone/PhoneController.kt,
  phone/PhoneGraph.kt, phone/RangeParser.kt, phone/LocalMediaServer.kt, phone/PhoneStream.kt, phone/PhoneStreamService.kt,
  phone/ShareActivity.kt, phone/PhoneHomeScreen.kt
  update/UpdateParser.kt, update/UpdateManager.kt, update/ApkDownloadWorker.kt, update/UpdateSection.kt
app/src/test/java/com/afudm/afuremote/
  mode/AppModeTest.kt, protocol/ProtocolTest.kt, classify/LinkClassifierTest.kt, pairing/TokenRegistryTest.kt,
  tv/TvRouterTest.kt, phone/RangeParserTest.kt, phone/MessagesTest.kt, phone/PhoneControllerTest.kt, update/UpdateParserTest.kt
```

---

### Task 1: Proje iskeleti + CI + mod seçimi

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_launcher.xml`, `app/src/main/res/drawable/banner.xml`, `app/src/main/res/drawable/ic_stat_remote.xml`, `app/src/main/java/com/afudm/afuremote/{AfuRemoteApp.kt, AppVisibility.kt, MainActivity.kt}`, `.../mode/AppMode.kt`, `.../mode/ModeStore.kt`, `.../ui/Theme.kt`, `.../ui/ModeSection.kt`, `.github/workflows/build.yml`
- Test: `app/src/test/java/com/afudm/afuremote/mode/AppModeTest.kt`

**Interfaces:**
- Produces: `enum class AppMode { TV, PHONE }` + `AppMode.detect(isTelevisionUi: Boolean, hasLeanback: Boolean, override: String?): AppMode`; `object ModeStore { fun current(context: Context): AppMode; fun setOverride(context: Context, value: String?) }`; `object AppVisibility { val isForeground: Boolean }`; `@Composable fun AfuTheme(content: @Composable () -> Unit)`; `@Composable fun ModeSection(onModeChange: (String?) -> Unit)`; MainActivity `--es mode tv|phone|auto` ekstrası.

- [ ] **Step 1: Dalı aç**

```bash
cd /c/Users/afuuu/Desktop/afuproject/AfuRemote && git switch -c v1
```

- [ ] **Step 2: Gradle dosyaları**

`settings.gradle.kts`:
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

rootProject.name = "AfuRemote"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.builtInKotlin=false
android.newDsl=false
android.nonTransitiveRClass=true
```

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystorePath: String? = System.getenv("AFUREMOTE_KEYSTORE_PATH")

android {
    namespace = "com.afudm.afuremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.afudm.afuremote"
        minSdk = 26
        targetSdk = 37
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.0.1"
    }

    signingConfigs {
        create("release") {
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("AFUREMOTE_KEYSTORE_PASS")
                keyAlias = System.getenv("AFUREMOTE_KEY_ALIAS")
                keyPassword = System.getenv("AFUREMOTE_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!keystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.10")
}
```

- [ ] **Step 3: Kaynaklar**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">AfuRemote</string>
    <string name="erisilebilirlik_aciklama">AfuRemote, telefondan gelen geri/ana ekran tuşlarını uygulamak ve paylaşılan videoları TV\'de açmak için bu izne ihtiyaç duyar. Ekran içeriğini okumaz.</string>
</resources>
```

`app/src/main/res/drawable/ic_launcher.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#7C5CFF" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#FFFFFF" android:fillType="evenOdd"
        android:pathData="M30,32h48a6,6 0,0 1,6 6v28a6,6 0,0 1,-6 6h-48a6,6 0,0 1,-6 -6v-28a6,6 0,0 1,6 -6zM48,44v16l14,-8z M44,80h20v4h-20z" />
</vector>
```

`app/src/main/res/drawable/banner.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="320dp" android:height="180dp"
    android:viewportWidth="320" android:viewportHeight="180">
    <path android:fillColor="#1B1530" android:pathData="M0,0h320v180h-320z" />
    <path android:fillColor="#7C5CFF" android:fillType="evenOdd"
        android:pathData="M110,50h100a10,10 0,0 1,10 10v50a10,10 0,0 1,-10 10h-100a10,10 0,0 1,-10 -10v-50a10,10 0,0 1,10 -10zM148,70v30l26,-15z" />
</vector>
```

`app/src/main/res/drawable/ic_stat_remote.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:fillType="evenOdd"
        android:pathData="M4,5h16a1,1 0,0 1,1 1v10a1,1 0,0 1,-1 1h-16a1,1 0,0 1,-1 -1v-10a1,1 0,0 1,1 -1zM10,8v6l5,-3z M8,19h8v1.5h-8z" />
</vector>
```

- [ ] **Step 4: Testi yaz** — `app/src/test/java/com/afudm/afuremote/mode/AppModeTest.kt`
```kotlin
package com.afudm.afuremote.mode

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeTest {
    @Test
    fun `television ui or leanback feature selects TV`() {
        assertEquals(AppMode.TV, AppMode.detect(isTelevisionUi = true, hasLeanback = false, override = null))
        assertEquals(AppMode.TV, AppMode.detect(isTelevisionUi = false, hasLeanback = true, override = null))
    }

    @Test
    fun `plain phone selects PHONE`() {
        assertEquals(AppMode.PHONE, AppMode.detect(isTelevisionUi = false, hasLeanback = false, override = null))
    }

    @Test
    fun `manual override wins over detection and unknown override is ignored`() {
        assertEquals(AppMode.PHONE, AppMode.detect(isTelevisionUi = true, hasLeanback = true, override = "phone"))
        assertEquals(AppMode.TV, AppMode.detect(isTelevisionUi = false, hasLeanback = false, override = "tv"))
        assertEquals(AppMode.PHONE, AppMode.detect(isTelevisionUi = false, hasLeanback = false, override = "garbage"))
    }
}
```

- [ ] **Step 5: Mod kodu**

`app/src/main/java/com/afudm/afuremote/mode/AppMode.kt`:
```kotlin
package com.afudm.afuremote.mode

enum class AppMode {
    TV, PHONE;

    companion object {
        /** override: "tv" | "phone" | null (otomatik). Bilinmeyen değer otomatik sayılır. */
        fun detect(isTelevisionUi: Boolean, hasLeanback: Boolean, override: String?): AppMode = when (override) {
            "tv" -> TV
            "phone" -> PHONE
            else -> if (isTelevisionUi || hasLeanback) TV else PHONE
        }
    }
}
```

`app/src/main/java/com/afudm/afuremote/mode/ModeStore.kt`:
```kotlin
package com.afudm.afuremote.mode

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object ModeStore {
    private const val PREFS = "afuremote_mode"
    private const val KEY = "override"

    fun current(context: Context): AppMode {
        val ui = context.getSystemService(UiModeManager::class.java)
        val tvUi = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val leanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return AppMode.detect(tvUi, leanback, prefs(context).getString(KEY, null))
    }

    fun setOverride(context: Context, value: String?) {
        prefs(context).edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
```

`app/src/main/java/com/afudm/afuremote/AppVisibility.kt`:
```kotlin
package com.afudm.afuremote

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Uygulamanın bir ekranı görünür mü? (Erişilebilirlik kapalıyken yalnız o zaman ekran açabiliriz.) */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0
    val isForeground: Boolean get() = started > 0

    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) { started = maxOf(0, started - 1) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
```

`app/src/main/java/com/afudm/afuremote/AfuRemoteApp.kt`:
```kotlin
package com.afudm.afuremote

import android.app.Application

class AfuRemoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppVisibility)
    }
}
```

`app/src/main/java/com/afudm/afuremote/ui/Theme.kt`:
```kotlin
package com.afudm.afuremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AfuPurple = Color(0xFF7C5CFF)

@Composable
fun AfuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = AfuPurple, background = Color(0xFF110E1C), surface = Color(0xFF1B1530)),
        content = content
    )
}
```

`app/src/main/java/com/afudm/afuremote/ui/ModeSection.kt`:
```kotlin
package com.afudm.afuremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ModeSection(onModeChange: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Çalışma modu")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onModeChange(null) }) { Text("Otomatik") }
            OutlinedButton(onClick = { onModeChange("tv") }) { Text("TV") }
            OutlinedButton(onClick = { onModeChange("phone") }) { Text("Telefon") }
        }
    }
}
```

`app/src/main/java/com/afudm/afuremote/MainActivity.kt` (Task 1 hali — Task 5/6 ekranları bağlar):
```kotlin
package com.afudm.afuremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afudm.afuremote.mode.AppMode
import com.afudm.afuremote.mode.ModeStore
import com.afudm.afuremote.ui.AfuTheme
import com.afudm.afuremote.ui.ModeSection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyModeExtra(intent)
        setContent {
            AfuTheme {
                var mode by remember { mutableStateOf(ModeStore.current(this)) }
                val change: (String?) -> Unit = { ModeStore.setOverride(this, it); mode = ModeStore.current(this) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp)) {
                        Text(if (mode == AppMode.TV) "AfuRemote TV" else "AfuRemote")
                        ModeSection(change)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyModeExtra(intent)) recreate()
    }

    /** e2e ve elle test için: `am start ... --es mode tv|phone|auto`. Değişiklik olduysa true. */
    private fun applyModeExtra(intent: Intent?): Boolean {
        when (intent?.getStringExtra("mode")) {
            "tv" -> ModeStore.setOverride(this, "tv")
            "phone" -> ModeStore.setOverride(this, "phone")
            "auto" -> ModeStore.setOverride(this, null)
            else -> return false
        }
        return true
    }
}
```

`app/src/main/AndroidManifest.xml` (Task 1 hali; sonraki görevler bileşen ekler):
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:name=".AfuRemoteApp"
        android:label="@string/app_name"
        android:icon="@drawable/ic_launcher"
        android:banner="@drawable/banner"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: CI** — `.github/workflows/build.yml`
```yaml
name: Build AfuRemote

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Install Gradle & generate wrapper
        run: |
          GRADLE_VERSION="9.6.0"
          wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O /tmp/gradle.zip
          unzip -q /tmp/gradle.zip -d /tmp/
          export PATH="/tmp/gradle-${GRADLE_VERSION}/bin:$PATH"
          gradle wrapper --gradle-version ${GRADLE_VERSION} --distribution-type bin
          chmod +x gradlew

      - uses: gradle/actions/setup-gradle@v6

      - name: Unit tests + debug APK
        run: ./gradlew test assembleDebug --no-daemon --stacktrace

      - name: Upload debug APK
        uses: actions/upload-artifact@v7
        with:
          name: AfuRemote-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
          retention-days: 7

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: build-reports
          path: |
            app/build/reports/
            app/build/test-results/
          retention-days: 7
```
`.gitignore`'a `gradlew`, `gradlew.bat`, `gradle/wrapper/` ekle (CI her seferinde üretir).

- [ ] **Step 7: Push ve CI'ı doğrula**

```bash
git add -A && git commit -m "feat: project skeleton, mode detection, CI

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push -u origin v1
sleep 15; R=$(gh run list --branch v1 --limit 1 --json databaseId -q '.[0].databaseId'); gh run watch $R --exit-status
```
Expected: `build` yeşil; `AppModeTest` 3 test PASS (`gh run view $R --log | grep -E "AppModeTest|BUILD SUCCESSFUL"`). Kırmızıysa log'dan hatayı düzelt (en olası: bağımlılık sürümü — Media3 1.11.1 bulunamazsa Maven Central'daki en güncel 1.x'e düş ve spec'e not düş).

---

### Task 2: Protokol + link sınıflandırıcı

**Files:**
- Create: `app/src/main/java/com/afudm/afuremote/protocol/Protocol.kt`, `app/src/main/java/com/afudm/afuremote/classify/LinkClassifier.kt`
- Test: `app/src/test/java/com/afudm/afuremote/protocol/ProtocolTest.kt`, `app/src/test/java/com/afudm/afuremote/classify/LinkClassifierTest.kt`

**Interfaces:**
- Produces: sabitler `PROTOCOL_VERSION`, `TV_PORT`, `PHONE_MEDIA_PORT`, `SERVICE_TYPE`, `TOKEN_HEADER`, `HATA_ERISILEBILIRLIK`; `@Serializable` `InfoResponse(id, name, model, version, protocol)`, `PairRequest(deviceName, deviceId)`, `PairResponse(token)`, `OpenRequest(url, title = "", forceMedia = false)`, `KeyRequest(key)`, `ApiResult(ok, hata = "")`; `enum RemoteKey(wire)` + `RemoteKey.fromWire(String): RemoteKey?`; `val ProtocolJson: Json`. `enum LinkKind { MEDIA, YOUTUBE, WEB }`, `data class ClassifiedLink(kind, url, youtubeId: String? = null)`, `object LinkClassifier { fun extractUrl(sharedText: String): String?; fun classify(url: String): ClassifiedLink? }`.

- [ ] **Step 1: Testleri yaz**

`ProtocolTest.kt`:
```kotlin
package com.afudm.afuremote.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolTest {
    @Test
    fun `open request round trips with defaults`() {
        val json = ProtocolJson.encodeToString(OpenRequest("https://a/b.mp4"))
        assertEquals(OpenRequest("https://a/b.mp4", "", false), ProtocolJson.decodeFromString<OpenRequest>(json))
    }

    @Test
    fun `unknown fields from a newer phone are ignored`() {
        val req = ProtocolJson.decodeFromString<KeyRequest>("""{"key":"vol_up","future":1}""")
        assertEquals("vol_up", req.key)
    }

    @Test(expected = SerializationException::class)
    fun `missing required field fails loudly`() {
        ProtocolJson.decodeFromString<PairRequest>("""{"deviceName":"x"}""")
    }

    @Test
    fun `remote keys map from wire names`() {
        assertEquals(RemoteKey.PLAY_PAUSE, RemoteKey.fromWire("play_pause"))
        assertEquals(RemoteKey.SEEK_BACK, RemoteKey.fromWire("seek_back"))
        assertNull(RemoteKey.fromWire("launch_missiles"))
    }
}
```

`LinkClassifierTest.kt`:
```kotlin
package com.afudm.afuremote.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkClassifierTest {
    @Test
    fun `media extensions go to the player`() {
        for (u in listOf("https://x.com/a.mp4", "http://x.com/v/film.MKV?token=1", "https://x.com/live/index.m3u8", "https://x.com/d/manifest.mpd", "https://x.com/a.webm")) {
            assertEquals(u, LinkKind.MEDIA, LinkClassifier.classify(u)?.kind)
        }
    }

    @Test
    fun `youtube variants yield the video id`() {
        val cases = mapOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc" to "dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://youtube.com/shorts/abcDEF12345" to "abcDEF12345",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ" to "dQw4w9WgXcQ"
        )
        for ((url, id) in cases) {
            val c = LinkClassifier.classify(url)
            assertEquals(url, LinkKind.YOUTUBE, c?.kind)
            assertEquals(url, id, c?.youtubeId)
        }
    }

    @Test
    fun `youtube pages without a video are plain web`() {
        assertEquals(LinkKind.WEB, LinkClassifier.classify("https://www.youtube.com/@kanal")?.kind)
    }

    @Test
    fun `other sites are web and non http is rejected`() {
        assertEquals(LinkKind.WEB, LinkClassifier.classify("https://www.trt.net.tr/canli")?.kind)
        assertNull(LinkClassifier.classify("ftp://x/a.mp4"))
        assertNull(LinkClassifier.classify("   "))
        assertNull(LinkClassifier.classify("javascript:alert(1)"))
    }

    @Test
    fun `url is extracted from shared text with punctuation around it`() {
        assertEquals("https://x.com/a.mp4", LinkClassifier.extractUrl("Bunu izle: https://x.com/a.mp4."))
        assertEquals("https://youtu.be/abc123XYZ", LinkClassifier.extractUrl("(https://youtu.be/abc123XYZ)"))
        assertEquals("https://x.com/a?b=1", LinkClassifier.extractUrl("\"https://x.com/a?b=1\", dedi"))
        assertNull(LinkClassifier.extractUrl("link yok burada"))
    }
}
```

- [ ] **Step 2: Kodu yaz**

`Protocol.kt`:
```kotlin
package com.afudm.afuremote.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = "v1"
const val TV_PORT = 9870
const val PHONE_MEDIA_PORT = 9871
const val SERVICE_TYPE = "_afuremote._tcp"
const val TOKEN_HEADER = "X-Afu-Token"
const val HATA_ERISILEBILIRLIK = "erisilebilirlik_kapali"

@Serializable
data class InfoResponse(val id: String, val name: String, val model: String, val version: String, val protocol: String = PROTOCOL_VERSION)

@Serializable
data class PairRequest(val deviceName: String, val deviceId: String)

@Serializable
data class PairResponse(val token: String)

@Serializable
data class OpenRequest(val url: String, val title: String = "", val forceMedia: Boolean = false)

@Serializable
data class KeyRequest(val key: String)

@Serializable
data class ApiResult(val ok: Boolean, val hata: String = "")

enum class RemoteKey(val wire: String) {
    BACK("back"), HOME("home"), VOL_UP("vol_up"), VOL_DOWN("vol_down"), MUTE("mute"),
    PLAY_PAUSE("play_pause"), SEEK_FWD("seek_fwd"), SEEK_BACK("seek_back");

    companion object {
        fun fromWire(value: String): RemoteKey? = entries.firstOrNull { it.wire == value }
    }
}

val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

`LinkClassifier.kt`:
```kotlin
package com.afudm.afuremote.classify

import java.net.URI

enum class LinkKind { MEDIA, YOUTUBE, WEB }

data class ClassifiedLink(val kind: LinkKind, val url: String, val youtubeId: String? = null)

object LinkClassifier {
    private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "m3u8", "mpd", "mov", "m4v", "ts")
    private val URL_IN_TEXT = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{6,}$")

    fun extractUrl(sharedText: String): String? =
        URL_IN_TEXT.find(sharedText)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')

    fun classify(url: String): ClassifiedLink? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        youtubeId(host, uri)?.let { return ClassifiedLink(LinkKind.YOUTUBE, trimmed, it) }
        val extension = uri.path.orEmpty().substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return ClassifiedLink(if (extension in MEDIA_EXTENSIONS) LinkKind.MEDIA else LinkKind.WEB, trimmed)
    }

    private fun youtubeId(host: String, uri: URI): String? {
        val path = uri.path.orEmpty()
        val id = when (host) {
            "youtu.be" -> path.trim('/').substringBefore('/')
            "youtube.com", "music.youtube.com" -> when {
                path == "/watch" -> queryParam(uri, "v")
                path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/") -> path.split('/').getOrNull(2)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { YOUTUBE_ID.matches(it) }
    }

    private fun queryParam(uri: URI, key: String): String? =
        uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == key }?.getOrNull(1)
}
```

- [ ] **Step 3: Push, CI'ı doğrula**

```bash
git add -A && git commit -m "feat: wire protocol and link classifier

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push; sleep 15; R=$(gh run list --branch v1 --limit 1 --json databaseId -q '.[0].databaseId'); gh run watch $R --exit-status
```
Expected: yeşil; `ProtocolTest` 4, `LinkClassifierTest` 5 test PASS.

---

### Task 3: Eşleştirme token'ları

**Files:**
- Create: `app/src/main/java/com/afudm/afuremote/pairing/TokenRegistry.kt`, `app/src/main/java/com/afudm/afuremote/pairing/PairingStore.kt`, `app/src/main/java/com/afudm/afuremote/phone/TokenStore.kt`
- Test: `app/src/test/java/com/afudm/afuremote/pairing/TokenRegistryTest.kt`

**Interfaces:**
- Consumes: `ProtocolJson` (Task 2).
- Produces: `class TokenRegistry(tokens: MutableMap<String,String> = mutableMapOf(), newToken: () -> String = { randomToken() })` { `issue(deviceId): String`, `isValid(token: String?): Boolean`, `snapshot(): Map<String,String>`, `clear()` } + `TokenRegistry.randomToken(): String` (48 hex); `interface TokenStore { tokenForTv(tvId): String?; saveTokenForTv(tvId, token); forgetTv(tvId); deviceId(): String }`; `class PairingStore(context): TokenStore` + `approvedDevices(): Map<String,String>`, `tvRegistry(): TokenRegistry`, `saveApproved(map)`.

- [ ] **Step 1: Test** — `TokenRegistryTest.kt`
```kotlin
package com.afudm.afuremote.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRegistryTest {
    @Test
    fun `issued token is valid and blank or unknown tokens are not`() {
        val reg = TokenRegistry()
        val t = reg.issue("telefon-1")
        assertTrue(reg.isValid(t))
        assertFalse(reg.isValid(null))
        assertFalse(reg.isValid(""))
        assertFalse(reg.isValid("uydurma"))
    }

    @Test
    fun `re-pairing the same device replaces its old token`() {
        var n = 0
        val reg = TokenRegistry(newToken = { "t${++n}" })
        val first = reg.issue("telefon-1")
        val second = reg.issue("telefon-1")
        assertNotEquals(first, second)
        assertFalse(reg.isValid(first))
        assertTrue(reg.isValid(second))
    }

    @Test
    fun `clear revokes everything and snapshot reflects state`() {
        val reg = TokenRegistry(mutableMapOf("a" to "x"))
        assertEquals(mapOf("a" to "x"), reg.snapshot())
        reg.clear()
        assertFalse(reg.isValid("x"))
        assertEquals(emptyMap<String, String>(), reg.snapshot())
    }

    @Test
    fun `random tokens are long and distinct`() {
        val a = TokenRegistry.randomToken()
        assertEquals(48, a.length)
        assertNotEquals(a, TokenRegistry.randomToken())
    }
}
```

- [ ] **Step 2: Kod**

`TokenRegistry.kt`:
```kotlin
package com.afudm.afuremote.pairing

import java.security.SecureRandom

/** TV tarafı: onaylı cihaz → token. NanoHTTPD birden çok iş parçacığından çağırır. */
class TokenRegistry(
    private val tokens: MutableMap<String, String> = mutableMapOf(),
    private val newToken: () -> String = { randomToken() }
) {
    @Synchronized fun issue(deviceId: String): String = newToken().also { tokens[deviceId] = it }

    @Synchronized fun isValid(token: String?): Boolean = !token.isNullOrBlank() && token in tokens.values

    @Synchronized fun snapshot(): Map<String, String> = tokens.toMap()

    @Synchronized fun clear() = tokens.clear()

    companion object {
        private val random = SecureRandom()

        fun randomToken(): String {
            val bytes = ByteArray(24)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
```

`phone/TokenStore.kt`:
```kotlin
package com.afudm.afuremote.phone

/** Telefon tarafı: TV başına token + bu cihazın kalıcı kimliği. */
interface TokenStore {
    fun tokenForTv(tvId: String): String?
    fun saveTokenForTv(tvId: String, token: String)
    fun forgetTv(tvId: String)
    fun deviceId(): String
}
```

`PairingStore.kt`:
```kotlin
package com.afudm.afuremote.pairing

import android.content.Context
import com.afudm.afuremote.phone.TokenStore
import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class PairingStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("afuremote_pairing", Context.MODE_PRIVATE)

    // ── TV tarafı ──
    fun approvedDevices(): Map<String, String> = read(KEY_APPROVED)
    fun tvRegistry(): TokenRegistry = TokenRegistry(approvedDevices().toMutableMap())
    fun saveApproved(map: Map<String, String>) = write(KEY_APPROVED, map)

    // ── Telefon tarafı ──
    override fun tokenForTv(tvId: String): String? = read(KEY_TV_TOKENS)[tvId]
    override fun saveTokenForTv(tvId: String, token: String) = write(KEY_TV_TOKENS, read(KEY_TV_TOKENS) + (tvId to token))
    override fun forgetTv(tvId: String) = write(KEY_TV_TOKENS, read(KEY_TV_TOKENS) - tvId)

    @Synchronized
    override fun deviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null) ?: TokenRegistry.randomToken().take(16).also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    private fun read(key: String): Map<String, String> =
        prefs.getString(key, null)?.let { runCatching { ProtocolJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()

    private fun write(key: String, map: Map<String, String>) {
        prefs.edit().putString(key, ProtocolJson.encodeToString(map)).apply()
    }

    private companion object {
        const val KEY_APPROVED = "approved"
        const val KEY_TV_TOKENS = "tv_tokens"
        const val KEY_DEVICE_ID = "device_id"
    }
}
```

- [ ] **Step 3: Push, CI yeşil** (komut Task 2 Step 3 ile aynı; commit mesajı `feat: pairing tokens and store`). Expected: `TokenRegistryTest` 4 test PASS.

---

### Task 4: TV istek yönlendiricisi (saf mantık)

**Files:**
- Create: `app/src/main/java/com/afudm/afuremote/tv/TvRouter.kt`
- Test: `app/src/test/java/com/afudm/afuremote/tv/TvRouterTest.kt`

**Interfaces:**
- Consumes: Task 2 protokol + `LinkClassifier`; Task 3 `TokenRegistry`.
- Produces: `enum class PairDecision { APPROVED, DENIED, CANNOT_PROMPT }`; `interface TvActions { fun info(): InfoResponse; suspend fun askPairApproval(req: PairRequest): PairDecision; fun open(link: ClassifiedLink, title: String): ApiResult; fun key(key: RemoteKey): ApiResult }`; `data class RouterResponse(val status: Int, val body: String)`; `class TvRouter(actions: TvActions, registry: TokenRegistry, onPairingsChanged: (Map<String,String>) -> Unit) { suspend fun handle(method: String, path: String, token: String?, body: String): RouterResponse }`.

- [ ] **Step 1: Test** — `TvRouterTest.kt`
```kotlin
package com.afudm.afuremote.tv

import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.PairResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRouterTest {
    private class FakeActions(var decision: PairDecision = PairDecision.APPROVED, var keyResult: ApiResult = ApiResult(true)) : TvActions {
        val opened = mutableListOf<ClassifiedLink>()
        val keys = mutableListOf<RemoteKey>()
        override fun info() = InfoResponse("tv-1", "Salon", "X", "0.1.0")
        override suspend fun askPairApproval(req: PairRequest) = decision
        override fun open(link: ClassifiedLink, title: String): ApiResult { opened += link; return ApiResult(true) }
        override fun key(key: RemoteKey): ApiResult { keys += key; return keyResult }
    }

    private val actions = FakeActions()
    private var saved: Map<String, String> = emptyMap()
    private val registry = TokenRegistry()
    private val router = TvRouter(actions, registry) { saved = it }

    private fun call(method: String, path: String, token: String? = null, body: String = "") =
        runBlocking { router.handle(method, path, token, body) }

    private fun pair(): String {
        val r = call("POST", "/v1/pair", body = """{"deviceName":"Tel","deviceId":"d1"}""")
        assertEquals(200, r.status)
        return ProtocolJson.decodeFromString<PairResponse>(r.body).token
    }

    @Test
    fun `info needs no token`() {
        val r = call("GET", "/v1/info")
        assertEquals(200, r.status)
        assertTrue(r.body.contains("\"protocol\":\"v1\""))
    }

    @Test
    fun `commands without a valid token are rejected`() {
        assertEquals(401, call("POST", "/v1/key", null, """{"key":"vol_up"}""").status)
        assertEquals(401, call("POST", "/v1/key", "yanlis", """{"key":"vol_up"}""").status)
        assertTrue(actions.keys.isEmpty())
    }

    @Test
    fun `approved pairing issues a working token and persists it`() {
        val token = pair()
        assertEquals(mapOf("d1" to token), saved)
        assertEquals(200, call("POST", "/v1/key", token, """{"key":"vol_up"}""").status)
        assertEquals(listOf(RemoteKey.VOL_UP), actions.keys)
    }

    @Test
    fun `denied pairing is 403 and prompt impossible is 409`() {
        actions.decision = PairDecision.DENIED
        assertEquals(403, call("POST", "/v1/pair", body = """{"deviceName":"T","deviceId":"d"}""").status)
        actions.decision = PairDecision.CANNOT_PROMPT
        val r = call("POST", "/v1/pair", body = """{"deviceName":"T","deviceId":"d"}""")
        assertEquals(409, r.status)
        assertTrue(r.body.contains(HATA_ERISILEBILIRLIK))
    }

    @Test
    fun `open classifies the link and forceMedia overrides it`() {
        val token = pair()
        assertEquals(200, call("POST", "/v1/open", token, """{"url":"https://x.com/a.mp4"}""").status)
        assertEquals(200, call("POST", "/v1/open", token, """{"url":"http://10.0.0.5:9871/m/abc","forceMedia":true}""").status)
        assertEquals(listOf(LinkKind.MEDIA, LinkKind.MEDIA), actions.opened.map { it.kind })
    }

    @Test
    fun `bad input never crashes the server`() {
        val token = pair()
        assertEquals(400, call("POST", "/v1/open", token, """{"url":"ftp://x"}""").status)
        assertEquals(400, call("POST", "/v1/open", token, "").status)
        assertEquals(400, call("POST", "/v1/open", token, "{bozuk").status)
        assertEquals(400, call("POST", "/v1/key", token, """{"key":"uc"}""").status)
        assertEquals(400, call("POST", "/v1/pair", body = """{"deviceName":"x"}""").status)
        assertEquals(404, call("GET", "/v1/open", token).status)
        assertEquals(404, call("POST", "/baska", token, "{}").status)
    }

    @Test
    fun `accessibility off maps to 409`() {
        val token = pair()
        actions.keyResult = ApiResult(false, HATA_ERISILEBILIRLIK)
        assertEquals(409, call("POST", "/v1/key", token, """{"key":"back"}""").status)
    }
}
```

- [ ] **Step 2: Kod** — `TvRouter.kt`
```kotlin
package com.afudm.afuremote.tv

import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.PairResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

enum class PairDecision { APPROVED, DENIED, CANNOT_PROMPT }

interface TvActions {
    fun info(): InfoResponse
    suspend fun askPairApproval(req: PairRequest): PairDecision
    fun open(link: ClassifiedLink, title: String): ApiResult
    fun key(key: RemoteKey): ApiResult
}

data class RouterResponse(val status: Int, val body: String)

class TvRouter(
    private val actions: TvActions,
    private val registry: TokenRegistry,
    private val onPairingsChanged: (Map<String, String>) -> Unit
) {
    suspend fun handle(method: String, path: String, token: String?, body: String): RouterResponse =
        try {
            route(method, path, token, body)
        } catch (e: IllegalArgumentException) { // SerializationException dahil
            error(400, "bozuk_istek")
        }

    private suspend fun route(method: String, path: String, token: String?, body: String): RouterResponse {
        if (method == "GET" && path == "/v1/info") return ok(ProtocolJson.encodeToString(actions.info()))
        if (method == "POST" && path == "/v1/pair") return pair(ProtocolJson.decodeFromString<PairRequest>(body))
        if (method != "POST" || (path != "/v1/open" && path != "/v1/key")) return error(404, "yok")
        if (!registry.isValid(token)) return error(401, "izin_yok")
        return if (path == "/v1/open") open(ProtocolJson.decodeFromString<OpenRequest>(body))
        else key(ProtocolJson.decodeFromString<KeyRequest>(body))
    }

    private suspend fun pair(req: PairRequest): RouterResponse = when (actions.askPairApproval(req)) {
        PairDecision.APPROVED -> {
            val token = registry.issue(req.deviceId)
            onPairingsChanged(registry.snapshot())
            ok(ProtocolJson.encodeToString(PairResponse(token)))
        }
        PairDecision.DENIED -> error(403, "reddedildi")
        PairDecision.CANNOT_PROMPT -> error(409, HATA_ERISILEBILIRLIK)
    }

    private fun open(req: OpenRequest): RouterResponse {
        val link = LinkClassifier.classify(req.url) ?: return error(400, "gecersiz_link")
        val target = if (req.forceMedia) link.copy(kind = LinkKind.MEDIA) else link
        return result(actions.open(target, req.title))
    }

    private fun key(req: KeyRequest): RouterResponse {
        val key = RemoteKey.fromWire(req.key) ?: return error(400, "bilinmeyen_tus")
        return result(actions.key(key))
    }

    private fun result(r: ApiResult) = RouterResponse(
        when {
            r.ok -> 200
            r.hata == HATA_ERISILEBILIRLIK -> 409
            else -> 500
        },
        ProtocolJson.encodeToString(r)
    )

    private fun ok(json: String) = RouterResponse(200, json)
    private fun error(status: Int, hata: String) = RouterResponse(status, ProtocolJson.encodeToString(ApiResult(false, hata)))
}
```

- [ ] **Step 3: Push, CI yeşil** (commit: `feat: TV request router`). Expected: `TvRouterTest` 7 test PASS.

---

### Task 5: TV çalışma zamanı + TV e2e

**Files:**
- Create: `net/LocalIp.kt`, `tv/{TvHttpServer, AfuTvService, NsdAdvertiser, AndroidTvActions, PairBroker, PairPromptActivity, PlayerActivity, PlayerRegistry, WebActivity, RemoteAccessibilityService, BootReceiver, TvHomeScreen}.kt`, `res/xml/accessibility_service.xml`, `scripts/ui.py`, `scripts/e2e.sh`
- Modify: `AndroidManifest.xml` (izinler + bileşenler), `MainActivity.kt` (TV ekranı + servis başlatma), `.github/workflows/build.yml` (e2e job)

**Interfaces:**
- Consumes: Task 2-4.
- Produces: `AfuTvService.start(context)`, `AfuTvService.resetPairings(context)`; `RemoteAccessibilityService.instance`; `PlayerRegistry.current: Player?`; `object LocalIp { fun wifiIpv4(): String? }`; `@Composable fun TvHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {})`; logcat sözleşmesi: `AfuRemotePlayer: state=PLAYING|PAUSED`, `AfuRemoteTv: TV sunucusu hazir`.

- [ ] **Step 1: Ağ yardımcısı** — `net/LocalIp.kt`
```kotlin
package com.afudm.afuremote.net

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalIp {
    /** Wi-Fi/Ethernet üzerindeki özel IPv4 (192.168.x.x, 10.x.x.x ...). */
    fun wifiIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
```

- [ ] **Step 2: HTTP sunucu + NSD** 

`tv/TvHttpServer.kt`:
```kotlin
package com.afudm.afuremote.tv

import com.afudm.afuremote.protocol.TOKEN_HEADER
import com.afudm.afuremote.protocol.TV_PORT
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

class TvHttpServer(private val router: TvRouter) : NanoHTTPD(TV_PORT) {
    override fun serve(session: IHTTPSession): Response {
        val body = if (session.method == Method.POST) readBody(session) else ""
        val r = runBlocking {
            router.handle(session.method.name, session.uri, session.headers[TOKEN_HEADER.lowercase()], body)
        }
        return newFixedLengthResponse(status(r.status), "application/json; charset=utf-8", r.body)
    }

    private fun status(code: Int): Response.Status = when (code) {
        200 -> Response.Status.OK
        400 -> Response.Status.BAD_REQUEST
        401 -> Response.Status.UNAUTHORIZED
        403 -> Response.Status.FORBIDDEN
        404 -> Response.Status.NOT_FOUND
        409 -> Response.Status.CONFLICT
        else -> Response.Status.INTERNAL_ERROR
    }

    private fun readBody(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return ""
        if (length <= 0) return ""
        val buffer = ByteArray(minOf(length, 64 * 1024))
        var read = 0
        while (read < buffer.size) {
            val n = session.inputStream.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }
}
```

`tv/NsdAdvertiser.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.afudm.afuremote.protocol.SERVICE_TYPE
import com.afudm.afuremote.protocol.TV_PORT

class NsdAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun register(serviceName: String) {
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = SERVICE_TYPE
            port = TV_PORT
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "NSD kayitli: ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, errorCode: Int) { Log.e(TAG, "NSD kayit hatasi: $errorCode") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, errorCode: Int) {}
        }
        listener = l
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun unregister() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
    }

    private companion object { const val TAG = "AfuRemoteTv" }
}
```

- [ ] **Step 3: Oynatıcı, web, erişilebilirlik**

`tv/PlayerRegistry.kt`:
```kotlin
package com.afudm.afuremote.tv

import androidx.media3.common.Player

/** Açık oynatıcı (yoksa null). Kumanda tuşları buna yönlendirilir; erişim ana iş parçacığında. */
object PlayerRegistry {
    @Volatile var current: Player? = null
}
```

`tv/PlayerActivity.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this)
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        val p = ExoPlayer.Builder(this).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "state=" + if (isPlaying) "PLAYING" else "PAUSED")
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "oynatma hatasi: ${error.errorCodeName}")
                Toast.makeText(this@PlayerActivity, "Video oynatılamadı: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        player = p
        view.player = p
        PlayerRegistry.current = p
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val p = player ?: return
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        if (PlayerRegistry.current === player) PlayerRegistry.current = null
        player?.release()
        player = null
        finish()
    }

    companion object {
        private const val TAG = "AfuRemotePlayer"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        fun intent(context: Context, url: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title)
    }
}
```

`tv/WebActivity.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback

class WebActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finish()
        }
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        web.loadUrl(url)
    }

    companion object {
        const val EXTRA_URL = "url"
        fun intent(context: Context, url: String): Intent = Intent(context, WebActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
```

`tv/RemoteAccessibilityService.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Geri / ana ekran tuşları ve (Android 10+ arka plan kısıtı nedeniyle) uygulama görünür değilken
 * oynatıcı / izin ekranı açabilmek için. Ekran içeriği okunmaz.
 */
class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: RemoteAccessibilityService? = null
    }
}
```

`res/xml/accessibility_service.xml`:
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="false"
    android:description="@string/erisilebilirlik_aciklama" />
```

- [ ] **Step 4: Eşleştirme onayı**

`tv/PairBroker.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.content.Context
import android.content.Intent
import com.afudm.afuremote.protocol.PairRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** HTTP isteği TV'deki "İzin ver / Reddet" cevabını bekler (en çok 60 sn). */
object PairBroker {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    suspend fun request(context: Context, launch: (Intent) -> LaunchResult, req: PairRequest): PairDecision {
        val answer = CompletableDeferred<Boolean>()
        pending[req.deviceId] = answer
        val intent = Intent(context, PairPromptActivity::class.java)
            .putExtra(PairPromptActivity.EXTRA_ID, req.deviceId)
            .putExtra(PairPromptActivity.EXTRA_NAME, req.deviceName)
        if (launch(intent) != LaunchResult.OK) {
            pending.remove(req.deviceId)
            return PairDecision.CANNOT_PROMPT
        }
        return try {
            if (withTimeoutOrNull(60_000) { answer.await() } == true) PairDecision.APPROVED else PairDecision.DENIED
        } finally {
            pending.remove(req.deviceId)
        }
    }

    fun answer(deviceId: String, approved: Boolean) {
        pending[deviceId]?.complete(approved)
    }
}
```

`tv/PairPromptActivity.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.ui.AfuTheme

class PairPromptActivity : ComponentActivity() {
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Bir telefon" }
        setContent {
            AfuTheme {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
                    ) {
                        Text("\"$name\" bu TV'yi kumanda etmek istiyor", fontSize = 26.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { reply(id, true) }, modifier = Modifier.focusRequester(focus)) { Text("İzin ver", fontSize = 20.sp) }
                            OutlinedButton(onClick = { reply(id, false) }) { Text("Reddet", fontSize = 20.sp) }
                        }
                    }
                }
            }
        }
    }

    private fun reply(id: String, approved: Boolean) {
        answered = true
        PairBroker.answer(id, approved)
        finish()
    }

    override fun onDestroy() {
        if (!answered && isFinishing) PairBroker.answer(intent.getStringExtra(EXTRA_ID).orEmpty(), false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "deviceId"
        const val EXTRA_NAME = "deviceName"
    }
}
```

- [ ] **Step 5: Android eylemleri** — `tv/AndroidTvActions.kt`
```kotlin
package com.afudm.afuremote.tv

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import com.afudm.afuremote.AppVisibility
import com.afudm.afuremote.BuildConfig
import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.PairingStore
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey

enum class LaunchResult { OK, NOT_ALLOWED, NO_APP }

class AndroidTvActions(private val context: Context, private val store: PairingStore) : TvActions {
    private val main = Handler(Looper.getMainLooper())

    fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    override fun info() = InfoResponse(id = store.deviceId(), name = deviceName(), model = Build.MODEL, version = BuildConfig.VERSION_NAME)

    override suspend fun askPairApproval(req: PairRequest): PairDecision = PairBroker.request(context, ::launch, req)

    override fun open(link: ClassifiedLink, title: String): ApiResult {
        val first = when (link.kind) {
            LinkKind.MEDIA -> launch(PlayerActivity.intent(context, link.url, title))
            LinkKind.WEB -> launch(WebActivity.intent(context, link.url))
            LinkKind.YOUTUBE -> launch(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${link.youtubeId}")).setPackage(YOUTUBE_TV)
            ).let { if (it == LaunchResult.NO_APP) launch(WebActivity.intent(context, link.url)) else it }
        }
        return when (first) {
            LaunchResult.OK -> ApiResult(true)
            LaunchResult.NOT_ALLOWED -> ApiResult(false, HATA_ERISILEBILIRLIK)
            LaunchResult.NO_APP -> ApiResult(false, "acacak_uygulama_yok")
        }
    }

    override fun key(key: RemoteKey): ApiResult {
        val audio = context.getSystemService(AudioManager::class.java)
        when (key) {
            RemoteKey.VOL_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            RemoteKey.VOL_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            RemoteKey.MUTE -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            RemoteKey.PLAY_PAUSE, RemoteKey.SEEK_FWD, RemoteKey.SEEK_BACK -> playerKey(audio, key)
            RemoteKey.BACK, RemoteKey.HOME -> {
                val service = RemoteAccessibilityService.instance ?: return ApiResult(false, HATA_ERISILEBILIRLIK)
                service.performGlobalAction(
                    if (key == RemoteKey.BACK) AccessibilityService.GLOBAL_ACTION_BACK else AccessibilityService.GLOBAL_ACTION_HOME
                )
            }
        }
        return ApiResult(true)
    }

    private fun playerKey(audio: AudioManager, key: RemoteKey) {
        val player = PlayerRegistry.current
        if (player == null) {
            val code = when (key) {
                RemoteKey.SEEK_FWD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                RemoteKey.SEEK_BACK -> KeyEvent.KEYCODE_MEDIA_REWIND
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            return
        }
        main.post {
            when (key) {
                RemoteKey.PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                RemoteKey.SEEK_FWD -> player.seekTo(player.currentPosition + 10_000)
                else -> player.seekTo(maxOf(0L, player.currentPosition - 10_000))
            }
        }
    }

    /** Android 10+ arka plandan ekran açmayı engeller: erişilebilirlik servisi bağlıysa onunla, değilse yalnız uygulama görünürken. */
    fun launch(intent: Intent): LaunchResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val starter: Context = RemoteAccessibilityService.instance
            ?: if (AppVisibility.isForeground) context else return LaunchResult.NOT_ALLOWED
        return try {
            starter.startActivity(intent)
            LaunchResult.OK
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NO_APP
        } catch (e: SecurityException) {
            LaunchResult.NOT_ALLOWED
        }
    }

    private companion object { const val YOUTUBE_TV = "com.google.android.youtube.tv" }
}
```

- [ ] **Step 6: Servis, açılışta başlatma, TV ekranı**

`tv/AfuTvService.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.afudm.afuremote.R
import com.afudm.afuremote.pairing.PairingStore
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.TV_PORT
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class AfuTvService : Service() {
    private var server: TvHttpServer? = null
    private var advertiser: NsdAdvertiser? = null
    private var registry: TokenRegistry? = null
    private lateinit var store: PairingStore

    override fun onCreate() {
        super.onCreate()
        store = PairingStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_RESET_PAIRINGS) {
            registry?.clear()
            store.saveApproved(emptyMap())
        }
        if (server == null) startServer()
        return START_STICKY
    }

    private fun startServer() {
        val reg = store.tvRegistry().also { registry = it }
        val actions = AndroidTvActions(applicationContext, store)
        val router = TvRouter(actions, reg) { store.saveApproved(it) }
        server = try {
            TvHttpServer(router).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        } catch (e: IOException) {
            Log.e(TAG, "TV sunucusu acilamadi", e)
            null
        }
        if (server != null) {
            Log.i(TAG, "TV sunucusu hazir: port $TV_PORT")
            advertiser = NsdAdvertiser(this).also { it.register("AfuRemote ${actions.deviceName()}".take(60)) }
        }
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "AfuRemote TV", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("AfuRemote TV hazır")
            .setContentText("Telefondan gelen linkler bu TV'de açılır")
            .setOngoing(true)
            .build()
        // Android 14+: türsüz ön plan servisi uygulamayı çökertir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        advertiser?.unregister()
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AfuRemoteTv"
        private const val CHANNEL = "afuremote_tv"
        private const val NOTIFICATION_ID = 9870
        const val ACTION_RESET_PAIRINGS = "com.afudm.afuremote.RESET_PAIRINGS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AfuTvService::class.java))
        }

        fun resetPairings(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AfuTvService::class.java).setAction(ACTION_RESET_PAIRINGS))
        }
    }
}
```

`tv/BootReceiver.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afudm.afuremote.mode.AppMode
import com.afudm.afuremote.mode.ModeStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && ModeStore.current(context) == AppMode.TV) {
            AfuTvService.start(context)
        }
    }
}
```

`tv/TvHomeScreen.kt`:
```kotlin
package com.afudm.afuremote.tv

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.net.LocalIp
import com.afudm.afuremote.ui.ModeSection
import kotlinx.coroutines.delay

@Composable
fun TvHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val ip = remember { LocalIp.wifiIpv4() ?: "Wi-Fi bağlantısı yok" }
    val accessibilityOn by produceState(RemoteAccessibilityService.instance != null) {
        while (true) {
            value = RemoteAccessibilityService.instance != null
            delay(1_000)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("AfuRemote TV hazır", fontSize = 30.sp)
        Text("Telefonda AfuRemote'u açın — bu TV listede kendiliğinden görünür.", fontSize = 18.sp)
        Text("Adres: $ip   ·   Sürüm $versionName")
        Text(
            if (accessibilityOn) "Erişilebilirlik: açık ✓"
            else "Erişilebilirlik: KAPALI — geri/ana ekran tuşları ve AfuRemote kapalıyken link açma için bir kez açın.",
            fontSize = 16.sp
        )
        if (!accessibilityOn) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Erişilebilirlik ayarlarını aç") }
        }
        OutlinedButton(onClick = { AfuTvService.resetPairings(context) }) { Text("Telefon onaylarını sıfırla") }
        footer()
        ModeSection(onModeChange)
    }
}
```

- [ ] **Step 7: MainActivity'yi TV ekranına bağla** — `setContent` gövdesini şununla değiştir:
```kotlin
        setContent {
            AfuTheme {
                var mode by remember { mutableStateOf(ModeStore.current(this)) }
                val change: (String?) -> Unit = { ModeStore.setOverride(this, it); mode = ModeStore.current(this) }
                LaunchedEffect(mode) { if (mode == AppMode.TV) AfuTvService.start(this@MainActivity) }
                Surface(Modifier.fillMaxSize()) {
                    when (mode) {
                        AppMode.TV -> TvHomeScreen(BuildConfig.VERSION_NAME, change)
                        AppMode.PHONE -> Column(Modifier.padding(24.dp)) {
                            Text("AfuRemote")
                            ModeSection(change)
                        }
                    }
                }
            }
        }
```
İmportlara ekle: `androidx.compose.runtime.LaunchedEffect`, `com.afudm.afuremote.tv.AfuTvService`, `com.afudm.afuremote.tv.TvHomeScreen`.

- [ ] **Step 8: Manifest** — `<manifest>` içine, `<application>`'dan önce:
```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

    <queries>
        <package android:name="com.google.android.youtube.tv" />
    </queries>
```
`<application>` içine, MainActivity'den sonra:
```xml
        <activity android:name=".tv.PlayerActivity" android:exported="false" android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize" />
        <activity android:name=".tv.WebActivity" android:exported="false" android:launchMode="singleTask" />
        <activity android:name=".tv.PairPromptActivity" android:exported="false" android:excludeFromRecents="true" />

        <service android:name=".tv.AfuTvService" android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <service android:name=".tv.RemoteAccessibilityService" android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service" />
        </service>

        <receiver android:name=".tv.BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 9: e2e betikleri**

`scripts/ui.py`: `Desktop/afuproject/AfuDM-apk/AfuTube/scripts/ui.py` dosyasını **aynen** kopyala (`cp ../AfuDM-apk/AfuTube/scripts/ui.py scripts/ui.py`).

`scripts/e2e.sh`:
```bash
#!/usr/bin/env bash
# AfuRemote uçtan uca testi — TEK emülatör, iki rol (TV sunucusu + telefon istemcisi).
set -u
APK="$1"
PKG=com.afudm.afuremote
OUT=e2e-out
MP4="https://www.w3schools.com/html/mov_bbb.mp4"
UI="python3 scripts/ui.py"
TOKEN=""
mkdir -p "$OUT"

fail() {
  echo "HATA: $*"
  adb exec-out screencap -p > "$OUT/hata.png"
  adb logcat -d > "$OUT/logcat-son.txt"
  grep -A25 "FATAL EXCEPTION" "$OUT/tam.log" | head -60
  exit 1
}
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/$1.xml" 2>/dev/null; }
wait_text() { # wait_text <sn> <metin> <dosya-adi>
  local lim=$1 text=$2 name=$3 t=0
  while [ $t -lt "$lim" ]; do dump "$name"; grep -q "$text" "$OUT/$name.xml" && return 0; sleep 2; t=$((t+2)); done
  return 1
}
wait_log() { # wait_log <sn> <desen>
  local lim=$1 pattern=$2 t=0
  while [ $t -lt "$lim" ]; do adb logcat -d | grep -q "$pattern" && return 0; sleep 2; t=$((t+2)); done
  return 1
}
api() { # api <METHOD> <yol> [govde] [zaman-asimi] -> HTTP kodu; yanıt $OUT/resp.json
  local method=$1 path=$2 body=${3:-} maxt=${4:-10}
  local args=(-s -o "$OUT/resp.json" -w "%{http_code}" --max-time "$maxt" -X "$method" -H "Content-Type: application/json")
  [ -n "$TOKEN" ] && args+=(-H "X-Afu-Token: $TOKEN")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}" "http://127.0.0.1:9870$path"
}
tap_text() { local xy; xy=$($UI tap-text "$OUT/$1.xml" "$2") || return 1; adb shell input tap $xy; }

adb install -r "$APK" || fail "APK kurulamadi"
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.tv.RemoteAccessibilityService"
adb shell settings put secure accessibility_enabled 1
adb logcat -c
adb logcat -v time > "$OUT/tam.log" &

# 1) TV modu + sunucu
adb shell am start -n "$PKG/.MainActivity" --es mode tv >/dev/null
wait_text 60 "AfuRemote TV" tv_home || fail "TV ana ekrani gelmedi"
adb forward tcp:9870 tcp:9870 >/dev/null
code=""
for _ in $(seq 1 15); do code=$(api GET /v1/info); [ "$code" = 200 ] && break; sleep 2; done
[ "$code" = 200 ] && grep -q '"protocol":"v1"' "$OUT/resp.json" || fail "/v1/info 200 donmedi ($code)"
echo "OK 1: TV sunucusu calisiyor"

# 2) izinsiz istek
code=$(api POST /v1/key '{"key":"vol_up"}'); [ "$code" = 401 ] || fail "tokensiz istek 401 olmali ($code)"
echo "OK 2: izinsiz istek reddedildi"

# 3) eslestirme: curl arkada bekler, TV'de "İzin ver" tiklanir
( code=$(api POST /v1/pair '{"deviceName":"e2e","deviceId":"e2e-cihaz"}' 70); echo "$code" > "$OUT/pair_code.txt"; cp "$OUT/resp.json" "$OUT/pair.json" ) &
PAIR_PID=$!
wait_text 30 "İzin ver" pair_prompt || fail "TV'de izin ekrani cikmadi"
tap_text pair_prompt "İzin ver" || fail "Izin ver dugmesi bulunamadi"
wait $PAIR_PID
[ "$(cat "$OUT/pair_code.txt")" = 200 ] || fail "eslestirme 200 donmedi ($(cat "$OUT/pair_code.txt"))"
TOKEN=$(python3 -c "import json;print(json.load(open('$OUT/pair.json'))['token'])")
echo "OK 3: eslestirme"

# 4) uygulama ARKA PLANDAYKEN link ac (erisilebilirlik yolu)
adb shell input keyevent KEYCODE_HOME; sleep 2
adb logcat -c
code=$(api POST /v1/open "{\"url\":\"$MP4\",\"title\":\"e2e\"}"); [ "$code" = 200 ] || fail "/v1/open ($code): $(cat "$OUT/resp.json")"
wait_log 60 "AfuRemotePlayer: state=PLAYING" || fail "video TV'de oynamadi"
echo "OK 4: arka plandayken link acildi, video oynuyor"

# 5) ses
adb shell cmd media_session volume --stream 3 --set 5 >/dev/null 2>&1
code=$(api POST /v1/key '{"key":"vol_up"}'); [ "$code" = 200 ] || fail "vol_up ($code)"
sleep 1
vol=$(adb shell cmd media_session volume --stream 3 --get | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')
[ "${vol:-0}" -gt 5 ] || fail "ses artmadi (ses=$vol)"
echo "OK 5: ses artti ($vol)"

# 6) duraklat
code=$(api POST /v1/key '{"key":"play_pause"}'); [ "$code" = 200 ] || fail "play_pause ($code)"
wait_log 15 "AfuRemotePlayer: state=PAUSED" || fail "video duraklamadi"
echo "OK 6: duraklat"

# 7) geri tusu oynaticiyi kapatir
code=$(api POST /v1/key '{"key":"back"}'); [ "$code" = 200 ] || fail "back ($code)"
sleep 3
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q PlayerActivity && fail "geri tusu oynaticiyi kapatmadi"
echo "OK 7: geri tusu"

# (Task 6 telefon adimlarini BURAYA ekler)

grep -q "FATAL EXCEPTION" "$OUT/tam.log" && fail "cokme var"
adb exec-out screencap -p > "$OUT/son.png"
echo "BASARILI: tum adimlar gecti"
```

- [ ] **Step 10: CI'a e2e job'u** — `build.yml` sonuna:
```yaml
  e2e:
    needs: build
    runs-on: ubuntu-latest
    timeout-minutes: 40
    steps:
      - uses: actions/checkout@v4

      - uses: actions/download-artifact@v4
        with:
          name: AfuRemote-debug
          path: apks

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Emulator e2e (TV + phone roles)
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          target: google_apis
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          script: bash scripts/e2e.sh apks/app-debug.apk

      - name: Upload e2e evidence
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: AfuRemote-e2e
          path: e2e-out/
          retention-days: 14
```

- [ ] **Step 11: Push, CI + e2e yeşil**
```bash
git add -A && git commit -m "feat: TV runtime (server, NSD, player, web, pairing prompt, accessibility) + e2e

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push; sleep 15; R=$(gh run list --branch v1 --limit 1 --json databaseId -q '.[0].databaseId'); gh run watch $R --exit-status
J=$(gh run view $R --json jobs -q '.jobs[]|select(.name=="e2e")|.databaseId'); gh run view --job $J --log | grep -E "OK [0-9]|BASARILI|HATA:"
```
Expected: `OK 1` … `OK 7` ve `BASARILI`. Kırmızıysa `AfuRemote-e2e` artifact'ındaki `hata.png`, `tam.log` ile kök nedeni bul (systematic-debugging); tahminle yama yapma.

---

### Task 6: Telefon tarafı (keşif, paylaşım, yerel video, kumanda ekranı) + e2e 8-9

**Files:**
- Create: `phone/{TvDevice, Messages, TvApi, TvClient, TvDiscovery, PhoneController, PhoneGraph, RangeParser, LocalMediaServer, PhoneStream, PhoneStreamService, ShareActivity, PhoneHomeScreen}.kt`
- Modify: `MainActivity.kt` (telefon ekranı), `AndroidManifest.xml`, `scripts/e2e.sh`
- Test: `phone/RangeParserTest.kt`, `phone/MessagesTest.kt`, `phone/PhoneControllerTest.kt`

**Interfaces:**
- Consumes: Task 2 protokol, Task 3 `TokenStore`/`PairingStore`, Task 5 `LocalIp`.
- Produces: `data class TvDevice(id, name, model, host, port, serviceName)` + `fun url(path: String): String`; `sealed interface SendResult { Ok; Unauthorized; Failed(message) }`; `interface TvApi { pair(tv, req): String?; open(tv, token, req): SendResult; key(tv, token, key): SendResult }`; `class PhoneController(api: TvApi, store: TokenStore, deviceName: String) { suspend fun open(tv, req, onPairing: () -> Unit): SendResult; suspend fun key(tv, key, onPairing: () -> Unit): SendResult }`; `object RangeParser { fun parse(header: String?, size: Long): ByteRange? }`; `object PhoneStream { fun publish(context, uri): String?; fun stop() }`; `@Composable fun PhoneHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {})`.

- [ ] **Step 1: Testler**

`RangeParserTest.kt`:
```kotlin
package com.afudm.afuremote.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeParserTest {
    @Test
    fun `open ended and bounded ranges`() {
        assertEquals(ByteRange(500, 999), RangeParser.parse("bytes=500-", 1000))
        assertEquals(ByteRange(0, 99), RangeParser.parse("bytes=0-99", 1000))
        assertEquals(ByteRange(900, 999), RangeParser.parse("bytes=900-5000", 1000))
    }

    @Test
    fun `suffix range takes the last bytes`() {
        assertEquals(ByteRange(500, 999), RangeParser.parse("bytes=-500", 1000))
        assertEquals(ByteRange(0, 999), RangeParser.parse("bytes=-5000", 1000))
    }

    @Test
    fun `unsatisfiable or malformed ranges are null`() {
        assertNull(RangeParser.parse("bytes=1000-", 1000))
        assertNull(RangeParser.parse("bytes=50-10", 1000))
        assertNull(RangeParser.parse("bytes=-", 1000))
        assertNull(RangeParser.parse("items=0-1", 1000))
        assertNull(RangeParser.parse("bytes=0-1,5-9", 1000))
        assertNull(RangeParser.parse(null, 1000))
    }

    @Test
    fun `length is inclusive`() {
        assertEquals(100L, ByteRange(0, 99).length)
    }
}
```

`MessagesTest.kt`:
```kotlin
package com.afudm.afuremote.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MessagesTest {
    @Test
    fun `tv error codes become turkish sentences`() {
        assertEquals(Messages.ACCESSIBILITY, Messages.forError(409, """{"ok":false,"hata":"erisilebilirlik_kapali"}"""))
        assertEquals("Bu link anlaşılamadı", Messages.forError(400, """{"ok":false,"hata":"gecersiz_link"}"""))
        assertEquals("TV hata verdi (500)", Messages.forError(500, "html sayfasi"))
    }

    @Test
    fun `phone side never shows the TV approve button label`() {
        for (m in listOf(Messages.UNREACHABLE, Messages.NO_TV, Messages.DENIED, Messages.WAITING_TV, Messages.ACCESSIBILITY)) {
            assertFalse(m, m.contains("İzin ver"))
        }
    }
}
```

`PhoneControllerTest.kt`:
```kotlin
package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneControllerTest {
    private val tv = TvDevice("tv-1", "Salon", "X", "192.168.1.20", 9870, "AfuRemote Salon")

    private class MemoryStore : TokenStore {
        val tokens = mutableMapOf<String, String>()
        override fun tokenForTv(tvId: String) = tokens[tvId]
        override fun saveTokenForTv(tvId: String, token: String) { tokens[tvId] = token }
        override fun forgetTv(tvId: String) { tokens.remove(tvId) }
        override fun deviceId() = "tel-1"
    }

    private class FakeApi(var validToken: String = "yeni", var pairAnswer: String? = "yeni") : TvApi {
        var pairCalls = 0
        val sentWith = mutableListOf<String>()
        override fun pair(tv: TvDevice, req: PairRequest): String? { pairCalls++; return pairAnswer }
        override fun open(tv: TvDevice, token: String, req: OpenRequest) = check(token)
        override fun key(tv: TvDevice, token: String, key: RemoteKey) = check(token)
        private fun check(token: String): SendResult { sentWith += token; return if (token == validToken) SendResult.Ok else SendResult.Unauthorized }
    }

    @Test
    fun `first command pairs once then reuses the token`() = runBlocking {
        val store = MemoryStore(); val api = FakeApi()
        var prompts = 0
        val c = PhoneController(api, store, "Telefonum")
        assertEquals(SendResult.Ok, c.key(tv, RemoteKey.VOL_UP) { prompts++ })
        assertEquals(SendResult.Ok, c.key(tv, RemoteKey.VOL_UP) { prompts++ })
        assertEquals(1, api.pairCalls)
        assertEquals(1, prompts)
        assertEquals("yeni", store.tokens["tv-1"])
    }

    @Test
    fun `stale token after TV reset re-pairs automatically`() = runBlocking {
        val store = MemoryStore().apply { tokens["tv-1"] = "eski" }
        val api = FakeApi()
        val c = PhoneController(api, store, "Telefonum")
        assertEquals(SendResult.Ok, c.open(tv, OpenRequest("https://x/a.mp4")) {})
        assertEquals(listOf("eski", "yeni"), api.sentWith)
        assertEquals("yeni", store.tokens["tv-1"])
    }

    @Test
    fun `denied pairing gives a clear message and stores nothing`() = runBlocking {
        val store = MemoryStore(); val api = FakeApi(pairAnswer = null)
        val c = PhoneController(api, store, "Telefonum")
        assertEquals(SendResult.Failed(Messages.DENIED), c.key(tv, RemoteKey.MUTE) {})
        assertEquals(emptyMap<String, String>(), store.tokens)
    }
}
```

- [ ] **Step 2: Saf telefon kodu**

`phone/TvDevice.kt`:
```kotlin
package com.afudm.afuremote.phone

data class TvDevice(val id: String, val name: String, val model: String, val host: String, val port: Int, val serviceName: String) {
    fun url(path: String): String = "http://${hostForUrl(host)}:$port$path"

    companion object {
        fun hostForUrl(host: String): String = if (host.contains(':')) "[" + host.replace("%", "%25") + "]" else host
    }
}

sealed interface SendResult {
    data object Ok : SendResult
    data object Unauthorized : SendResult
    data class Failed(val message: String) : SendResult
}
```

`phone/Messages.kt`:
```kotlin
package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString

/** Telefonda gösterilen metinler. "İzin ver" ifadesi burada KULLANILMAZ (TV düğmesinin etiketi). */
object Messages {
    const val UNREACHABLE = "TV'ye ulaşılamadı — TV açık ve aynı Wi-Fi'de mi?"
    const val NO_TV = "TV bulunamadı — TV'de AfuRemote açık mı, telefon ve TV aynı Wi-Fi'de mi?"
    const val DENIED = "TV onay vermedi ya da 60 sn içinde yanıt gelmedi"
    const val WAITING_TV = "TV ekranındaki onayı bekliyor… (TV kumandasıyla onaylayın)"
    const val ACCESSIBILITY = "TV'de AfuRemote için Erişilebilirlik iznini açın (TV: Ayarlar → Erişilebilirlik → AfuRemote)"

    fun forError(code: Int, body: String?): String {
        val hata = body?.let { runCatching { ProtocolJson.decodeFromString<ApiResult>(it).hata }.getOrNull() }.orEmpty()
        return when (hata) {
            HATA_ERISILEBILIRLIK -> ACCESSIBILITY
            "gecersiz_link" -> "Bu link anlaşılamadı"
            "acacak_uygulama_yok" -> "TV'de bunu açabilecek uygulama yok"
            "bozuk_istek" -> "TV isteği anlamadı (sürümler uyumsuz olabilir — ikisini de güncelleyin)"
            else -> "TV hata verdi ($code)"
        }
    }
}
```

`phone/TvApi.kt`:
```kotlin
package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey

interface TvApi {
    /** Onaylanırsa token, reddedilir/ulaşılamazsa null. En çok ~70 sn sürer. */
    fun pair(tv: TvDevice, req: PairRequest): String?
    fun open(tv: TvDevice, token: String, req: OpenRequest): SendResult
    fun key(tv: TvDevice, token: String, key: RemoteKey): SendResult
}
```

`phone/PhoneController.kt`:
```kotlin
package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Token yoksa ya da TV token'ı tanımıyorsa (TV sıfırlandı) kendiliğinden yeniden eşleşir. */
class PhoneController(private val api: TvApi, private val store: TokenStore, private val deviceName: String) {
    suspend fun open(tv: TvDevice, req: OpenRequest, onPairing: () -> Unit): SendResult =
        withAuth(tv, onPairing) { api.open(tv, it, req) }

    suspend fun key(tv: TvDevice, key: RemoteKey, onPairing: () -> Unit): SendResult =
        withAuth(tv, onPairing) { api.key(tv, it, key) }

    private suspend fun withAuth(tv: TvDevice, onPairing: () -> Unit, call: (String) -> SendResult): SendResult =
        withContext(Dispatchers.IO) {
            val token = store.tokenForTv(tv.id) ?: pairNow(tv, onPairing) ?: return@withContext SendResult.Failed(Messages.DENIED)
            val first = call(token)
            if (first != SendResult.Unauthorized) return@withContext first
            store.forgetTv(tv.id)
            val fresh = pairNow(tv, onPairing) ?: return@withContext SendResult.Failed(Messages.DENIED)
            call(fresh)
        }

    private fun pairNow(tv: TvDevice, onPairing: () -> Unit): String? {
        onPairing()
        val token = api.pair(tv, PairRequest(deviceName, store.deviceId())) ?: return null
        store.saveTokenForTv(tv.id, token)
        return token
    }
}
```

`phone/RangeParser.kt`:
```kotlin
package com.afudm.afuremote.phone

data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

object RangeParser {
    private val SINGLE = Regex("""^bytes=(\d*)-(\d*)$""")

    /** Tek aralıklı "bytes=a-b" başlığı; karşılanamaz ya da bozuksa null. */
    fun parse(header: String?, size: Long): ByteRange? {
        if (header == null || size <= 0) return null
        val (a, b) = SINGLE.matchEntire(header.trim())?.destructured ?: return null
        return when {
            a.isEmpty() && b.isEmpty() -> null
            a.isEmpty() -> {
                val suffix = b.toLong()
                if (suffix <= 0) null else ByteRange(maxOf(0L, size - suffix), size - 1)
            }
            else -> {
                val start = a.toLong()
                val end = if (b.isEmpty()) size - 1 else minOf(b.toLong(), size - 1)
                if (start >= size || start > end) null else ByteRange(start, end)
            }
        }
    }
}
```

- [ ] **Step 3: Android telefon kodu**

`phone/TvClient.kt`:
```kotlin
package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.PairResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.protocol.TOKEN_HEADER
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TvClient(private val http: OkHttpClient = defaultHttp()) : TvApi {
    private val pairHttp = http.newBuilder().readTimeout(70, TimeUnit.SECONDS).build()

    fun info(host: String, port: Int): InfoResponse? = try {
        http.newCall(Request.Builder().url("http://${TvDevice.hostForUrl(host)}:$port/v1/info").build()).execute().use { r ->
            if (!r.isSuccessful) null else ProtocolJson.decodeFromString<InfoResponse>(r.body?.string().orEmpty())
        }
    } catch (e: IOException) { null } catch (e: IllegalArgumentException) { null }

    override fun pair(tv: TvDevice, req: PairRequest): String? = try {
        pairHttp.newCall(post(tv, "/v1/pair", ProtocolJson.encodeToString(req), null)).execute().use { r ->
            if (r.code != 200) null else ProtocolJson.decodeFromString<PairResponse>(r.body?.string().orEmpty()).token
        }
    } catch (e: IOException) { null } catch (e: IllegalArgumentException) { null }

    override fun open(tv: TvDevice, token: String, req: OpenRequest): SendResult =
        send(tv, token, "/v1/open", ProtocolJson.encodeToString(req))

    override fun key(tv: TvDevice, token: String, key: RemoteKey): SendResult =
        send(tv, token, "/v1/key", ProtocolJson.encodeToString(KeyRequest(key.wire)))

    private fun send(tv: TvDevice, token: String, path: String, json: String): SendResult = try {
        http.newCall(post(tv, path, json, token)).execute().use { r ->
            when (r.code) {
                200 -> SendResult.Ok
                401 -> SendResult.Unauthorized
                else -> SendResult.Failed(Messages.forError(r.code, r.body?.string()))
            }
        }
    } catch (e: IOException) {
        SendResult.Failed(Messages.UNREACHABLE)
    }

    private fun post(tv: TvDevice, path: String, json: String, token: String?): Request =
        Request.Builder().url(tv.url(path)).post(json.toRequestBody(JSON))
            .apply { if (token != null) header(TOKEN_HEADER, token) }
            .build()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
```

`phone/TvDiscovery.kt`:
```kotlin
package com.afudm.afuremote.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.afudm.afuremote.protocol.SERVICE_TYPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume

class TvDiscovery(context: Context, private val client: TvClient) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolveLock = Mutex() // Android 13 ve öncesi aynı anda tek çözümleme
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private var users = 0

    @Synchronized fun acquire() { users++; if (users == 1) start() }
    @Synchronized fun release() { users = maxOf(0, users - 1); if (users == 0) stop() }

    private fun start() {
        _devices.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) { scope.launch { resolveAndVerify(service) } }
            override fun onServiceLost(service: NsdServiceInfo) {
                _devices.update { list -> list.filterNot { it.serviceName == service.serviceName } }
            }
            override fun onDiscoveryStarted(serviceType: String) { Log.i(TAG, "TV aramasi basladi") }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.e(TAG, "arama baslamadi: $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    private fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
    }

    private suspend fun resolveAndVerify(service: NsdServiceInfo) {
        val resolved = resolveLock.withLock { resolve(service) } ?: return
        val host = pickHost(resolved) ?: return
        val info = client.info(host, resolved.port) ?: return
        val device = TvDevice(info.id, info.name, info.model, host, resolved.port, service.serviceName)
        Log.i(TAG, "TV bulundu: ${info.name} ($host)")
        _devices.update { list -> list.filterNot { it.id == device.id } + device }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) { if (cont.isActive) cont.resume(info) }
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
        })
    }

    @Suppress("DEPRECATION")
    private fun pickHost(info: NsdServiceInfo): String? {
        val all: List<InetAddress> = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        return (all.firstOrNull { it is Inet4Address } ?: all.firstOrNull())?.hostAddress
    }

    private companion object { const val TAG = "AfuRemotePhone" }
}
```

`phone/PhoneGraph.kt`:
```kotlin
package com.afudm.afuremote.phone

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.afudm.afuremote.pairing.PairingStore

/** Telefon tarafı nesneleri tek yerde (MainActivity ve ShareActivity paylaşır). */
object PhoneGraph {
    class Graph(val store: PairingStore, val client: TvClient, val discovery: TvDiscovery, val controller: PhoneController)

    @Volatile private var instance: Graph? = null

    fun get(context: Context): Graph = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(app: Context): Graph {
        val store = PairingStore(app)
        val client = TvClient()
        val name = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        return Graph(store, client, TvDiscovery(app, client), PhoneController(client, store, name))
    }
}
```

`phone/LocalMediaServer.kt`:
```kotlin
package com.afudm.afuremote.phone

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.PHONE_MEDIA_PORT
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

/** Paylaşılan TEK videoyu tahmin edilemez bir yolda, Range destekli sunar. */
class LocalMediaServer(private val context: Context) : NanoHTTPD(PHONE_MEDIA_PORT) {
    private data class Shared(val uri: Uri, val secret: String, val size: Long, val mime: String)

    @Volatile private var shared: Shared? = null

    fun publish(uri: Uri): String? {
        val size = sizeOf(uri)
        if (size <= 0) return null
        val secret = TokenRegistry.randomToken().take(32)
        shared = Shared(uri, secret, size, context.contentResolver.getType(uri) ?: "video/mp4")
        return "/m/$secret"
    }

    override fun serve(session: IHTTPSession): Response {
        val s = shared ?: return notFound()
        if (session.uri != "/m/${s.secret}") return notFound()
        val header = session.headers["range"]
        val range = RangeParser.parse(header, s.size)
        if (header != null && range == null) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                .apply { addHeader("Content-Range", "bytes */${s.size}") }
        }
        val input = runCatching { context.contentResolver.openInputStream(s.uri) }.getOrNull() ?: return notFound()
        return if (range == null) {
            newFixedLengthResponse(Response.Status.OK, s.mime, input, s.size).apply { addHeader("Accept-Ranges", "bytes") }
        } else {
            skipFully(input, range.start)
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, s.mime, input, range.length).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/${s.size}")
            }
        }
    }

    private fun sizeOf(uri: Uri): Long {
        val fromFd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
        if (fromFd != null && fromFd > 0) return fromFd
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }.getOrNull() ?: -1L
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) left -= skipped else if (input.read() < 0) return else left -= 1
        }
    }

    private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
}
```

`phone/PhoneStream.kt`:
```kotlin
package com.afudm.afuremote.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.afudm.afuremote.net.LocalIp
import com.afudm.afuremote.protocol.PHONE_MEDIA_PORT
import fi.iki.elonen.NanoHTTPD

object PhoneStream {
    private var server: LocalMediaServer? = null

    /** Videoyu yayınlar, TV'nin açacağı adresi döner (Wi-Fi yoksa / okunamazsa null). */
    @Synchronized
    fun publish(context: Context, uri: Uri): String? {
        val app = context.applicationContext
        val ip = LocalIp.wifiIpv4() ?: return null
        val s = server ?: LocalMediaServer(app).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); server = it }
        val path = s.publish(uri) ?: return null
        // Ön plan servisi: paylaşım ekranı kapansa da yayın sürer; URI okuma izni servise devredilir.
        ContextCompat.startForegroundService(
            app,
            Intent(app, PhoneStreamService::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return "http://$ip:$PHONE_MEDIA_PORT$path"
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }
}
```

`phone/PhoneStreamService.kt`:
```kotlin
package com.afudm.afuremote.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.afudm.afuremote.R

class PhoneStreamService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "TV'ye video akışı", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, PhoneStreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("Video TV'ye akıyor")
            .setContentText("Bitince durdurun")
            .addAction(0, "Durdur", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        PhoneStream.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL = "afuremote_stream"
        const val NOTIFICATION_ID = 9871
        const val ACTION_STOP = "com.afudm.afuremote.STOP_STREAM"
    }
}
```

`phone/ShareActivity.kt`:
```kotlin
package com.afudm.afuremote.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.ui.AfuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ShareActivity : ComponentActivity() {
    private sealed interface Shared {
        data class Link(val url: String, val title: String) : Shared
        data class LocalVideo(val uri: Uri) : Shared
    }

    private val status = MutableStateFlow("TV aranıyor…")
    private val choices = MutableStateFlow<List<TvDevice>>(emptyList())
    private lateinit var graph: PhoneGraph.Graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = PhoneGraph.get(this)
        setContent {
            AfuTheme {
                val text by status.collectAsState()
                val tvs by choices.collectAsState()
                Surface {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AfuRemote")
                        Text(text)
                        tvs.forEach { tv ->
                            Button(onClick = { choices.value = emptyList(); lifecycleScope.launch { deliver(tv) } }) {
                                Text("${tv.name} · ${tv.model}")
                            }
                        }
                        OutlinedButton(onClick = { finish() }) { Text("Kapat") }
                    }
                }
            }
        }
        val shared = parse(intent)
        if (shared == null) {
            status.value = "Paylaşılan içerikte link ya da video yok"
            return
        }
        graph.discovery.acquire()
        lifecycleScope.launch {
            withTimeoutOrNull(8_000) { graph.discovery.devices.first { it.isNotEmpty() } }
            delay(1_000) // diğer TV'ler de gelsin
            val all = graph.discovery.devices.value
            when {
                all.isEmpty() -> status.value = Messages.NO_TV
                all.size == 1 -> deliver(all.first(), shared)
                else -> { status.value = "Hangi TV?"; choices.value = all; pending = shared }
            }
        }
    }

    private var pending: Shared? = null

    private suspend fun deliver(tv: TvDevice, shared: Shared? = pending) {
        val item = shared ?: return
        status.value = "${tv.name} TV'sine gönderiliyor…"
        val request = when (item) {
            is Shared.Link -> OpenRequest(item.url, item.title)
            is Shared.LocalVideo -> {
                val url = PhoneStream.publish(this, item.uri) ?: run { status.value = "Video okunamadı ya da Wi-Fi yok"; return }
                OpenRequest(url, "Telefondan video", forceMedia = true)
            }
        }
        when (val r = graph.controller.open(tv, request) { status.value = Messages.WAITING_TV }) {
            SendResult.Ok -> { status.value = "TV'de açıldı ✓"; delay(1_200); finish() }
            SendResult.Unauthorized -> status.value = Messages.DENIED
            is SendResult.Failed -> status.value = r.message
        }
    }

    private fun parse(intent: Intent?): Shared? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        if (type.startsWith("video/")) {
            return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { Shared.LocalVideo(it) }
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val url = LinkClassifier.extractUrl(text) ?: return null
        return Shared.Link(url, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
    }

    override fun onDestroy() {
        if (::graph.isInitialized && isFinishing) graph.discovery.release()
        super.onDestroy()
    }
}
```
Not: `graph.discovery.release()` yalnız `acquire()` çağrıldıysa anlamlı; `release()` sayacı 0'ın altına inmez (Step 3 `TvDiscovery`), bu yüzden erken çıkışta da güvenli.

`phone/PhoneHomeScreen.kt`:
```kotlin
package com.afudm.afuremote.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.ui.ModeSection
import kotlinx.coroutines.launch

@Composable
fun PhoneHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val graph = remember { PhoneGraph.get(context) }
    val devices by graph.discovery.devices.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    var status by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        graph.discovery.acquire()
        onDispose { graph.discovery.release() }
    }

    fun send(block: suspend (TvDevice) -> SendResult) {
        val tv = selected ?: run { status = Messages.NO_TV; return }
        scope.launch {
            status = "Gönderiliyor…"
            status = when (val r = block(tv)) {
                SendResult.Ok -> "✓ ${tv.name}"
                SendResult.Unauthorized -> Messages.DENIED
                is SendResult.Failed -> r.message
            }
        }
    }
    val onPairing: () -> Unit = { status = Messages.WAITING_TV }
    fun key(k: RemoteKey) = send { graph.controller.key(it, k, onPairing) }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AfuRemote", fontSize = 26.sp)
        if (devices.isEmpty()) Text("TV aranıyor… (TV'de AfuRemote açık ve aynı Wi-Fi'de olmalı)")
        devices.forEach { tv ->
            val mark = if (tv.id == selected?.id) "● " else "○ "
            OutlinedButton(onClick = { selectedId = tv.id }, modifier = Modifier.fillMaxWidth()) { Text("$mark${tv.name} · ${tv.model}") }
        }
        if (status.isNotBlank()) Text(status)

        OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Link") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val url = LinkClassifier.extractUrl(link) ?: run { status = "Geçerli bir link yazın"; return@Button }
            send { graph.controller.open(it, OpenRequest(url), onPairing) }
        }, modifier = Modifier.fillMaxWidth()) { Text("TV'de aç") }

        Text("Kumanda")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { key(RemoteKey.BACK) }) { Text("↩ Geri") }
            Button(onClick = { key(RemoteKey.HOME) }) { Text("⌂ Ana ekran") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { key(RemoteKey.VOL_DOWN) }) { Text("Ses −") }
            Button(onClick = { key(RemoteKey.MUTE) }) { Text("Sessiz") }
            Button(onClick = { key(RemoteKey.VOL_UP) }) { Text("Ses +") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { key(RemoteKey.SEEK_BACK) }) { Text("⏪ 10 sn") }
            Button(onClick = { key(RemoteKey.PLAY_PAUSE) }) { Text("⏯") }
            Button(onClick = { key(RemoteKey.SEEK_FWD) }) { Text("10 sn ⏩") }
        }
        Text("Sürüm $versionName")
        footer()
        ModeSection(onModeChange)
    }
}
```

- [ ] **Step 4: MainActivity + manifest**

`MainActivity.kt` — `AppMode.PHONE ->` dalını `AppMode.PHONE -> PhoneHomeScreen(BuildConfig.VERSION_NAME, change)` yap; import `com.afudm.afuremote.phone.PhoneHomeScreen`; artık kullanılmayan `Column/Text/padding/dp/ModeSection` importlarını kaldır.

Manifest `<application>` içine:
```xml
        <activity android:name=".phone.ShareActivity" android:exported="true"
            android:theme="@android:style/Theme.Material.Dialog.NoActionBar"
            android:excludeFromRecents="true" android:taskAffinity="">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
            </intent-filter>
        </activity>

        <service android:name=".phone.PhoneStreamService" android:exported="false"
            android:foregroundServiceType="connectedDevice" />
```

- [ ] **Step 5: e2e telefon adımları** — `scripts/e2e.sh`'de `# (Task 6 telefon adimlarini BURAYA ekler)` satırını şununla değiştir:
```bash
# 8) telefon modu: ayni cihazdaki TV kendiliginden listede
adb shell am start -n "$PKG/.MainActivity" --es mode phone >/dev/null
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
wait_text 40 "$MODEL" phone_home || fail "telefon modunda TV listede gorunmedi (model: $MODEL)"
echo "OK 8: TV kendiliginden bulundu"

# 9) Paylas -> AfuRemote: ilk kez -> TV onayi -> video TV'de oynar
adb logcat -c
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Film: $MP4" -n "$PKG/.phone.ShareActivity" >/dev/null
wait_text 40 "İzin ver" share_pair || fail "paylasimda TV onay ekrani cikmadi"
tap_text share_pair "İzin ver" || fail "Izin ver dugmesi bulunamadi"
wait_log 60 "AfuRemotePlayer: state=PLAYING" || fail "paylasilan link TV'de oynamadi"
echo "OK 9: Paylas -> TV'de acildi"
```

- [ ] **Step 6: Push, CI + e2e yeşil** (commit: `feat: phone side - discovery, share, local video, remote screen`). Expected: `RangeParserTest` 4, `MessagesTest` 2, `PhoneControllerTest` 3 PASS; e2e `OK 1`…`OK 9` + `BASARILI`.

---

### Task 7: Uygulama içi güncelleme + imzalı release

**Files:**
- Create: `update/{UpdateParser, UpdateManager, ApkDownloadWorker, UpdateSection}.kt`, `res/xml/file_paths.xml`, `.github/workflows/release.yml`
- Modify: `MainActivity.kt` (her iki ekrana `footer = { UpdateSection(...) }`), `AndroidManifest.xml` (FileProvider + WorkManager FGS türü)
- Test: `update/UpdateParserTest.kt`

**Interfaces:**
- Consumes: `ProtocolJson`.
- Produces: `data class AppUpdate(versionName, versionCode, releaseNotes, apkUrl, checksumUrl)`; `object AppVersion { fun code(name: String): Int }`; `object UpdateParser { const val APK = "AfuRemote-universal.apk"; fun latest(json: String, currentVersionCode: Int): AppUpdate? }`; `object Sha256 { fun verify(file: File, expectedText: String): Boolean }`; `object UpdateManager { suspend fun check(currentVersionCode: Int): AppUpdate?; fun shouldCheckAutomatically(context): Boolean; fun markChecked(context); fun enqueueDownload(context, update) }`; `@Composable fun UpdateSection(versionCode: Int, versionName: String)`.

- [ ] **Step 1: Test** — `UpdateParserTest.kt`
```kotlin
package com.afudm.afuremote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateParserTest {
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false, assets: Boolean = true) = """
        {"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"body":"notlar $tag","assets":[
          ${if (assets) """{"name":"AfuRemote-universal.apk","browser_download_url":"https://x/$tag.apk"},
          {"name":"AfuRemote-universal.apk.sha256","browser_download_url":"https://x/$tag.sha"}""" else ""}
        ]}
    """.trimIndent()

    @Test
    fun `newest stable release wins`() {
        val json = "[${release("afuremote-v0.2.0")},${release("afuremote-v0.10.0")},${release("afuremote-v0.3.0")}]"
        val u = UpdateParser.latest(json, AppVersion.code("0.1.0"))
        assertEquals("0.10.0", u?.versionName)
        assertEquals("https://x/afuremote-v0.10.0.apk", u?.apkUrl)
        assertEquals("https://x/afuremote-v0.10.0.sha", u?.checksumUrl)
    }

    @Test
    fun `prerelease draft foreign tags and assetless releases are skipped`() {
        val json = "[${release("afuremote-v9.0.0", prerelease = true)},${release("afuremote-v8.0.0", draft = true)}," +
            "${release("v7.0.0")},${release("afuremote-v6.0.0", assets = false)},${release("afuremote-v0.2.0")}]"
        assertEquals("0.2.0", UpdateParser.latest(json, AppVersion.code("0.1.0"))?.versionName)
    }

    @Test
    fun `same or older version is not an update`() {
        val json = "[${release("afuremote-v0.2.0")}]"
        assertNull(UpdateParser.latest(json, AppVersion.code("0.2.0")))
        assertNull(UpdateParser.latest(json, AppVersion.code("1.0.0")))
    }

    @Test
    fun `version codes order numerically`() {
        assertEquals(1_002_003, AppVersion.code("1.2.3"))
        assertEquals(1_002_003, AppVersion.code("1.2.3-test"))
        assertTrue(AppVersion.code("0.10.0") > AppVersion.code("0.9.9"))
    }

    @Test
    fun `sha256 verify accepts sha256sum output and rejects mismatch`() {
        val f = File.createTempFile("afuremote", ".apk").apply { writeText("AfuRemote") }
        val hex = MessageDigest.getInstance("SHA-256").digest("AfuRemote".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(Sha256.verify(f, "$hex  AfuRemote-universal.apk\n"))
        assertFalse(Sha256.verify(f, "0".repeat(64)))
        f.delete()
    }
}
```

- [ ] **Step 2: Kod**

`update/UpdateParser.kt`:
```kotlin
package com.afudm.afuremote.update

import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest

data class AppUpdate(val versionName: String, val versionCode: Int, val releaseNotes: String, val apkUrl: String, val checksumUrl: String)

object AppVersion {
    fun code(versionName: String): Int {
        val parts = versionName.substringBefore('-').split('.')
        fun part(i: Int) = parts.getOrNull(i)?.toIntOrNull() ?: 0
        return part(0) * 1_000_000 + part(1) * 1_000 + part(2)
    }
}

object UpdateParser {
    const val APK = "AfuRemote-universal.apk"
    private val TAG = Regex("""^afuremote-v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)$""")

    fun latest(json: String, currentVersionCode: Int): AppUpdate? =
        ProtocolJson.parseToJsonElement(json).jsonArray
            .mapNotNull { runCatching { toUpdate(it.jsonObject) }.getOrNull() }
            .maxByOrNull { it.versionCode }
            ?.takeIf { it.versionCode > currentVersionCode }

    private fun toUpdate(o: JsonObject): AppUpdate? {
        if (o["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
        if (o["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val version = TAG.matchEntire(o["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty())?.groupValues?.get(1) ?: return null
        val assets = o["assets"]?.jsonArray.orEmpty().associate { a ->
            val ao = a.jsonObject
            ao["name"]?.jsonPrimitive?.contentOrNull.orEmpty() to ao["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        val apk = assets[APK].orEmpty()
        val sha = assets["$APK.sha256"].orEmpty()
        if (apk.isBlank() || sha.isBlank()) return null
        return AppUpdate(version, AppVersion.code(version), o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(), apk, sha)
    }
}

object Sha256 {
    fun verify(file: File, expectedText: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedText.trim().split(Regex("\\s+")).firstOrNull().orEmpty(), ignoreCase = true)
    }
}
```

`update/UpdateManager.kt`:
```kotlin
package com.afudm.afuremote.update

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object UpdateManager {
    private const val API = "https://api.github.com/repos/pirncedark/AfuRemote/releases"
    private const val PREFS = "afuremote_updates"
    private const val LAST_CHECK = "last_check"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    suspend fun check(currentVersionCode: Int): AppUpdate? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub ${r.code}")
            UpdateParser.latest(r.body?.string().orEmpty(), currentVersionCode)
        }
    }

    fun shouldCheckAutomatically(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(LAST_CHECK, 0L) >= DAY_MS

    fun markChecked(context: Context) = prefs(context).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()

    fun enqueueDownload(context: Context, update: AppUpdate) {
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(workDataOf(ApkDownloadWorker.KEY_APK to update.apkUrl, ApkDownloadWorker.KEY_SHA to update.checksumUrl))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("afuremote_update", ExistingWorkPolicy.REPLACE, request)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
```

`update/ApkDownloadWorker.kt`:
```kotlin
package com.afudm.afuremote.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.afudm.afuremote.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = progressInfo(0)

    override suspend fun doWork(): Result = runCatching {
        runCatching { setForeground(progressInfo(0)) }
        val apk = File(applicationContext.cacheDir, "AfuRemote-update.apk")
        download(inputData.getString(KEY_APK)!!, apk)
        val expected = URL(inputData.getString(KEY_SHA)!!).openStream().bufferedReader().use { it.readText() }
        check(Sha256.verify(apk, expected)) { "İndirilen dosyanın güvenlik özeti tutmadı" }
        install(apk)
        Result.success()
    }.getOrElse { Result.failure(workDataOf("error" to (it.localizedMessage ?: "Güncelleme indirilemedi"))) }

    private fun download(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        val total = connection.contentLengthLong
        var done = 0L
        var last = -1
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    val percent = if (total > 0) (done * 100 / total).toInt() else 0
                    if (percent != last) { last = percent; runCatching { setForegroundAsync(progressInfo(percent)) } }
                }
            }
        }
    }

    /** Ön plandaysa kurulum ekranı hemen açılır; arka plandaysa "kurmak için dokun" bildirimi kalır. */
    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(INSTALL_CHANNEL, "AfuRemote güncellemeleri", NotificationManager.IMPORTANCE_HIGH))
        val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("AfuRemote güncellemesi hazır")
            .setContentText("Kurmak için dokunun. Verileriniz silinmez.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(INSTALL_ID, notification) }
        runCatching { applicationContext.startActivity(intent) }
    }

    private fun progressInfo(percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "AfuRemote güncelleme indirme", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("AfuRemote güncellemesi indiriliyor")
            .setContentText("%$percent")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
    }

    companion object {
        const val KEY_APK = "apkUrl"
        const val KEY_SHA = "checksumUrl"
        private const val INSTALL_CHANNEL = "afuremote_update_install"
        private const val PROGRESS_CHANNEL = "afuremote_update_progress"
        private const val INSTALL_ID = 7301
        private const val PROGRESS_ID = 7302
    }
}
```

`update/UpdateSection.kt`:
```kotlin
package com.afudm.afuremote.update

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UpdateSection(versionCode: Int, versionName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<AppUpdate?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (UpdateManager.shouldCheckAutomatically(context)) {
            UpdateManager.markChecked(context)
            runCatching { UpdateManager.check(versionCode) }.getOrNull()?.let { found = it }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Yüklü sürüm: $versionName")
        OutlinedButton(onClick = {
            scope.launch {
                status = "Denetleniyor…"
                runCatching { UpdateManager.check(versionCode) }
                    .onSuccess { if (it == null) status = "Uygulama güncel" else { status = ""; found = it } }
                    .onFailure { status = "Denetlenemedi — internet bağlantısını kontrol edin" }
            }
        }) { Text("Güncellemeleri denetle") }
        if (status.isNotBlank()) Text(status)
    }

    found?.let { update ->
        AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Yeni AfuRemote sürümü: ${update.versionName}") },
            text = { Text(update.releaseNotes.take(400).ifBlank { "Hata düzeltmeleri ve iyileştirmeler." }) },
            confirmButton = {
                TextButton(onClick = {
                    found = null
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    UpdateManager.enqueueDownload(context, update)
                    Toast.makeText(context, "Güncelleme indiriliyor — bildirimden takip edebilirsiniz", Toast.LENGTH_LONG).show()
                }) { Text("İndir ve kur") }
            },
            dismissButton = { TextButton(onClick = { found = null }) { Text("Sonra") } }
        )
    }
}
```

`res/xml/file_paths.xml`:
```xml
<paths>
    <cache-path name="updates" path="." />
</paths>
```

Manifest `<application>` içine:
```xml
        <provider android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
        </provider>

        <service android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync" tools:node="merge" />
```

`MainActivity.kt`: `TvHomeScreen(BuildConfig.VERSION_NAME, change) { UpdateSection(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME) }` ve `PhoneHomeScreen(BuildConfig.VERSION_NAME, change) { UpdateSection(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME) }`; import `com.afudm.afuremote.update.UpdateSection`.

- [ ] **Step 3: Release iş akışı** — `.github/workflows/release.yml`
```yaml
name: Release AfuRemote

on:
  push:
    tags:
      - 'afuremote-v*'

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Install Gradle & generate wrapper
        run: |
          GRADLE_VERSION="9.6.0"
          wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O /tmp/gradle.zip
          unzip -q /tmp/gradle.zip -d /tmp/
          export PATH="/tmp/gradle-${GRADLE_VERSION}/bin:$PATH"
          gradle wrapper --gradle-version ${GRADLE_VERSION} --distribution-type bin
          chmod +x gradlew

      - name: Decode keystore
        env:
          AFUREMOTE_KEYSTORE_B64: ${{ secrets.AFUREMOTE_KEYSTORE_B64 }}
        run: echo "$AFUREMOTE_KEYSTORE_B64" | base64 --decode > "$RUNNER_TEMP/afuremote-release.p12"

      - name: Test + signed release APK
        env:
          AFUREMOTE_KEYSTORE_PATH: ${{ runner.temp }}/afuremote-release.p12
          AFUREMOTE_KEYSTORE_PASS: ${{ secrets.AFUREMOTE_KEYSTORE_PASS }}
          AFUREMOTE_KEY_ALIAS: ${{ secrets.AFUREMOTE_KEY_ALIAS }}
          AFUREMOTE_KEY_PASS: ${{ secrets.AFUREMOTE_KEY_PASS }}
        run: |
          VERSION_NAME="${GITHUB_REF_NAME#afuremote-v}"
          BASE_VERSION="${VERSION_NAME%%-*}"
          VERSION_CODE=$(echo "$BASE_VERSION" | awk -F. '{print ($1*1000000)+($2*1000)+$3}')
          ./gradlew test assembleRelease --no-daemon --stacktrace -PversionCode="$VERSION_CODE" -PversionName="$VERSION_NAME"

      - name: Prepare assets
        run: |
          mkdir -p release-assets
          cp app/build/outputs/apk/release/app-release.apk release-assets/AfuRemote-universal.apk
          (cd release-assets && sha256sum AfuRemote-universal.apk > AfuRemote-universal.apk.sha256)

      - uses: softprops/action-gh-release@v2
        with:
          name: AfuRemote ${{ github.ref_name }}
          prerelease: ${{ contains(github.ref_name, '-test') || contains(github.ref_name, '-rc') || contains(github.ref_name, '-beta') || contains(github.ref_name, '-alpha') }}
          body: |
            # AfuRemote ${{ github.ref_name }}

            Share a link or a video from your phone and it opens on your Android TV on the same Wi-Fi. Your phone also works as a TV remote.

            ## Install
            Install the same `AfuRemote-universal.apk` on the phone and on the Android TV. Later versions update from inside the app without removing data.

            ---

            ## Türkçe
            Telefondan paylaşılan link ya da video aynı Wi-Fi'deki Android TV'de açılır; telefon TV kumandası olur.
            Aynı `AfuRemote-universal.apk` dosyasını telefona ve TV'ye kurun. Sonraki sürümler uygulamanın içinden, veriler silinmeden gelir.
            TV'de bir kez: Ayarlar → Erişilebilirlik → AfuRemote → Aç (geri/ana ekran tuşları ve arka planda açma için).
          files: |
            release-assets/AfuRemote-universal.apk
            release-assets/AfuRemote-universal.apk.sha256
```
`app-release.apk` yalnız imzalanmışsa üretilir (imzasızda `app-release-unsigned.apk`) → `cp` düşer: imzasız sürüm yayına çıkamaz.

- [ ] **Step 4: İmza anahtarı (bir kez, bu PC'de, Python ile)**
```bash
mkdir -p /c/Users/afuuu/Desktop/afuproject/_gizli
python - <<'EOF'
import base64, datetime, secrets, pathlib
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "AfuRemote"), x509.NameAttribute(NameOID.ORGANIZATION_NAME, "afuproject")])
now = datetime.datetime.now(datetime.timezone.utc)
cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
        .serial_number(x509.random_serial_number()).not_valid_before(now)
        .not_valid_after(now + datetime.timedelta(days=365 * 30)).sign(key, hashes.SHA256()))
pw = secrets.token_urlsafe(24)
data = pkcs12.serialize_key_and_certificates(b"afuremote", key, cert, None, serialization.BestAvailableEncryption(pw.encode()))
d = pathlib.Path(r"C:\Users\afuuu\Desktop\afuproject\_gizli")
(d / "afuremote-release.p12").write_bytes(data)
(d / "afuremote-release.pass.txt").write_text(pw)
(d / "afuremote-release.b64").write_text(base64.b64encode(data).decode())
print("tamam")
EOF
cd /c/Users/afuuu/Desktop/afuproject/AfuRemote
gh secret set AFUREMOTE_KEYSTORE_B64 < ../_gizli/afuremote-release.b64
gh secret set AFUREMOTE_KEYSTORE_PASS < ../_gizli/afuremote-release.pass.txt
gh secret set AFUREMOTE_KEY_PASS < ../_gizli/afuremote-release.pass.txt
printf 'afuremote' | gh secret set AFUREMOTE_KEY_ALIAS
rm ../_gizli/afuremote-release.b64
gh secret list
```
Expected: 4 secret listelenir. Kullanıcıya not: `_gizli\afuremote-release.p12` + `.pass.txt` yedeğini PC dışına alın (kaybolursa güncellemeler "silmeden üstüne kurulum" ile gelemez).

- [ ] **Step 5: Push, CI + e2e yeşil** (commit: `feat: in-app updates and signed release workflow`). Expected: `UpdateParserTest` 5 PASS; e2e hâlâ `BASARILI`.

- [ ] **Step 6: main'e birleştir ve ilk sürüm**
```bash
gh pr create --base main --head v1 --title "AfuRemote v1: share to TV + phone remote" --body "Implements docs/superpowers/specs/2026-09-23-afuremote-design.md. Emulator e2e (TV + phone roles) green.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
gh pr merge --squash --delete-branch=false
git switch main && git pull -q
git tag afuremote-v0.1.0 && git push origin afuremote-v0.1.0
sleep 15; R=$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId'); gh run watch $R --exit-status
gh release view afuremote-v0.1.0 --json assets -q '.assets[].name'
curl -sL https://github.com/pirncedark/AfuRemote/releases/download/afuremote-v0.1.0/AfuRemote-universal.apk.sha256
gh api repos/pirncedark/AfuRemote/releases/tags/afuremote-v0.1.0 -q '.assets[]|select(.name=="AfuRemote-universal.apk")|.digest'
```
Expected: iki asset; `.sha256` içindeki özet GitHub `digest` ile aynı. Kullanıcıya indirme linki + "TV'de bir kez Erişilebilirlik → AfuRemote → Aç" talimatı (Telegram + terminal).
