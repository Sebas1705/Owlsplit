# P2 · Firebase dev + prueba de humo GitLive — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Crear el proyecto Firebase de desarrollo, guardar su configuración en Doppler y demostrar con un test instrumentado que el SDK KMP de GitLive hace autenticación anónima, escritura y escucha en tiempo real contra Firestore real desde Android, y que compila para iOS.

**Architecture:** Nuevo módulo `data:remote`, que en P6 alojará el acceso a Firestore. En P2 solo contiene las dependencias de GitLive y un test instrumentado que inicializa Firebase con opciones pasadas como argumentos de instrumentación (sin `google-services.json` en el módulo). La app aplica el plugin `google-services` solo cuando existe el archivo, para que CI siga compilando sin secretos.

**Tech Stack:** `dev.gitlive:firebase-auth`/`firebase-firestore` 2.7.0, Google Services plugin 4.5.0, AndroidX Test (runner 1.7.0, ext-junit 1.3.0), firebase-tools (`npx`), Doppler CLI.

**Platform:** KMP (Android + iOS)

**Depende de:** P1 terminado.

---

## Contexto para quien ejecuta

- Mismo entorno que P1 (`JAVA_HOME`, `ANDROID_HOME`). Repo en `~/Documents/Repos/Owlsplit`.
- Las acciones sobre la cuenta de Google/Firebase se hacen con la sesión del usuario en `npx firebase-tools`. **Pasos marcados 👤 los hace el usuario** (inicio de sesión interactivo y activar proveedores en la consola); el agente le pide que los haga y espera confirmación.
- Si el id `owlsplit-dev` está cogido, usar `owlsplit-dev-<4 letras>` y sustituirlo en **todos** los comandos y en `docs/FIREBASE.md`.
- Identificadores por entorno (se fijan aquí y los usan P5–P11):

