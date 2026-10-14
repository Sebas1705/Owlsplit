# MVP acceptance criteria

The seven criteria from the [spec, section 12](designs/2026-10-07-owlsplit-core-design.md#12-mvp-acceptance-criteria), with how each one is checked and its status. Reviewed before each `vX.Y.Z` release (P11d).

| # | Criterion | How it is checked | Status (2026-10-08) |
|---|---|---|---|
| 1 | Two devices (Android + iOS) with anonymous accounts share a group via link and see the same expenses and balances to the cent | Automated: `FirestoreSyncEmulatorTest` (two devices against the emulators, `scripts/run-sync-emulator-test.sh`), `InviteUseCasesTest`, `InviteViewModelsTest`. Manual: invite from one Android, join from another with the link or the QR and compare Balances. | **Android ↔ Android verified against `owlsplit`** (2026-10-08): Ana creates "Prueba" and an expense; Luis, in another app with his anonymous account, opens `owlsplit.web.app/j/…`, goes through onboarding, joins and adds "Taxi" €20 split equally; both see Ana −10,00 € / Luis +10,00 €. **iOS pending**: the app compiles and links in CI; an iPhone is missing (Mac or TestFlight, with the Apple account). |
| 2 | With one device in airplane mode, expenses are created, edited and deleted; on reconnecting both converge and nothing is lost | Automated: `SyncEngineTest` (outbox, automatic upload, `flush`), `OutboxProcessorTest`, `FirestoreSyncEmulatorTest` (convergence). Manual: airplane mode on one, three operations, reconnect, compare. | **Met** (2026-10-08, against `owlsplit`): in airplane mode Ana added "Super" €15 and deleted "Cena", and Luis added "Bus" €4; on reconnecting both show the same three expenses and Ana −4,50 € / Luis +4,50 €. |
| 3 | A concurrent edit of the same expense produces a conflict notice and the losing version can be recovered from the history | Automated: `SyncEngineTest` ("a pulled change over a pending local edit becomes a conflict"), `FirestoreSyncEmulatorTest` ("…a concurrent edit becomes one conflict"), `HistoryUseCasesTest` and `HistoryViewModelTest` (reapply or discard). | **Met** (P6, P9c). |
| 4 | A USD expense in a EUR group uses the frozen rate and balances do not change the next day | Design: the expense stores `fxRateScaled` when created and balances only read that value (`ledger`); the day's rate only suggests the value when editing. Automated: `ledger` tests and `FrankfurterRatesTest` (cache and stale rate without network). | **Met** (P3, P9b). |
| 5 | Linking Google keeps all groups; deleting the account leaves its members as ghosts and no personal data remains | Automated: `AccountUseCasesTest` (linking keeps the uid; deleting leaves ghosts and deletes `users/{uid}`), `ProfileViewModelTest`. Manual: link from Profile and check the groups; delete and check the account-less member from another device. | **Delete: met** (2026-10-08): Luis deleted his account and Ana sees him as "Sin cuenta" ("No account") with his expenses and the same balance. **Link Google: pending on the owner** (sign in with a real Google account on the phone; the agent does not enter credentials). |
| 6 | An equal-split expense is added in < 10 s from Home | Manual with a stopwatch: Home → Expense → amount → description → Save (the default split is equal among everyone and "me" paid). | 4-tap flow + keyboard. **Measurement pending** on the first tester build. |
| 7 | All tests green in CI, including the rules tests and the iOS build | CI on every PR: `detekt`, tests (207 `@Test`), migrations, screenshots, rules on the emulator and the iOS app for the simulator. | **Met**: since P11c, CI compiles and links the full iOS app (Swift, Firebase via SPM) on every PR. |

## Before the first tester release

What only the owner can do:

1. ~~Enable Authentication in `owlsplit`~~ (done on 2026-10-08; configuration refreshed).
2. For iOS: Apple Developer account (Team ID), which completes Universal Links (`apple-app-site-association`), Sign in with Apple and signing (TestFlight).

Left for the owner: link Google (criterion 5) and time an expense (criterion 6) on the build delivered via App Distribution.
