# Implementation plans — Core (MVP)

The core spec ([2026-10-07-owlsplit-core-design.md](../designs/2026-10-07-owlsplit-core-design.md)) covers several independent subsystems, so it is implemented in **chained plans**. Each plan leaves software that compiles, passes its tests and can be tried out.

Plans are written in detail (full code, exact commands) **right before executing them**, because each one depends on how the previous one turned out. The first three are already written.

## Plans

| # | Plan | Verifiable deliverable | Depends on | Status |
|---|---|---|---|---|
| P1 | [Project foundation](2026-10-07-p01-foundation.md) | KMP project generated from the template, current versions, template sample removed, app that starts on Android, green CI | — | Done |
| P2 | [Firebase dev + GitLive smoke test](2026-10-07-p02-firebase-smoke.md) | `owlsplit-dev` project, Doppler, per-environment configuration and an instrumented test doing anonymous auth + write + listener against real Firestore | P1 | Done |
| P3 | [Money and balance calculation](2026-10-07-p03-money-ledger.md) | `core:money` and `domain` (`ledger`): currencies, splitting, conversion, balances and simplification, with table and property tests | P1 | Done |
| P4 | [Local database and repositories](2026-10-07-p04-local-db-repositories.md) | Full SQLDelight schema (entities, outbox, cursors, conflicts), DAOs, repositories with Flows, verified migrations | P1, P3 | Done |
| P5 | [Firestore rules](2026-10-07-p05-firestore-rules.md) | `firebase/firestore.rules`, indexes and `firebase/rules-test` with the emulator, in CI | P2 | Done |
| P6 | Sync engine: [P6a engine](2026-10-07-p06a-sync-engine.md) · [P6b Firestore](2026-10-07-p06b-firestore-adapter.md) | `data:remote` + `data:sync`: outbox, upload with backoff, incremental listeners, conflicts, lost access; tests with fake Firestore | P4, P5 | Done |
| P7 | [Design system](2026-10-07-p07-design-system.md) | `OwlsplitTheme`, tokens, Inter, Tabler icons, `core:ui` components with previews and light/dark screenshots | P1 | Done |
| P8 | [Accounts and onboarding](2026-10-07-p08-accounts-onboarding.md) | Anonymous auth, onboarding, profile, link Google/Apple, delete account | P6, P7 | Done (Google pending activation in the console; Apple in P11) |
| P9 | Groups, expenses and balances (screens): [P9a](2026-10-08-p09a-groups-expenses.md) · [P9b](2026-10-08-p09b-currencies-payers.md) · [P9c](2026-10-08-p09c-history-filters.md) | Home, group (expenses, balances, settle up), expense and split editor, transfers, history, multi-currency with Frankfurter | P6, P7, P8 | Done |
| P10 | Invites: [P10a](2026-10-08-p10a-invites.md) · [P10b](2026-10-08-p10b-qr-links.md) | Codes, QR, App Links / Universal Links with Firebase Hosting, joining and claiming a member | P9 | Done (iOS Universal Links in P11) |
| P11 | Polish and delivery: [P11a languages and accessibility](2026-10-08-p11a-i18n-a11y.md) · [P11b delivery](2026-10-08-p11b-release.md) · [P11c iOS](2026-10-08-p11c-ios.md) · [P11d acceptance](../ACCEPTANCE.md) | es/en i18n, accessibility, Crashlytics, a single Firebase project, signing, distribution through App Distribution, iOS (Xcode project, Apple, Universal Links), acceptance criteria checklist | P10 | P11a–c done; P11d (acceptance) with manual tests pending on Authentication and the Apple account |
| P12 | After the beta ([market study](../research/2026-10-09-market-study.md)): [P12a one-tap settle up](2026-10-09-p12a-settle-links.md) · [P12b summary and export](2026-10-09-p12b-stats-export.md) | Get paid by Bizum, PayPal or Revolut from the balance; group summary and CSV and PDF export | P11 | Done |
| P13 | Switching and shared flats: [P13a import from Splitwise](2026-10-10-p13a-splitwise-import.md) · [P13b recurring expenses](2026-10-10-p13b-recurring.md) | Bring a Splitwise group over with the same balances; expenses that repeat on their own (rent, subscriptions) | P12 | Done |
| P14 | [Member identity](2026-10-12-p14-identity.md) ([study](../designs/2026-10-12-member-identity.md)) | Leave and come back without losing anything: the member remembers its accounts, reassociates automatically with the same account and is recovered with another after confirming; Google accounts visible | P13 | Done |
| P15 | [Update notices](2026-10-12-p15-updates.md) | Home notifies when there is a new version on the download page and opens its APK; «Ahora no» ("Not now") waits for the next one | P14 | Done |
| P16 | Google Play: [owner guide](../play/PLAY-CONSOLE-GUIDE.md) | Privacy and account deletion on the website, store listing (generated texts, graphics and screenshots) in `fastlane/metadata`, AAB upload on every tag, promotion to production via workflow; the app installed from Play doesn't show the APK notice | P15 | In progress (owner steps in Play Console) |

## Dependencies and parallelism

```
P1 ──┬── P2 ── P5 ──┐
     ├── P3 ── P4 ──┴── P6 ──┬── P8 ── P9 ── P10 ── P11
     └── P7 ─────────────────┘
```

- **P2, P3 and P7** can run in parallel after P1: they don't share files, except `settings.gradle.kts` and `gradle/libs.versions.toml`, which are touched in small tasks and integrated with rebase.
- **P2 goes first** if there is only one person: it is the risk test for the GitLive SDK (ADR 0002). If it fails, the `data:remote` design changes.
- Each plan ends with a green build, green tests and one commit per task.

## Environment (SEBAS-PC)

```bash
export JAVA_HOME="/c/Users/sebss/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="/c/Users/sebss/Android/sdk"
```

The system JDK 26 doesn't work for Gradle/Kotlin. iOS doesn't build on Windows: it is built in CI (macOS).

## Pinned versions (2026-10-07, validated in P1)

| Component | Version | Note |
|---|---|---|
| Gradle | 9.5.1 | 9.1 compiles build-logic with Kotlin 2.2, which can't read Kotlin 2.4 metadata; AGP 8 breaks with Gradle ≥ 9.6 |
| Kotlin | 2.4.20 | |
| Compose Multiplatform | 1.11.1 (material3 1.9.0) | 1.12 requires compileSdk 37 |
| Android Gradle Plugin | 8.13.2 | Supports at most compileSdk 36. AGP 9 requires the `com.android.kotlin.multiplatform.library` plugin; it will be migrated in a separate plan when the template does |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |
| SQLDelight | 2.4.1 | |
| Koin | 4.2.2 | |
| Ktor | 3.5.2 | 3.6 pulls in OkHttp 5.5, which requires compileSdk 37 |
| Lifecycle (JetBrains) | 2.10.0 | 2.11 requires compileSdk 37 |
| Coil | 3.5.0 | 3.6 requires compileSdk 37 |
| GitLive Firebase | 2.7.0 | Validated in P2 |
| kotlinx-coroutines / serialization / datetime | 1.11.0 / 1.11.0 / 0.8.0 | |
| Detekt | 1.23.8 | With its Kotlin pinned to 2.0.21 (known incompatibility with Kotlin ≥ 2.1) |