| Entorno | Sufijo | Android applicationId / iOS bundle id |
|---|---|---|
| development | `.dev` | `es.sebas1705.owlsplit.dev` |
| staging | `.stg` | `es.sebas1705.owlsplit.stg` |
| production | — | `es.sebas1705.owlsplit` |

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Modificar | `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `composeApp/build.gradle.kts`, `.gitignore`, `docs/FIREBASE.md`, `MODULE_MAP.md`, `docs/plans/README.md` |
| Crear | `data/remote/build.gradle.kts` |
| Crear | `data/remote/src/androidInstrumentedTest/kotlin/es/sebas1705/owlsplit/data/remote/FirebaseSmokeTest.kt` |
| Crear | `firebase/firebase.json`, `firebase/.firebaserc`, `firebase/firestore.rules`, `firebase/firestore.indexes.json` |
| Crear | `scripts/doppler-sync-local.sh`, `scripts/run-firebase-smoke.sh` |

---

### Task 1: Proyecto Firebase `owlsplit-dev`

- [ ] **Step 1: 👤 Iniciar sesión en firebase-tools**

Pedir al usuario que ejecute en su terminal y confirme:
```bash
npx firebase-tools login
```
Comprobar: `npx firebase-tools login:list` muestra su cuenta.

- [ ] **Step 2: Crear el proyecto**

```bash
npx firebase-tools projects:create owlsplit-dev --display-name "Owlsplit Dev"
```
Expected: `Your Firebase project is ready!`. Plan Spark (sin facturación).

- [ ] **Step 3: Crear la base de datos Firestore en Europa**

```bash
npx firebase-tools firestore:databases:create "(default)" --location eur3 --project owlsplit-dev
```
Expected: `Successfully created projects/owlsplit-dev/databases/(default)`.

- [ ] **Step 4: 👤 Activar autenticación anónima**

Pedir al usuario: consola de Firebase → proyecto *Owlsplit Dev* → Authentication → *Comenzar* → *Sign-in method* → **Anónimo** → Habilitar → Guardar. (Google y Apple se activan en P8.)

- [ ] **Step 5: Registrar las apps Android e iOS de desarrollo**

```bash
npx firebase-tools apps:create ANDROID "Owlsplit Dev" --package-name es.sebas1705.owlsplit.dev --project owlsplit-dev
npx firebase-tools apps:create IOS "Owlsplit Dev" --bundle-id es.sebas1705.owlsplit.dev --project owlsplit-dev
npx firebase-tools apps:list --project owlsplit-dev
```
Expected: dos apps listadas; anotar sus **App ID** (`1:…:android:…` y `1:…:ios:…`).

- [ ] **Step 6: Descargar las configuraciones a un directorio temporal** (nunca al repo)

```bash
T="$(mktemp -d)"
npx firebase-tools apps:sdkconfig ANDROID <ANDROID_APP_ID> --project owlsplit-dev > "$T/google-services.json"
npx firebase-tools apps:sdkconfig IOS <IOS_APP_ID> --project owlsplit-dev > "$T/GoogleService-Info.plist"
grep -o '"current_key": "[^"]*"' "$T/google-services.json" | head -1
```
Expected: se ve una `current_key` (API key). Si el primer renglón del archivo es un mensaje de la CLI en lugar de JSON, borrarlo.

### Task 2: Doppler

- [ ] **Step 1: Crear el proyecto Doppler y cargar secretos de `dev`**

```bash
doppler projects create owlsplit --description "Owlsplit app config"
doppler secrets set --project owlsplit --config dev \
  GOOGLE_SERVICES_JSON="$(cat "$T/google-services.json")" \
  GOOGLE_SERVICE_INFO_PLIST="$(cat "$T/GoogleService-Info.plist")" \
  FIREBASE_PROJECT_ID="owlsplit-dev" \
  FIREBASE_ANDROID_APP_ID="<ANDROID_APP_ID>" \
  FIREBASE_IOS_APP_ID="<IOS_APP_ID>" \
  FIREBASE_API_KEY="$(grep -o '"current_key": "[^"]*"' "$T/google-services.json" | head -1 | cut -d'"' -f4)"
doppler secrets --project owlsplit --config dev --only-names
rm -rf "$T"
```
Expected: los seis nombres listados. Los configs `stg` y `prd` existen vacíos (se rellenan en P11).

- [ ] **Step 2: Script de sincronización local**

Crear `scripts/doppler-sync-local.sh`:
```bash
#!/usr/bin/env bash
# Writes the Firebase config files of one Doppler config (dev by default) to the
# paths the build expects. Both files are git-ignored.
set -euo pipefail
CONFIG="${1:-dev}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

doppler secrets get GOOGLE_SERVICES_JSON --project owlsplit --config "$CONFIG" --plain \
  > "$ROOT/composeApp/google-services.json"
doppler secrets get GOOGLE_SERVICE_INFO_PLIST --project owlsplit --config "$CONFIG" --plain \
  > "$ROOT/iosApp/iosApp/GoogleService-Info.plist"

echo "Firebase config for '$CONFIG' written to composeApp/ and iosApp/iosApp/"
```

```bash
chmod +x scripts/doppler-sync-local.sh
ls iosApp/iosApp/ >/dev/null && ./scripts/doppler-sync-local.sh dev
git status --short
```
Expected: el script escribe ambos archivos y `git status` **no** los muestra (ya están en `.gitignore` desde P1). Si `iosApp/iosApp` no existe, ver la carpeta real con `ls iosApp` y corregir la ruta en el script.

- [ ] **Step 3: Commit**

```bash
git add scripts/doppler-sync-local.sh
git commit -m "chore: add Doppler sync script for Firebase config"
```

### Task 3: Id de aplicación por entorno y plugin google-services

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `composeApp/build.gradle.kts`

- [ ] **Step 1: Catálogo**

En `gradle/libs.versions.toml`, añadir en `[versions]`:
```toml
gitlive-firebase        = "2.7.0"
google-services         = "4.5.0"
androidx-test-runner    = "1.7.0"
androidx-test-core      = "1.7.0"
androidx-test-ext-junit = "1.3.0"
```
en `[libraries]`:
```toml
gitlive-firebase-auth      = { module = "dev.gitlive:firebase-auth",      version.ref = "gitlive-firebase" }
gitlive-firebase-firestore = { module = "dev.gitlive:firebase-firestore", version.ref = "gitlive-firebase" }
androidx-test-runner       = { module = "androidx.test:runner",           version.ref = "androidx-test-runner" }
androidx-test-core         = { module = "androidx.test:core",             version.ref = "androidx-test-core" }
androidx-test-ext-junit    = { module = "androidx.test.ext:junit",        version.ref = "androidx-test-ext-junit" }
```
y en `[plugins]`:
```toml
google-services = { id = "com.google.gms.google-services", version.ref = "google-services" }
```

- [ ] **Step 2: Declarar el plugin en la raíz**

En `build.gradle.kts`, dentro de `plugins { }`, añadir:
```kotlin
    alias(libs.plugins.google.services)       apply false
```

- [ ] **Step 3: Sufijo de id y google-services condicional en `composeApp/build.gradle.kts`**

Justo después del bloque `require(appEnvironment in templetryProfiles) { … }`, añadir:
```kotlin
// One Firebase app per environment: development and staging install side by side
// with production thanks to the applicationId suffix.
val applicationIdSuffixByEnv = mapOf("development" to ".dev", "staging" to ".stg", "production" to "")

android {
    defaultConfig {
        applicationIdSuffix = applicationIdSuffixByEnv.getValue(appEnvironment).ifEmpty { null }
    }
}

// The Firebase config comes from Doppler (scripts/doppler-sync-local.sh) and is git-ignored.
// Without it the app still builds (CI, fresh clones) but Firebase is not initialised.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("composeApp/google-services.json missing: building without Firebase config")
}
```

- [ ] **Step 4: Compilar con y sin configuración**

```bash
./gradlew :composeApp:assembleDebug 2>&1 | grep -E "BUILD|google-services"
mv composeApp/google-services.json /tmp/gs.json
./gradlew :composeApp:assembleDebug 2>&1 | grep -E "BUILD|google-services"
mv /tmp/gs.json composeApp/google-services.json
```
Expected: ambas `BUILD SUCCESSFUL`; la segunda muestra el aviso `google-services.json missing`. Si la primera falla con `No matching client found for package name 'es.sebas1705.owlsplit.dev'`, la app Android de la Tarea 1 se registró con otro paquete: corregirla.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts composeApp/build.gradle.kts
git commit -m "build: per-environment application id and optional google-services"
```

