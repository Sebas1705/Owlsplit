# P1 · Project foundation — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Generate the Owlsplit project from the Templetry `kmp/modular-features` template, update it to the current stable versions, remove the sample code and end up with an app that starts on Android with green CI.

**Architecture:** The template structure is kept (conventions in `build-logic`, `core`/`data`/`domain`/`feature` layers, Koin, SQLDelight). The user/login sample and every `feature:*` module are removed; the real data and feature modules arrive in P4–P10. No desktop target.

**Tech Stack:** Gradle 9.1.0, Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 8.13.2, Koin 4.2.2, SQLDelight 2.4.1, Ktor 3.6.0, Detekt 1.23.8.

**Platform:** KMP (Android + iOS)

---

## Context for the executor

- Repo: `C:\Users\sebss\Documents\Repos\Owlsplit` (Git Bash: `~/Documents/Repos/Owlsplit`). It already contains `README.md`, `.gitignore` and `docs/`. **Do not overwrite** those files with the template's.
- Required environment in every terminal:
  ```bash
  export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
  export ANDROID_HOME="/c/Users/sebss/Android/sdk"
  ```
- Template CLI: `templetry` (already installed in `~/go/bin`).
- Commits use the user's configured identity, **without** `Co-Authored-By` lines or "Generated with" footers.
- Root package after the rename: `es.sebas1705.owlsplit` (path `es/sebas1705/owlsplit`). The convention plugins are renamed to `owlsplit.kmp.*`.

## File map

| Action | Path |
|---|---|
| Create (render) | everything in the template except `README.md` and `.gitignore` |
| Create | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` |
| Rewrite | `gradle/libs.versions.toml` |
| Rewrite | `build.gradle.kts` |
| Rewrite | `settings.gradle.kts` |
| Rewrite | `build-logic/convention/src/main/kotlin/ext/KotlinMultiplatformExtensions.kt` |
| Modify | `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/KmpAppConventionPlugin.kt`, `build-logic/convention/src/main/kotlin/KmpDataConventionPlugin.kt` |
| Rewrite | `core/ui/build.gradle.kts`, `composeApp/build.gradle.kts`, `domain/usecases/build.gradle.kts`, `data/local/build.gradle.kts` |
| Rewrite | `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/App.kt`, `.../di/AppModule.kt` |
| Create | `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/Meta.sq`, `data/local/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/local/MetaQueriesTest.kt` |
| Delete | the whole `feature/`, `data/network/`, `data/models/`, `data/repository/`, `domain/usecases/.../user/`, `data/local/.../dao/UserLocalDao.kt`, `data/local/.../db/UserEntity.sq`, `AI_INDEX.md`, `CHANGELOG.md`, `docs/ai/`, `.github/workflows/{release,security,changelog-label-gate}.yml` |
| Rewrite | `.github/workflows/validate.yml` → `.github/workflows/ci.yml`, `MODULE_MAP.md` |
| Modify | `README.md` (Development section), `.gitignore` |

---

### Task 1: Generate the project from the template

**Files:**
- Create: the whole template tree inside the repo (except `README.md`, `.gitignore`)

- [ ] **Step 1: Render into the scratchpad**

```bash
SCR="$(mktemp -d)"
templetry init kmp/modular-features --out "$SCR/owlsplit" \
  --set project_name=Owlsplit \
  --set base_package=es.sebas1705.owlsplit \
  --feature desktop=false
