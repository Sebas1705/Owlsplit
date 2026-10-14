# 0005 · Money as integer minor units

- **Status:** accepted · 2026-10-07

## Context
Several devices compute balances separately, with no server to arbitrate. Any rounding difference produces different balances depending on who looks, and the group sum would also stop being zero.

## Decision
- Amounts as `Long` in **minor units** according to each currency's ISO 4217 decimals.
- Splits with **largest remainders** and deterministic tie-break (`sortKey`, `id`).
- Exchange rates as `Long` with scale 10⁹, **frozen** on each expense; conversion of the net per member with deterministic correction of the imbalance.
- Invariant: the sum of the group's balances is exactly 0, verified with property tests.

Exact rules in [DOMAIN.md](../DOMAIN.md).

## Rejected alternatives
- **`Double`**: representation errors and different results depending on the order of operations.
- **Multiplatform BigDecimal** (e.g. ionspin bignum): correct, but heavier and unnecessary with values bounded in `Long`.
