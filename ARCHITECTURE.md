# Architecture

## Overview

Owlsplit is a **Kotlin Multiplatform** template targeting **Android** and **iOS**.
It follows **Clean Architecture** with a strict layered module graph.

```
┌──────────────────────────────────────────────────────┐
│                   composeApp / iosApp                │  ← Entry points
├──────────────────────────────────────────────────────┤
│              feature:… (P8–P10)                      │  ← UI + MVI
├──────────────────────────────────────────────────────┤
│                  domain:usecases                     │  ← Business logic
├──────────────────────────────────────────────────────┤
│   data:repository  │  data:remote   │  data:local    │  ← Data layer
├──────────────────────────────────────────────────────┤
│  data:models │ core:common │ core:ui │ core:security  │  ← Shared foundations
└──────────────────────────────────────────────────────┘
```

## Layers (dependency direction: up → down)

| Layer | Modules | Can depend on |
|-------|---------|---------------|
| App   | `composeApp`, `iosApp` | feature, domain, data, core |
| Feature | `feature:*` | domain, data:models, core |
| Domain | `domain:usecases` | data:repository (interface), data:models, core:common |
| Data | `data:repository`, `data:remote`, `data:sync`, `data:local` | data:models, core |
| Core | `core:*` | nothing project-local |

## Key patterns

### MVI
Every feature module has:
- `*State : MVIBaseState` — immutable data class
- `*Intent : MVIBaseIntent` — sealed interface
- `*ViewModel : MVIBaseViewModel<S,I>` — handles intents, emits state

State mutations happen via `updateState { reducer }` (non-suspend).
Coroutine work happens inside `execute { suspend block }`.
**Never** call suspend functions inside the `updateState {}` lambda.

### Expect / Actual
Platform-specific implementations live under:
- `androidMain` — Android SDK
- `iosMain` — Kotlin/Native + Darwin/Foundation

Classes with `expect`: `SecureStorage`, `DatabaseDriverFactory`, `httpEngineFactory`, `createDataStore`.

### Dependency Injection
[Koin 4.x](https://insert-koin.io/) — DSL-based, KMP-native, no annotation processing.
All modules are wired in `composeApp/src/commonMain/.../di/AppModule.kt`.

### Local database
[SQLDelight 2.x](https://sqldelight.github.io/) generates type-safe Kotlin from `.sq` files.
Schema files live in `data/local/src/commonMain/sqldelight/`.

### Networking
[Ktor 3.x](https://ktor.io/) with per-platform engine:
- Android → OkHttp
- iOS → Darwin (NSURLSession)

### Image loading
[Coil 3.x](https://coil-kt.github.io/coil/) — KMP-native, Compose integration, Ktor network fetcher.

### Navigation
`org.jetbrains.androidx.navigation:navigation-compose` 2.9.1 with `@Serializable` type-safe routes.

## Convention plugins

See `build-logic/` — all build config is encapsulated in convention plugins to keep module
`build.gradle.kts` files small. Pick the right plugin:

| Plugin ID | Use for |
|-----------|---------|
| `owlsplit.kmp.library` | Any library module |
| `owlsplit.kmp.feature` | Feature modules (+ Compose) |
| `owlsplit.kmp.data` | Data modules (+ KSP) |
| `owlsplit.kmp.domain` | Domain/use-case modules |
| `owlsplit.kmp.core` | Core modules |
| `owlsplit.kmp.app` | composeApp |
| `owlsplit.detekt` | Add Detekt to any module |
