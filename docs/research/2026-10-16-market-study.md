# Market study, second pass (2026-10-16)

An update of [the 2026-10-09 study](2026-10-09-market-study.md), written at v1.0.0-beta.12. Four of the eight priorities of that study are done (pay links, statistics and export, Splitwise import, recurring expenses). Sources are store listings, help centres, press and review aggregators; many comparison posts are written by competitors, Reddit could not be read, and Google Play prices were not verified, so figures are indicative.

## Summary

- **Owlsplit already gives away what the others charge for or removed.** Splitwise caps free users at about 3–5 expenses a day and keeps receipts, conversion, charts and search in Pro (~$4.99/month). Settle Up keeps receipts, recurring expenses, categories and charts in Premium (3.99 €/month, 21.99 €/year in Spain). Tricount dropped Premium in its relaunch under bunq and with it CSV/PDF export, personal mode, statistics, saved custom splits, custom categories and per-expense rates.
- **The real rival in Spain is Tricount**: free, no ads, and bunq reports Spanish users split over 4.6 B€ in 2025 (+50 %). Owlsplit cannot win on price; it wins on reliability (offline with recoverable conflicts, history with restore), on what Tricount removed, on not being a bank's front door, and on settling up the Spanish way (Bizum). No incumbent supports Bizum.
- **What is missing now is reach, not features**: push notifications, a web page for guests who do not install anything, the App Store, and receipts.
- **Banks only close the loop between their own customers** (BBVA, imagin&Split, Revolut Group Bills, N26 shared spaces, bunq). Mixed groups fall back to manual transfers or cash.
- **Bizum has no public link, URL scheme or API** for person-to-person requests; copying amount and phone and opening the bank app is about the best a third-party app can do.
- **An unclaimed opening: IBAN with an EPC (SEPA) QR.** Since 9 Oct 2025 the Instant Payments Regulation makes euro-area instant transfers cost no more than a normal one, with free verification of payee. No expense-splitting app advertises it. Whether Spanish banking apps scan EPC QRs needs a hands-on check; it is mature in Germany, Austria, the Netherlands, Belgium and Finland.
- **AI-first apps** (receipt → itemized split; natural-language entry such as "Ana paid 45 for dinner") are numerous but none shows real traction. Receipt splitting is becoming table stakes; natural-language entry is still a niche. On-device OCR keeps it free.
- **The most cited growth lever is a guest link that needs no install** (Spliit, Kittysplit, Settle Up, Revolut, HippoSplit). Android widgets are practically unoccupied.

## Where Owlsplit stands

| Feature | Owlsplit | Splitwise | Tricount | Settle Up | Splid | Spliit |
|---|---|---|---|---|---|---|
| Unlimited, no ads | Yes | No (daily cap, ads) | Yes | Ads (Premium removes them) | Yes (more groups paid) | Yes |
| No sign-up | Yes (anonymous, Google optional) | No | Yes | No | Yes (group code) | Yes (link) |
| Offline with sync | Yes, recoverable conflicts | Not stated | Yes | Yes | Yes (optional sync) | No (web) |
| Per-expense rate | Yes, frozen ECB | Pro | Removed | Real time | Automatic | Limited |
| Statistics | Yes | Pro | Removed ("insights") | Premium | Total only | Not stated |
| Export | CSV, PDF | CSV (JSON in Pro) | Removed | CSV (Excel in Premium) | PDF (Excel paid) | CSV |
| Recurring | Yes | Yes | Not stated | Premium | No | Roadmap |
| History with restore | Per expense | Expenses and deleted groups | Not stated | Not stated | Not stated | Deleted groups, 30 days |
| Splitwise import | Yes | — | Yes | No | No | Roadmap |
| Settle up in Spain | Bizum, PayPal.me, revolut.me | Venmo/PayPal (US only) | bunq requests | None | None | None |
| Receipt photo / scan | No | Pro (OCR, items) | Photo | Premium | No | Yes (optional AI) |
| Notifications | No | Yes | Not stated | Yes, automatic reminders | Not stated | No |
| Web for guests | No | Full web app | Web (per third parties) | View balances by link | No | It is the web |
| iOS published | No | Yes | Yes | Yes | Yes | PWA |

User complaints back Owlsplit's strengths: after paywalls, the commonest are reliability (lost groups, wrong totals, sync failures in Settle Up and Tricount) and features removed by redesigns.

## Payment links, checked

- **PayPal.me** documents amount and currency in the path (`paypal.me/user/25EUR`): worth adding the currency for groups not in euros.
- **revolut.me** documents no amount prefill; Revolut's terms say the payer types it. The prefilled link must be tested, keeping the copy-to-clipboard fallback.
- **Wise** prefills an amount only with Quick Pay (Wise Business).
- **Bizum–Wero**: a February 2026 agreement targets person-to-person payments across 13 countries in 2026; not confirmed live.

## Priorities

