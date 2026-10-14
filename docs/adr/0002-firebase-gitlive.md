# 0002 · Firebase with the GitLive KMP SDK

- **Status:** accepted · 2026-10-07

## Context
We need authentication, real-time shared storage between members and, later on, notifications and files. The author already runs Firebase in YouKnow (rules tested with the emulator, Doppler, App Distribution, minimal service accounts).

## Decision
**Firebase**: Authentication (anonymous, Google, Apple), Firestore, Hosting (invites), Crashlytics and App Distribution, accessed from common code with `dev.gitlive:firebase-*`. Spark plan during the MVP, no Cloud Functions or Storage.

## Consequences
- Security and validation in the Firestore rules, tested with the emulator in CI.
- Dependency on the GitLive SDK: the first step of the plan is a smoke test on Android and iOS (anonymous auth, write, listener). If it failed on iOS, the alternative is the Firestore REST API with Ktor behind the same `data:remote` interface.
- Features that need a server (push to other users, cleanup) come with the Blaze plan in later subprojects.

## Rejected alternatives
- **Supabase**: better relational model, but offline would have to be built anyway and there is no prior experience with it.
- **Own backend** (Ktor or Workers + D1): maximum control, too much work before having something usable.

## Validation (P2, 2026-10-07)
Smoke test passed with GitLive 2.7.0 (`data:remote`, `scripts/run-firebase-smoke.sh`): anonymous auth, write and real-time listener on Android (API 36 emulator) against `owlsplit-dev`; the iOS framework with GitLive builds in CI. Running it on an iOS device is still pending; it needs a Mac and the Xcode project the template does not generate (P11 checklist).