ls "$SCR/owlsplit"
```
Expected: a listing with `build-logic composeApp config core data domain feature gradle iosApp settings.gradle.kts ...` and a `.templetry-answers.yml`. If `init` does not write `.templetry-answers.yml`, note it in the commit (`templetry update` needs it).

- [ ] **Step 2: Check the identity rename**

```bash
grep -rIl "dev.template.kmpbase\|kmpbase\|KMPNativeBase" "$SCR/owlsplit" | grep -v "/.git/" | head
ls "$SCR/owlsplit/composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit"
```
Expected: the `grep` returns nothing; the `ls` shows `App.kt config di`.

- [ ] **Step 3: Copy into the repo without overwriting existing files**

```bash
cd ~/Documents/Repos/Owlsplit
cp -rn "$SCR/owlsplit/." .
cat "$SCR/owlsplit/.gitignore" >> .gitignore
git status --short | head -40
```
Expected: `.gitignore` shows as modified (`M`), `README.md` does not appear and the remaining files show as new (`??`).

- [ ] **Step 4: Remove duplicates from `.gitignore` and the rule that ignores the Detekt config**

The template ignores `detekt.yml`, but `config/detekt/detekt.yml` is repo configuration. Run:
```bash
awk '!seen[$0]++ || /^$/' .gitignore > .gitignore.tmp && mv .gitignore.tmp .gitignore
sed -i '/^detekt.yml$/d' .gitignore
git check-ignore config/detekt/detekt.yml; echo "exit=$?"
```
Expected: `exit=1` (the file is no longer ignored).

- [ ] **Step 5: Commit the render as is (baseline)**

```bash
git add -A
git commit -m "chore: scaffold from Templetry kmp/modular-features"
```

### Task 2: Gradle 9.1.0 wrapper

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Modify: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Generate the wrapper in an empty directory** (inside the repo it would fail while evaluating `build-logic` with the old versions)

```bash
W="$(mktemp -d)"; cd "$W"
~/.tools/gradle-8.10.2/bin/gradle wrapper --gradle-version 9.1.0 --distribution-type bin
cp gradlew gradlew.bat ~/Documents/Repos/Owlsplit/
cp gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties ~/Documents/Repos/Owlsplit/gradle/wrapper/
cd ~/Documents/Repos/Owlsplit
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```
Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip`

- [ ] **Step 2: Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper
git commit -m "build: add Gradle 9.1.0 wrapper"
```

### Task 3: Update versions and build conventions

**Files:**
- Rewrite: `gradle/libs.versions.toml`, `build.gradle.kts`, `build-logic/convention/src/main/kotlin/ext/KotlinMultiplatformExtensions.kt`
- Modify: `build-logic/convention/build.gradle.kts`, `KmpAppConventionPlugin.kt`, `KmpDataConventionPlugin.kt`

- [ ] **Step 1: Rewrite `gradle/libs.versions.toml`**

KSP and BuildKonfig are removed (unused), and the `compose.*` accessors (deprecated in CMP ≥ 1.10) are replaced with explicit coordinates.

```toml
[versions]
kotlin                  = "2.4.20"
compose-multiplatform   = "1.12.1"
compose-material3       = "1.9.0"
agp                     = "8.13.2"

coroutines              = "1.11.0"
serialization           = "1.11.0"
datetime                = "0.8.0"
immutable-collections   = "0.5.2"

lifecycle               = "2.11.0"
navigation              = "2.9.2"

ktor                    = "3.6.0"
koin                    = "4.2.2"
sqldelight              = "2.4.1"
datastore               = "1.2.1"
coil                    = "3.6.3"
kermit                  = "2.2.0"
multiplatform-settings  = "1.3.0"

detekt                  = "1.23.8"
detekt-kotlin           = "2.0.21"

security-crypto         = "1.1.0-beta01"
leakcanary              = "2.14"

[libraries]
kotlinx-coroutines-core    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core",    version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test",    version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime           = { module = "org.jetbrains.kotlinx:kotlinx-datetime",           version.ref = "datetime" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "immutable-collections" }

compose-runtime            = { module = "org.jetbrains.compose.runtime:runtime",                   version.ref = "compose-multiplatform" }
compose-foundation         = { module = "org.jetbrains.compose.foundation:foundation",             version.ref = "compose-multiplatform" }
compose-ui                 = { module = "org.jetbrains.compose.ui:ui",                             version.ref = "compose-multiplatform" }
compose-ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview",             version.ref = "compose-multiplatform" }
compose-animation          = { module = "org.jetbrains.compose.animation:animation",               version.ref = "compose-multiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose-multiplatform" }
compose-material3          = { module = "org.jetbrains.compose.material3:material3",               version.ref = "compose-material3" }

lifecycle-viewmodel         = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel",         version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose   = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose",   version.ref = "lifecycle" }
navigation-compose          = { module = "org.jetbrains.androidx.navigation:navigation-compose",         version.ref = "navigation" }