### Task 4: Módulo `data:remote` con GitLive

**Files:**
- Create: `data/remote/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Registrar el módulo**

En `settings.gradle.kts`, sustituir `include(":data:local")` por:
```kotlin
include(":data:local", ":data:remote")
```

- [ ] **Step 2: `data/remote/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kmp.data)
}

kotlin {
    androidTarget {
        // Instrumented tests share the `test` source-set tree so they can live in
        // androidInstrumentedTest and run on a device with connectedDebugAndroidTest.
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
```

- [ ] **Step 3: Compilar Android**

```bash
./gradlew :data:remote:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`. Si falla por incompatibilidad de metadatos de Kotlin o de AGP con GitLive 2.7.0, **parar y avisar**: es justo el riesgo que mide este plan (ver Tarea 8).

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts data/remote/build.gradle.kts
git commit -m "build: add data:remote module with GitLive Firebase"
```

### Task 5: Reglas temporales de Firestore para la prueba

**Files:**
- Create: `firebase/firebase.json`, `firebase/.firebaserc`, `firebase/firestore.rules`, `firebase/firestore.indexes.json`

- [ ] **Step 1: Configuración de firebase-tools**

`firebase/firebase.json`:
```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "emulators": {
    "firestore": { "port": 8080 },
    "auth": { "port": 9099 },
    "singleProjectMode": true
  }
}
```
`firebase/.firebaserc`:
```json
{
  "projects": {
    "dev": "owlsplit-dev"
  }
}
```
`firebase/firestore.indexes.json`:
```json
{ "indexes": [], "fieldOverrides": [] }
```

- [ ] **Step 2: Reglas mínimas** (P5 las sustituye por las reales)

`firebase/firestore.rules`:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // P2 smoke test only: each user may read and write its own document.
    match /smoke/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

- [ ] **Step 3: Desplegar a dev**

```bash
cd firebase && npx firebase-tools deploy --only firestore --project dev && cd ..
```
Expected: `Deploy complete!`.

- [ ] **Step 4: Commit**

```bash
git add firebase
git commit -m "chore: firebase config and temporary smoke-test rules"
```

### Task 6: Test instrumentado de humo

**Files:**
- Create: `data/remote/src/androidInstrumentedTest/kotlin/es/sebas1705/owlsplit/data/remote/FirebaseSmokeTest.kt`
- Create: `scripts/run-firebase-smoke.sh`

- [ ] **Step 1: Escribir el test**

```kotlin
package es.sebas1705.owlsplit.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class SmokeDoc(val nonce: String)

/**
 * Proves the GitLive SDK works end to end against the real dev project:
 * anonymous sign-in, a write, and a real-time listener that sees it.
 *
 * Firebase options arrive as instrumentation arguments (see scripts/run-firebase-smoke.sh),
 * so no google-services.json is needed in this module.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseSmokeTest {

    private fun arg(name: String): String =
        requireNotNull(InstrumentationRegistry.getArguments().getString(name)) {
            "Missing instrumentation argument '$name' — run scripts/run-firebase-smoke.sh"
        }

    @Test
    fun anonymousAuthWriteAndListen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = Firebase.initialize(
            context,
            FirebaseOptions(
                applicationId = arg("firebaseAppId"),
                apiKey = arg("firebaseApiKey"),
                projectId = arg("firebaseProjectId"),
            ),
            name = "smoke-${Random.nextInt(1_000_000)}",
        )
        val auth = Firebase.auth(app)
        val firestore = Firebase.firestore(app)

        val uid = assertNotNull(auth.signInAnonymously().user).uid
        val doc = firestore.collection("smoke").document(uid)
        val nonce = Random.nextLong().toString()

        try {
            doc.set(SmokeDoc(nonce))
            val seen = withTimeout(20_000) {
                doc.snapshots.first { it.exists && it.data<SmokeDoc>().nonce == nonce }
            }
            assertEquals(nonce, seen.data<SmokeDoc>().nonce)
        } finally {
            doc.delete()
            auth.currentUser?.delete()
        }
    }
}
```

- [ ] **Step 2: Script para lanzarlo con los valores de Doppler**

`scripts/run-firebase-smoke.sh`:
```bash
#!/usr/bin/env bash
# Runs the GitLive smoke test on the connected device/emulator against owlsplit-dev.
set -euo pipefail
cd "$(dirname "$0")/.."
doppler run --project owlsplit --config dev -- bash -c '
  ./gradlew :data:remote:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.firebaseAppId="$FIREBASE_ANDROID_APP_ID" \
    -Pandroid.testInstrumentationRunnerArguments.firebaseApiKey="$FIREBASE_API_KEY" \
    -Pandroid.testInstrumentationRunnerArguments.firebaseProjectId="$FIREBASE_PROJECT_ID"
'
```
```bash
chmod +x scripts/run-firebase-smoke.sh
```

- [ ] **Step 3: Ejecutar con el emulador arrancado** (el de P1, `owlsplit_api36`)

```bash
"$ANDROID_HOME/platform-tools/adb" devices
./scripts/run-firebase-smoke.sh 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL` y `Finished 1 tests`. El informe está en `data/remote/build/reports/androidTests/connected/debug/index.html`.

Fallos posibles:

| Síntoma | Causa y acción |
|---|---|
| `PERMISSION_DENIED` en `set` | Reglas no desplegadas o `projectId` erróneo: repetir Tarea 5 y revisar `FIREBASE_PROJECT_ID` |
| `ADMIN_ONLY_OPERATION` / `OPERATION_NOT_ALLOWED` en `signInAnonymously` | Proveedor anónimo sin activar: repetir Tarea 1, Step 4 |
| `TimeoutCancellationException` | El listener no recibe: es un fallo del SDK → Tarea 8 |
| `NoSuchMethodError` / `ClassNotFoundException` de `com.google.firebase` | Incompatibilidad GitLive ↔ BoM de Firebase → Tarea 8 |

- [ ] **Step 4: Comprobar que no quedan restos en Firestore**

```bash
npx firebase-tools firestore:delete smoke --recursive --project owlsplit-dev --force 2>&1 | tail -1
```
Expected: no había documentos (el test borra el suyo) o los borra.

- [ ] **Step 5: Commit**

```bash
git add data/remote/src scripts/run-firebase-smoke.sh
git commit -m "test: GitLive smoke test against the dev Firebase project"
```

### Task 7: Compilación iOS con GitLive en CI

- [ ] **Step 1: Hacer que `composeApp` dependa de `data:remote`** (para que el framework de iOS enlace GitLive)

En `composeApp/build.gradle.kts`, dentro de `commonMain.dependencies`, después de `implementation(project(":data:local"))`, añadir:
```kotlin
            implementation(project(":data:remote"))
```

- [ ] **Step 2: Compilar Android y abrir PR para que corra el job de iOS**

```bash
./gradlew :composeApp:assembleDebug | tail -1
git switch -c p2/firebase-smoke
git add composeApp/build.gradle.kts
git commit -m "build: link data:remote into the app"
git push -u origin p2/firebase-smoke
gh pr create --title "P2: Firebase dev + GitLive smoke test" --body "Implements docs/plans/2026-10-07-p02-firebase-smoke.md"
gh pr checks --watch
```
Expected: `Android` e `iOS framework` en verde. Los frameworks son **estáticos** (`isStatic = true`), así que los símbolos del SDK de Firebase iOS no se resuelven aquí sino al enlazar la app en Xcode; el job solo comprueba que el código Kotlin de GitLive compila para iOS. Si aun así falla (por ejemplo `Undefined symbols … FIR…` o un error de cinterop de GitLive), **parar y avisar** con el log: entra en la decisión de la Tarea 8. Añadir el SDK de Firebase iOS al proyecto `iosApp` (Swift Package Manager) necesita Xcode y forma parte del checklist de P11.

- [ ] **Step 3: Fusionar**

Con los checks en verde:
```bash
gh pr merge --merge --delete-branch
git switch main && git pull
```

### Task 8: Decisión sobre GitLive

- [ ] **Step 1: Registrar el resultado**

Si las Tareas 4, 6 y 7 pasaron: añadir al final de `docs/adr/0002-firebase-gitlive.md`:
```markdown
## Validación (P2, <fecha>)
Prueba de humo superada con GitLive 2.7.0: auth anónima, escritura y listener en Android contra `owlsplit-dev`; framework iOS compilado en CI. La ejecución en un dispositivo iOS queda pendiente de un Mac (checklist de P11).
```
Si alguna falló de forma no resoluble: **no seguir con P6**. Escribir el error exacto en `docs/adr/0002-firebase-gitlive.md` bajo `## Validación` y avisar al usuario con las dos salidas del ADR: (a) probar `3.0.0-alpha02` de GitLive; (b) implementar `data:remote` sobre la API REST de Firestore con Ktor. La elección es del usuario.

- [ ] **Step 2: Actualizar documentación**

- `docs/FIREBASE.md`, tabla de proyectos: fila `development` con el id real y los App ID de Android e iOS.
- `MODULE_MAP.md`: añadir `| data:remote | Firebase (GitLive): auth y Firestore | P6, P8 |`.
- `docs/plans/README.md`: estado de P2 → `Hecho`.

```bash
git add docs MODULE_MAP.md
git commit -m "docs: record P2 smoke test result"
git push
```

## Verificación final de P2

- `./scripts/run-firebase-smoke.sh` → 1 test pasado contra `owlsplit-dev`.
- La app compila con y sin `google-services.json`; con él se instala como `es.sebas1705.owlsplit.dev`.
- CI verde en el PR, incluido el job de iOS.
- Ningún secreto en git: `git log -p | grep -c "current_key"` → `0`.
