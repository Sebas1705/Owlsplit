# Owlsplit — Core (MVP): design

- **Date:** 2026-10-07
- **Status:** approved (pending final review of the written document)
- **Subproject:** 1 of 5 (see [ROADMAP](../ROADMAP.md))
- **Inspiration:** Settle Up (Play Store) — same problem, better design and more features

## 1. What Owlsplit is

Mobile app for recording shared group expenses (trips, flats, dinners, gifts) and knowing at any time who owes whom and how to settle up with the fewest payments. It works offline and syncs across all group members.

### MVP goals

1. Record a regular expense in **under 10 seconds** (amount → description → save, with the group's defaults).
2. See your own and the group's balance **without reading numbers** (visual hierarchy + bars).
3. Work **100% offline** and converge on reconnect, without losing data.
4. Join a group **without signing up** (anonymous account, link or QR).
5. All devices compute **exactly** the same balances (deterministic integer arithmetic).

### MVP non-goals

Settling up with Bizum/PayPal/Revolut, push reminders, recurring expenses, budgets, statistics, export, receipt scanning, attachments, web and desktop versions. They are in the [ROADMAP](../ROADMAP.md); the data model already reserves their fields.

## 2. Platforms and stack

| Decision | Choice | ADR |
|---|---|---|
| Platforms | Android + iOS with **Kotlin Multiplatform** and **Compose Multiplatform** | [0001](../adr/0001-kotlin-multiplatform.md) |
| Project base | Templetry `kmp/modular-features` template (versions updated when generating) | [0001](../adr/0001-kotlin-multiplatform.md) |
| Backend | **Firebase** (Auth, Firestore) with the GitLive KMP SDK | [0002](../adr/0002-firebase-gitlive.md) |
| Persistence and sync | **SQLDelight** as the source of truth + custom sync engine (outbox) | [0003](../adr/0003-local-first-sqldelight-sync.md) |
| Accounts | Firebase Auth **anonymous** + Google / Apple linking | [0004](../adr/0004-anonymous-auth-linking.md) |
| Money | `Long` integers in minor units, deterministic remainder distribution | [0005](../adr/0005-money-as-minor-units.md) |
| Visual direction | **Clean fintech** (Revolut/Monzo style) | [0006](../adr/0006-fintech-visual-direction.md) |
| UI architecture | Template MVI, Koin, type-safe Navigation | template |
| Exchange rates | Frankfurter (ECB), no key, cached; manual rate as fallback | [DOMAIN](../DOMAIN.md#currencies) |

- Package / bundle id: `es.sebas1705.owlsplit`
- Constraint: **iOS does not build on Windows**. Day-to-day development happens on Android; iOS is built in CI (macOS runner) or on a Mac.
- `development` / `staging` / `production` profiles (from the template), each with its own Firebase project and configuration in Doppler (`owlsplit`).

## 3. MVP functional scope

### 3.1 Account and onboarding
- On first launch an **anonymous account** is created automatically; only name and avatar are asked for (optional photo or initials avatar).
- In Profile: **link** Google (Android and iOS) and Apple (iOS; mandatory on the App Store if Google is offered). Linking keeps the uid and all data.
- Non-intrusive reminder to link after creating the first group or the fifth expense (“if you lose your phone, you lose your groups”).
- **Delete account** from Profile (Play Store and App Store requirement), see [FIREBASE](../FIREBASE.md#account-deletion).
- Skippable 3-step onboarding: welcome → name/avatar → create or join a group.

### 3.2 Groups
- Create with name, icon (icon catalog), icon accent color (8 shades), **base currency** and optional dates (trip).
- Edit, **archive** (leaves Home, still accessible) and **leave** the group. There is no hard deletion of groups in the MVP: the last member to leave leaves it archived and inaccessible.
- Limits: 50 members, 10,000 expenses per group (enough; protects performance and cost).

### 3.3 Members
- A member is a person **within a group**. They can be a **ghost** (no user, just name/avatar) or be **linked** to a user.
- **Default weight** (integer ≥ 1; e.g. couple = 2) used by the “by weights” split.
- **Deactivate** instead of delete if they have activity (they stop appearing in new splits; their history and balance remain). A member with no activity can be deleted.
- When joining through an invitation, the user **claims** a ghost member or creates a new one.

### 3.4 Expenses
- Fields: description, amount, currency, date, category (fixed MVP list), payer(s), split, optional note.
- **Payers**: one (by default, the current user) or several with amounts that must add up to the total.
- **Split types**: equal shares (default, among all active members), by weights, exact amounts, percentages. Exact calculation rule in [DOMAIN](../DOMAIN.md#split).
- Edit and delete (soft delete, recoverable from history for 30 days).
- List grouped by day, with filters by person and category and search by description.

### 3.5 Transfers (payments between members)
- “Ana pagó 23,50 € a Luis”. Created manually or from a “Cómo saldar” (How to settle up) suggestion (one tap).
- They affect balances just like an expense (payer = who pays, single beneficiary = who receives).

### 3.6 Balances and “How to settle up”
- **Net balance** per member in the group's base currency: what they paid minus their share, plus/minus transfers. The sum of all balances is always **exactly 0**.
- **How to settle up**: list of suggested payments using the deterministic greedy algorithm (like Settle Up). See [DOMAIN](../DOMAIN.md#debt-simplification).
- Home shows the user's **total balance** by adding up their groups converted to their preferred currency (approximate, marked as such if there are several currencies).

### 3.7 Multi-currency
- Each group has a base currency; each expense can be in any ISO 4217 currency.
- When creating an expense in another currency, the current exchange rate is **frozen** (editable by hand). Balances never change because the market changes.

### 3.8 Invitations
- Each group has a regenerable **invitation code**. It is shared as a **link** and a **QR**.
- The link is a Firebase Hosting URL (`https://<project>.web.app/j/<code>`) configured as an **App Link** (Android) and **Universal Link** (iOS); without the app installed it shows a page with store links. Firebase Dynamic Links is not used (service shut down in 2025). Custom domain later.
- Joining also works by typing the code or scanning the QR from the app.
- Regenerating the code invalidates previous links.

### 3.9 History
- Per-group log of additions, edits (with a readable diff: “12,40 € → 14,40 €”), deletions, restorations, joins and sync conflicts.

### 3.10 Settings
- Profile (name, avatar, link, sign out only if linked, delete account), preferred currency, theme (system / light / dark), language (es / en).

## 4. Domain model

Full details, invariants and algorithms in [DOMAIN.md](../DOMAIN.md). Summary:

```
User(uid, displayName, avatar, preferredCurrency, linkedProviders, createdAt)
Group(id, name, icon, accent, baseCurrency, startDate?, endDate?, inviteCode,
      memberUids[], createdBy, archivedBy[], createdAt, updatedAt, version, deleted)
Member(id, groupId, name, avatar, defaultWeight, userUid?, active, sortKey,
       updatedAt, version, deleted)
Expense(id, groupId, title, category, note?, date, currency, amountMinor,
        fxRateToBase, payers{memberId→amountMinor}, split{type, shares{memberId→value}},
        receiptUrl?, recurrenceId?, createdBy, updatedAt, version, deleted)
Transfer(id, groupId, fromMemberId, toMemberId, amountMinor, currency, fxRateToBase,
         date, note?, createdBy, updatedAt, version, deleted)
ChangeLogEntry(id, groupId, entityType, entityId, action, authorUid, diff, createdAt)
```

`receiptUrl` and `recurrenceId` exist from the MVP and stay empty until subprojects 3 and 5.

## 5. Architecture

Structure of the `kmp/modular-features` template (Clean Architecture, dependencies pointing down):

```
composeApp / iosApp                       entry point, DI, root navigation
feature:onboarding  feature:home  feature:group  feature:expense-editor
feature:settle      feature:invite feature:history feature:settings
domain:usecases                           CreateExpense, CalculateBalances, SimplifyDebts,
                                          ConvertCurrency, JoinGroup, ClaimMember, LinkAccount…
data:models  data:local (SQLDelight)  data:remote (Firebase GitLive)
data:sync    data:fx (Ktor → Frankfurter)  data:repository
core:common  core:money  core:ui (Owlsplit design system)  core:security
```

- `core:money` and `domain:usecases` are **pure common Kotlin**, with no platform dependencies: critical logic is written and tested once.
- The UI only reads from SQLDelight (Flows). It never waits on the network.
- Each feature follows the template's MVI (`State`, `Intent`, `ViewModel`).

## 6. Sync

Details in [SYNC.md](../SYNC.md). Summary:

1. **Atomic local write**: entity + `outbox` entry in the same SQLDelight transaction. The UI updates instantly.
2. **Upload**: a worker drains the outbox in order when there is network (WorkManager on Android; on returning to the foreground and on a timer on iOS), with exponential backoff.
3. **Optimistic concurrency**: each write carries `version = base version + 1`; Firestore rules reject versions that don't chain. **First to reach the server wins**; the rejected edit is not lost: it is stored in history as a conflict and the user is notified with an option to reapply it.
4. **Download**: per group, Firestore listeners on each subcollection with `updatedAt >= cursor - margin`; merged locally if the remote version is higher.
5. **Soft deletes** (`deleted = true`) so they propagate; local purge after 30 days.
6. **Visible status**: synced / N pending changes / offline / error, on Home and in each group.
7. Removal or loss of access: the group stays local in **read-only** mode with a “you no longer have access” notice.

## 7. Firebase

Details in [FIREBASE.md](../FIREBASE.md). Collections:

```
users/{uid}
groups/{groupId}
groups/{groupId}/members/{memberId}
groups/{groupId}/expenses/{expenseId}
groups/{groupId}/transfers/{transferId}
groups/{groupId}/changelog/{entryId}
invites/{code}                     → { groupId }
```

Key rules: only uids in `memberUids` read or write a group; joining only adds your own uid and requires the current code; expense validation (payers = total, consistent split, valid currency, chained `version`, `updatedAt == request.time`). Tested against the emulator in CI. **No Cloud Functions in the MVP** (free Spark plan).

## 8. Visual design and UX

**Clean fintech** direction: figures take center stage, very light gray background with rounded white containers (black and graphite in dark mode), a single electric blue accent, circular action buttons and pills. Tokens, components and rules in [DESIGN_SYSTEM.md](../DESIGN_SYSTEM.md). Approved mockup: [mockups/owlsplit-fintech-v3.html](../mockups/owlsplit-fintech-v3.html).

### Navigation

Bottom bar with **Inicio · Actividad · Perfil** (Home · Activity · Profile).

| Screen | Contents |
|---|---|
| Home | Large total balance (you are owed / you owe), sync status, circular quick actions (Expense, Settle up, Group, Join), list of groups with your balance in each |
| Group | Header with icon, name and your large balance; **Gastos · Saldos · Saldar** (Expenses · Balances · Settle up) segmented control |
| Expenses | List by day, filters (person, category), search, add button |
| Balances | Row per member with a thin proportional bar and balance (green = is owed; black = owes) |
| Settle up | Suggested payments with a “Saldar” (Settle up) button (creates the transfer) and recorded transfers |
| Expense editor | Full screen: huge amount first with custom keypad, currency chip, description with category, “Pagó” (Paid by) and “Entre” (Split between) as rows, tappable avatars to include/exclude and live per-person share |
| Split editor | Type (equal, weights, amounts, %), per-member values with live validation (“faltan 3,20 €”) |
| Invite | Large QR, link, share, regenerate code |
| Join | Scan QR or type code → group preview → choose a ghost member or create one |
| Activity | History of all groups, filterable by group |
| Profile | Account, link, preferences, delete account |

Empty states with the Owlsplit logo and a clear action (“Crea tu primer grupo”).

### Accessibility
- Minimum AA contrast in both themes; color is never the only indicator (sign + “te deben/debes” text).
- Dynamic text up to 200% without clipping amounts (large amounts scale with a limit and wrap).
- Screen reader descriptions for avatars, bars and amounts (“Ana, le deben 58 euros con 20”).
- Respect “reduce motion”. Touch targets ≥ 48 dp.

### Localization
Spanish and English from the MVP (Compose Multiplatform resources). Number, date and currency formats follow the locale.

## 9. Error handling

| Situation | Behavior |
|---|---|
| No network | Everything works; “offline” indicator; outbox accumulates |
| Transient upload failure | Retry with exponential backoff (max. 15 min between attempts) |
| Version conflict | The server version is kept; the local one goes to history as a conflict; notice with “Reaplicar mi cambio” (Reapply my change) |
| Permission denied (removed) | Group goes read-only with a notice; that group's outbox is discarded after confirmation |
| Validation (split doesn't add up) | Saving is blocked with an inline message; it never reaches the network |
| Exchange rate unavailable | Ask for a manual rate; never block saving if the user enters one |
| Invalid or expired invitation | Clear message and option to request a new link |
| Unexpected error | Crashlytics (non-fatal) + generic message; stack traces are never shown |

## 10. Testing

- **`core:money` and `domain`** (commonTest): edge-case tables (indivisible cents, weights, multiple payers, zero-decimal currencies like JPY, percentages that don't add up to 100) and **property tests**: sum of balances = 0; simplification preserves net balances; identical results under input permutations; number of suggested payments ≤ n − 1.
- **`data:sync`**: in-memory fake Firestore; outbox, retries, ordering, conflicts, deletions, loss of access, restart mid-upload (idempotency).
- **`data:local`**: verified SQLDelight migrations.
- **Firestore rules**: emulator with allowed and denied cases for each rule.
- **UI**: Compose tests for the key flows (create group, add expense, settle up, join) and screenshots in light and dark.
- Manual verification on an Android emulator on every PR with UI changes; iOS in CI.

## 11. CI/CD

- **PR**: Android build, common `allTests`, detekt, rules tests with the emulator, iOS framework build on a macOS runner.
- **main**: deploy Firestore rules and indexes (production project via a least-privilege service account).
- **Tag `vX.Y.Z`**: signed APK/AAB → GitHub Release + Firebase App Distribution (`testers` group). Secrets in Doppler `owlsplit` (dev/stg/prd), read-only token in GitHub.
- Dependency policy as in YouKnow (Dependabot grouped minor+patch).

## 12. MVP acceptance criteria

1. Two devices (Android + iOS) with anonymous accounts share a group by link and see the same expenses and balances to the cent.
2. With one device in airplane mode, expenses are created, edited and deleted; on reconnect both converge and nothing is lost.
3. A concurrent edit of the same expense produces a conflict notice and the losing version is recoverable from history.
4. A USD expense in a EUR group uses the frozen rate and balances don't change the next day.
5. Linking Google keeps all groups; deleting the account leaves their members as ghosts and no personal data remains.
6. An equal-split expense is added in < 10 s from Home (measured in a manual test).
7. All tests green in CI, including rules tests and the iOS build.

## 13. Risks and mitigations

| Risk | Mitigation |
|---|---|
| KMP template with old versions (Kotlin 2.1, CMP 1.7) | Update to current stable versions when generating, before adding code |
| Maturity of the GitLive SDK on iOS | Smoke prototype (anonymous auth + write + listener) in the first plan step; fallback: Firestore REST with Ktor |
| No Mac for iOS | CI on macOS; manual iOS validation at milestones |
| Sync engine complexity | Bounded design (per document, no field merging), exhaustive tests with fake Firestore |
| Firestore cost with many listeners | Listeners only for open or recent groups; incremental queries by `updatedAt` |
| “Owlsplit” name | Checked on 2026-10-07: `owlsplit.com` and `owlsplit.app` available, no trademarks in TMview (EUIPO, OEPM, USPTO, WIPO…) and no apps with that name. Before launch: buy the domains and register the trademark with the EUIPO (classes 9 and 36). The initial name “Owlo” was ruled out due to a trademark registered in France (classes 9 and 42) and an “OWLO” app in both stores |
