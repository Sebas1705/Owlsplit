# P8 · Accounts and onboarding — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** What the spec asks for in [3.1 Account and onboarding](../designs/2026-10-07-owlsplit-core-design.md#31-account-and-onboarding) and the account part of [3.10 Settings](../designs/2026-10-07-owlsplit-core-design.md#310-settings): a guaranteed anonymous session before any data is created, 3-step onboarding, profile (name, avatar, preferred currency, theme), link Google, sign in with an existing Google account on a fresh install, sign out (linked accounts only), delete account, and the nudge to link. Plus the app's navigation shell (Home · Activity · Profile) where P9 will put its screens.

**Architecture:**
- `data:remote`: `AccountService` (Firebase Auth: session, link, sign in, sign out, delete) and `ProfileRemote` (`users/{uid}`).
- `data:repository`: `ProfileStore` (local profile in DataStore) and new operations `GroupRepository.leave` and `MemberRepository.release`; `LocalData.wipe()` erases the local database.
- `domain:usecases`: `DeleteAccount` and `ShouldSuggestLinking`.
- `feature:onboarding` and `feature:profile` (template MVI, Koin, Compose Multiplatform); `composeApp` mounts the root navigation.
- Google on Android with **Credential Manager** (`androidx.credentials` + `googleid`); the `idToken` is turned into a Firebase credential with GitLive. An `expect`/`actual` `GoogleSignIn` isolates the platform.

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation Compose 2.9.2 (typed routes with `@Serializable`), Koin 4.2 (`koinViewModel`), DataStore 1.2.1, GitLive Auth 2.7.0, AndroidX Credentials 1.6.0, Google Identity `googleid` 1.2.1, Roborazzi for screenshots.

**Platform:** KMP (Android verified on emulator; iOS compiles in CI)

**Depends on:** P6 (engine and adapter), P7 (design system).

---

## Decisions

