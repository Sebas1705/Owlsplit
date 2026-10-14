# P5 · Firestore rules — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Replace the temporary P2 rules with the real ones from [FIREBASE.md](../FIREBASE.md): members-only access, joining with the current code, leaving, claiming members, chained versions with server time, shape validation of every document and create-only history. Everything tested with the emulator, locally and in CI, and deployed to `owlsplit-dev`.

**Architecture:** A single `firebase/firestore.rules` with helper functions. The tests live in `firebase/rules-test` (Node + Vitest + `@firebase/rules-unit-testing`) and run inside `firebase emulators:exec` against a `demo-owlsplit` project (no credentials). CI adds a job with Java 21 + Node. Production deployment from CI arrives in P11; here it is deployed to dev with the user's session.

**Tech Stack:** Firestore Security Rules v2, firebase-tools 15.32.1, `@firebase/rules-unit-testing` 5.0.2, `firebase` 12.19.0 (web SDK, only for the tests), Vitest 5.0.3, Node 24 locally / 22 in CI, JDK 21 (emulator).

**Platform:** Backend (Firebase); affects Android and iOS equally.

**Depends on:** P2 (project and `firebase/`).

---

## Decisions in this plan

1. **Joining without being able to read the group.** A non-member cannot read the group, so they know neither `version` nor `memberUids`. The join is written with server transforms: `memberUids: arrayUnion(uid)`, `version: increment(1)`, `updatedAt: serverTimestamp()`, `updatedBy: uid`, `lastJoinCode: code`. The rules validate the resulting document. P10 implements it this way in the client.
2. **Sums cannot be checked in rules.** Rules have no loops, so they cannot add up the values of `payers` or `splitValues`. They validate types, ranges and sizes; the sums are guaranteed by the client (P3 domain) and, if an inconsistent expense ever arrived, `ObserveGroupBalances` (P4) leaves it out. [FIREBASE.md](../FIREBASE.md), which said otherwise, is corrected.
3. **No deletes.** No document is deleted from the client (soft delete with `deleted`), except `invites/{code}` when the code is regenerated.
4. **The P2 `smoke/{uid}` document is kept** so the smoke test can be repeated when GitLive is upgraded.
5. **Document schema** (what P6 will write): the same fields as `data:models`, with `updatedAt` as a server timestamp and without the local `syncState`/`serverVersion` fields.

## File map

| Action | Path |
|---|---|
| Rewrite | `firebase/firestore.rules` |
| Create | `firebase/rules-test/package.json`, `package-lock.json` (generated), `vitest.config.mjs`, `test/helpers.mjs`, `test/groups.test.mjs`, `test/subcollections.test.mjs`, `test/misc.test.mjs` |
| Modify | `firebase/firebase.json` (emulator UI disabled), `.gitignore` (`node_modules`, emulator logs), `.github/workflows/ci.yml`, `docs/FIREBASE.md`, `docs/plans/README.md` |

---

### Task 1: Test environment

- [ ] **Step 1: Test package**

`firebase/rules-test/package.json`:
```json
{
  "name": "owlsplit-rules-test",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest run"
  },
  "devDependencies": {
    "@firebase/rules-unit-testing": "5.0.2",
    "firebase": "12.19.0",
    "vitest": "5.0.3"
  }
}
```
`firebase/rules-test/vitest.config.mjs`:
```js
import { defineConfig } from 'vitest/config';

// One emulator for every test file: run files one after another.
export default defineConfig({
  test: { fileParallelism: false, testTimeout: 20000, hookTimeout: 30000 },
});
```
```bash
cd firebase/rules-test && npm install && cd ../..
```
Expected: `package-lock.json` created, no high-severity vulnerabilities.

- [ ] **Step 2: Emulator without UI and `.gitignore`**

In `firebase/firebase.json`, inside `emulators`, add `"ui": { "enabled": false }`. In `.gitignore` add:
```
node_modules/
firebase/*-debug.log
```

- [ ] **Step 3: Test helpers**

