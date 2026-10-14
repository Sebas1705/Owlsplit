# P13a · Import a Splitwise group — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Third proposal of the [market study](../research/2026-10-09-market-study.md). Remove the cost of switching: a Splitwise group comes into Owlsplit with its people, expenses and payments, and **with the same balances**.

**Architecture:**
- `domain`:
  - `SplitwiseCsv.parse` reads the spreadsheet Splitwise exports ("Export as spreadsheet").
    - Format: five fixed columns (date, description, category, cost and currency, with headers in any language), then one column per person with *paid − their share*, and at the end a totals row without cost.
    - Rows in the *Payment* category with a single recipient become payments.
    - Each expense becomes an exact split that yields the same net amounts:
      - those with a positive balance pay;
      - those who owe get what they owe as their share;
      - the rest of the cost is the share of whoever paid the most.
    - Categories are mapped by English and Spanish keywords.
    - Numbers with a decimal point or comma are accepted.
  - `ImportGroup` creates the group in the most used currency.
    - I join as the person I choose or with my profile name.
    - Everyone else joins as members without an account.
    - Expenses and payments are recorded in date order.
    - Other currencies use today's rate (Frankfurter). If there is no connection, those rows are left out with a warning.
- `core:ui`: `FilePicker`/`PickedFile`.
- `composeApp`:
  - `AndroidFilePicker` uses the system picker (SAF), registered in the `ActivityResultRegistry` for each pick.
  - `IosFilePicker` uses `UIDocumentPickerViewController` (CSV or text), with a copy of the file.
- `feature:groups`: `ImportScreen`/`ImportViewModel`. It explains how to export, lets you pick the file and shows a preview with the group name, counts, warnings and "¿Quién eres tú?" ("Who are you?"). After importing, it opens the group.
  - Opened from "¿Vienes de Splitwise? Importa un grupo" ("Coming from Splitwise? Import a group") on the new group screen.

**Platform:** KMP (Android tested on the emulator; iOS compiles in CI).

**Depends on:** P12.

---

## Decisions

1. **Exact balances over the original split.** The CSV only gives net amounts per person. The rebuilt split may not match the one recorded in Splitwise (who paid which share), but the balances always add up, and that is what matters when switching apps.
2. **Today's exchange rate** for rows in another currency, because Splitwise doesn't export the rate it used. The screen says so when there is no connection.
3. **No Tricount format for now.** Tricount doesn't offer a stable, documented export. If one appears, another reader is added next to `SplitwiseCsv`.

## Tasks

- [x] Task 1: `SplitwiseCsv` (RFC 4180 CSV, numbers, categories, payments) with tests. Commit.
- [x] Task 2: `ImportGroup` with tests (balances equal to Splitwise's, currencies, me). Commit.
- [x] Task 3: `FilePicker` on Android and iOS, screen, navigation and es/en strings. Test on the emulator with a CSV: preview, import and balances. Commit.
