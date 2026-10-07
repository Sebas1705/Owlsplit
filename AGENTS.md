# Agent Operating Contract

This file defines rules for AI agents editing this codebase.

## Must-read before any change

1. `ARCHITECTURE.md` — layers, patterns, what depends on what
2. `MODULE_MAP.md` — which module exports which class

## Non-negotiable rules

- **Never put suspend calls inside `updateState { }` lambda** — it is a non-suspend `(S) -> S`
- **Never skip `execute { }` wrapper** — all coroutine work in ViewModels goes through it
- **Never import across forbidden layer boundaries** — feature cannot import feature; domain cannot import data:remote
- **Always declare `:core:security` explicitly** in any module that transitively uses `SecureStorage`
- **Always use convention plugins** — do not copy-paste `kotlin.multiplatform { }` blocks
- **Detekt must pass** — run `./gradlew detekt` before marking task done

## Adding a feature

Follow the 5-step recipe in `MODULE_MAP.md#adding-a-new-feature-module`.

## Changing the database schema

1. Edit the `.sq` file in `data/local/src/commonMain/sqldelight/`. Once a schema has shipped, never edit it in place: add a migration `n.sqm` instead.
2. Run `./gradlew :data:local:generateCommonMainAppDatabaseSchema` **on its own** (not together with other tasks) to refresh the `databases/n.db` snapshot.
3. Run `./gradlew verifySqlDelightMigration` and update the row mapping in `data/repository/Rows.kt`.
4. SQLite on Android 7 is 3.9: no `UPSERT`, and booleans are `INTEGER` 0/1 (no `AS Boolean`).

## Environment

- Kotlin 2.4.20 / Compose Multiplatform 1.11.1 / AGP 8.13.2 / Gradle 9.5.1 (see docs/plans/README.md for why these pins)
- Targets: `androidTarget` (minSdk 24, compileSdk 36), `iosArm64 + iosSimulatorArm64`
- DI: Koin 4.x (no Hilt — incompatible with KMP)
- DB: SQLDelight 2.x (no Room — not yet fully KMP)

## Required checks before finishing

```sh templetry:checks
./gradlew build
```

## This project came from a template

Four facts you cannot infer from the code in front of you:

- **Never hand-edit `.templetry-answers.yml`.** It records what generated this project. Editing it makes the next update merge against a state that never existed.
- **Before writing a capability by hand, run `templetry pieces`.** Auth, RBAC, audit trails, API keys and whole CRUD resources may already exist as pieces for this template. Adopting one is `templetry add <name>`, and it brings its own tests.
- **`templetry update` pulls improvements from the template** through a three-way merge that keeps your edits. Use it instead of copying files from the template by hand.
- **Directives like `tpl:if` belong to the template, not here.** If you find one in this project, it is a rendering bug worth reporting — do not try to interpret it.