ktor-client-core                = { module = "io.ktor:ktor-client-core",                version.ref = "ktor" }
ktor-client-okhttp              = { module = "io.ktor:ktor-client-okhttp",              version.ref = "ktor" }
ktor-client-darwin              = { module = "io.ktor:ktor-client-darwin",              version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-logging             = { module = "io.ktor:ktor-client-logging",             version.ref = "ktor" }
ktor-client-auth                = { module = "io.ktor:ktor-client-auth",                version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

koin-core              = { module = "io.insert-koin:koin-core",              version.ref = "koin" }
koin-android           = { module = "io.insert-koin:koin-android",           version.ref = "koin" }
koin-compose           = { module = "io.insert-koin:koin-compose",           version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
koin-test              = { module = "io.insert-koin:koin-test",              version.ref = "koin" }

sqldelight-runtime        = { module = "app.cash.sqldelight:runtime",               version.ref = "sqldelight" }
sqldelight-coroutines     = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver",        version.ref = "sqldelight" }
sqldelight-native-driver  = { module = "app.cash.sqldelight:native-driver",         version.ref = "sqldelight" }
sqldelight-jvm-driver     = { module = "app.cash.sqldelight:sqlite-driver",         version.ref = "sqldelight" }

datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
datastore-preferences      = { module = "androidx.datastore:datastore-preferences",      version.ref = "datastore" }

coil-compose      = { module = "io.coil-kt.coil3:coil-compose",       version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }

kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }

multiplatform-settings             = { module = "com.russhwolf:multiplatform-settings",             version.ref = "multiplatform-settings" }
multiplatform-settings-no-arg      = { module = "com.russhwolf:multiplatform-settings-no-arg",      version.ref = "multiplatform-settings" }
multiplatform-settings-coroutines  = { module = "com.russhwolf:multiplatform-settings-coroutines",  version.ref = "multiplatform-settings" }

security-crypto = { module = "androidx.security:security-crypto",          version.ref = "security-crypto" }
leakcanary      = { module = "com.squareup.leakcanary:leakcanary-android", version.ref = "leakcanary" }

detekt-formatting = { module = "io.gitlab.arturbosch.detekt:detekt-formatting", version.ref = "detekt" }

[bundles]
ktor-common         = ["ktor-client-core", "ktor-client-content-negotiation", "ktor-client-logging", "ktor-client-auth", "ktor-serialization-kotlinx-json"]
koin-compose-bundle = ["koin-compose", "koin-compose-viewmodel"]
settings-bundle     = ["multiplatform-settings", "multiplatform-settings-coroutines"]
compose-core        = ["compose-runtime", "compose-foundation", "compose-ui", "compose-animation", "compose-material3", "compose-components-resources", "compose-ui-tooling-preview"]

[plugins]
kotlin-multiplatform  = { id = "org.jetbrains.kotlin.multiplatform",          version.ref = "kotlin" }
kotlin-serialization  = { id = "org.jetbrains.kotlin.plugin.serialization",   version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose",                       version.ref = "compose-multiplatform" }
compose-compiler      = { id = "org.jetbrains.kotlin.plugin.compose",         version.ref = "kotlin" }
android-application   = { id = "com.android.application",                     version.ref = "agp" }
android-library       = { id = "com.android.library",                         version.ref = "agp" }
sqldelight            = { id = "app.cash.sqldelight",                         version.ref = "sqldelight" }
detekt                = { id = "io.gitlab.arturbosch.detekt",                 version.ref = "detekt" }

kmp-library     = { id = "owlsplit.kmp.library" }
kmp-feature     = { id = "owlsplit.kmp.feature" }
kmp-data        = { id = "owlsplit.kmp.data" }
kmp-domain      = { id = "owlsplit.kmp.domain" }
kmp-core        = { id = "owlsplit.kmp.core" }
kmp-app         = { id = "owlsplit.kmp.app" }
owlsplit-detekt = { id = "owlsplit.detekt" }
```

- [ ] **Step 2: Rewrite `build.gradle.kts` (root)**

```kotlin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.sqldelight)            apply false
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// Detekt 1.23.x is compiled against Kotlin 2.0.21; Gradle would otherwise align its
// Kotlin dependencies with the project's Kotlin and Detekt fails at startup.
allprojects {
    configurations.matching { it.name == "detekt" || it.name == "detektPlugins" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(libs.versions.detekt.kotlin.get())
        }
    }
}
```

- [ ] **Step 3: Check that `build-logic` uses neither KSP nor BuildKonfig**

```bash
grep -rn "ksp\|buildkonfig" build-logic
```
Expected: only the `apply(plugin = "com.google.devtools.ksp")` line in `KmpDataConventionPlugin.kt`, which is removed in the next step.

- [ ] **Step 4: Rewrite `KmpDataConventionPlugin.kt` without KSP**

Path: `build-logic/convention/src/main/kotlin/KmpDataConventionPlugin.kt`
```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Data modules: library + serialization.
 * The SQLDelight plugin is applied per module, only where a database is needed.
 */
class KmpDataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "owlsplit.kmp.library")
    }
}
```

- [ ] **Step 5: Rewrite `ext/KotlinMultiplatformExtensions.kt`**

`kotlinOptions` is an error since Kotlin 2.2; `compilerOptions` is used instead. `iosX64` is removed (Intel simulators, not supported as a main tier in Kotlin 2.4).

```kotlin
package ext

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal const val COMPILE_SDK = 36
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 24