`firebase/rules-test/test/helpers.mjs`:
```js
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { Timestamp, doc, setDoc } from 'firebase/firestore';

export const GROUP = 'g1';
export const CODE = 'ABCD2345';

export async function createEnv() {
  return initializeTestEnvironment({
    projectId: 'demo-owlsplit',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
}

/** Firestore client acting as [uid]. */
export const as = (env, uid) => env.authenticatedContext(uid).firestore();

export const synced = (version = 1, by = 'ana') => ({
  version,
  updatedAt: Timestamp.fromMillis(1_000),
  updatedBy: by,
  deleted: false,
});

export function groupData(overrides = {}) {
  return {
    name: 'Ibiza 2026', icon: 'plane', accent: 'BLUE', baseCurrency: 'EUR',
    startDate: null, endDate: null, inviteCode: CODE, memberUids: ['ana'], archivedBy: [],
    createdBy: 'ana', createdAt: 1_000, lastJoinCode: null,
    ...synced(),
    ...overrides,
  };
}

export function memberData(overrides = {}) {
  return {
    name: 'Ana', avatar: null, defaultWeight: 1, userUid: 'ana', active: true, sortKey: 0,
    ...synced(),
    ...overrides,
  };
}

export function expenseData(overrides = {}) {
  return {
    title: 'Cena', category: 'FOOD', note: null, date: '2026-07-12', currency: 'EUR',
    amountMinor: 4860, fxRateScaled: 1_000_000_000, payers: { m1: 4860 }, splitType: 'EQUAL',
    splitValues: { m1: 1, m2: 1 }, receiptUrl: null, recurrenceId: null, createdBy: 'ana', createdAt: 1_000,
    ...synced(),
    ...overrides,
  };
}

export function transferData(overrides = {}) {
  return {
    fromMemberId: 'm2', toMemberId: 'm1', amountMinor: 1215, currency: 'EUR', fxRateScaled: 1_000_000_000,
    date: '2026-07-13', note: null, createdBy: 'ana', createdAt: 1_000,
    ...synced(),
    ...overrides,
  };
}

/** Seeds documents bypassing the rules. */
export async function seed(env, writes) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [path, data] of Object.entries(writes)) await setDoc(doc(db, path), data);
  });
}
```

- [ ] **Step 4: Commit**

```bash
git add firebase/rules-test firebase/firebase.json .gitignore
git commit -m "test(rules): emulator test harness for Firestore rules"
```

### Task 2: Rules

- [ ] **Step 1: Write the rules**

