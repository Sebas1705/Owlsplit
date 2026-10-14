# P12a · One-tap settle up (Bizum, PayPal, Revolut) — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** The first proposal of the [market study](../research/2026-10-09-market-study.md): paying what you owe should be one tap away. Each person can publish on their profile how they want to get paid (Bizum, PayPal.me, Revolut). Whoever owes them money sees those methods when settling up. Whoever is owed can send a message requesting payment with their details.

**Architecture:**
- `data:models`: `PayMethods(bizum, paypal, revolut)`. `UserProfile.payMethods`. `Member.payMethods` (null if none).
- `data:local`: `member_entity.pay_methods` column (JSON), with the `1.sqm` migration and the `2.db` snapshot. New `selectByUser` query.
- `data:repository`: `MemberRepository.publishMyPayMethods` copies the methods to my members in every group. It syncs, but **doesn't go into the history**: it is personal data, not a group change. When leaving a group, my ghost member loses the methods.
- `data:remote`: `payMethods` in `MemberDto` (optional when reading older documents) and in `users/{uid}`, to restore it on another phone.
- `firebase/firestore.rules`: `payMethods` is a map with a fixed shape (`bizum` ≤ 20, `paypal` and `revolut` ≤ 40). Only the member's own person publishes it (`userUid == uid()`); anyone can delete it (when releasing). Tests in `subcollections.test.mjs`.
- `domain`: `PayLinks` cleans what is typed (`@ana`, `paypal.me/ana` → `ana`; phone without spaces) and builds the links (`https://paypal.me/ana/12.50EUR`, `https://revolut.me/ana`). `SaveProfile`, `GroupActions.create` and `ClaimMember` publish the methods.
- `feature:profile`: "Cómo te pagan" ("How you get paid") section, with per-field validation.
- `feature:groups`: `SettleDialog` replaces the settle-up confirmation:
  - If I'm paying and the other person has methods: "Pagar con…" ("Pay with…"). PayPal and Revolut open their link. Bizum copies the phone number, because it has no public link.
  - If I'm being paid: "Pedir el pago" ("Request payment") shares a message with my details. If I have no details, a hint leads to the profile.
  - In every case the payment is recorded by hand with "Registrar" ("Record"). The app never sees the money move.

**Platform:** KMP (Android tested on emulator; iOS compiles in CI). `LocalUriHandler` and `LocalClipboardManager` work on both platforms, and the message uses the existing `TextSharer`.

**Depends on:** P11.

---

## Decisions

1. **No payment integration.** Neither Bizum nor the banks offer a public API to start a payment from another app. Opening the service and recording afterwards avoids payment licences (PSD2) and never touches money.
2. **The details live on the member**, not in `users/{uid}`, because the rules only let the owner read the profile. Copying them to each group makes them visible exactly to whoever needs them.
3. **Outside the history.** A phone number shouldn't stay forever in the change log. Also, an own edit without differences reads as "salió del grupo" ("left the group") in old entries (P9c), so these writes don't create an entry.
4. **Compatibility:** the rules accept documents without `payMethods`, and writes use `merge = true`, so an older version doesn't erase the methods. Versions before this one don't read the new field, but only testers on the latest build exist.

## Tasks

- [x] Task 1: model, column and migration, DTO, rules and their tests. Commit.
- [x] Task 2: `publishMyPayMethods`, release on leave, `PayLinks` and use cases, with tests. Commit.
- [x] Task 3: profile ("Cómo te pagan") and `SettleDialog`, with es/en strings and a test on the emulator. Commit.