internal fun Project.configureKmp(
    block: KotlinMultiplatformExtension.() -> Unit = {},
) {
    extensions.configure<KotlinMultiplatformExtension> {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = project.name
                isStatic = true
            }
        }
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
        block()
    }
}

internal fun Project.configureAndroidLibrary(namespace: String) {
    extensions.configure<CommonExtension<*, *, *, *, *, *>>("android") {
        this.namespace = namespace
        compileSdk = COMPILE_SDK
        defaultConfig { minSdk = MIN_SDK }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
```

- [ ] **Step 6: SDK 36 in `KmpAppConventionPlugin.kt`**

```bash
F=build-logic/convention/src/main/kotlin/KmpAppConventionPlugin.kt
sed -i 's/compileSdk = 35/compileSdk = ext.COMPILE_SDK/; s/minSdk *= 24/minSdk        = ext.MIN_SDK/; s/targetSdk *= 35/targetSdk     = ext.TARGET_SDK/' "$F"
grep -n "Sdk" "$F"
```
Expected: three lines with `ext.COMPILE_SDK`, `ext.MIN_SDK`, `ext.TARGET_SDK`.

- [ ] **Step 7: Install the Android 36 platform**

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0"
ls "$ANDROID_HOME/platforms"
```
Expected: `android-36` is listed.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts build-logic
git commit -m "build: update to Kotlin 2.4.20, CMP 1.12.1, AGP 8.13.2 and SDK 36"
```

### Task 4: Remove the template sample code

**Files:**
- Delete: `feature/`, `data/network/`, `data/models/`, `data/repository/`, `domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/usecases/user/`, `data/local/src/commonMain/kotlin/es/sebas1705/owlsplit/data/local/dao/`, `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/UserEntity.sq`
- Rewrite: `settings.gradle.kts`, `domain/usecases/build.gradle.kts`, `data/local/build.gradle.kts`, `core/ui/build.gradle.kts`, `composeApp/build.gradle.kts`, `App.kt`, `AppModule.kt`
- Create: `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/Meta.sq`

- [ ] **Step 1: Delete the sample**

```bash
git rm -r -q feature data/network data/models data/repository \
  domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/usecases/user \
  data/local/src/commonMain/kotlin/es/sebas1705/owlsplit/data/local/dao \
  data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/UserEntity.sq
```

- [ ] **Step 2: Rewrite `settings.gradle.kts`**

```kotlin
rootProject.name = "Owlsplit"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
    }
}

include(":composeApp")

include(
    ":core:common",
    ":core:ui",
    ":core:security",
    ":core:network",
    ":core:datastore",
    ":core:logging",
    ":core:testing",
)

include(":data:local")