`firebase/firestore.rules`:
```
rules_version = '2';

// Owlsplit — see docs/FIREBASE.md. Documents are never deleted by clients (soft delete via
// `deleted`), except invites when a group regenerates its code. Every write carries
// version = previous + 1, updatedAt = server time and updatedBy = the caller.
service cloud.firestore {
  match /databases/{database}/documents {

    // ── Helpers ──────────────────────────────────────────────────────────────
    function signedIn() { return request.auth != null; }
    function uid() { return request.auth.uid; }
    function data() { return request.resource.data; }
    function changedKeys() { return request.resource.data.diff(resource.data).affectedKeys(); }

    function stamped() {
      return data().updatedAt == request.time && data().updatedBy == uid();
    }
    function created() { return data().version == 1 && stamped() && data().deleted == false; }
    function nextVersion() { return data().version == resource.data.version + 1 && stamped(); }

    function group(groupId) { return get(/databases/$(database)/documents/groups/$(groupId)); }
    function isMemberOf(groupId) { return signedIn() && uid() in group(groupId).data.memberUids; }

    function isText(v, min, max) { return v is string && v.size() >= min && v.size() <= max; }
    function isOptText(v, max) { return v == null || (v is string && v.size() <= max); }
    function isOptDate(v) { return v == null || isDate(v); }
    function isDate(v) { return v is string && v.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$'); }
    function isCurrency(v) { return v is string && v.matches('^[A-Z]{3}$'); }
    function isAmount(v) { return v is int && v > 0 && v <= 1000000000000; }
    function isRate(v) { return v is int && v > 0 && v <= 900000000000000; }
    function isMemberMap(m) { return m is map && m.size() > 0 && m.size() <= 50; }
    function isMeta(d) { return d.version is int && d.updatedBy is string && d.deleted is bool; }

    // ── Users ────────────────────────────────────────────────────────────────
    match /users/{userId} {
      allow read, write: if signedIn() && uid() == userId;
    }

    // ── Groups ───────────────────────────────────────────────────────────────
    match /groups/{groupId} {
      function validGroup() {
        return data().keys().hasOnly(['name', 'icon', 'accent', 'baseCurrency', 'startDate', 'endDate',
            'inviteCode', 'memberUids', 'archivedBy', 'createdBy', 'createdAt', 'lastJoinCode',
            'version', 'updatedAt', 'updatedBy', 'deleted'])
          && isText(data().name, 1, 60) && isText(data().icon, 1, 40) && isText(data().accent, 1, 20)
          && isCurrency(data().baseCurrency) && isOptDate(data().startDate) && isOptDate(data().endDate)
          && data().inviteCode is string && data().inviteCode.size() == 8
          && data().memberUids is list && data().memberUids.size() >= 1 && data().memberUids.size() <= 50
          && data().archivedBy is list && isMeta(data());
      }
      // Members may only add or remove their own uid in archivedBy.
      function archiveIsOwn() {
        let before = resource.data.archivedBy.toSet();
        let after = data().archivedBy.toSet();
        return after.difference(before).hasOnly([uid()]) && before.difference(after).hasOnly([uid()]);
      }
      function editAsMember() {
        return uid() in resource.data.memberUids && validGroup() && nextVersion()
          && !changedKeys().hasAny(['createdBy', 'createdAt', 'lastJoinCode'])
          && data().memberUids == resource.data.memberUids
          && archiveIsOwn();
      }
      function leave() {
        return uid() in resource.data.memberUids && nextVersion()
          && changedKeys().hasOnly(['memberUids', 'version', 'updatedAt', 'updatedBy'])
          && data().memberUids.toSet() == resource.data.memberUids.toSet().difference([uid()].toSet());
      }
      // Written with arrayUnion/increment/serverTimestamp: the joiner cannot read the group.
      function join() {
        return !(uid() in resource.data.memberUids) && nextVersion()
          && changedKeys().hasOnly(['memberUids', 'lastJoinCode', 'version', 'updatedAt', 'updatedBy'])
          && data().lastJoinCode == resource.data.inviteCode
          && data().memberUids == resource.data.memberUids.concat([uid()])
          && data().memberUids.size() <= 50;
      }

      allow read: if signedIn() && uid() in resource.data.memberUids;
      allow create: if signedIn() && validGroup() && created()
        && data().memberUids == [uid()] && data().createdBy == uid() && data().archivedBy == [];
      allow update: if signedIn() && (editAsMember() || leave() || join());
      allow delete: if false;

      // ── Members ────────────────────────────────────────────────────────────
      match /members/{memberId} {
        function validMember() {
          return data().keys().hasOnly(['name', 'avatar', 'defaultWeight', 'userUid', 'active', 'sortKey',
              'version', 'updatedAt', 'updatedBy', 'deleted'])
            && isText(data().name, 1, 40) && isOptText(data().avatar, 300)
            && data().defaultWeight is int && data().defaultWeight >= 1 && data().defaultWeight <= 100
            && (data().userUid == null || data().userUid is string)
            && data().active is bool && data().sortKey is int && isMeta(data());
        }
        // A member can be claimed (null → me) or released (me → null), never taken from someone else.
        function claimIsOwn() {
          return !changedKeys().hasAny(['userUid'])
            || (resource.data.userUid == null && data().userUid == uid())
            || (resource.data.userUid == uid() && data().userUid == null);
        }

        allow read: if isMemberOf(groupId);
        allow create: if isMemberOf(groupId) && validMember() && created()
          && (data().userUid == null || data().userUid == uid());
        allow update: if isMemberOf(groupId) && validMember() && nextVersion() && claimIsOwn();
        allow delete: if false;
      }

      // ── Expenses ───────────────────────────────────────────────────────────
      match /expenses/{expenseId} {
        // Sums (payers = amount, exact shares = amount) cannot be checked without loops:
        // the client guarantees them and ObserveGroupBalances skips inconsistent entries.
        function validExpense() {
          return data().keys().hasOnly(['title', 'category', 'note', 'date', 'currency', 'amountMinor',
              'fxRateScaled', 'payers', 'splitType', 'splitValues', 'receiptUrl', 'recurrenceId',
              'createdBy', 'createdAt', 'version', 'updatedAt', 'updatedBy', 'deleted'])
            && isText(data().title, 1, 80)
            && data().category in ['FOOD', 'GROCERIES', 'TRANSPORT', 'ACCOMMODATION', 'LEISURE', 'SHOPPING',
              'HOME', 'UTILITIES', 'HEALTH', 'GIFTS', 'OTHER']
            && isOptText(data().note, 500) && isDate(data().date) && isCurrency(data().currency)
            && isAmount(data().amountMinor) && isRate(data().fxRateScaled)
            && isMemberMap(data().payers) && isMemberMap(data().splitValues)
            && data().splitType in ['EQUAL', 'WEIGHTS', 'EXACT', 'PERCENT']
            && isOptText(data().receiptUrl, 500) && isOptText(data().recurrenceId, 100) && isMeta(data());
        }

        allow read: if isMemberOf(groupId);
        allow create: if isMemberOf(groupId) && validExpense() && created() && data().createdBy == uid();
        allow update: if isMemberOf(groupId) && validExpense() && nextVersion()
          && !changedKeys().hasAny(['createdBy', 'createdAt']);
        allow delete: if false;
      }

      // ── Transfers ──────────────────────────────────────────────────────────
      match /transfers/{transferId} {
        function validTransfer() {
          return data().keys().hasOnly(['fromMemberId', 'toMemberId', 'amountMinor', 'currency', 'fxRateScaled',
              'date', 'note', 'createdBy', 'createdAt', 'version', 'updatedAt', 'updatedBy', 'deleted'])
            && data().fromMemberId is string && data().toMemberId is string
            && data().fromMemberId != data().toMemberId
            && isAmount(data().amountMinor) && isCurrency(data().currency) && isRate(data().fxRateScaled)
            && isDate(data().date) && isOptText(data().note, 500) && isMeta(data());
        }

        allow read: if isMemberOf(groupId);
        allow create: if isMemberOf(groupId) && validTransfer() && created() && data().createdBy == uid();
        allow update: if isMemberOf(groupId) && validTransfer() && nextVersion()
          && !changedKeys().hasAny(['createdBy', 'createdAt', 'fromMemberId', 'toMemberId', 'amountMinor']);
        allow delete: if false;
      }

      // ── History (create-only) ──────────────────────────────────────────────
      match /changelog/{entryId} {
        allow read: if isMemberOf(groupId);
        allow create: if isMemberOf(groupId)
          && data().keys().hasOnly(['entityType', 'entityId', 'action', 'authorUid', 'diff', 'createdAt'])
          && data().entityType in ['GROUP', 'MEMBER', 'EXPENSE', 'TRANSFER']
          && data().action in ['CREATED', 'UPDATED', 'DELETED', 'RESTORED', 'JOINED', 'CONFLICT']
          && data().authorUid == uid() && data().entityId is string
          && data().diff is list && data().diff.size() <= 20;
        allow update, delete: if false;
      }
    }

    // ── Invites: doc id = invite code ────────────────────────────────────────
    match /invites/{code} {
      function validInvite() {
        return data().keys().hasOnly(['groupId', 'groupName', 'groupIcon', 'memberCount', 'createdBy', 'createdAt'])
          && data().groupId is string && isText(data().groupName, 1, 60) && isText(data().groupIcon, 1, 40)
          && data().memberCount is int && data().createdBy == uid();
      }

      allow get: if signedIn();
      allow list: if false;
      allow create: if validInvite() && isMemberOf(data().groupId) && group(data().groupId).data.inviteCode == code;
      allow update: if validInvite() && isMemberOf(resource.data.groupId) && data().groupId == resource.data.groupId;
      allow delete: if isMemberOf(resource.data.groupId);
    }

    // ── P2 smoke test (kept to re-run it when GitLive is upgraded) ───────────
    match /smoke/{userId} {
      allow read, write: if signedIn() && uid() == userId;
    }
  }
}
```

