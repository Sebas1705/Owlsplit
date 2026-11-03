# Group admin, permissions and merging people — Design

**Date:** 2026-11-03 · **Status:** proposed; implemented in staged pull requests (A: model and rules, B: admin and permissions UI, C: merging people).

Three requests, one design:

1. Every group has an **admin**: its creator at first, and the role can be handed over.
2. The admin manages the group: removes members, grants or takes away **permissions**, settles balances they are not part of.
3. Two members that are really the same person can be **merged** into one, keeping all the data.

## 1. Decisions

| Question | Decision | Why |
|---|---|---|
| What is the admin bound to? | A **member id** (`adminMemberId` on the group), not an account. | A member survives account changes (Google sign-in, new phone); an uid does not. `SwitchToGoogle` already re-claims the same member. |
| Groups that exist today | `adminMemberId` is absent. The **creator's member** (`createdBy` uid → the member whose `userUid` or `pastUids` has it) is the admin; if nobody matches, the group has no admin and behaves as today. | No migration write, no race between devices. |
| Who can take the role | Only the current admin hands it over, to an **active member with an account**. Leaving as the admin hands it over to the member who joined earliest (by `sortKey`) with an account; a group left with no one with an account has no admin. | A group never ends up stuck with an admin that cannot act. |
| Permissions | Three flags per member, all **on** by default: `ADD_EXPENSES`, `EDIT_EXPENSES` (edit and delete expenses), `RECORD_PAYMENTS`. Stored as a set of the flags **taken away** (`deniedPermissions`), so missing means everything allowed (old members and old clients keep working). | Safe default and forward compatible. |
| What the admin always can | Everything: no flag applies to them. | |
| What anybody can | Read, see balances, change their own member's name, avatar and pay methods, leave the group. | |
| Settling | A member with `RECORD_PAYMENTS` records payments **in which they are payer or receiver**. The admin records any payment. | The admin settles what does not involve them, as asked. |
| Member management | Add, rename, weight, deactivate, delete, merge, permissions, hand over admin, regenerate the invite code and edit the group: **admin only**. | The admin "manages" the group. |
| Enforcement | **Client** (use cases refuse and the UI hides) **and rules** for what is cheap to check: only the admin changes `adminMemberId`, `deniedPermissions`, other members' fields and the group's settings. Per-flag enforcement on expenses and payments needs the author's member id on each document (`byMember`), checked with one `get()`. It is written by new clients and enforced only when present. | An old client that omits `byMember` cannot be forced; permissions are a house rule between friends, not a security boundary. The admin-only matters are enforced for everyone. |

## 2. Data

Group document: `adminMemberId: string?`.
Member document: `deniedPermissions: string[]` (default empty).
Expense and transfer documents: `byMember: string?` (new clients write it).

Local database, schema 4: `group_entity.admin_member_id TEXT`, `member_entity.denied_permissions TEXT NOT NULL DEFAULT ''` (comma separated), `expense_entity.by_member` and `transfer_entity.by_member` (`TEXT`, nullable). DTOs get the keys with defaults, so a missing key decodes; rules list the keys in `hasOnly`.

The admin is derived in one place (`GroupRoles` in the domain): given the group, its members and the current uid it answers `isAdmin`, `can(permission)`, `canSettle(from, to)`, and `successor()`.

## 3. Rules

- `groups/{g}` update: `adminMemberId`, group settings and the invite code change only if the caller is the admin (their member has `userUid == auth.uid` and is `adminMemberId`, or the group has no `adminMemberId` and the caller is `createdBy`). Handing over also needs the new admin to be an existing active member.
- `members/{m}` update: the member itself may change only its personal fields (name, avatar, pay methods, `linked`, claim/release as today). Everything else, including `deniedPermissions` and `active`, is admin only. Creating a member and deleting one are admin only, except joining (`addMe`/claim) as today.
- `expenses`, `transfers`: when `byMember` is present, `get(members/byMember)` must belong to the caller and not deny the flag; a payment by a non-admin must name `byMember` as payer or receiver.

Every rule that reads a member costs a billed read; at most one per write.

## 4. Merging people

`MergeMembers(source, target)`, admin only.

- Both must be members of the group and different. At most **one** of them may have an account (`userUid != null`): that one is the survivor; if neither has one, `target` is. Two people with accounts cannot be merged (it would take an account away from somebody).
- Every expense: `payers` and `splitValues` keys `source → target` (amounts and weights of both are **added** when the target is already there); the shares stay exact, so balances do not change.
- Every payment: `fromMemberId`/`toMemberId` `source → target`; a payment that becomes target → target is deleted.
- The survivor keeps its name, avatar and pay methods, takes the **sum of `pastUids`** and the source's `linked` if it has none.
- The source is soft deleted and a history entry says it was merged (`MERGED` action on the member, with the target's id).
- Writes go through the outbox in order: expenses and payments first, the source's deletion last, so an interrupted upload never leaves references to a member that is gone.
- Balances before and after must be equal; the use case checks it and refuses otherwise.

## 5. UI

- Members screen: a crown on the admin, a menu per member for the admin (permissions, make admin, merge into…, delete). Non-admins see the list read only, except their own row.
- Settle tab: «Record a payment» offers only the payments the caller may record; the admin gets every pair.
- Hidden or disabled actions for missing permissions, with a line saying who the admin is.
- The web page for guests respects the same flags (it writes `byMember`).

## 6. Compatibility

Everything is optional on the wire. Old clients ignore the new keys on documents they read, and their writes keep working as before except the admin-only ones, which the rules reject (their outbox entry is retried and ends as a conflict, as any rejected write). The release notes say the group needs the update to manage it.
