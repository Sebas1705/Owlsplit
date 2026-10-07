# P8 · Cuentas y onboarding — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Lo que el spec pide en [3.1 Cuenta y onboarding](../designs/2026-10-07-owlsplit-core-design.md#31-cuenta-y-onboarding) y la parte de cuenta de [3.10 Ajustes](../designs/2026-10-07-owlsplit-core-design.md#310-ajustes): sesión anónima garantizada antes de crear datos, onboarding de 3 pasos, perfil (nombre, avatar, divisa preferida, tema), vincular Google, entrar con una cuenta Google existente en una instalación nueva, cerrar sesión (solo vinculada), borrar cuenta, y el aviso para vincular. Además, el armazón de navegación de la app (Inicio · Actividad · Perfil) donde P9 pondrá sus pantallas.

**Architecture:**
- `data:remote`: `AccountService` (Firebase Auth: sesión, vincular, entrar, cerrar sesión, borrar) y `ProfileRemote` (`users/{uid}`).
- `data:repository`: `ProfileStore` (perfil local en DataStore) y operaciones nuevas `GroupRepository.leave` y `MemberRepository.release`; `LocalData.wipe()` borra la base local.
- `domain:usecases`: `DeleteAccount` y `ShouldSuggestLinking`.
- `feature:onboarding` y `feature:profile` (MVI de la plantilla, Koin, Compose Multiplatform); `composeApp` monta la navegación raíz.
- Google en Android con **Credential Manager** (`androidx.credentials` + `googleid`); el `idToken` se convierte en credencial de Firebase con GitLive. Una `expect`/`actual` `GoogleSignIn` aísla la plataforma.

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation Compose 2.9.2 (rutas tipadas con `@Serializable`), Koin 4.2 (`koinViewModel`), DataStore 1.2.1, GitLive Auth 2.7.0, AndroidX Credentials 1.6.0, Google Identity `googleid` 1.2.1, Roborazzi para capturas.

**Platform:** KMP (Android verificado en emulador; iOS compila en CI)

**Depende de:** P6 (motor y adaptador), P7 (sistema de diseño).

---

## Decisiones

