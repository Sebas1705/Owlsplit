# P1 · Base del proyecto — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Generar el proyecto Owlsplit desde la plantilla Templetry `kmp/modular-features`, actualizarlo a las versiones estables actuales, quitar el código de ejemplo y dejar una app que arranca en Android con CI verde.

**Architecture:** Se conserva la estructura de la plantilla (convenciones en `build-logic`, capas `core`/`data`/`domain`/`feature`, Koin, SQLDelight). Se elimina el ejemplo de usuario/login y todos los módulos `feature:*`; los módulos de datos y features reales llegan en P4–P10. Sin target de escritorio.

**Tech Stack:** Gradle 9.1.0, Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 8.13.2, Koin 4.2.2, SQLDelight 2.4.1, Ktor 3.6.0, Detekt 1.23.8.

**Platform:** KMP (Android + iOS)

---

## Contexto para quien ejecuta

- Repo: `C:\Users\sebss\Documents\Repos\Owlsplit` (Git Bash: `~/Documents/Repos/Owlsplit`). Ya contiene `README.md`, `.gitignore` y `docs/`. **No sobrescribir** esos archivos con los de la plantilla.
- Entorno obligatorio en cada terminal:
  ```bash
  export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
  export ANDROID_HOME="/c/Users/sebss/Android/sdk"
  ```
- CLI de plantillas: `templetry` (ya instalado en `~/go/bin`).
- Commits con la identidad configurada del usuario, **sin** líneas `Co-Authored-By` ni pies "Generated with".
- Paquete raíz tras el renombrado: `es.sebas1705.owlsplit` (ruta `es/sebas1705/owlsplit`). Los plugins de convención pasan a llamarse `owlsplit.kmp.*`.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Crear (render) | todo lo de la plantilla salvo `README.md` y `.gitignore` |
| Crear | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` |
| Reescribir | `gradle/libs.versions.toml` |
| Reescribir | `build.gradle.kts` |
| Reescribir | `settings.gradle.kts` |
| Reescribir | `build-logic/convention/src/main/kotlin/ext/KotlinMultiplatformExtensions.kt` |
| Modificar | `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/KmpAppConventionPlugin.kt`, `build-logic/convention/src/main/kotlin/KmpDataConventionPlugin.kt` |
| Reescribir | `core/ui/build.gradle.kts`, `composeApp/build.gradle.kts`, `domain/usecases/build.gradle.kts`, `data/local/build.gradle.kts` |
| Reescribir | `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/App.kt`, `.../di/AppModule.kt` |
| Crear | `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/Meta.sq`, `data/local/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/local/MetaQueriesTest.kt` |
| Borrar | `feature/` entero, `data/network/`, `data/models/`, `data/repository/`, `domain/usecases/.../user/`, `data/local/.../dao/UserLocalDao.kt`, `data/local/.../db/UserEntity.sq`, `AI_INDEX.md`, `CHANGELOG.md`, `docs/ai/`, `.github/workflows/{release,security,changelog-label-gate}.yml` |
| Reescribir | `.github/workflows/validate.yml` → `.github/workflows/ci.yml`, `MODULE_MAP.md` |
| Modificar | `README.md` (sección Desarrollo), `.gitignore` |

---

### Task 1: Generar el proyecto desde la plantilla

**Files:**
- Create: todo el árbol de la plantilla dentro del repo (salvo `README.md`, `.gitignore`)

- [ ] **Step 1: Renderizar en el scratchpad**

```bash
SCR="$(mktemp -d)"
templetry init kmp/modular-features --out "$SCR/owlsplit" \
  --set project_name=Owlsplit \
  --set base_package=es.sebas1705.owlsplit \
  --feature desktop=false
