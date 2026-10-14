# P12b · Group summary and CSV/PDF export — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Second proposal of the [market study](../research/2026-10-09-market-study.md), which Splitwise only offers in Pro. A per-group **Summary** screen and the option to share the accounts as a **spreadsheet (CSV)** or as a printable **PDF summary**, with no server and no cost.

**Architecture:**
- `domain`: `GroupStats.of(...)`, which computes in the base currency:
  - the total, the number of expenses and the daily average (using the trip dates if any);
  - what each person paid and what their share is;
  - the breakdown by category and by month.

  Transfers don't count as spending. Inconsistent expenses are ignored, same as in balances. The functions `Expense.inBase` and `Expense.sharesInBase` are public for the CSV.
- `core:common`: `SimplePdf`, a minimal PDF writer in common Kotlin:
  - A4 pages, Helvetica regular and bold, and rectangles for the bars;
  - WinAnsi text (Spanish and the rest of Western Europe, plus «€»), with Helvetica widths to right-align numbers;
  - no dependencies, so it produces the same file on Android and iOS.
- `core:ui`:
  - `FileSharer`, which shares a file created in memory;
  - `DateTexts`/`rememberDateTexts()`, which format dates and months outside composition.
- `composeApp`:
  - `AndroidFileSharer`: writes to `cache/exports` and shares it with a `FileProvider` (`${applicationId}.exports`, `res/xml/file_paths.xml`);
  - `IosFileSharer`: writes to the temporary directory and opens `UIActivityViewController`.
- `feature:groups`:
  - `StatsScreen`/`StatsViewModel`: figures, per person, per category and per month (the latter only with more than one month). The «Exportar» ("Export") menu offers PDF and CSV.
  - `GroupReport.csv`: one row per expense and per payment, and one column per member with their share in the base currency. That way, what they paid minus their column gives their balance. In languages with a decimal comma, «;» is the separator. It carries a UTF-8 BOM for Excel.
  - `GroupReport.pdf`: figures, people, categories, months, what is left to settle and the expense list.
  - Opened from «Resumen» ("Summary") in the group menu.

**Platform:** KMP (Android tested on the emulator; iOS compiles in CI).

**Depends on:** P12a.

---

## Decisions

1. **Own PDF writer instead of platform APIs.** `PdfDocument` (Android) and `UIGraphicsPDFRenderer` (iOS) would require two implementations, and the iOS one can't be tested here. A report of text and bars fits in about 200 common lines with tests.
2. **The CSV matches the balances.** Payments appear as rows, the payer under «Pagado por» ("Paid by") and the recipient in their column. Summing up, each person gets their balance, and the file can be used to check the accounts.
3. **Amounts truncated to the cent** in the summary's conversions. There may be a one-cent difference with the balances, which distribute the remainder; this is documented in `GroupStats`.
4. **One file at a time** in `cache/exports`: the previous one is deleted when exporting. The `FileProvider` only exposes that folder.

## Tasks

- [x] Task 1: `GroupStats` with tests. Commit.
- [x] Task 2: `SimplePdf` with tests (xref, escapes, widths). Commit.
- [x] Task 3: `FileSharer` on Android (FileProvider) and iOS. Commit.
- [x] Task 4: `GroupReport` (CSV and PDF) with tests, `StatsScreen`, navigation and es/en strings. Test on the emulator: summary, share PDF and CSV, and PDF print preview. Commit.
