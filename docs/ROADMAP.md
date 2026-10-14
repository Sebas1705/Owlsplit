# Roadmap

Owlsplit is built in independent subprojects. Each one goes through **design → plan → implementation** and has its own document in `docs/designs/`.

| # | Subproject | Status | Depends on |
|---|---|---|---|
| 1 | **Core (MVP)** | Design approved | — |
| 2 | One-tap settle-up and reminders | Pending | 1 |
| 3 | Recurring expenses | Done (P13b) | 1 |
| 4 | Budgets, statistics and export | Pending | 1 |
| 5 | Receipt scanning | Pending | 1 (better after 4) |

## 1. Core (MVP)

Linkable anonymous accounts, groups, members with weights, expenses with multiple payers and four split types, transfers, balances, debt simplification, multi-currency with a frozen rate, invitations by link and QR, history, offline-first sync. Design: [designs/2026-10-07-owlsplit-core-design.md](designs/2026-10-07-owlsplit-core-design.md).

## 2. One-tap settle-up and reminders

- When tapping “Saldar” (Settle up), open **Bizum, PayPal or Revolut** with amount and description prefilled when the app allows it (PayPal.me and Revolut links do; Bizum has no public link: copy amount and phone number). Each user sets up their payment methods in their profile.
- Friendly **reminders** to whoever owes, via push notification and a shareable message.
- Group activity notifications (new expense, you've been paid back).
- Requires **FCM** and **Cloud Functions** (Blaze plan) to send push to other users.

## 3. Recurring expenses

- Templates with a frequency (weekly, monthly, yearly, every N days) that generate the expense automatically: rent, utilities, subscriptions.
- Uses the `recurrenceId` field reserved in the MVP.
- Open decision: generation on the client (the first device that opens the group, with a deterministic id to avoid duplicates) or on the server (scheduled Cloud Functions).

## 4. Budgets, statistics and export

- Budget per group or trip and per category, with progress and a warning when getting close.
- Charts: spending per person, per category and per day; trip daily average.
- Export to **CSV** and **PDF** (summary and balances).
- All computed locally on SQLDelight (main reason for [ADR 0003](adr/0003-local-first-sqldelight-sync.md)).

## 5. Receipt scanning

- Receipt photo → on-device OCR (ML Kit on Android, Vision on iOS) that fills in amount, date and merchant.
- **Line-by-line** splitting: assign each item to one or more people, with tax and tip prorated.
- Attach the photo to the expense (uses `receiptUrl`; requires Cloud Storage, Blaze plan).
- Optional: extraction with a multimodal model for hard receipts.

## Unprioritized ideas

Home-screen balance widget, shortcuts (Siri / App Shortcuts), lightweight web for guests without the app, desktop version (the template already includes the JVM target), custom domain for invitation links.
