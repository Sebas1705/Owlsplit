# P9a · Groups, expenses and balances — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** The app does what it exists for: create a group, add members, record expenses in the group's currency with any split type, see balances and settle up with one tap. Screens from [3.2–3.6 of the spec](../designs/2026-10-07-owlsplit-core-design.md#32-groups) and the [mockup](../mockups/owlsplit-fintech-v3.html).

**Out of P9a (moved to P9b):** expenses in another currency and Frankfurter rates, total balance converted across currencies, multiple payers, trip dates and an expense date other than today, filters and search, history and the Activity tab, manual transfer, delete and restore from history.

**Architecture:**
- The input types (`GroupInput`, `MemberInput`, `ExpenseInput`, `TransferInput`) move to `data:models`: features can't see `data:repository`.
- `domain:usecases`: `groups` package (`ObserveHome`, `ObserveGroupDetail`, `GroupActions`, `MemberActions`, `SettleUp`) and `expenses` (`ExpenseDraft` with live validation on top of `ShareCalculator`, `SaveExpense`).
- Three new modules: `feature:home` (Home), `feature:groups` (group screen, group editor, members) and `feature:expense` (expense and split editor). Navigation in `composeApp`.
- `core:ui`: group icon catalog (`GroupIcons`) and category icon catalog (`CategoryIcons`), expense row (`ExpenseRow`).

**Tech Stack:** Compose Multiplatform 1.11.1, Navigation Compose 2.9.2 (typed routes), Koin, Roborazzi.

**Platform:** KMP (Android verified on emulator; iOS compiles in CI)

**Depends on:** P3 (ledger), P4 (repositories), P7 (components), P8 (profile and navigation).

---

## Decisions

1. **My member** in a group = the one whose `userUid` = my uid. My balance on Home and in the group header is that member's; if I have no member (rare case) no balance is shown.
2. **Home**: total balance = sum of my balances in the groups whose base currency is my preferred currency; if there are groups in other currencies, this is noted below ("y 12,00 USD en otros grupos" ("and 12.00 USD in other groups")). Conversion comes in P9b. List of groups not archived by me, sorted by last activity. Quick actions: Expense (pick a group if there are several), Settle up (pick a group and open Settle up), Group (create). "Join" appears in P10.
3. **Create group**: name, icon (12 icons), color (8), base currency (short list; the full list comes with the P9b currency picker). The creator joins with their profile name.
4. **Members**: add a ghost member (name and default weight), edit, deactivate (if they have transactions) or delete (if they don't). My member can't be deactivated or deleted.
5. **Expense editor**: amount with a custom keypad, description, category, payer (one; me by default), split. Default is equal shares among active members. Weights, amounts and percentages are edited per member with live validation ("faltan 3,20 €" ("3.20 € left")); it can't be saved unless it adds up. Using the `ShareCalculator` remainder rule, each share's preview is exactly what will be saved.
6. **Settle up**: the suggested payments from `DebtSimplifier`; "Saldar" ("Settle up") asks for confirmation and creates the transfer for the suggested amount in the base currency, dated today. Below, the recorded transfers.
7. **Expense list**: grouped by day, with who paid, the amount and my share. Tapping opens the editor.
8. **Archive / leave group**: archiving removes it from Home (it stays in the P9b archived list); leaving asks for confirmation and returns to Home.

## Tasks

### Task 1: Groundwork
- [ ] Move the input types to `data:models` (`Inputs.kt`).
- [ ] `core:ui`: `GroupIcons` (key ↔ Tabler icon, 12), `CategoryIcons` + category names, `ExpenseRow`.
- [ ] Commit.

### Task 2: Use cases
- [ ] `ObserveHome`, `ObserveGroupDetail`, `GroupActions`, `MemberActions`, `SettleUp`, `ExpenseDraft`/`SaveExpense`.
- [ ] Tests with fake repositories (`core:testing`): my member's balance, total per currency, validation of each split type, suggested payments → transfer, can't deactivate my member.
- [ ] Commit.

### Task 3: `feature:home`
- [ ] ViewModel + screen + screenshots (empty, with groups, light/dark). Link nudge (from P8) moved here.
- [ ] Commit.

### Task 4: `feature:groups`
- [ ] Group editor, group screen (Expenses · Balances · Settle up), members. ViewModels with tests; screenshots.
- [ ] Commit.

### Task 5: `feature:expense`
- [ ] Expense and split editor. ViewModel with tests; screenshots.
- [ ] Commit.

### Task 6: App
- [ ] Routes and navigation; Koin. Full test on the emulator: create a group, add two members, equal expense, expense by amounts, balances, settle up.
- [ ] Commit.

### Task 7: Documentation and PR
- [ ] `MODULE_MAP.md`, `DESIGN_SYSTEM.md` (new components), plan index. `./gradlew build` green. PR.
