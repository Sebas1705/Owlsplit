# Domain

Owlsplit's business rules. Everything described here lives in `core:money` and `domain:usecases` (pure common Kotlin) and must produce **exactly** the same result on every device.

## Entities

| Entity | Fields | Notes |
|---|---|---|
| `User` | `uid`, `displayName`, `avatar`, `preferredCurrency`, `linkedProviders`, `createdAt` | One per Firebase Auth account |
| `Group` | `id`, `name`, `icon`, `accent`, `baseCurrency`, `startDate?`, `endDate?`, `inviteCode`, `memberUids[]`, `createdBy`, `archivedBy[]`, sync metadata | `baseCurrency` cannot be changed once there are expenses (MVP) |
| `Member` | `id`, `groupId`, `name`, `avatar`, `defaultWeight`, `userUid?`, `active`, `sortKey`, sync metadata | null `userUid` = ghost; a user has at most one member per group |
| `Expense` | `id`, `groupId`, `title`, `category`, `note?`, `date`, `currency`, `amountMinor`, `fxRateToBase`, `payers`, `split`, `receiptUrl?`, `recurrenceId?`, `createdBy`, sync metadata | |
| `Transfer` | `id`, `groupId`, `fromMemberId`, `toMemberId`, `amountMinor`, `currency`, `fxRateToBase`, `date`, `note?`, `createdBy`, sync metadata | |
| `ChangeLogEntry` | `id`, `groupId`, `entityType`, `entityId`, `action`, `authorUid`, `diff`, `createdAt` | Append-only, never edited |

**Sync metadata** (all editable entities): `updatedAt` (server time), `version` (integer, starts at 1), `deleted` (boolean), `updatedBy` (uid).

**Identifiers**: client-generated UUID v4, so entities can be created offline without collisions.

**Dates**: `date` is a local date (`LocalDate`) with no time: the expense "is from July 12" in any time zone. `createdAt` and `updatedAt` are UTC instants.

**MVP categories** (fixed list, with icon): food, groceries, transport, accommodation, leisure, shopping, home, utilities, health, gifts, other.

## Money

- An amount is `Money(amountMinor: Long, currency: Currency)`. `Float` and `Double` are **never** used for money.
- `Currency` holds the ISO 4217 code and its **decimals** (EUR 2, JPY 0, KWD 3), taken from an embedded table.
- Maximum amount per expense: 10¹² minor units (avoids overflow in group sums).
- Only amounts in the same currency are added; mixing currencies without conversion is a programming error (exception).

## Split

Input: the expense `amountMinor`, the list of beneficiaries ordered by `sortKey` then `id`, and the split type. Output: `shares: Map<MemberId, Long>` whose sum is **exactly** `amountMinor`.

| Type | Value per member | Calculation |
|---|---|---|
| `EQUAL` | — (included or not) | `amount / n` each; remainder by largest remainders |
| `WEIGHTS` | integer ≥ 1 | `amount * w_i / Σw` (integer part); remainder by largest remainders |
| `EXACT` | amount in minor units | Validation: the sum must equal `amount`; otherwise it cannot be saved |
| `PERCENT` | percentage in hundredths (0–10000) | Validation: the sum must be 10000; then as `WEIGHTS` with those values |

**Remainder allocation (largest remainder method, deterministic)**: after integer division, the `r` leftover units are handed out one by one to the `r` members with the **largest fractional remainder** (`amount * w_i mod Σw`); on equal remainders, the first in order (`sortKey`, `id`) wins. So €10.00 split among 3 always gives 3.34 / 3.33 / 3.33 with the extra cent going to the same member on every device.

**Payers**: `payers: Map<MemberId, Long>` must sum to `amountMinor`. With a single payer, the full amount.

## Currencies

- Each expense and transfer stores `fxRateToBase`: how many units of the base currency **one major unit** of the expense currency is worth, as a scaled decimal `Long` with **scale 10⁹** (e.g. 1 USD = 0.923456789 EUR → `923456789`).
- If the expense currency is the base currency, `fxRateToBase = 10⁹`.
- The rate is **frozen** when the expense currency is chosen (source: Frankfurter, ECB rates). The user can edit it. If no rate is available (no network and no cache, or a currency not covered by the ECB), the user must enter it; a made-up rate is never used.
- Implementation (`data:fx`, P9b): the rate is read as text and converted to fixed point (it never goes through `Double`); cached per pair in DataStore for the profile's `FX_CACHE_SECONDS` (0 in development, 12 h in staging and production). If the network fails, the last stored rate is used even if expired, showing its date.
- **Converting an expense to the base currency without breaking zero-sum**:
  1. Compute the **net per member in the expense currency**: `paid_i − share_i` (integers; they sum to 0).
  2. Convert each net: `net_i × rate × 10^(dec_base − dec_expense) / 10⁹`, rounded toward zero.
  3. The resulting imbalance (sum ≠ 0) is distributed unit by unit by largest remainders, with the same deterministic tie-break. Result: base-currency nets that sum to exactly 0.
- With `Long` arithmetic and bounded values there is no overflow; if an intermediate product could overflow, emulated 128-bit multiplication (`multiplyHigh`) is used, covered by tests.

## Balances

`balance_i = Σ (base-currency nets of non-deleted expenses) + Σ (transfers: +amount to the payer, −amount to the recipient, in base currency)`.

Invariant: **Σ balance_i = 0** always. Checked in property tests and with a debug assertion.

Sign convention: positive balance = **is owed**; negative = **owes**.

## Debt simplification

Deterministic greedy algorithm (the one Settle Up uses):

1. Split creditors (balance > 0) and debtors (balance < 0); discard zero balances.
2. Sort both by absolute amount descending; on equal amounts, by `sortKey` and `id`.
3. Match the largest debtor with the largest creditor; payment = minimum of both absolute values; subtract; reinsert whichever still has a balance; repeat.

Guaranteed (and tested) properties: preserves net balances exactly; at most `n − 1` payments; same result for any permutation of the input. It does not guarantee the absolute minimum number of payments (an NP-hard problem); it is the same approximation used by apps in this space.

MVP option: if the user turns it off for the group, "Cómo saldar" ("How to settle") shows the debts unsimplified (each debtor pays whoever covered for them).

## Validation invariants (client and rules)

- `title`: 1–80 characters; `note`: ≤ 500.
- `amountMinor` > 0 and ≤ 10¹².
- `payers` and `shares`: keys = existing members of the group, values ≥ 0, exact sums.
- An expense must have at least one beneficiary with a share > 0.
- `fromMemberId ≠ toMemberId` in transfers.
- `defaultWeight` between 1 and 100.
