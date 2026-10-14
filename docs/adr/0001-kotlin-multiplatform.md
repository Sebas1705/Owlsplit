# 0001 · Kotlin Multiplatform + Compose Multiplatform

- **Status:** accepted · 2026-10-07

## Context
A shared-expenses app is only useful if the whole group uses it, and groups mix Android and iOS. The author is fluent in Kotlin, Compose and Android (YouKnow) and maintains Templetry templates for `android` and `kmp`.

## Decision
Android and iOS from the MVP with **Kotlin Multiplatform** and **Compose Multiplatform**, generating the project with the Templetry template `kmp/modular-features` (layered Clean Architecture, MVI, Koin, SQLDelight, Ktor, Coil, dev/stg/prd profiles). On generation, the template's versions (Kotlin 2.1 and CMP 1.7 originally) are updated to the current stable ones. The template's desktop target is disabled in the MVP.

## Consequences
- Domain, data, sync and UI are 100 % shared; only the integration points (Google/Apple Sign-In, WorkManager, links) are platform-specific.
- iOS does not build on Windows: CI with a macOS runner and manual iOS validation at milestones.

## Rejected alternatives
- **Android only** (`android/modular-features`): faster to start, but leaves out half the group.
- **Android + lightweight web**: does not cover the native experience on iPhone.
- **Flutter / React Native**: throw away the existing Kotlin experience and templates.