ls "$SCR/owlsplit"
```
Expected: lista con `build-logic composeApp config core data domain feature gradle iosApp settings.gradle.kts ...` y un `.templetry-answers.yml`. Si `init` no escribe `.templetry-answers.yml`, anotarlo en el commit (lo necesita `templetry update`).

- [ ] **Step 2: Comprobar el renombrado de identidad**

```bash
grep -rIl "dev.template.kmpbase\|kmpbase\|KMPNativeBase" "$SCR/owlsplit" | grep -v "/.git/" | head
ls "$SCR/owlsplit/composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit"
```
Expected: el `grep` no devuelve nada; el `ls` muestra `App.kt config di`.

- [ ] **Step 3: Copiar al repo sin pisar lo existente**

```bash
cd ~/Documents/Repos/Owlsplit
cp -rn "$SCR/owlsplit/." .
cat "$SCR/owlsplit/.gitignore" >> .gitignore
git status --short | head -40
```
Expected: `.gitignore` aparece como modificado (`M`), `README.md` no aparece y el resto de archivos salen como nuevos (`??`).

- [ ] **Step 4: Quitar duplicados del `.gitignore` y la regla que ignora la config de Detekt**

La plantilla ignora `detekt.yml`, pero `config/detekt/detekt.yml` es configuración del repo. Ejecutar:
```bash
awk '!seen[$0]++ || /^$/' .gitignore > .gitignore.tmp && mv .gitignore.tmp .gitignore
sed -i '/^detekt.yml$/d' .gitignore
git check-ignore config/detekt/detekt.yml; echo "exit=$?"
```
Expected: `exit=1` (el archivo ya no se ignora).

- [ ] **Step 5: Commit del render tal cual (línea base)**

```bash
git add -A
git commit -m "chore: scaffold from Templetry kmp/modular-features"
```

### Task 2: Wrapper de Gradle 9.1.0

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Modify: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Generar el wrapper en un directorio vacío** (en el repo fallaría al evaluar `build-logic` con versiones antiguas)

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

### Task 3: Actualizar versiones y convenciones de build

**Files:**
- Rewrite: `gradle/libs.versions.toml`, `build.gradle.kts`, `build-logic/convention/src/main/kotlin/ext/KotlinMultiplatformExtensions.kt`
- Modify: `build-logic/convention/build.gradle.kts`, `KmpAppConventionPlugin.kt`, `KmpDataConventionPlugin.kt`

- [ ] **Step 1: Reescribir `gradle/libs.versions.toml`**

Se quitan KSP y BuildKonfig (no se usan) y los accesores `compose.*` se sustituyen por coordenadas explícitas (deprecados en CMP ≥ 1.10).

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

- [ ] **Step 2: Reescribir `build.gradle.kts` (raíz)**

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

- [ ] **Step 3: Comprobar que `build-logic` no usa KSP ni BuildKonfig**

```bash
grep -rn "ksp\|buildkonfig" build-logic
```
Expected: solo la línea `apply(plugin = "com.google.devtools.ksp")` de `KmpDataConventionPlugin.kt`, que se elimina en el paso siguiente.

- [ ] **Step 4: Reescribir `KmpDataConventionPlugin.kt` sin KSP**

Ruta: `build-logic/convention/src/main/kotlin/KmpDataConventionPlugin.kt`
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

- [ ] **Step 5: Reescribir `ext/KotlinMultiplatformExtensions.kt`**

`kotlinOptions` es un error desde Kotlin 2.2; se usa `compilerOptions`. Se elimina `iosX64` (simuladores Intel, sin soporte en Kotlin 2.4 como tier principal).

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

- [ ] **Step 6: SDK 36 en `KmpAppConventionPlugin.kt`**

```bash
F=build-logic/convention/src/main/kotlin/KmpAppConventionPlugin.kt
sed -i 's/compileSdk = 35/compileSdk = ext.COMPILE_SDK/; s/minSdk *= 24/minSdk        = ext.MIN_SDK/; s/targetSdk *= 35/targetSdk     = ext.TARGET_SDK/' "$F"
grep -n "Sdk" "$F"
```
Expected: tres líneas con `ext.COMPILE_SDK`, `ext.MIN_SDK`, `ext.TARGET_SDK`.

- [ ] **Step 7: Instalar la plataforma Android 36**

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0"
ls "$ANDROID_HOME/platforms"
```
Expected: aparece `android-36`.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts build-logic
git commit -m "build: update to Kotlin 2.4.20, CMP 1.12.1, AGP 8.13.2 and SDK 36"
```

### Task 4: Quitar el código de ejemplo de la plantilla

**Files:**
- Delete: `feature/`, `data/network/`, `data/models/`, `data/repository/`, `domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/usecases/user/`, `data/local/src/commonMain/kotlin/es/sebas1705/owlsplit/data/local/dao/`, `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/UserEntity.sq`
- Rewrite: `settings.gradle.kts`, `domain/usecases/build.gradle.kts`, `data/local/build.gradle.kts`, `core/ui/build.gradle.kts`, `composeApp/build.gradle.kts`, `App.kt`, `AppModule.kt`
- Create: `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/Meta.sq`

- [ ] **Step 1: Borrar el ejemplo**

```bash
git rm -r -q feature data/network data/models data/repository \
  domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/usecases/user \
  data/local/src/commonMain/kotlin/es/sebas1705/owlsplit/data/local/dao \
  data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/UserEntity.sq