include(":domain:usecases")
```

- [ ] **Step 3: `domain/usecases/build.gradle.kts` without data dependencies**

```kotlin
plugins { alias(libs.plugins.kmp.domain) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

- [ ] **Step 4: `data/local/build.gradle.kts` without `data:models`**

```kotlin
plugins {
    alias(libs.plugins.kmp.data)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("es.sebas1705.owlsplit.data.local.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}
```

- [ ] **Step 5: Minimal `Meta.sq` schema** (key-value table that P4 will use for internal markers, e.g. the sync schema version)

Path: `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/Meta.sq`
```sql
CREATE TABLE meta (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

get:
SELECT value FROM meta WHERE key = ?;

put:
INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?);
```

- [ ] **Step 6: `core/ui/build.gradle.kts` with explicit coordinates**

```kotlin
plugins {
    alias(libs.plugins.kmp.core)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
    }
}
```

- [ ] **Step 7: Rewrite `composeApp/build.gradle.kts`**

The template's `AppConfig` generator is kept (profiles, Templetry ADR-0018); `apiBaseUrl` becomes the exchange-rate API, the only HTTP API in the MVP.

```kotlin
plugins {
    alias(libs.plugins.kmp.app)
}

// ── Environment profiles ─────────────────────────────────────────────────────
// The active profile is resolved here and generated into commonMain as one typed
// object every target reads.
val templetryProfiles = mapOf(
    //             fxApiBaseUrl                      verbose  cacheSeconds
    "development" to Triple("https://api.frankfurter.dev/v1", true, 0),
    "staging" to Triple("https://api.frankfurter.dev/v1", true, 43_200),
    "production" to Triple("https://api.frankfurter.dev/v1", false, 43_200),
)

val appEnvironment: String =
    (project.findProperty("appEnv") as String?)
        ?: System.getenv("APP_ENV")
        ?: "development"

require(appEnvironment in templetryProfiles) {
    "Unknown appEnv \"$appEnvironment\" — want one of ${templetryProfiles.keys}"
}

val generateAppConfig = tasks.register("generateAppConfig") {
    val environment = appEnvironment
    val profile = templetryProfiles.getValue(environment)
    val outputDir = layout.buildDirectory.dir("generated/templetry/commonMain/kotlin")

    inputs.property("environment", environment)
    outputs.dir(outputDir)

    doLast {
        val packageDir = outputDir.get().asFile.resolve("es/sebas1705/owlsplit/config")
        packageDir.mkdirs()
        packageDir.resolve("AppConfig.kt").writeText(
            """
            |package es.sebas1705.owlsplit.config
            |
            |/** The active environment profile. Generated at build time — do not edit. */
            |object AppConfig {
            |    const val ENVIRONMENT: String = "$environment"
            |    const val FX_API_BASE_URL: String = "${profile.first}"
            |    const val VERBOSE_LOGGING: Boolean = ${profile.second}
            |    const val FX_CACHE_SECONDS: Int = ${profile.third}
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig)
        }
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:security"))
            implementation(project(":core:network"))
            implementation(project(":core:datastore"))
            implementation(project(":core:logging"))
            implementation(project(":data:local"))
            implementation(project(":domain:usecases"))
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
    }
}

dependencies {
    debugImplementation(libs.leakcanary)
}
```

- [ ] **Step 8: Rewrite `App.kt`**

Path: `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/App.kt`
```kotlin
package es.sebas1705.owlsplit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.config.EnvironmentBadge
import es.sebas1705.owlsplit.core.ui.theme.AppTheme

/** Root composable. Navigation and real screens arrive with P8–P9. */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme(modifier) {
        EnvironmentBadge {
            Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Owlsplit", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
```

- [ ] **Step 9: Rewrite `AppModule.kt`**

Path: `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/di/AppModule.kt`
```kotlin
package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.createDataStore
import es.sebas1705.owlsplit.core.security.SecureStorage
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.data.local.createDatabase
import org.koin.dsl.module

val appModule = module {
    single { SecureStorage() }
    single { AppPreferences(createDataStore()) }
    single { createDatabase(get<DatabaseDriverFactory>()) }
}
```

- [ ] **Step 10: Check that no references to the sample remain**

```bash
grep -rIn "feature\.\|UserApi\|UserRepository\|GetMeUseCase\|UserLocalDao\|data:models\|data:network\|data:repository" \
  --include=*.kt --include=*.kts . | grep -v "/build/"
```
Expected: no results.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "refactor: remove template sample features and user example"
```

### Task 5: Build, run on Android and fix

**Files:** whatever the compiler points at (see the table of known failures)

- [ ] **Step 1: Build**

```bash
./gradlew :composeApp:assembleDebug --stacktrace 2>&1 | tail -40
```
Expected: `BUILD SUCCESSFUL`.

Known failures and how to fix them:

| Error | Fix |
|---|---|
| `Unresolved reference 'kotlinOptions'` in some module's `build.gradle.kts` | Replace the block with `compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }` |
| `Unresolved reference: compose` (`compose.runtime` accessors, etc.) in a module | Replace the Compose dependency block with `implementation(libs.bundles.compose.core)` |
| `Unresolved reference 'Icons'` in `core:ui` (`ErrorScreen.kt`) | Add `org.jetbrains.compose.material:material-icons-core:1.7.3` to the catalog as `compose-material-icons-core` and to `core/ui` |
| `desktopMain` or `jvm("desktop")` in some file | Delete those lines (rendering with `desktop=false` should have removed them; if not, it is a template bug to report to Templetry) |
| `Plugin [id: 'owlsplit.kmp.…'] was not found` | Check that `build-logic/convention/build.gradle.kts` registers the `owlsplit.*` ids (identity rename) |

Repeat Step 1 until `BUILD SUCCESSFUL`. Every unplanned fix is noted in the commit message.

- [ ] **Step 2: Tests and Detekt**

```bash
./gradlew testDebugUnitTest detekt 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`. If Detekt flags template code, fix it (do not disable rules) unless the rule clashes with Compose (`FunctionNaming` on `@Composable`): in that case add `ignoreAnnotated: ['Composable']` in `config/detekt/detekt.yml` under `naming > FunctionNaming`.

- [ ] **Step 3: Start it on the emulator**

```bash
"$ANDROID_HOME/emulator/emulator" -list-avds
```
If there is none, create one: `"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd -n owlsplit_api36 -k "system-images;android-36;google_apis;x86_64"` (installing the image first with `sdkmanager "system-images;android-36;google_apis;x86_64"`). Start it in the background and then:
```bash
"$ANDROID_HOME/platform-tools/adb" wait-for-device
./gradlew :composeApp:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n es.sebas1705.owlsplit/.MainActivity
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > /tmp/owlsplit-p1.png
```
Expected: the screenshot shows "Owlsplit" centered and the `development` environment badge.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "build: make the scaffold compile and run on Android"
```

### Task 6: Local schema test

**Files:**
- Test: `data/local/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/local/MetaQueriesTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package es.sebas1705.owlsplit.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaQueriesTest {
    private fun newDb(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }

    @Test
    fun `missing key returns null`() {
        assertNull(newDb().metaQueries.get("schema").executeAsOneOrNull())
    }

    @Test
    fun `put overwrites the previous value`() {
        val db = newDb()
        db.metaQueries.put("schema", "1")
        db.metaQueries.put("schema", "2")
        assertEquals("2", db.metaQueries.get("schema").executeAsOne())
    }
}
```

- [ ] **Step 2: Run**

```bash
./gradlew :data:local:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, 2 tests passed (`data/local/build/reports/tests/testDebugUnitTest/index.html`).