The roadmap ([ROADMAP.md](../ROADMAP.md)) follows this order: reach first, then less friction to record and settle, then polish.

| # | What | Effort | Needs |
|---|---|---|---|
| 1 | Push notifications and automatic reminders (rate-limited) | M–L | Blaze, FCM, Cloud Functions |
| 2 | Web for guests on the invite link (view, add, mark paid, "who are you?") | L | Hosting, light web client on the same rules and App Check |
| 3 | App Store release | M | Apple Developer account |
| 4 | Receipts: photo, on-device OCR, itemized split with tax and tip prorated | L | Cloud Storage (Blaze) for photos |
| 5 | **IBAN with EPC QR** in pay methods and settle up | S | Nothing |
| 6 | Polish settling up: currency in PayPal.me, check revolut.me, bank picker for Bizum, "did you pay?" on return, a "pay me" QR | S | Nothing |
| 7 | Natural-language entry (es/en), always confirmed in the form | M–L | Local parser, optional LLM |
| 8 | Android widget and quick actions | M | Jetpack Glance |
| 9 | Group bin with restore for 30 days | S–M | Soft delete, scheduled clean-up |
| 10 | Default split per group (couples, flats) beyond member weights | S | Nothing |
| 11 | Comments on expenses | M | After 1 |
| 12 | Tricount import (only if a real export file can be had) | S–M | Sample file |
| 13 | Budgets | M | Nothing |

**Not recommended**: bank-statement import (PSD2: regulation, cost, third party, against the privacy promise), a WhatsApp bot, and cloud AI as the core feature. **To watch**: Bizum–Wero interoperability and a reported (unlaunched) bill-splitting feature in Apple Wallet.

## Sources

- Established apps: [Splitwise Pro](https://www.splitwise.com/pro), [Finny — Splitwise pricing 2026](https://getfinny.app/blog/splitwise-pricing-2026) and [alternatives](https://getfinny.app/blog/best-splitwise-alternatives-2026) (competitor), [Trustpilot — Splitwise](https://nl-be.trustpilot.com/review/splitwise.com?page=3), [Tricount — what happened with Premium](https://help.tricount.com/articles/what-happened-with-tricount-premium), [Tricount new app](https://tricount.com/new-app), [Settle Up — App Store ES](https://apps.apple.com/es/app/settle-up-gastos-de-grupo/id737534985), [Splid — App Store](https://apps.apple.com/us/app/splid-split-group-bills/id991473495), [Spliit — GitHub](https://github.com/spliit-app/spliit), [Kittysplit — mobile apps](https://blog.kittysplit.com/mobile-app-release/)
- Reviews: [onclarity — Tricount](https://www.onclarity.com/leaderboard/insight/tricount), [Tricount — duplicate names](https://help.tricount.com/articles/why-am-i-seeing-duplicate-names-in-my-tricount), [justuseapp — Settle Up](https://justuseapp.com/en/app/737534985/settle-up-group-expenses/reviews)
- Banks: [Forbes España — BBVA](https://forbes.es/economia/433770/bbva-permite-a-clientes-y-no-clientes-en-espana-utilizar-la-app-para-compartir-gastos/), [imagin&Split](https://www.imagin.com/en/app/imaginsplit-split-bills-app), [Revolut — Group Bills terms](https://www.revolut.com/en-BR/legal/group-bills-terms/), [N26 — shared spaces](https://support.n26.com/en-es/app-and-features/spaces/what-are-shared-spaces), [TechCrunch — PayPal Pools](https://techcrunch.com/2024/11/19/paypal-revives-its-money-pooling-feature/), [Ibercaja — Bizum](https://www.ibercaja.es/particulares/banca-digital/servicios/paga-con-bizum/), [El Economista — Bizum and Wero](https://www.eleconomista.es/banca-finanzas/noticias/13766287/02/26/el-bizum-europeo-ya-es-una-realidad-asi-podras-enviar-dinero-al-instante-entre-13-paises.html), [Genbeta — Bizum app](https://www.genbeta.com/actualidad/bizum-trabaja-nueva-app-que-permitira-compartir-gastos-grupos-amigos)
- SEPA: [Bird & Bird — Instant Payments Regulation](https://twobirds.com/en/insights/2024/global/instant-payments-regulation), [easybill — GiroCode](https://www.easybill.de/?p=33581)
- Payment links: [PayPal.me](https://www.paypal.com/an/cshelp/article/what-is-paypalme-help432), [Revolut — revolut.me terms](https://www.revolut.com/en-ES/legal/payment-terms-revme/)
- New entrants: [Splitty](https://apps.apple.com/lc/app/splitty-split-bills-with-ai/id6753145718), [SaySplit — Product Hunt](https://www.producthunt.com/products/saysplit), [HippoSplit](https://www.hunted.space/product/hipposplit), [EzSplit](https://ezsplit.it.com/), [SplitHouse](https://apps.apple.com/qa/app/splithouse/id6759072027)
