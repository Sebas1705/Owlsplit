# Member identity: leaving, returning and accounts — Study

**Date:** 2026-10-12 · **Status:** approved (option A, 2026-10-12); plan P14.

The goal is for a person's expenses, debts and history to stay tied to them even if they:

- leave the group and come back,
- switch phones,
- or sign in with a different account.

In addition, if they sign in with Google, their member must be tied to a permanent identifier and not just to a name.

## 1. How it works today

| Situation | What happens | Is anything lost? |
|---|---|---|
| Someone joins with «Soy nuevo» (I'm new) | Their member is created with `userUid` = their uid | — |
| Someone joins by picking a name (ghost) | The ghost gets their `userUid` (`claim`) | — |
| They leave the group | Their member keeps its expenses, but `userUid` becomes `null` and their payment details are deleted. Their uid leaves `memberUids` | Expenses and balances are not. **The member ↔ person link is** |
| They return with **the same account** | They see the list of ghosts and have to **pick themselves by hand** | Nothing, but it relies on recognizing themselves by name |
| They return with **another account** (another phone without Google, or after reinstalling while anonymous) | Same: they pick themselves by hand | Nothing, but **anyone with the invitation can pick that profile** |
| They delete their account | They leave all their groups (same as leaving) | Same as leaving |
| They sign in with Google on another phone | The uid is the same: they stay in their groups and get everything back | — |

What already works well:

- **History is not lost.** Expenses, splits and payments point to the *member id*, not the account. History entries store the author's uid. P9c rebuilds who each uid belongs to from the `JOINED` entries and the `userUid` changes, so names are kept after leaving.
- **With Google, identity is already permanent.** The Firebase uid is the same on any phone signed in with that Google account. With an anonymous account, the uid is lost on uninstall.

## 2. Problems

1. **The member forgets who it belonged to.** On leaving, `userUid` becomes `null` and nothing else is stored. On returning, the person is not recognized, even with the same account.
2. **An abandoned profile can't be told apart from a ghost created by hand.** In «¿Quién eres?» (Who are you?), «Carlos (salió del grupo)» and «Marta (añadida por Ana, sin cuenta)» look the same.
3. **Anyone can take over someone else's profile.** The rules allow claiming any ghost, including one that belonged to another account, and thus inheriting its balance.
4. **You can't see who has their account protected.** Other members don't know whether a profile is tied to Google (and therefore always recoverable) or to an anonymous account that can be lost.

## 3. Proposal

### 3.1 The member remembers its accounts: `pastUids`

A `pastUids: List<String>` list is added to the member with the accounts that held it. On leaving or deleting the account, your uid is added to the member's `pastUids` at the same time as `userUid` becomes `null`.

- **Returning with the same account (anonymous or Google): automatic reassociation.** On joining, if your uid is in some member's `pastUids`, the app claims it without asking. You get back your name, balances and history, and history shows it as «Carlos volvió al grupo».
- **With Google this is permanent.** Since the Google uid doesn't change, any phone you sign in to with that account recognizes you. With an anonymous account, it only recognizes you as long as you don't reinstall the app.

### 3.2 Abandoned profiles, visible as such

A member with `userUid == null` and a non-empty `pastUids` is an **abandoned profile**:

- In Members, Balances and «¿Quién eres?» it is shown as «Carlos · salió del grupo», separate from members without an account who were added by hand.
- It keeps its balance, expenses and history. Nothing is deleted.

### 3.3 Returning with another account: claiming an abandoned profile

This is the case of someone who lost an anonymous account or signs in from a different account. There are two options; **the decision is the owner's**:

- **A. Open, but visible (recommended for the beta).**
  - Anyone who joins can pick an abandoned profile, as today.
  - The app asks for explicit confirmation: «Este perfil era de otra cuenta. Elígelo solo si eres Carlos».
  - It is recorded in history as «Carlos (otra cuenta) recuperó el perfil de Carlos», visible to everyone.
- **B. With approval.**
  - Picking an abandoned profile creates a request.
  - A member with an account approves it from the group, and in the meantime you join as «Soy nuevo».
  - It is safer, but slower, and needs a requests document with its own rules. Without push notifications (Blaze plan), the approver only finds out when opening the app.

### 3.4 Visible Google identity

A `linked: Boolean` field is added to the member, published by the person themselves just like payment details. It is `true` when their account is linked to Google.

- Members shows «cuenta con Google», so the group knows which profiles are always recoverable.
- When claiming an abandoned profile that was linked to Google from another account, the notice highlights it: «Este perfil era de una cuenta de Google».

### 3.5 Firestore rules

- `pastUids`: list of strings, at most 20, that can only grow. Only your own uid can be added, and only when you release your member (`userUid` goes from your uid to `null`).
- Claiming is still `null → my uid`. In option B, claiming an abandoned profile with another account requires an approved request.
- `linked`: only the member's person can change it (same as `payMethods`).

### 3.6 Data and migration

- Local schema 2 → 3: the `past_uids` (JSON) and `linked` columns are added.
- For existing documents, `pastUids` is missing and reads as empty. Ghosts that left before this change can get their `pastUids` from history (the `JOINED` entries and the `userUid` changes say which uid each member had). This is done on the client, the first time a group member opens the app.

## 4. Testing

With three emulators:

1. Carlos (anonymous) leaves and returns with the same account: he is reassociated automatically, without picking.
2. Carlos with Google leaves, reinstalls and signs in with Google on another emulator: he is reassociated automatically.
3. Anonymous Carlos reinstalls (new account) and joins: he sees «Carlos · salió del grupo». With option A, he confirms and gets it back, and history says so. With option B, it stays pending until Ana approves it.
4. Another person tries to pick Carlos's profile: they see the notice. With option B, they don't get it without approval.
5. Balances, history and names are kept in all cases.
6. Rules are tested in the emulator: `pastUids` can't be rewritten and someone else's uid can't be added.
