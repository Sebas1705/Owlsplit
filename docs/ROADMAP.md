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
| **Pay by IBAN with an EPC (SEPA) QR**, and "did you pay?" on coming back from the payment app | beta.13 |
| **Android widget** with the balance and "add expense", plus a launcher shortcut | beta.13 |
| **Going back into a group I left** (the "group bin": groups cannot be deleted, only left) | beta.13 |
| **New expenses follow the members' weights** (the default split per group) | beta.13 |
| **Natural-language entry** in Spanish and English, on the device ("Ana pagó 45 de cena con Luis") | beta.13 |
| **Web for guests** at `owlsplit.web.app/g/{code}`: join, see expenses and balances, add expenses, record payments ([design](designs/2026-10-16-web-guests.md)) | site, 2026-10-16 |

## Next

| # | Subproject | Effort | Needs |
|---|---|---|---|
| 1 | **Push notifications and reminders**: new expense, you are owed, you were paid; automatic, rate-limited reminders | M–L | Blaze, FCM, Cloud Functions |
| 2 | **Web for guests, next**: edit and delete expenses, other currencies, "Save with Google" so the guest keeps the account in the app, and a reCAPTCHA App Check provider before enforcing it | M | Nothing (reCAPTCHA is free) |
| 3 | **App Store** (TestFlight, review) | M | Apple Developer account |
| 4 | **Receipts**: photo, on-device OCR (ML Kit / Vision), line-by-line split with tax and tip prorated | L | Cloud Storage (Blaze) for the photos |
| 5 | **Comments on expenses** | M | After 1 |
| 6 | **Polish settling up, the rest**: a bank picker for Bizum (needs the banks' Android package names checked), and testing whether revolut.me fills in the amount | S | Nothing |
| 7 | **Import from Tricount**, only if a real export file can be had | S–M | A sample file |
| 8 | **Budgets** per group or trip | M | Nothing |

What is left at the top needs a paid Firebase plan (1, 4) or an Apple account (3).

## Not now

Bank-statement import (PSD2: regulation, cost and a third party, against the privacy promise), a WhatsApp bot, cloud AI as the core feature, flat chores and shopping lists, couple finances with bank accounts. To watch: Bizum–Wero interoperability (Bizum for friends in other EU countries) and a reported bill-splitting feature in Apple Wallet.