1. **Sesión antes que datos.** La app no deja crear nada hasta tener uid: el onboarding termina con la sesión anónima lista (si no hay red la primera vez, el paso final espera con un aviso "Necesitas conexión la primera vez"). Así se cierra el hueco anotado en P6b (`createdBy` vacío).
2. **Perfil**: nombre (1–40), avatar de iniciales con un color de `GroupAccent` (se guarda como `"accent:GREEN"`; la foto llegará con Storage), divisa preferida (ISO) y tema (sistema/claro/oscuro). Vive en DataStore (la UI nunca espera a la red) y se copia a `users/{uid}` con `set(merge)` cuando hay sesión; el SDK lo encola si no hay red.
3. **Vincular Google** (`linkWithCredential`) conserva uid y datos. Si la credencial ya pertenece a otra cuenta, en P8 se informa y no se hace nada: el traspaso de grupos de [FIREBASE.md](../FIREBASE.md#autenticación) necesita unirse por código, que llega con P10.
4. **Entrar con Google en una instalación nueva** ("Ya tengo cuenta" en la bienvenida): `signInWithCredential`; la base local está vacía y el motor baja los grupos de esa cuenta. Solo se ofrece si no hay datos locales.
5. **Cerrar sesión** solo con cuenta vinculada: sube lo pendiente (si no puede, avisa y no cierra), borra la base local, cierra sesión y vuelve al onboarding.
6. **Borrar cuenta** ([FIREBASE.md](../FIREBASE.md#borrado-de-cuenta)): subir lo pendiente; por cada grupo con acceso, liberar mi miembro (`userUid = null`) y salir (`memberUids` sin mí; sin entrada de historial, como archivar, porque tras salir ya no se puede escribir en el grupo); subir; borrar `users/{uid}`; `currentUser.delete()`; borrar la base local y las preferencias. Sin red se detiene antes de tocar nada remoto que no se pueda completar y lo dice. Si Firebase pide inicio de sesión reciente (cuenta Google), se pide Google otra vez y se reintenta.
7. **Apple** se aplaza a P11: el proyecto `iosApp` aún no tiene `.xcodeproj` (fallo de la plantilla) y no se puede probar desde Windows. Es obligatorio para publicar en App Store si se ofrece Google; queda en el checklist de P11.
8. **Aviso para vincular**: tras el primer grupo o el quinto gasto, si la cuenta es anónima y no se ha descartado. Se muestra en Inicio (tarjeta descartable); P9 lo integra en el Inicio real.
9. **Idioma**: los textos van en recursos de Compose (`composeResources/values/strings.xml`, español). El inglés y el selector de idioma, en P11.
10. **Navegación**: barra inferior Inicio · Actividad · Perfil. Inicio y Actividad muestran estados vacíos hasta P9. El catálogo de diseño pasa a una entrada "Catálogo de diseño" en Perfil, solo en `development`.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Crear | `data/remote/.../AccountService.kt`, `ProfileRemote.kt` |
| Crear | `data/repository/.../ProfileStore.kt`, `LocalData.kt`; modificar `GroupRepositoryImpl.kt`, `MemberRepositoryImpl.kt`, `Repositories.kt` |
| Modificar | `data/local/.../*.sq` (`deleteAll` por tabla) |
| Modificar | `core/datastore/.../AppPreferences.kt` (tema de tres valores, perfil, aviso descartado) |
| Crear | `domain/usecases/.../account/DeleteAccount.kt`, `ShouldSuggestLinking.kt` + tests |
| Crear | `feature/onboarding/**`, `feature/profile/**` (+ capturas Roborazzi) |
| Crear | `composeApp/.../navigation/**`, `GoogleSignIn` (`expect`/`actual`) |
| Modificar | `composeApp/.../App.kt`, `di/AppModule.kt`, `sync/SyncModule.kt` |
| Modificar | `settings.gradle.kts`, `gradle/libs.versions.toml` |
| Modificar | `docs/FIREBASE.md`, `docs/plans/README.md`, `MODULE_MAP.md` |

---

### Task 1: Datos de cuenta y perfil
- [ ] `deleteAll` en cada `.sq` y `LocalData.wipe()` (una transacción). Test: tras escribir grupos y gastos, `wipe` deja todo vacío.
- [ ] `MemberRepository.release(memberId)` y `GroupRepository.leave(groupId)` (+ `myMember(groupId)`). Tests: el payload sube `userUid = null`; salir quita mi uid, no deja historial y va en el outbox después de liberar el miembro.
- [ ] `AppPreferences`: `themeMode`, `linkNudgeDismissed`, perfil (`name`, `avatar`, `currency`); `ProfileStore` sobre ella.
- [ ] Commit.

### Task 2: `AccountService` y `ProfileRemote`
- [ ] `AccountService`: `session: Flow<Session?>` (uid, anónima, proveedores), `ensureSession()`, `linkGoogle(idToken)`, `signInWithGoogle(idToken)`, `signOut()`, `delete()`; resultados tipados (`Linked`, `CredentialInUse`, `RecentLoginRequired`, `Failed`).
- [ ] `ProfileRemote.save(uid, profile)` y `delete(uid)`.
- [ ] Commit.

### Task 3: Casos de uso
- [ ] `ShouldSuggestLinking` (tests de tabla).
- [ ] `DeleteAccount` con puertos para el motor, la cuenta y el perfil remoto (tests con falsos: orden de pasos, sin red no borra la cuenta, reautenticación).
- [ ] Commit.

### Task 4: `feature:onboarding`
- [ ] Bienvenida → nombre y avatar → crear grupo o unirse (en P8 ambos terminan el onboarding y abren Inicio; P9/P10 los conectan). Saltar en cualquier paso usa "Yo" como nombre provisional. "Ya tengo cuenta" en la bienvenida.
- [ ] ViewModel con tests; capturas claro/oscuro.
- [ ] Commit.

### Task 5: `feature:profile`
- [ ] Cuenta (nombre, avatar, estado anónima/vinculada), Vincular Google, divisa preferida, tema, Cerrar sesión (si vinculada), Borrar cuenta (confirmación), Catálogo (solo dev).
- [ ] ViewModel con tests; capturas.
- [ ] Commit.

### Task 6: App: navegación, Google y aviso
- [ ] Navegación raíz (onboarding si no hay perfil; si no, pestañas). Tema según preferencia.
- [ ] `GoogleSignIn` Android (Credential Manager; `default_web_client_id` de `google-services.json`) e iOS (no disponible hasta P11).
- [ ] Huella SHA-1 de depuración en la app Android de `owlsplit-dev` (`firebase apps:android:sha:create`). Activar Google en la consola de Authentication (paso manual del propietario) y volver a sincronizar `google-services.json` desde Doppler.
- [ ] Probar en el emulador: onboarding, perfil, tema, borrar cuenta; vincular cuando Google esté activado.
- [ ] Commit.

### Task 7: Documentación y PR
- [ ] `FIREBASE.md` (perfil en `users/{uid}`, Apple aplazado), `MODULE_MAP.md`, índice de planes. PR y CI verde.