```

- [ ] **Step 2: Reescribir `settings.gradle.kts`**

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

- [ ] **Step 3: `domain/usecases/build.gradle.kts` sin dependencias de datos**

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

- [ ] **Step 4: `data/local/build.gradle.kts` sin `data:models`**

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

- [ ] **Step 5: Esquema mínimo `Meta.sq`** (tabla clave-valor que P4 usará para marcas internas, p. ej. la versión del esquema de sincronización)

Ruta: `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/Meta.sq`
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

- [ ] **Step 6: `core/ui/build.gradle.kts` con coordenadas explícitas**

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

- [ ] **Step 7: Reescribir `composeApp/build.gradle.kts`**

Se conserva el generador de `AppConfig` de la plantilla (perfiles, ADR-0018 de Templetry); `apiBaseUrl` pasa a ser la API de tipos de cambio, que es la única API HTTP del MVP.

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

- [ ] **Step 8: Reescribir `App.kt`**

Ruta: `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/App.kt`
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

- [ ] **Step 9: Reescribir `AppModule.kt`**

Ruta: `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/di/AppModule.kt`
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

- [ ] **Step 10: Comprobar que no quedan referencias al ejemplo**

```bash
grep -rIn "feature\.\|UserApi\|UserRepository\|GetMeUseCase\|UserLocalDao\|data:models\|data:network\|data:repository" \
  --include=*.kt --include=*.kts . | grep -v "/build/"
```
Expected: sin resultados.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "refactor: remove template sample features and user example"
```

### Task 5: Compilar, arrancar en Android y corregir

**Files:** los que señale el compilador (ver tabla de fallos conocidos)

- [ ] **Step 1: Compilar**

```bash
./gradlew :composeApp:assembleDebug --stacktrace 2>&1 | tail -40
```
Expected: `BUILD SUCCESSFUL`.

Fallos conocidos y su corrección:

| Error | Corrección |
|---|---|
| `Unresolved reference 'kotlinOptions'` en algún `build.gradle.kts` de módulo | Sustituir el bloque por `compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }` |
| `Unresolved reference: compose` (accesores `compose.runtime`, etc.) en un módulo | Sustituir el bloque de dependencias Compose por `implementation(libs.bundles.compose.core)` |
| `Unresolved reference 'Icons'` en `core:ui` (`ErrorScreen.kt`) | Añadir `org.jetbrains.compose.material:material-icons-core:1.7.3` al catálogo como `compose-material-icons-core` y a `core/ui` |
| `desktopMain` o `jvm("desktop")` en algún archivo | Borrar esas líneas (el render con `desktop=false` debería haberlas quitado; si no, es un fallo de la plantilla que se anota para Templetry) |
| `Plugin [id: 'owlsplit.kmp.…'] was not found` | Revisar que `build-logic/convention/build.gradle.kts` registra los ids `owlsplit.*` (renombrado de identidad) |

