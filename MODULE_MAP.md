# Module map

| Module | Contents | Plan that extends it |
|---|---|---|
| `composeApp` | Android/iOS entry point (iOS: `iosApp/project.yml` with XcodeGen; sharing, scanner, Google via `GoogleSignInBridge`), root DI, `AppConfig` and `isDebugBuild`, Crashlytics (`CrashReporting`), app language (`LocalAppLocale`), navigation (onboarding → Home · Activity · Profile tabs), `SyncStarter`, Google with Credential Manager, `TextSharer` (share sheet), `AndroidQrScanner`, App Links (`IncomingInvites`), Google Play In-App Updates on Play installs (`PlayStoreUpdates`, flexible flow) | P9 |
| `core:common` | MVI base (`execute` errors go to `UnexpectedErrors`), `AppResult`, utilities | — |
| `core:ui` | `OwlsplitTheme` (tokens, Inter, shapes), `MoneyFormat` and `LocalMoneyFormat`, components (including `QrCode` and `SwitchRow`) and `DesignCatalog`; Roborazzi screenshots | P9 |
| `core:security` | `SecureStorage` | — |
| `core:network` | Per-platform Ktor client | P9 (exchange rates) |
| `core:datastore` | Preferences (`AppPreferences`: theme, language, local profile, link reminder; `DisplaySettings`) | — |
| `core:logging` | Kermit | — |
| `core:testing` | `CoroutineTestRule`, account/sync fakes, `InMemoryLedger` (the four repositories in memory), `FakeInvites` for domain and ViewModel tests, and `assertAccessible()` (androidMain) for screenshots | — |
| `core:money` | Money, currencies, exact multiply-divide, largest-remainder splitting, exchange rates | — |
| `data:models` | Documents (`Group`, `Member`, `Expense`, `Transfer`, `ChangeLogEntry`) with `SyncMeta`, serializable | P6 |
| `data:local` | Full SQLDelight v1 schema (`AppDatabase`), `databases/1.db` snapshot and `verifyMigrations` | P6, P9 |
| `data:repository` | Repositories with Flows (each write saves the entity, compacts the outbox and records history in one transaction) and the `sync` engine (`RemoteStore`, `SyncEngine`) | P8, P9 |
| `data:fx` | `FrankfurterRates` (Ktor): ECB rates in fixed point, per-pair cache in DataStore | — |
| `data:remote` | Firebase (GitLive): `FirestoreRemoteStore` (engine adapter), `FirebaseAccountService`, `FirestoreProfileRemote` (`users/{uid}`), `FirestoreInviteService` (`invites/{code}` and join); smoke test and sync test against emulators | P10 |
| `domain:usecases` | `ledger` (shares, nets, balances, suggested payments), `ObserveGroupBalances` and `account` (onboarding, Google, profile, sign out, delete account, link reminder), `groups` (Home, detail, actions, settle up), `expenses` (drafts with live validation) and `invite` (share, regenerate, preview, join, claim member) | P9b |
| `feature:onboarding` | Welcome → name and avatar → first group; "Ya tengo cuenta" (I already have an account) with Google | P9, P10 |
| `feature:profile` | Profile tab: profile, link Google, preferred currency, theme, sign out, delete account | P11 |
| `feature:home` | Home: total balance, sync status, quick actions, groups, link reminder | P9b (converted balance) |
| `feature:groups` | Group screen (Expenses · Balances · Settle up), group and member editor | P9b, P10 |
| `feature:history` | Group history and Activity tab: readable changes, restore deletions, reapply or discard conflicts | P11 |
| `feature:expense` | Expense editor: custom keypad, category, payer and the four split types with live validation | P9b (currencies, multiple payers, date) |
| `feature:invite` | Invite (QR, code, link, share, regenerate) and Join (scan, code or link → preview → join → claim member or add me) | — |

Naming: `feature:groups`, not `feature:group` (Gradle reserves `group` in type-safe accessors). Features only see `domain`, `data:models` and `core` (ARCHITECTURE.md). The sync engine lives in `data:repository` (`sync` package), not in a `data:sync` module.

Strings and accessibility (P11a): every module with resources has `composeResources/values/strings.xml` in English (default language) and `values-es/strings.xml` in Spanish, with the same keys; in English the apostrophe is `’` (Compose does not interpret `\'`). Each screen's screenshot tests call `assertAccessible()` before capturing.