1. **Session before data.** The app doesn't let you create anything until there is a uid: onboarding ends with the anonymous session ready (if there is no network the first time, the final step waits with a notice "Necesitas conexión la primera vez" ("You need a connection the first time")). This closes the gap noted in P6b (empty `createdBy`).
2. **Profile**: name (1–40), initials avatar with a `GroupAccent` color (stored as `"accent:GREEN"`; photos will come with Storage), preferred currency (ISO) and theme (system/light/dark). It lives in DataStore (the UI never waits for the network) and is copied to `users/{uid}` with `set(merge)` when there is a session; the SDK queues it if offline.
3. **Link Google** (`linkWithCredential`) keeps the uid and data. If the credential already belongs to another account, in P8 the user is told and nothing happens: the group handover in [FIREBASE.md](../FIREBASE.md#authentication) needs joining by code, which comes with P10.
4. **Sign in with Google on a fresh install** ("Ya tengo cuenta" ("I already have an account") on the welcome screen): `signInWithCredential`; the local database is empty and the engine downloads that account's groups. Only offered when there is no local data.
5. **Sign out** only with a linked account: uploads pending changes (if it can't, it warns and doesn't sign out), erases the local database, signs out and returns to onboarding.
6. **Delete account** ([FIREBASE.md](../FIREBASE.md#account-deletion)): upload pending changes; for each accessible group, release my member (`userUid = null`) and leave (`memberUids` without me; no history entry, like archiving, because after leaving you can no longer write to the group); upload; delete `users/{uid}`; `currentUser.delete()`; erase the local database and preferences. Offline, it stops before touching anything remote that can't be completed and says so. If Firebase requires a recent sign-in (Google account), Google is requested again and it retries.
7. **Apple** is postponed to P11: the `iosApp` project doesn't have an `.xcodeproj` yet (template bug) and it can't be tested from Windows. It is mandatory for App Store publishing if Google is offered; it stays on the P11 checklist.
8. **Link nudge**: after the first group or the fifth expense, if the account is anonymous and it hasn't been dismissed. Shown on Home (dismissible card); P9 integrates it into the real Home.
9. **Language**: strings go in Compose resources (`composeResources/values/strings.xml`, Spanish). English and the language picker come in P11.
10. **Navigation**: bottom bar Home · Activity · Profile. Home and Activity show empty states until P9. The design catalog moves to a "Catálogo de diseño" ("Design catalog") entry in Profile, only in `development`.

## File map

| Action | Path |
|---|---|
| Create | `data/remote/.../AccountService.kt`, `ProfileRemote.kt` |
| Create | `data/repository/.../ProfileStore.kt`, `LocalData.kt`; modify `GroupRepositoryImpl.kt`, `MemberRepositoryImpl.kt`, `Repositories.kt` |
| Modify | `data/local/.../*.sq` (`deleteAll` per table) |
| Modify | `core/datastore/.../AppPreferences.kt` (three-value theme, profile, dismissed nudge) |
| Create | `domain/usecases/.../account/DeleteAccount.kt`, `ShouldSuggestLinking.kt` + tests |
| Create | `feature/onboarding/**`, `feature/profile/**` (+ Roborazzi screenshots) |
| Create | `composeApp/.../navigation/**`, `GoogleSignIn` (`expect`/`actual`) |
| Modify | `composeApp/.../App.kt`, `di/AppModule.kt`, `sync/SyncModule.kt` |
| Modify | `settings.gradle.kts`, `gradle/libs.versions.toml` |
| Modify | `docs/FIREBASE.md`, `docs/plans/README.md`, `MODULE_MAP.md` |

---

### Task 1: Account and profile data
- [ ] `deleteAll` in each `.sq` and `LocalData.wipe()` (one transaction). Test: after writing groups and expenses, `wipe` leaves everything empty.
- [ ] `MemberRepository.release(memberId)` and `GroupRepository.leave(groupId)` (+ `myMember(groupId)`). Tests: the payload uploads `userUid = null`; leaving removes my uid, leaves no history and goes into the outbox after releasing the member.
- [ ] `AppPreferences`: `themeMode`, `linkNudgeDismissed`, profile (`name`, `avatar`, `currency`); `ProfileStore` on top of it.
- [ ] Commit.

### Task 2: `AccountService` and `ProfileRemote`
- [ ] `AccountService`: `session: Flow<Session?>` (uid, anonymous, providers), `ensureSession()`, `linkGoogle(idToken)`, `signInWithGoogle(idToken)`, `signOut()`, `delete()`; typed results (`Linked`, `CredentialInUse`, `RecentLoginRequired`, `Failed`).
- [ ] `ProfileRemote.save(uid, profile)` and `delete(uid)`.
- [ ] Commit.

### Task 3: Use cases
- [ ] `ShouldSuggestLinking` (table tests).
- [ ] `DeleteAccount` with ports for the engine, the account and the remote profile (tests with fakes: step order, offline doesn't delete the account, reauthentication).
- [ ] Commit.

### Task 4: `feature:onboarding`
- [ ] Welcome → name and avatar → create or join a group (in P8 both finish onboarding and open Home; P9/P10 wire them up). Skipping at any step uses "Yo" ("Me") as a provisional name. "Ya tengo cuenta" on the welcome screen.
- [ ] ViewModel with tests; light/dark screenshots.
- [ ] Commit.

### Task 5: `feature:profile`
- [ ] Account (name, avatar, anonymous/linked status), Link Google, preferred currency, theme, Sign out (if linked), Delete account (confirmation), Catalog (dev only).
- [ ] ViewModel with tests; screenshots.
- [ ] Commit.

### Task 6: App: navigation, Google and nudge
- [ ] Root navigation (onboarding if there is no profile; otherwise, tabs). Theme per preference.
- [ ] `GoogleSignIn` on Android (Credential Manager; `default_web_client_id` from `google-services.json`) and iOS (not available until P11).
- [ ] Debug SHA-1 fingerprint on the `owlsplit-dev` Android app (`firebase apps:android:sha:create`). Enable Google in the Authentication console (manual owner step) and re-sync `google-services.json` from Doppler.
- [ ] Test on the emulator: onboarding, profile, theme, delete account; linking once Google is enabled.
- [ ] Commit.

### Task 7: Documentation and PR
- [ ] `FIREBASE.md` (profile in `users/{uid}`, Apple postponed), `MODULE_MAP.md`, plan index. PR and green CI.
