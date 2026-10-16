# Roadmap

Owlsplit is built in independent subprojects. Each one goes through **design → plan → implementation**; the bigger ones have their own document in `docs/designs/`. The order of what is next comes from [the 2026-10-16 market study](research/2026-10-16-market-study.md): reach first (more people can join and come back), then less friction to record and settle up, then polish.

## Done

| Subproject | Since |
|---|---|
| **Core**: linkable anonymous accounts, groups, members with weights, expenses with multiple payers and four split types, transfers, balances, debt simplification, multi-currency with a frozen rate, invitations by link and QR, history with restore, offline-first sync ([design](designs/2026-10-07-owlsplit-core-design.md)) | beta.1 |
| **Recurring expenses** (P13b) | beta.3 |
| **Settle up with Bizum, PayPal.me and revolut.me** and payment methods in the profile (P12a); a shareable reminder from Settle up (beta.9) | beta.3 |
| **Statistics and export**: group summary with CSV and PDF (P12b); a Stats tab across groups (beta.10) and charts (beta.11) | beta.3 |
| **Import from Splitwise** (CSV, P13a) | beta.3 |
| Update notice (P15, beta.5), Google Play in-app updates and App Check, not enforced (beta.9) | beta.5 |

## Next

| # | Subproject | Effort | Needs |
|---|---|---|---|
| 1 | **Pay by IBAN with an EPC (SEPA) QR**: IBAN and holder name in the profile, a QR the payer scans with their bank app, IBAN and amount to copy | S | Nothing |
| 2 | **Polish settling up**: currency in PayPal.me, check revolut.me's amount, a bank picker for Bizum, "did you pay?" on coming back, a "pay me" QR | S | Nothing |
| 3 | **Push notifications and reminders**: new expense, you are owed, you were paid; automatic, rate-limited reminders | M–L | Blaze, FCM, Cloud Functions |
| 4 | **Web for guests** on the invite link: see balances, add an expense, mark a payment, "who are you?", without installing | L | Hosting and a light web client on the same rules and App Check |
| 5 | **App Store** (TestFlight, review) | M | Apple Developer account |
| 6 | **Receipts**: photo, on-device OCR (ML Kit / Vision), line-by-line split with tax and tip prorated | L | Cloud Storage (Blaze) for the photos |
| 7 | **Natural-language entry** in Spanish and English ("Ana paid 45 for dinner"), always confirmed in the form | M–L | Local parser; optional LLM |
| 8 | **Android widget** with the balance and quick add; quick settings tile and app actions | M | Jetpack Glance |
| 9 | **Group bin**: deleted groups can be restored for 30 days | S–M | Soft delete and a scheduled clean-up |
| 10 | **Default split per group** (couples, flats) beyond member weights | S | Nothing |
| 11 | **Comments on expenses** | M | After 3 |
| 12 | **Import from Tricount**, only if a real export file can be had | S–M | A sample file |
| 13 | **Budgets** per group or trip | M | Nothing |

Items 1 and 2 go first although reach matters more: they are small, need no paid plan, and nobody else offers them, while 3 and 6 wait on the Blaze plan and 5 on an Apple account.

## Not now

Bank-statement import (PSD2: regulation, cost and a third party, against the privacy promise), a WhatsApp bot, cloud AI as the core feature, flat chores and shopping lists, couple finances with bank accounts. To watch: Bizum–Wero interoperability (Bizum for friends in other EU countries) and a reported bill-splitting feature in Apple Wallet.