- [ ] **Step 2: Check that they compile**

```bash
cd firebase && npx --yes firebase-tools@15.32.1 emulators:exec --only firestore --project demo-owlsplit "echo rules ok" 2>&1 | tail -3; cd ..
```
Expected: `rules ok` and no `Error compiling rules`.

- [ ] **Step 3: Commit**

```bash
git add firebase/firestore.rules
git commit -m "feat(rules): Firestore security rules for groups, members, expenses, transfers, history and invites"
```

### Task 3: Group tests

`firebase/rules-test/test/groups.test.mjs`:
```js
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp, arrayRemove, arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, increment, query,
  serverTimestamp, setDoc, updateDoc, where,
} from 'firebase/firestore';
import { CODE, GROUP, as, createEnv, groupData, seed } from './helpers.mjs';

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }) });
});

const bump = (by) => ({ version: increment(1), updatedAt: serverTimestamp(), updatedBy: by });

describe('create', () => {
  test('a user creates a group with only themselves as member', async () => {
    const data = groupData({ version: 1, updatedAt: serverTimestamp(), updatedBy: 'ana' });
    await assertSucceeds(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
  test('cannot create a group for someone else', async () => {
    const data = groupData({ memberUids: ['ana', 'zoe'], updatedAt: serverTimestamp() });
    await assertFails(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
  test('the client cannot choose updatedAt', async () => {
    const data = groupData({ updatedAt: Timestamp.now() });
    await assertFails(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
  test('extra fields are rejected', async () => {
    const data = groupData({ updatedAt: serverTimestamp(), owner: 'ana' });
    await assertFails(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
});

describe('read', () => {
  test('members read, strangers do not', async () => {
    await assertSucceeds(getDoc(doc(as(env, 'luis'), `groups/${GROUP}`)));
    await assertFails(getDoc(doc(as(env, 'zoe'), `groups/${GROUP}`)));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `groups/${GROUP}`)));
  });
  test('a member can list their groups with array-contains', async () => {
    const q = query(collection(as(env, 'ana'), 'groups'), where('memberUids', 'array-contains', 'ana'));
    await assertSucceeds(getDocs(q));
  });
});

describe('edit', () => {
  test('a member renames with a chained version', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { name: 'Ibiza', ...bump('luis') }));
  });
  test('a stale version is rejected', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), {
      name: 'Ibiza', version: 1, updatedAt: serverTimestamp(), updatedBy: 'luis',
    }));
  });
  test('updatedBy must be the caller', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { name: 'Ibiza', ...bump('ana') }));
  });
  test('a member cannot remove someone else', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { memberUids: ['luis'], ...bump('luis') }));
  });
  test('archiving touches only your own uid', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { archivedBy: arrayUnion('luis'), ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { archivedBy: arrayUnion('ana'), ...bump('luis') }));
  });
  test('groups are never deleted', async () => {
    await assertFails(deleteDoc(doc(as(env, 'ana'), `groups/${GROUP}`)));
  });
});

describe('join and leave', () => {
  test('a stranger joins with the current code', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('zoe'), lastJoinCode: CODE, ...bump('zoe'),
    }));
  });
  test('a wrong code is rejected', async () => {
    await assertFails(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('zoe'), lastJoinCode: 'WRONG234', ...bump('zoe'),
    }));
  });
  test('a joiner cannot add someone else or change anything else', async () => {
    await assertFails(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('max'), lastJoinCode: CODE, ...bump('zoe'),
    }));
    await assertFails(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('zoe'), lastJoinCode: CODE, name: 'Mío', ...bump('zoe'),
    }));
  });
  test('a member leaves by removing only themselves', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { memberUids: arrayRemove('luis'), ...bump('luis') }));
  });
});
```

