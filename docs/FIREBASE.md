# Firebase

Decision in [ADR 0002](adr/0002-firebase-gitlive.md). KMP SDK: `dev.gitlive:firebase-*`.

## Project

A single Firebase project, **`owlsplit`** (decided 2026-10-08: until there are real users, separating development, staging and production is not worth tripling the configuration). Doppler: `owlsplit/prd`. Hosting: `owlsplit.web.app`.

| Project | Status | Firestore | Apps |
|---|---|---|---|
| `owlsplit` | Created 2026-10-08 (P11b); Authentication enabled (Anonymous, Google) | `(default)`, `eur3` | Android `1:596963610720:android:44ad7ca230b5953346f305` (`es.sebas1705.owlsplit`, release builds: testers and users) · Android `1:596963610720:android:61aa6038a2186e6a46f305` (`es.sebas1705.owlsplit.debug`, local debug builds) · iOS `1:596963610720:ios:c3a61a92a48f3cd446f305` (`es.sebas1705.owlsplit`) · Web `1:596963610720:web:bb1f763b3bfaaff346f305` ("Owlsplit web", the web for guests at `/g/{code}`; its config is read from Hosting's `/__/firebase/init.json`) |

Debug builds are a separate app in the same project so they can be installed alongside the testers' one (the signature differs) and do not send their crashes to Crashlytics; they share the database, so local testing uses clearly identifiable test groups. The `owlsplit-dev` (P2–P11a) and `owlsplit-stg` (P11b) projects were deleted.

Creation notes: a new project comes with the Firestore API disabled (enable it before creating the database), and Authentication can only be initialized from the console (*Get started*); via API it requires Identity Platform with billing. **Spark** plan (free) during the MVP: no Cloud Functions or Storage (Storage requires Blaze for new buckets; it will come with receipt scanning).

MVP services: **Authentication** (anonymous, Google, Apple), **Firestore**, **Hosting** (invite page + `assetlinks.json` + `apple-app-site-association`), **Crashlytics** and **App Distribution**.

## Collections

```
users/{uid}
  displayName, avatar ("accent:<COLOR>"), preferredCurrency, linkedProviders[], updatedAt

groups/{groupId}
  name, icon, accent, baseCurrency, startDate?, endDate?,
  inviteCode, memberUids[], createdBy, archivedBy[],
  lastJoinCode?, updatedAt, updatedBy, version, deleted

groups/{groupId}/members/{memberId}
groups/{groupId}/expenses/{expenseId}
groups/{groupId}/transfers/{transferId}
groups/{groupId}/changelog/{entryId}

invites/{code}
  groupId, groupName, groupIcon, memberCount, createdBy, createdAt
```

Composite indexes: ascending `updatedAt` on each subcollection (incremental queries from [SYNC](SYNC.md#download-firestore--local)).

## Document format

Written by `FirestoreRemoteStore` (`data:remote/.../Dtos.kt`):

- The document id is the model's id; `groupId` comes from the path and is not stored.
- `version` (integer), `updatedAt` (server `Timestamp`), `updatedBy` (uid) and `deleted` on groups, members, expenses and transfers. The history carries server `updatedAt` in addition to `createdAt`.
- `createdAt` (client ms), amounts and exchange rates: integers. Dates: text `YYYY-MM-DD`. Enums: their name.
- Optional fields are written as explicit `null` (the rules read every key).
- `payers` and `splitValues`: maps `memberId → integer`. `diff`: list of `{field, before, after}`.

## Rules

Implemented in `firebase/firestore.rules` and tested with the emulator in `firebase/rules-test` (33 tests; job `Firestore rules (emulator)` in CI). Locally:

```bash
cd firebase && npx firebase-tools emulators:exec --only firestore --project demo-owlsplit "npm --prefix rules-test test"
```

- **Authentication required** everywhere (including anonymous users).
- **Every write** carries `version` = previous + 1 (or 1 on create), `updatedAt == request.time` (`serverTimestamp()`) and `updatedBy` = the writer. Each document's keys are closed (`hasOnly`).
- **Nothing is deleted** from the client (soft delete with `deleted`), except `invites/{code}` when regenerating the code.
- `users/{uid}`: only its owner reads and writes.
- `groups/{id}`:
  - **Read**: members (`uid in memberUids`); the list of my groups is queried with `array-contains`.
  - **Create**: `memberUids == [uid]`, `createdBy == uid`, `archivedBy == []`.
  - **Edit (member)**: does not change `memberUids`, `createdBy`, `createdAt` or `lastJoinCode`; in `archivedBy` only adds or removes their own uid.
  - **Join (non-member)**: since they cannot read the group, they write with transforms: `memberUids: arrayUnion(uid)`, `version: increment(1)`, `updatedAt: serverTimestamp()`, `lastJoinCode` = code. The rules require the code to be the current one and that only their own uid is added (max. 50 members).
  - **Leave**: only removes their own uid (`arrayRemove`).
- `members`, `expenses`, `transfers`: read and write only by group members (`get()` of the group). Types, ranges and sizes are validated: texts with maximum length, ISO currency, `YYYY-MM-DD` date, integer amount in (0, 10¹²], integer exchange rate, category and split type from the list, payer and split maps with 1–50 entries, weight 1–100.
  - **Sums are not validated** (payers = amount, exact shares = amount): rules have no loops. The client guarantees them (P3 domain) and `ObserveGroupBalances` (P4) leaves out and flags any inconsistent expense.
  - **Claim member**: `userUid` only goes from null to one's own uid or from one's own uid to null; nobody else's is ever taken or released. The client checks that a user does not claim two members of the same group.
  - On expenses and transfers `createdBy` and `createdAt` do not change; on transfers neither do source, destination or amount (they are deleted and created again).
- `changelog`: create if you are a member, `authorUid == uid` and `updatedAt == request.time` (downloaded by server time); **never** edit or delete.
- `invites/{code}` (id = code): `get` for any authenticated user, `list` forbidden. Only members can create, and only with the group's current code; it contains `groupId`, `groupName`, `groupIcon` and `memberCount` to preview before joining.
- `smoke/{uid}`: kept to repeat the P2 smoke test when updating GitLive.

## Authentication

- First launch: `signInAnonymously`.
- Link: `currentUser.linkWithCredential` (Google with Credential Manager on Android and Google Sign-In on iOS; Apple with AuthenticationServices on iOS). This is the normal case and moves no data.
- **The credential already belongs to another account** (e.g. the app was reinstalled): the user is offered to sign in to that account bringing along the anonymous account's groups, in this order:
  1. Still as anonymous: the groups, their invite codes and the claimed members are saved locally, and those members are released (`userUid = null`).
  2. `signInWithCredential` with the existing account.
  3. For each saved group: join with the code and claim the same member.
  4. If step 2 fails or is cancelled, the members are claimed again as anonymous (nothing is lost).

  The anonymous uid stays unused in those groups' `memberUids` (not shown; it will be cleaned up once there are Cloud Functions).
- Sign-out is only available with a linked account (for an anonymous one it would mean losing access).

### Status (P8)

- Implemented in `FirebaseAccountService` and the use cases in `domain:usecases/account`. The app signs in anonymously on its own (`SyncStarter`) and onboarding does not finish without a session.
- **Google on Android**: Credential Manager (`AndroidGoogleCredentials`) with the *Web client id* `default_web_client_id`, which the Google Services plugin generates from `google-services.json` **only when Google is enabled** in Authentication. Without it, the app hides "Ya tengo cuenta" ("I already have an account") and shows "Vincular estará disponible pronto" ("Linking will be available soon").
  - Registered fingerprints: the debug one on the `.debug` app and the release one on the release app (2026-10-08).
  - To enable it: Firebase console → Authentication → Sign-in method → Google → Enable (support email); then `scripts/setup-firebase-env.sh` and `scripts/doppler-sync-local.sh`.
- **"Ya tengo cuenta"** (new install): `signInWithCredential` and the profile is recovered from `users/{uid}`.
- **The credential already belongs to another account**: for now the user is informed and nothing changes; the group transfer described above needs joining by code (P10).
- **Apple and iOS**: deferred to P11 (the Xcode project is missing); mandatory to publish on the App Store if Google is offered.

### Status (P10a)

- `FirestoreInviteService`: opening Invite flushes the outbox and writes `invites/{code}`; regenerating deletes the previous one. Joining writes to `groups/{id}` only `memberUids` (`arrayUnion`), `lastJoinCode`, `version` (`increment`), `updatedAt` and `updatedBy`, which the `join()` rule checks against the current code.
- Tested against the old `owlsplit-dev` (2026-10-08): invite, clear the app data (new anonymous user), join with the code and claim a member without an account.

### Hosting and App Links (P10b)

- `firebase/hosting` is served at `https://owlsplit.web.app`: `/j/<code>` → `join.html` (code, open the app, steps if it is not installed), `/` home page and `/.well-known/assetlinks.json`. `firebase.json` defines its own `ignore` because the default one excludes files starting with a dot (`.well-known`).
- `assetlinks.json` declares `es.sebas1705.owlsplit` with the SHA‑256 of the release key (`99:0B:…:18:C3`) and `es.sebas1705.owlsplit.debug` with SEBAS-PC's debug one (`B3:DD:…:CE:F5`). Another debug key (another PC) needs its fingerprint here and in the debug app. Check on the device:

  ```sh
  adb shell pm verify-app-links --re-verify es.sebas1705.owlsplit.debug
  adb shell pm get-app-links es.sebas1705.owlsplit.debug
  ```

- Pending (P11c): `apple-app-site-association` for Universal Links (needs the Apple Team ID).

## Account deletion

Without Cloud Functions, the client does it in this order (retryable):
1. For each group: set `userUid = null` on their member (it becomes a ghost, the group's accounting history is kept) and remove their uid from `memberUids`.
2. Delete `users/{uid}`.
3. `currentUser.delete()` (may require recent re-authentication).
4. Delete the local database.

Names the user wrote in expenses of other people's groups remain (they are group data). This is documented in the privacy policy.

## Secrets and configuration

Doppler `owlsplit/prd` stores everything: `GOOGLE_SERVICES_JSON` (both Android apps in one file), `GOOGLE_SERVICE_INFO_PLIST`, `FIREBASE_PROJECT_ID`, `FIREBASE_ANDROID_APP_ID` (the release one, for App Distribution), `FIREBASE_IOS_APP_ID`, `FIREBASE_API_KEY`, the signing (`SIGNING_*`) and the CI service account (`FIREBASE_SERVICE_ACCOUNT_JSON`). GitHub only has `DOPPLER_TOKEN`, read-only.

- `scripts/doppler-sync-local.sh`: writes `google-services.json` and `GoogleService-Info.plist` into `composeApp/` and `iosApp/` (git-ignored).
- `scripts/setup-firebase-env.sh`: downloads the apps' configuration and stores it in Doppler. Run it again after enabling Google or registering fingerprints: it changes `google-services.json`.
- `scripts/setup-release-keystore.sh`: creates the release key (once only; already done), stores it in Doppler and registers its fingerprints. The `.jks` copy lives in `Documents/owlsplit-release.jks` (outside the repo).
- `node scripts/setup-ci-access.mjs [tester emails]`: service account `owlsplit-ci@owlsplit` with minimal permissions (App Distribution, rules, Hosting, indexes), its key in Doppler, `DOPPLER_TOKEN` in GitHub and the `testers` group. Running it again rotates the key and adds testers.

Distribution to testers: [RELEASE.md](RELEASE.md).

## App Check

The apps send App Check tokens, but **nothing is enforced yet**: until enforcement is turned on in the console, requests without a valid token still go through and only show up as unverified in the metrics. The point is to protect the Spark quota (50K reads/day) from scripts that use the public config.

| Build | Provider | Code |
|---|---|---|
| Android release (`es.sebas1705.owlsplit`) | Play Integrity | `composeApp/src/androidRelease/.../appcheck/AppCheckSetup.kt` (`firebase-appcheck-playintegrity`, native SDK: GitLive has no App Check module) |
| Android debug (`es.sebas1705.owlsplit.debug`) | Debug provider; logs its debug token at start-up (logcat tag `DebugAppCheckProvider`, plus `OwlsplitAppCheck` while it is not registered) | `composeApp/src/androidDebug/.../appcheck/AppCheckSetup.kt` (`firebase-appcheck-debug`, debug builds only) |
| iOS debug | Debug provider; the token is printed in the Xcode console | `iosApp/iosApp/iOSApp.swift` |
| iOS release | DeviceCheck. **Pending**: needs the Apple Team ID (DeviceCheck key, or App Attest with its entitlement) | `iosApp/iosApp/iOSApp.swift` |
| Web for guests ("Owlsplit web") | reCAPTCHA v3; on `localhost` a debug token printed in the browser console. Off while `RECAPTCHA_SITE_KEY` is empty | `firebase/hosting/js/guest.js` |

Owner steps in the console (Firebase → **App Check**):

1. **Apps → Android `es.sebas1705.owlsplit` → Play Integrity → Register.** SHA-256 of the release key: `99:0B:AD:25:1C:E0:37:EE:8A:CB:2D:70:57:07:E5:F4:96:83:8E:5C:25:A4:0A:67:DD:FE:27:21:FC:3A:18:C3`. If Play App Signing re-signs the app, also add the **app signing key** SHA-256 from Play Console (Test and release → App integrity). In Play Console, App integrity → Play Integrity API, link the `owlsplit` Cloud project.
2. **Debug tokens**: run a debug build, copy the token from logcat (`adb logcat -s DebugAppCheckProvider`) or the Xcode console, and add it under the `.debug` Android app (or the iOS app) → ⋮ → **Manage debug tokens**. One per install (clearing the app data makes a new one). Emulator tests (`FirestoreSyncEmulatorTest`) do not need it: the emulators ignore App Check.
3. **Watch the metrics** (App Check → APIs → Cloud Firestore and Authentication) for one or two weeks: the share of verified requests should be close to 100 % before enforcing.
   - Builds from **App Distribution** are not installed by Play, so Play Integrity does not recognise them: their requests stay unverified. Before enforcing, testers must install from the Play testing track (or get a debug token each).
   - Older app versions send no token at all: enforcing cuts them off.
4. **Enforce** for Cloud Firestore first (APIs → Cloud Firestore → Enforce); then Authentication if the metrics stay clean. It can be undone from the same screen, and it takes up to 15 minutes to apply.
5. iOS: register DeviceCheck (or App Attest) once there is an Apple Team ID, before enforcing, or iOS users are blocked.
6. **Web**: create a reCAPTCHA **v3** key at [google.com/recaptcha/admin](https://www.google.com/recaptcha/admin/create) (domains `owlsplit.web.app`, `owlsplit.firebaseapp.com` and `localhost`). Its **site key** goes in `RECAPTCHA_SITE_KEY` (`firebase/hosting/js/guest.js`, public); its **secret key** goes in Firebase → App Check → Apps → Owlsplit web → **reCAPTCHA** → Save (never in the repo). Before enforcing, the web's requests must show as verified too, or guests are cut off.

## Deployments

- Every push to `main` that touches `firebase/` deploys rules, indexes and Hosting (`.github/workflows/deploy-firebase.yml`, account `owlsplit-ci`). The rules already passed their tests in the PR. Manually: `cd firebase && npx firebase-tools deploy --only firestore,hosting`.
- **Crashlytics**: enabled in release builds (manifest, `firebase_crashlytics_collection_enabled`), disabled in debug builds. Unexpected screen errors are logged as non-fatal and the app shows a generic message.
