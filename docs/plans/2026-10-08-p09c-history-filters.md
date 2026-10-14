# P9c · History, activity, filters and conflicts — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Finish P9: filters and search in the expense list ([3.4](../designs/2026-10-07-owlsplit-core-design.md#34-expenses)), per-group history with readable changes and the Activity tab ([3.9](../designs/2026-10-07-owlsplit-core-design.md#39-history)), restoring deleted expenses for 30 days, and recovering the losing version of a conflict (MVP acceptance criterion 3).

**Architecture:**
- `data`: queries `selectAllByGroup` (expenses including deleted ones), `selectRecent` (history across all groups), `conflict.selectAll/selectById`; `ChangeLogRepository.observeRecent`, `ExpenseRepository.observeExpenses(includeDeleted)` and `ConflictRepository` (reapply as a normal edit, or discard). No schema changes.
- `domain`: `ExpenseFilter` (search, person, category), `ObserveHistory` / `ObserveActivity` (entries with author, subject, currency and available actions), `RestoreExpense`, `ResolveConflict`.
- `feature:history`: a group's history screen and the Activity tab (same list); filters in `feature:groups`.

**Platform:** KMP (Android verified on emulator; iOS compiles in CI)

**Depends on:** P9b.

---

## Decisions

1. **Filters**: search by description (case- and accent-insensitive), person (paid or takes part) and category; they combine. They live in the group ViewModel (not persisted).
2. **Text of each entry**: "Ana añadió «Cena» · 60,00 €", "Luis cambió «Cena»: importe 12,40 € → 14,40 €; concepto «Cena» → «Cena larga»", "Ana borró «Taxi»", "Marta se unió", "Conflicto en «Cena»" (Ana added / Luis changed amount and description / Ana deleted / Marta joined / Conflict in). Fields that don't summarize well (payers, split, icon, color) appear only by name ("reparto cambiado" ("split changed")).
3. **Author**: the group member with that `userUid`; if they are no longer there, "Alguien" ("Someone"). My entries say "Tú" ("You"). *Updated on 2026-10-09:* the history remembers which member each account was (join entries and the `userUid` link that member changes now store), so someone who left the group or deleted their account keeps their name, and their departure reads "Luis salió del grupo" ("Luis left the group"). "Alguien" remains only if nothing indicates who it was.
4. **Restore**: on expense deletion entries, if the expense is still deleted and the deletion is less than 30 days old.
5. **Conflicts**: on the conflict entry, "Reaplicar mi cambio" ("Reapply my change": writes my version again on top of the winner, with a new version) and "Descartar" ("Discard"). While there are unresolved conflicts, Activity shows them at the top.
6. **Activity**: the latest 200 entries from all my groups, with the group name.

## Tasks

- [ ] Task 1: data (queries, `ConflictRepository`) with tests. Commit.
- [ ] Task 2: domain (`ExpenseFilter`, `ObserveHistory`, `ObserveActivity`, `RestoreExpense`, `ResolveConflict`) with tests. Commit.
- [ ] Task 3: filters on the group screen. Tests and screenshots. Commit.
- [ ] Task 4: `feature:history` (group and Activity) with tests and screenshots; navigation. Commit.
- [ ] Task 5: test on the emulator, documentation, `./gradlew build`, PR.