- [ ] **Step 3: Commit**

```bash
git add data/local/src/androidUnitTest
git commit -m "test: cover the meta key-value table"
```

### Task 7: GitHub Actions CI

**Files:**
- Delete: `.github/workflows/release.yml`, `.github/workflows/security.yml`, `.github/workflows/changelog-label-gate.yml`, `.github/workflows/validate.yml`
- Create: `.github/workflows/ci.yml`

The repo is private: macOS minutes count ×10 against the 2,000 free minutes per month. That is why the iOS job only runs on PRs to `main` and when triggered manually. CodeQL is not available on private repos without GitHub Advanced Security, and the desktop release does not apply. The release pipeline arrives in P11.

- [ ] **Step 1: Replace the workflows**

```bash
git rm -q .github/workflows/release.yml .github/workflows/security.yml \
  .github/workflows/changelog-label-gate.yml .github/workflows/validate.yml
```

Create `.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
    branches: [main]
    paths-ignore: ["docs/**", "**/*.md"]
  pull_request:
    branches: [main]
    paths-ignore: ["docs/**", "**/*.md"]
  workflow_dispatch:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  android:
    name: Android (detekt, tests, assemble)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew detekt testDebugUnitTest :composeApp:assembleDebug --continue
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: "**/build/reports/tests/"

  ios:
    name: iOS framework
    if: github.event_name == 'pull_request' || github.event_name == 'workflow_dispatch'
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 2: Commit and push**

```bash
git add .github
git commit -m "ci: Android checks on every push, iOS framework on PRs"
git push
gh run list --limit 1
```

- [ ] **Step 3: Check the Android job and trigger the iOS one once**

```bash
gh run watch "$(gh run list --limit 1 --json databaseId -q '.[0].databaseId')" --exit-status
gh workflow run CI --ref main
```
Expected: the first run ends in `success`. Follow the second one (`workflow_dispatch`, includes iOS) with `gh run watch`; if the iOS job fails, the error is fixed in this task, because it is the only iOS verification until a Mac is available.

### Task 8: Repo documentation

**Files:**
- Delete: `AI_INDEX.md`, `CHANGELOG.md`, `docs/ai/`
- Rewrite: `MODULE_MAP.md`
- Modify: `README.md`, `ARCHITECTURE.md`, `AGENTS.md`

- [ ] **Step 1: Delete the template documents that describe the sample**

```bash
git rm -r -q AI_INDEX.md CHANGELOG.md docs/ai
```

- [ ] **Step 2: Rewrite `MODULE_MAP.md`**

```markdown
# Module map