- [ ] **Step 1:** Write the file.
- [ ] **Step 2: Run**

```bash
cd firebase && npx --yes firebase-tools@15.32.1 emulators:exec --only firestore --project demo-owlsplit "npm --prefix rules-test test" 2>&1 | tail -15; cd ..
```
Expected: `groups.test.mjs` 16 tests green. If any fails, read the reason given by the emulator (`evaluation error at L…`) and fix the rule, not the test, unless the test contradicts FIREBASE.md.

- [ ] **Step 3: Commit**

```bash
git add firebase/rules-test/test/groups.test.mjs
git commit -m "test(rules): groups — create, read, edit, archive, join and leave"
```

### Task 4: Subcollection tests

`firebase/rules-test/test/subcollections.test.mjs`:
```js
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, increment, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { GROUP, as, createEnv, expenseData, groupData, memberData, seed, transferData } from './helpers.mjs';

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, {
    [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }),
    [`groups/${GROUP}/members/m1`]: memberData(),
    [`groups/${GROUP}/members/ghost`]: memberData({ name: 'Marta', userUid: null, sortKey: 1 }),
    [`groups/${GROUP}/expenses/e1`]: expenseData(),
  });
});

const fresh = (by) => ({ version: 1, updatedAt: serverTimestamp(), updatedBy: by });
const bump = (by) => ({ version: increment(1), updatedAt: serverTimestamp(), updatedBy: by });
const path = (p) => `groups/${GROUP}/${p}`;

describe('members', () => {
  test('a member adds a ghost member', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'luis'), path('members/m3')), memberData({ name: 'Zoe', userUid: null, sortKey: 2, ...fresh('luis') })));
  });
  test('a stranger cannot add members', async () => {
    await assertFails(setDoc(doc(as(env, 'zoe'), path('members/m3')), memberData({ userUid: null, ...fresh('zoe') })));
  });
  test('a ghost can be claimed by the caller', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), path('members/ghost')), { userUid: 'luis', ...bump('luis') }));
  });
  test('nobody takes a member claimed by someone else', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { userUid: 'luis', ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { userUid: null, ...bump('luis') }));
  });
  test('weights stay within 1..100', async () => {
    await assertFails(updateDoc(doc(as(env, 'ana'), path('members/m1')), { defaultWeight: 0, ...bump('ana') }));
  });
});

describe('expenses', () => {
  test('a member creates a valid expense', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'luis'), path('expenses/e2')), expenseData({ createdBy: 'luis', ...fresh('luis') })));
  });
  test('invalid amounts, currencies, categories and dates are rejected', async () => {
    const db = as(env, 'ana');
    for (const bad of [{ amountMinor: 0 }, { amountMinor: 1.5 }, { currency: 'eur' }, { category: 'BEER' }, { date: '12/07/2026' }, { payers: {} }]) {
      await assertFails(setDoc(doc(db, path('expenses/e9')), expenseData({ ...bad, ...fresh('ana') })));
    }
  });
  test('strangers cannot read expenses', async () => {
    await assertFails(getDoc(doc(as(env, 'zoe'), path('expenses/e1'))));
  });
  test('editing keeps the author and chains the version', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), path('expenses/e1')), { title: 'Cena larga', ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('expenses/e1')), { createdBy: 'luis', ...bump('luis') }));
  });
  test('soft delete is an update; hard delete is forbidden', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), path('expenses/e1')), { deleted: true, ...bump('ana') }));
    await assertFails(deleteDoc(doc(as(env, 'ana'), path('expenses/e1'))));
  });
});

describe('transfers', () => {
  test('a member records a transfer between two different members', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'ana'), path('transfers/t1')), transferData(fresh('ana'))));
    await assertFails(setDoc(doc(as(env, 'ana'), path('transfers/t2')), transferData({ toMemberId: 'm2', ...fresh('ana') })));
  });
});
```

