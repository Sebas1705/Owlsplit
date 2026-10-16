# Web for guests (2026-10-16)

Roadmap item: the invite link opens the group in the browser, so a friend can join, see the balances, add an expense and record a payment without installing anything. Spliit, Kittysplit, Settle Up and Revolut grow this way ([market study](../research/2026-10-16-market-study.md)).

## Decisions

- **A light web client, not Compose for Web.** Plain ES modules with the Firebase JS SDK (12.19.0 from gstatic), served by Hosting at `/g/{code}`. No build step, like the rest of the site. The KMP modules (SQLDelight, GitLive) do not run in the browser, and the guest only needs a small part of the app.
- **Same data, same rules.** The guest is an anonymous Firebase user who joins the group exactly as the app does (`memberUids` arrayUnion with `lastJoinCode`), then claims a member without account or adds one. The Firestore rules do not change. Every write is the app's DTO with every key, and an entity write goes with its `changelog` entry in one batch, so the apps pick it up (docs/SYNC.md).
- **Same money.** `money.js` ports `Allocation`, `ShareCalculator`, `NetCalculator` (with `toBase`), `BalanceCalculator` and `DebtSimplifier` with BigInt. `firebase/rules-test/fixtures/ledger.json` is generated and checked by a Kotlin test, and checked by a JS test, so both compute the same balances and suggestions.
- **Config.** The web app "Owlsplit web" (`1:596963610720:web:bb1f763b3bfaaff346f305`) is read from Hosting's `/__/firebase/init.json`, so nothing is stored in the repo.
- **App Check** is not enforced. When it is, the web needs a reCAPTCHA provider first.

## First version

1. `/j/{code}` keeps opening the app and gains **Open in the browser**.
2. `/g/{code}`: sign in anonymously (the browser keeps it), read `invites/{code}`, **Join**, then **Who are you?** (unclaimed members, or "I'm new" with a name).
3. The group: my balance, the expenses (newest first), the balances and the suggested payments.
4. **Add expense**: amount, title, category, date, who paid, who takes part (equal split, or by weights when the members' weights differ), in the group currency.
5. **Settle up**: for my debts, the payee's ways to be paid (Bizum phone to copy, PayPal.me with the amount, revolut.me, IBAN to copy) and **Record the payment** (a transfer in the group currency).
6. Spanish and English, with the site's language switch.

Not in the first version: editing or deleting expenses, other currencies (they are read and converted at their frozen rate, but not created), receipts, and linking Google on the web (a guest who installs the app later joins again from the app).

## Risks

- **One account per browser.** An anonymous guest who clears the browser loses that account. Their member stays, and they can claim it again.
- **50 accounts per group** (the rules' limit on `memberUids`). Each browser counts once.
- **Data without validation in the rules** (sums of payers and shares): the client guarantees them, as the app does. The web only creates equal or weight splits with a single payer, so they always add up.