| Module | Contents | Plan that extends it |
|---|---|---|
| `composeApp` | Android/iOS entry point, root DI, per-environment `AppConfig` | P8–P9 |
| `core:common` | Base MVI, `AppResult`, utilities | — |
| `core:ui` | Theme and shared components | P7 |
| `core:security` | `SecureStorage` | — |
| `core:network` | Per-platform Ktor client | P9 (exchange rates) |
| `core:datastore` | Preferences (`AppPreferences`) | P8 |
| `core:logging` | Kermit | — |
| `core:testing` | Test utilities | — |
| `core:money` (created in P3) | Money, currencies, remainder allocation, exchange rates | P3 |
| `data:local` | SQLDelight (`AppDatabase`) | P4 |
| `domain:usecases` | Use cases; `ledger` (balances and debts) | P3 |

Planned modules: `data:models`, `data:repository` (P4), `data:remote`, `data:sync` (P2, P6), `feature:*` (P8–P10).
```

- [ ] **Step 3: Development section in `README.md`**

Add before `## License`:
```markdown
## Development

Requirements: JDK 21 and Android SDK (platform 36). iOS only builds on macOS.

```bash
export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="/c/Users/sebss/Android/sdk"
./gradlew :composeApp:assembleDebug          # development APK
./gradlew testDebugUnitTest detekt           # tests and static analysis
./gradlew :composeApp:assembleDebug -PappEnv=staging   # another environment
```

Module structure in [MODULE_MAP.md](MODULE_MAP.md); implementation plans in [docs/plans](docs/plans/README.md).
```
and change the status line to: `> **Status:** under construction (P1 done). See [plans](docs/plans/README.md).`

- [ ] **Step 4: Remove references to the sample from the other documents**

```bash
grep -n "feature:auth\|feature:home\|feature:profile\|feature:settings\|data:network\|UserApi\|desktop\|Desktop" ARCHITECTURE.md AGENTS.md CONTRIBUTING.md
```
For each line found: if it is a table row or list item about a deleted module or the desktop target, delete the line; if it is the layer diagram in `ARCHITECTURE.md`, replace `feature:auth │ feature:home │ feature:…` with `feature:… (P8–P10)` and remove "Desktop (JVM)" from the description. Repeat the `grep` until it returns nothing.

- [ ] **Step 5: Commit and push**

```bash
git add -A
git commit -m "docs: adapt template docs to Owlsplit"
git push
```

### Task 9: Mark P1 as done

- [ ] **Step 1:** In `docs/plans/README.md`, change the status of P1 to `Done`.
- [ ] **Step 2: Commit**

```bash
git add docs/plans/README.md
git commit -m "docs: mark P1 as done"
git push
```

## Final verification of P1

- `./gradlew detekt testDebugUnitTest :composeApp:assembleDebug` → `BUILD SUCCESSFUL`.
- The app starts on the emulator and shows "Owlsplit".
- The latest CI run is green, including the manually triggered iOS job.
- `git status` clean; `grep -rI "kmpbase\|KMPNativeBase" --include=*.kt --include=*.kts .` empty.