Repetir el Step 1 hasta `BUILD SUCCESSFUL`. Cada corrección no prevista se anota en el mensaje del commit.

- [ ] **Step 2: Tests y Detekt**

```bash
./gradlew testDebugUnitTest detekt 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`. Si Detekt marca código de la plantilla, corregirlo (no desactivar reglas) salvo que la regla choque con Compose (`FunctionNaming` en `@Composable`): en ese caso añadir `ignoreAnnotated: ['Composable']` en `config/detekt/detekt.yml` bajo `naming > FunctionNaming`.

- [ ] **Step 3: Arrancar en el emulador**

```bash
"$ANDROID_HOME/emulator/emulator" -list-avds
```
Si no hay ninguno, crear uno: `"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd -n owlsplit_api36 -k "system-images;android-36;google_apis;x86_64"` (instalando antes la imagen con `sdkmanager "system-images;android-36;google_apis;x86_64"`). Arrancarlo en segundo plano y luego:
```bash
"$ANDROID_HOME/platform-tools/adb" wait-for-device
./gradlew :composeApp:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n es.sebas1705.owlsplit/.MainActivity
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > /tmp/owlsplit-p1.png
```
Expected: la captura muestra "Owlsplit" centrado y la insignia de entorno `development`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "build: make the scaffold compile and run on Android"
```

### Task 6: Test del esquema local

**Files:**
- Test: `data/local/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/local/MetaQueriesTest.kt`

- [ ] **Step 1: Escribir el test**

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

- [ ] **Step 2: Ejecutar**

```bash
./gradlew :data:local:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, 2 tests pasados (`data/local/build/reports/tests/testDebugUnitTest/index.html`).

- [ ] **Step 3: Commit**

```bash
git add data/local/src/androidUnitTest
git commit -m "test: cover the meta key-value table"
```

### Task 7: CI de GitHub Actions

**Files:**
- Delete: `.github/workflows/release.yml`, `.github/workflows/security.yml`, `.github/workflows/changelog-label-gate.yml`, `.github/workflows/validate.yml`
- Create: `.github/workflows/ci.yml`

El repo es privado: los minutos de macOS cuentan ×10 sobre los 2000 gratuitos al mes. Por eso el job de iOS solo corre en PRs hacia `main` y a mano. CodeQL no está disponible en repos privados sin GitHub Advanced Security, y el release de escritorio no aplica. El pipeline de release llega en P11.

- [ ] **Step 1: Sustituir los workflows**

```bash
git rm -q .github/workflows/release.yml .github/workflows/security.yml \
  .github/workflows/changelog-label-gate.yml .github/workflows/validate.yml
```

Crear `.github/workflows/ci.yml`:
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

- [ ] **Step 2: Commit y push**

```bash
git add .github
git commit -m "ci: Android checks on every push, iOS framework on PRs"
git push
gh run list --limit 1
```

- [ ] **Step 3: Comprobar el job de Android y lanzar el de iOS una vez**

```bash
gh run watch "$(gh run list --limit 1 --json databaseId -q '.[0].databaseId')" --exit-status
gh workflow run CI --ref main
```
Expected: el primer run termina en `success`. Seguir el segundo (`workflow_dispatch`, incluye iOS) con `gh run watch`; si el job de iOS falla, el error se corrige en esta tarea porque es la única verificación de iOS hasta que haya un Mac.

### Task 8: Documentación del repo

