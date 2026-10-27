# Owlsplit: offline-first sync for shared expenses

How I built a local-first sync engine and an exact integer money ledger so that every device, and a browser with no app installed, computes the same balances for a shared group.

## Context

[Owlsplit](https://github.com/Sebas1705/Owlsplit) is a Kotlin Multiplatform app (Android and iOS) for splitting group expenses. Several people edit the same group from their own phones, often on a trip with no coverage. The backend is Firebase (Firestore and Auth) on the free Spark plan, so there is no server of mine to arbitrate conflicts or compute balances: each client does it.

## Problem

Two requirements pull in different directions:

- **Offline edits on several devices.** The app has to work fully offline, queue writes, and reconcile them later without silently losing anyone's edit.
- **Identical money on every device.** Balances are computed locally on each phone. A one-cent rounding difference means two people see different debts, and the group total stops being zero.

Firestore's own offline cache covers the first point only partially: it is not a queryable database, and I wanted balances and statistics to be plain local SQL queries. The free quota (50K reads a day) was a third constraint.

## Approach

**Local-first storage.** [ADR 0003](https://github.com/Sebas1705/Owlsplit/blob/main/docs/adr/0003-local-first-sqldelight-sync.md) makes SQLDelight the UI's only source of truth; Firestore is just the exchange medium. The Firestore SDK's persistence is disabled so there are never two diverging caches. A local write is one transaction: the entity goes to `PENDING` with `version = serverVersion + 1`, and an `outbox` row is inserted or *compacted*, so ten offline edits of the same expense become one upload carrying the original `baseVersion`.

**Optimistic concurrency, not last-write-wins.** Uploads are strict FIFO per group with exponential backoff (`min(2^attempts s, 15 min)` plus jitter). The security rules only accept `version == previous + 1`, so the first write to reach the server wins. On a rejection the engine reads the remote document and decides: same version and same content means it was our own write whose acknowledgement was lost; a newer version with different content is a **conflict**, stored in a `conflict` table and surfaced in the history with *Reapply* or *Discard*. I rejected last-write-wins by timestamp in the ADR because it costs the same and loses edits silently. Real Firestore surfaced one subtle case: the listener delivers our own write *before* `set` returns, so without tracking the in-flight upload every upload became a false conflict. The full design is in [docs/SYNC.md](https://github.com/Sebas1705/Owlsplit/blob/main/docs/SYNC.md).

**Download driven by the history.** Instead of one listener per collection, each group has a single listener on its append-only `changelog`; every entry names the document to fetch. Because the outbox uploads a document before its history entry, the document always exists when the entry arrives. By the cost model in SYNC.md, an idle start with 10 groups (3 archived) drops from about 50 reads to about 17.

**Money as integers.** [ADR 0005](https://github.com/Sebas1705/Owlsplit/blob/main/docs/adr/0005-money-as-minor-units.md) and [docs/DOMAIN.md](https://github.com/Sebas1705/Owlsplit/blob/main/docs/DOMAIN.md) define the rules: amounts are `Long` minor units per ISO 4217 decimals (never `Double`), exchange rates are `Long` with scale 10⁹ frozen on each expense, and splits use the largest-remainder method with a deterministic tie-break:

```kotlin
fun byWeights(total: Long, weights: List<Long>): List<Long> {
    val weightSum = weights.sum()
    val parts = LongArray(weights.size)
    val remainders = LongArray(weights.size)
    weights.forEachIndexed { i, w ->
        val qr = mulDivRem(total, w, weightSum)
        parts[i] = qr.quotient; remainders[i] = qr.remainder
    }
    val leftover = total - parts.sum()
    weights.indices
        .sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
        .take(leftover.toInt()).forEach { parts[it]++ }
    return parts.toList()
}
```

€10.00 among three is always 3.34 / 3.33 / 3.33, with the extra cent on the same member everywhere. Foreign-currency expenses convert each member's *net* and redistribute the rounding imbalance the same way, so balances still sum to exactly zero. A deterministic greedy simplifier settles debts in at most `n − 1` payments.

**A second implementation, kept honest by fixtures.** The [guest web view](https://github.com/Sebas1705/Owlsplit/blob/main/docs/designs/2026-10-16-web-guests.md) at `/g/{code}` lets a friend join from a browser. The KMP modules don't run there, so I ported the ledger to plain JavaScript with BigInt ([money.js](https://github.com/Sebas1705/Owlsplit/blob/main/firebase/hosting/js/money.js)). To stop the two from drifting, a Kotlin test, [LedgerFixturesTest](https://github.com/Sebas1705/Owlsplit/blob/main/domain/usecases/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/domain/usecases/balances/LedgerFixturesTest.kt), generates 60 seeded random groups (mixed currencies, all four split types, the odd inconsistent entry) with the app's expected balances and payments into [ledger.json](https://github.com/Sebas1705/Owlsplit/blob/main/firebase/rules-test/fixtures/ledger.json). It fails if the committed file is stale, and a Vitest suite asserts that `money.js` reproduces every case exactly.

## Result

- The sync engine has unit tests against a fake store (compaction, ordering, backoff, both conflict branches, access loss, process death mid-upload) and a two-device test against the Firestore and Auth emulators with the real rules.
- The repository has 322 `@Test` functions in Kotlin; the fixtures add 60 cross-language cases.
- [Kover](https://github.com/Sebas1705/Owlsplit/pull/81) reports 79.6 % merged line coverage (6,978 / 8,769), enforced with a 75 % floor in CI. `core:money` is at 99 %, `domain:usecases` at 97 % and `data:repository` (the sync engine) at 92 %.

## Lessons

- **Determinism is a design property, not a test outcome.** Integer units and fixed tie-breaks made "every device agrees" something I could state and then check.
- **When logic is duplicated across languages, generate the oracle from one side.** Shared fixtures turned a risky port into a mechanical check.
- **Trade-offs I accepted:** sync is per document (no field merge), the history-driven download assumes every write path logs an entry, the greedy simplifier is not the global minimum, and `data:local` and `data:remote` have no unit tests.
- **Next:** run the emulator sync test in CI (today it needs a connected Android emulator), add tests for the local and remote adapters, and move remote purging of soft-deleted data to Cloud Functions as the docs plan.