- [ ] **Step 1:** Write the file.
- [ ] **Step 2:** Run the same command as in Task 3. Expected: 11 more tests green.
- [ ] **Step 3: Commit**

```bash
git add firebase/rules-test/test/subcollections.test.mjs
git commit -m "test(rules): members, expenses and transfers"
```

### Task 5: History, invites and users

`firebase/rules-test/test/misc.test.mjs`:
```js
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore';
import { CODE, GROUP, as, createEnv, groupData, seed } from './helpers.mjs';

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, {
    [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }),
    [`invites/${CODE}`]: { groupId: GROUP, groupName: 'Ibiza 2026', groupIcon: 'plane', memberCount: 2, createdBy: 'ana', createdAt: 1 },
  });
});

const entry = (author) => ({
  entityType: 'EXPENSE', entityId: 'e1', action: 'CREATED', authorUid: author, diff: [], createdAt: 1,
});

describe('changelog', () => {
  test('members append history as themselves, never edit it', async () => {
    const ref = doc(as(env, 'luis'), `groups/${GROUP}/changelog/c1`);
    await assertSucceeds(setDoc(ref, entry('luis')));
    await assertFails(updateDoc(ref, { action: 'DELETED' }));
    await assertFails(deleteDoc(ref));
  });
  test('history cannot be written in someone else\'s name', async () => {
    await assertFails(setDoc(doc(as(env, 'luis'), `groups/${GROUP}/changelog/c2`), entry('ana')));
  });
});

describe('invites', () => {
  test('any signed-in user can open an invite by code but not list them', async () => {
    await assertSucceeds(getDoc(doc(as(env, 'zoe'), `invites/${CODE}`)));
    await assertFails(getDocs(collection(as(env, 'zoe'), 'invites')));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `invites/${CODE}`)));
  });
  test('only members create the invite of the group current code', async () => {
    const data = { groupId: GROUP, groupName: 'Ibiza 2026', groupIcon: 'plane', memberCount: 2, createdBy: 'luis', createdAt: 2 };
    await env.clearFirestore();
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }) });
    await assertSucceeds(setDoc(doc(as(env, 'luis'), `invites/${CODE}`), data));
    await assertFails(setDoc(doc(as(env, 'luis'), 'invites/OTHER234'), data));
    await assertFails(setDoc(doc(as(env, 'zoe'), `invites/${CODE}`), { ...data, createdBy: 'zoe' }));
  });
});

describe('users', () => {
  test('a user reads and writes only their own profile', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'ana'), 'users/ana'), { displayName: 'Ana' }));
    await assertFails(getDoc(doc(as(env, 'luis'), 'users/ana')));
  });
});
```

