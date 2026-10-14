# P9b · Currencies, payers, dates and manual payments — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Complete P9's money handling: expenses in any currency with a frozen exchange rate ([3.7](../designs/2026-10-07-owlsplit-core-design.md#37-multi-currency), [DOMAIN · Currencies](../DOMAIN.md#currencies)), multiple payers, expense date and note, trip dates, manual transfers, and the Home total balance converted to the preferred currency.

**Out (P9c):** filters and search, per-group history and the Activity tab, restoring deleted items, and recovering the losing version of a conflict.

**Architecture:**
- `FxRates` port in `data:repository` (`fx` package); new `data:fx` implementation with Ktor against Frankfurter (`/latest?base=USD&symbols=EUR`), rates read as text (`FxRate.parse`, never `Double`) and cached in DataStore (`KeyValueCache` from `core:datastore`) with the profile's expiry (`AppConfig.FX_CACHE_SECONDS`). If the network fails, the last saved rate is used, with its date; if there is none, the user types it.
- `domain`: `GetFxRate`, `ConvertTotal` (Home) and `RecordTransfer`; `ExpenseDraft` gains `currency`, `fxRate`, `payers` (several), `date` and `note`, with their validation (`MissingRate`, `PayersSumMismatch`).
- `core:money`: `Currency.all` for the picker.
- `core:ui`: `CurrencyPicker` (dialog with search), `DateField` (Material 3 date picker).

**Tech Stack:** Ktor 3.5.2 (already in `core:network`), Material 3 DatePicker (Compose Multiplatform), DataStore.

**Platform:** KMP (Android verified on emulator; iOS compiles in CI)

**Depends on:** P9a.

---

## Decisions

1. **Frozen rate**: fetched when choosing a currency other than the group's and stored in the expense (`fxRateScaled`). Editing an expense keeps its rate; it is only fetched again if the currency changes. The user can type a different one (`1 USD = 0,92 EUR`). Without a rate, it can't be saved.
2. **Source**: Frankfurter (ECB, no key). Frankfurter doesn't cover every currency in the table: for those, a manual rate.
3. **Cache**: per currency pair, value `rate|date|savedAt`. Valid for `FX_CACHE_SECONDS` (0 in development: always fetches); an expired rate is only used if the network fails, showing the date.
4. **Multiple payers**: "Varios" ("Several") switch under "Pagó" ("Paid"). With one, they pay the full amount and it adjusts automatically; with several, each amount is entered by hand with a live warning ("Faltan 5,00 € de pagadores" ("5.00 € missing from payers")).
5. **Date and note**: the default date is today; it is changed with the picker. Optional note (≤ 500).
6. **Trip dates**: optional in the group editor (start ≤ end). They are no longer lost when editing a group.
7. **Manual transfer**: from Settle up, "Registrar un pago" ("Record a payment"): from, to (different), amount and note, in the group's currency, dated today. (Other currencies in transfers are not offered in the MVP.)
8. **Total balance**: groups in other currencies are converted with the latest available rate and the total is marked as approximate ("≈"); if some rate is missing, those groups stay separate.

## Tasks

### Task 1: Exchange rates
- [ ] `Currency.all`; `KeyValueCache` in `core:datastore`; `FxRates` port; `data:fx` module (`FrankfurterRates`) with tests (Ktor MockEngine: normal response, unsupported currency, network down with and without cache, valid cache).
- [ ] `GetFxRate`, `ConvertTotal` with tests.
- [ ] Commit.

### Task 2: Full expense
- [ ] `ExpenseDraft`: currency, rate, payers, date, note; validation and saving. Tests.
- [ ] Editor: currency chip + `CurrencyPicker`, exchange-rate row, equivalent in the group's currency, multiple payers, date and note. ViewModel tests and screenshots.
- [ ] Commit.

### Task 3: Group and payments
- [ ] Trip dates in the group editor; `RecordTransfer` and the "Registrar un pago" screen. Tests and screenshots.
- [ ] Home with `ConvertTotal`. Tests.
- [ ] Commit.

### Task 4: App, documentation and PR
- [ ] Koin and navigation; test on the emulator (expense in USD with a real Frankfurter rate, multiple payers, manual transfer).
- [ ] `docs/DOMAIN.md` (cache), `MODULE_MAP.md`, `DESIGN_SYSTEM.md`, index. `./gradlew build`. PR.