**Files:**
- Delete: `AI_INDEX.md`, `CHANGELOG.md`, `docs/ai/`
- Rewrite: `MODULE_MAP.md`
- Modify: `README.md`, `ARCHITECTURE.md`, `AGENTS.md`

- [ ] **Step 1: Borrar documentos de la plantilla que describen el ejemplo**

```bash
git rm -r -q AI_INDEX.md CHANGELOG.md docs/ai
```

- [ ] **Step 2: Reescribir `MODULE_MAP.md`**

```markdown
# Mapa de módulos

| Módulo | Contenido | Plan que lo amplía |
|---|---|---|
| `composeApp` | Entrada Android/iOS, DI raíz, `AppConfig` por entorno | P8–P9 |
| `core:common` | MVI base, `AppResult`, utilidades | — |
| `core:ui` | Tema y componentes compartidos | P7 |
| `core:security` | `SecureStorage` | — |
| `core:network` | Cliente Ktor por plataforma | P9 (tipos de cambio) |
| `core:datastore` | Preferencias (`AppPreferences`) | P8 |
| `core:logging` | Kermit | — |
| `core:testing` | Utilidades de test | — |
| `core:money` (se crea en P3) | Dinero, divisas, reparto de restos, tipos de cambio | P3 |
| `data:local` | SQLDelight (`AppDatabase`) | P4 |
| `domain:usecases` | Casos de uso; `ledger` (saldos y deudas) | P3 |

Módulos previstos: `data:models`, `data:repository` (P4), `data:remote`, `data:sync` (P2, P6), `feature:*` (P8–P10).
```

- [ ] **Step 3: Sección de desarrollo en `README.md`**

Añadir antes de `## Licencia`:
```markdown
## Desarrollo

Requisitos: JDK 21 y Android SDK (platform 36). iOS solo compila en macOS.

```bash
export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="/c/Users/sebss/Android/sdk"
./gradlew :composeApp:assembleDebug          # APK de desarrollo
./gradlew testDebugUnitTest detekt           # tests y análisis estático
./gradlew :composeApp:assembleDebug -PappEnv=staging   # otro entorno
```

Estructura de módulos en [MODULE_MAP.md](MODULE_MAP.md); planes de implementación en [docs/plans](docs/plans/README.md).
```
y cambiar la línea de estado por: `> **Estado:** en construcción (P1 completado). Ver [planes](docs/plans/README.md).`

- [ ] **Step 4: Quitar del resto de documentos las referencias al ejemplo**

```bash
grep -n "feature:auth\|feature:home\|feature:profile\|feature:settings\|data:network\|UserApi\|desktop\|Desktop" ARCHITECTURE.md AGENTS.md CONTRIBUTING.md
```
Para cada línea encontrada: si es una fila de tabla o un elemento de lista sobre un módulo borrado o el escritorio, borrar la línea; si es el diagrama de capas de `ARCHITECTURE.md`, sustituir `feature:auth │ feature:home │ feature:…` por `feature:… (P8–P10)` y quitar "Desktop (JVM)" de la descripción. Repetir el `grep` hasta que no devuelva nada.

- [ ] **Step 5: Commit y push**

```bash
git add -A
git commit -m "docs: adapt template docs to Owlsplit"
git push
```

### Task 9: Marcar P1 como hecho

- [ ] **Step 1:** En `docs/plans/README.md`, cambiar el estado de P1 a `Hecho`.
- [ ] **Step 2: Commit**

```bash
git add docs/plans/README.md
git commit -m "docs: mark P1 as done"
git push
```

## Verificación final de P1

- `./gradlew detekt testDebugUnitTest :composeApp:assembleDebug` → `BUILD SUCCESSFUL`.
- La app arranca en el emulador y muestra "Owlsplit".
- El último run de CI está en verde, incluido el job de iOS lanzado a mano.
- `git status` limpio; `grep -rI "kmpbase\|KMPNativeBase" --include=*.kt --include=*.kts .` vacío.
