# Market study and feature review (2026-10-09)

What Owlsplit offers today (v1.0.0-beta.2), what the competition offers and what is worth adding. Sources at the end; pricing figures and limits come from store listings, reviews and blogs (many by competitors), so they are indicative.

## Summary

- **Mature market with moderate growth**: reports put expense-splitting apps between ~0.6 and ~1.3 billion USD in 2025, with annual growth of 7% to 15% depending on the source. The figures vary widely; they only serve as an order of magnitude.
- **Splitwise is still the benchmark, but its free tier annoys users**: a daily expense limit (≈3–5, not published), ads with a wait, and basic features behind Pro (~$4.99/month or $39.99/year). It is the most cited reason for switching apps.
- **The real rival in Spain and Europe is Tricount**: free, no ads or limits, no sign-up, web for people who don't install anything; since 2022 it belongs to bunq, which uses it to acquire bank customers.
- **Banks are moving in**: Bizum (groups of up to 30 people, percentage splits and built-in payment), BBVA and Revolut (Group Bills, also for non-customers) already split expenses inside their app. Their weak point: they require a specific bank and don't work for expenses in other currencies or for mixed groups.
- **The 2025–2026 wave is AI on receipts**: scanning and line-by-line splitting (Splitwise Pro, SplitMyExpenses, Spliit with OpenAI and a dozen new apps).
- **Where Owlsplit wins**: free and unlimited, no ads, no sign-up, works 100% offline with resolved conflicts, multi-currency with a frozen rate, history with restore, accessible and in Spanish and English. Few combine real offline, no account and multi-currency without paying.
- **What it lacks compared to the market**: one-tap settle-up (Bizum/PayPal/Revolut), reminders and notifications, recurring expenses, receipts (photo, scanning, line splitting), statistics and export, web for guests, and import from other apps.

## Competitors

| App | Model | Free | Paid | Notes |
|---|---|---|---|---|
| **Splitwise** | Freemium with limit | Unlimited groups, standard splits, debt simplification; ads and a daily expense limit | Pro (~$4.99/month, $39.99/year): receipt scanning and splitting, currency conversion, charts, search, export, no limit | Leader; negative reviews over the limit since 2023; built-in payments only in the US (Venmo/PayPal) and India (Paytm) |
| **Tricount** (bunq) | Free | Everything: no ads or limits, no sign-up, multi-currency, web for guests, offline | Premium discontinued | bunq card expenses are added automatically; customer-acquisition app for the bank |
| **Settle Up** | Freemium with ads | Groups, balances, minimum transfers, change notifications | Premium (~$19.99/year or $3.99/month; or per group, from $5.99/week to $149.99 lifetime): no ads, receipt photos, categories, recurring, scheduled | Web and Windows in addition to mobile |
| **Splid** | Free + purchases | No account (group code), offline, 150+ currencies, PDF | More groups and Excel | Widely used for trips |
| **Spliit** | Open source (web/PWA) | Groups by link without accounts, categories, CSV | AI scanning (OpenAI cost borne by whoever hosts it) | Funded by donations |
| **SplitMyExpenses** and AI apps | Freemium | Splits and balances | AI receipt splitting, summaries and automatic categories | Imports from Splitwise |
| **Bizum / BBVA / Revolut** | Free (banking) | Groups, splitting, built-in payment | — | Tied to their bank or network; Revolut supports non-customers via web |
| **Flatastic** (flats) | Freemium | Chores, shopping list, expenses | No ads, history, CSV, photos | Different product: flat organization |
| **Honeydue** (couples) | Free with ads | Joint accounts, reminders, chat | — | Different product: couple finances with bank |

## Owlsplit today

| Feature | Owlsplit | In the market |
|---|---|---|
| Unlimited expenses and groups, no ads | ✅ | Tricount, Splid; Splitwise limits, Settle Up shows ads |
| Join without sign-up (link or QR) | ✅ | Tricount, Splid, Spliit, Revolut (web) |
| Works offline and converges | ✅ with recoverable conflicts | Tricount, Splid, Settle Up (per reviews) |
| Four split types, multiple payers, weights | ✅ | Common; Splitwise Pro for some |
| Multi-currency with frozen rate (ECB) | ✅ free | Splitwise only in Pro; Splid, Tricount yes |
| Debt simplification | ✅ | All |
| History with restore and conflicts | ✅ | Uncommon (Settle Up has history) |
| Members without an account who are claimed later | ✅ | Splitwise/Tricount in a similar way |
| Accessibility (AA, 200% text, screen readers) and es/en languages | ✅ | Rarely advertised |
| **Settle up with Bizum / PayPal / Revolut** | ❌ (roadmap 2) | Bizum/BBVA/Revolut integrate payment; Splitwise only US/India |
| **Reminders and push notifications** | ❌ (roadmap 2) | Settle Up, Splitwise, Flatastic |
| **Recurring expenses** | ❌ (roadmap 3) | Splitwise, Settle Up Premium, SplitMyExpenses |
| **Statistics and export (CSV/PDF)** | ❌ (roadmap 4) | Splitwise Pro, Splid (PDF), Spliit (CSV) |
| **Receipt photo and scanning, line splitting** | ❌ (roadmap 5) | Splitwise Pro, SplitMyExpenses, Spliit, AI apps |
| **Web for people who don't install the app** | ❌ (invitation page only) | Tricount, Spliit, Revolut |
| **Import from Splitwise / Tricount** | ❌ (was not on the roadmap) | SplitMyExpenses imports from Splitwise |
| iPhone | Builds in CI; Apple account missing | All |

