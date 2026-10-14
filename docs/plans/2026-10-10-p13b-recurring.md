# P13b · Recurring expenses — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Fourth proposal of the [market study](../research/2026-10-09-market-study.md) and item 3 of the [ROADMAP](../ROADMAP.md). Rent, utilities or subscriptions are entered once and repeat on their own every week, month or year, with no server and also offline.

**Architecture:**
- `domain`:
  - `Recurrence` (`RepeatUnit`) is stored in the `recurrenceId` field, reserved since the MVP, in the form `<series>|<unit><every>` (`…|M1`) or `<series>|stop`. All expenses in a series share the series id. Dates are computed from the first one (the anchor), so a 31st doesn't drift.
  - `GenerateRecurring` adds the expenses due up to today:
    - it copies the latest expense, so a rent that goes up keeps the new amount;
    - each generated expense has a fixed id `<series>_<date>`;
    - it generates at most 24 per pass.
  - `SaveExpense`:
    - "Nunca" ("Never") marks the expense with `stop`, which stops the whole series;
    - another period starts a new series from that expense and stops the previous one at its last expense.
- `data`:
  - `ExpenseInput.recurrenceId`;
  - `ExpenseRepository.create(…, id)` accepts a fixed id.
  - Neither the schema nor the rules change, because the field already existed (≤ 100 characters).
- `feature`:
  - "Repetir" ("Repeat") row (Never, every week, month or year) in the expense editor, with an explanation;
  - "· se repite" ("· repeats") in the group list;
  - the group and the home screen generate what is due when opened.

**Platform:** KMP (Android tested on the emulator; iOS compiles in CI).

**Depends on:** P12.

---

## Decisions

1. **Generated on the phone**, not with Cloud Functions, so the Blaze plan isn't needed. It works offline and the write syncs like any other.
2. **No duplicates.** Two measures:
   - only the series creator generates (if they left, the member with the lowest uid);
   - ids are deterministic, so nothing is added twice even if it runs several times or on several phones of the same person.

   If two phones on the same account generate at the same time offline, the second upload falls into the normal P6 conflict (same content) and is resolved from the conflict notice.
3. **Stopping is one tap away**, on any expense in the series. No separate templates screen is needed: the template is the latest expense.
4. **If the series has no visible expenses, nothing is generated.** If all of a series' expenses are deleted, it is considered finished. If only one is deleted, the next one keeps being generated.

## Tasks

- [x] Task 1: `Recurrence`, `GenerateRecurring` and the `SaveExpense` changes, with tests (31-day months, one-off, stop, change of period, creator only). Commit.
- [x] Task 2: `ExpenseInput.recurrenceId` and the fixed id in the repositories. Commit.
- [x] Task 3: "Repetir" row, "se repite" in the list, generation when opening the group and home, and es/en strings. Test on the emulator: a monthly rent dated two months ago generates September and October on return. Commit.