- [ ] **Step 1:** Write the file.
- [ ] **Step 2:** Run. Expected: all three files green (16 + 11 + 5 = 32 tests).
- [ ] **Step 3: Commit**

```bash
git add firebase/rules-test/test/misc.test.mjs
git commit -m "test(rules): history, invites and user profiles"
```

### Task 6: CI, deployment to dev and documentation

- [ ] **Step 1: CI job**

In `.github/workflows/ci.yml`, add:
```yaml
  firestore-rules:
    name: Firestore rules (emulator)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 21 }
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: firebase/rules-test/package-lock.json
      - run: npm ci --prefix firebase/rules-test
      - working-directory: firebase
        run: npx --yes firebase-tools@15.32.1 emulators:exec --only firestore --project demo-owlsplit "npm --prefix rules-test test"
```

- [ ] **Step 2: Deploy to dev**

```bash
cd firebase && npx --yes firebase-tools@15.32.1 deploy --only firestore --project dev; cd ..
./scripts/run-firebase-smoke.sh 2>&1 | grep -E "BUILD|Finished"
```
Expected: `Deploy complete!` and the smoke test still passes (the `smoke/{uid}` rule is kept).

- [ ] **Step 3: Documentation**

- `docs/FIREBASE.md`, section "Rules": the join is written with `arrayUnion`/`increment`/`serverTimestamp`; the rules validate shape, types and ranges, **not** the sums (guaranteed by the client and defended by `ObserveGroupBalances`); `invites` allows `get` but not `list`; nothing is deleted except invites; the tests are in `firebase/rules-test` (`npm test` inside `emulators:exec`).
- `docs/plans/README.md`: P5 → `Done`.

- [ ] **Step 4: Commit, push and PR**

```bash
git add -A
git commit -m "ci: Firestore rules tests on the emulator; docs: rules as implemented"
git push -u origin p5/firestore-rules
gh pr create --title "P5: Firestore rules" --body "Implements docs/plans/2026-10-07-p05-firestore-rules.md"
```

## Final verification of P5

- 32 rules tests green locally and in the `Firestore rules (emulator)` job.
- Rules deployed to `owlsplit-dev`; the P2 smoke test is still green.