## Proposal

**Positioning**: *the expense-splitting app that sets no limits and asks nothing of you* — truly free, no account, no ads, no bank behind it, works on the plane and made in Spain (Bizum). Against Tricount the difference can't be price (it's free too): it lies in the design, reliable offline with history, privacy (it's not a bank's front door) and covering what people currently ask for separately (paying, reminding, receipts).

**Priorities** (high impact and contained effort first):

| # | What | Why | Effort | Needs |
|---|---|---|---|---|
| 1 | **One-tap settle-up**: Bizum (copy amount and phone number, open the bank app), PayPal.me, revolut.me; payment methods in the profile | Settling up is where banks win; in Spain everyone pays with Bizum | S–M | Nothing (links and clipboard) |
| 2 | **CSV/PDF export and basic statistics** (per person, category, day; trip average) | Splitwise charges for it; closes out trips and flats; all local | M | Nothing |
| 3 | **Import from Splitwise (CSV)** and, if possible, Tricount | Removes switching cost right when Splitwise is annoying its users | M | Nothing |
| 4 | **Recurring expenses** (rent, utilities, subscriptions) | Essential for flats and couples | M | Client-side generation with deterministic id |
| 5 | **Notifications and reminders** (new expense, you're owed, you've been paid) | Retention: without notifications the app gets forgotten | M–L | Blaze plan, FCM and Cloud Functions |
| 6 | **Receipts**: attached photo, on-device scanning (ML Kit / Vision) and line splitting | Fastest-growing area; on-device is free and private compared to cloud AI | L | Cloud Storage (Blaze) for photos |
| 7 | **Web for guests**: view balances and add an expense from the link without installing | Tricount, Spliit and Revolut have it; turns the link into immediate use | L | Hosting + web (Compose for Web or a lightweight site) |
| 8 | iPhone on the App Store (Apple, TestFlight) | Half of Spain uses iPhone | M | Apple Developer account |

**Not recommended for now**: flat chores and shopping lists (Flatastic) and couple finances with bank accounts (Honeydue): they are different products and dilute focus; connecting bank accounts involves regulation (PSD2).

**Business model** (to decide later): keep everything that is free today free and unlimited (it's the advantage over Splitwise). If costs need covering (Blaze: notifications and photos), an optional subscription for what costs money (cloud receipt photos, advanced scanning) or a one-time payment per group, like Settle Up, with no ads.

## Sources

- Splitwise, pricing and limits: [getfinny.app — Splitwise pricing 2026](https://getfinny.app/blog/splitwise-pricing-2026), [getfinny.app — alternatives 2026](https://getfinny.app/blog/best-splitwise-alternatives-2026), [Trustpilot](https://au.trustpilot.com/review/splitwise.com), [Yellow Slice — UX review](https://www.yellowslice.in/blog/splitwise-app-review-ux-ui-improvements), [Kimola — review analysis](https://kimola.com/reports/splitwise-feedback-analysis-unlock-user-sentiments-google-play-154450)
- Splitwise, features: [APKMirror — Splitwise 26.6.3](https://www.apkmirror.com/apk/splitwise/splitwise/splitwise-26-6-3-release/), [APKMirror — Splitwise 24.6.1](https://www.apkmirror.com/?p=6558120)
- Tricount: [App Store](https://apps.apple.com/us/app/-/id1021178150), [TechCrunch — acquisition by bunq](https://techcrunch.com/2022/05/03/bunq-to-acquire-group-expenses-app-tricount/amp), [tricount.com](https://tricount.com/update)
- Settle Up: [App Store](https://apps.apple.com/app/id737534985), [APKMirror — 10.0.990](https://apkmirror.com/apk/step-up-labs/settle-up-group-expenses/settle-up-group-expenses-10-0-990-release)
- Splid and Spliit: [Splid — App Store](https://apps.apple.com/gb/app/splid-split-group-bills/id991473495), [Spliit — GitHub](https://github.com/spliit-app/spliit), [Spliit — Open Collective](https://opencollective.com/spliit)
- Comparisons: [SplitMyExpenses — best apps 2026](https://www.splitmyexpenses.com/articles/best-bill-splitting-apps), [Are We Even](https://www.areweeven.com/blog/best-splitwise-alternatives), [Product Hunt](https://www.producthunt.com/products/splitwise/alternatives), [Dineo — expense-sharing apps 2026](https://www.dineo.es/blog/mejores-app-compartir-gastos/)
- Banking: [Revolut — Group Bills](https://help.revolut.com/en-AU/help/app-features/splitting-expenses-with-group-bills/), [Xataka Android — Bizum app](https://www.xatakandroid.com/aplicaciones-android/probamos-app-independiente-bizum-envios-dinero-multiples-grupos-app-banco), [BBVA — sharing expenses](https://www.bbva.es/general/app-compartir-gastos.html)
- AI and receipts: [SplitMyExpenses — Callin](https://callin.io/ai-tools/splitmyexpenses), [Split – Share with Ease](https://appshunter.io/ios/app/split-share-with-ease/id6502971930)
- Other segments: [Flatastic Premium](https://www.flatastic-app.com/en/premium/), [Finny — apps for couples](https://getfinny.app/blog/budgeting-app-for-couples)
- Market size: [GII / SkyQuest](https://www.giiresearch.com/report/sky2078683-bill-splitting-apps-market-size-share-growth.html), [Lucintel](https://www.lucintel.com/es/bill-splitting-app-market.aspx)
