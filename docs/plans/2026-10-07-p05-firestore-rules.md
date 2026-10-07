# P5 · Reglas de Firestore — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Sustituir las reglas temporales de P2 por las reales de [FIREBASE.md](../FIREBASE.md): acceso solo para miembros, unirse con el código vigente, salir, reclamar miembros, versiones encadenadas con hora del servidor, validación de forma de cada documento e historial de solo creación. Todo probado con el emulador, en local y en CI, y desplegado a `owlsplit-dev`.

**Architecture:** Un único `firebase/firestore.rules` con funciones auxiliares. Las pruebas viven en `firebase/rules-test` (Node + Vitest + `@firebase/rules-unit-testing`) y corren dentro de `firebase emulators:exec` contra un proyecto `demo-owlsplit` (sin credenciales). CI añade un job con Java 21 + Node. El despliegue a producción con CI llega en P11; aquí se despliega a dev con la sesión del usuario.

**Tech Stack:** Firestore Security Rules v2, firebase-tools 15.32.1, `@firebase/rules-unit-testing` 5.0.2, `firebase` 12.19.0 (SDK web, solo para los tests), Vitest 5.0.3, Node 24 en local / 22 en CI, JDK 21 (emulador).

**Platform:** Backend (Firebase); afecta a Android e iOS por igual.

**Depende de:** P2 (proyecto y `firebase/`).

---

## Decisiones de este plan

1. **Unirse sin poder leer el grupo.** Un no miembro no puede leer el grupo, así que no conoce `version` ni `memberUids`. La unión se escribe con transformaciones del servidor: `memberUids: arrayUnion(uid)`, `version: increment(1)`, `updatedAt: serverTimestamp()`, `updatedBy: uid`, `lastJoinCode: código`. Las reglas validan el documento resultante. P10 lo implementa así en el cliente.
2. **Sumas no verificables en reglas.** Las reglas no tienen bucles, así que no pueden sumar los valores de `payers` o `splitValues`. Validan tipos, rangos y tamaños; las sumas las garantiza el cliente (dominio de P3) y, si llegara un gasto inconsistente, `ObserveGroupBalances` (P4) lo deja fuera. Se corrige [FIREBASE.md](../FIREBASE.md), que decía lo contrario.
3. **Sin borrados.** Ningún documento se borra desde el cliente (borrado lógico con `deleted`), salvo `invites/{code}` al regenerar el código.
4. **El documento `smoke/{uid}` de P2 se mantiene** para poder repetir la prueba de humo al actualizar GitLive.
5. **Esquema de los documentos** (lo que P6 escribirá): los mismos campos que `data:models`, con `updatedAt` como timestamp del servidor y sin el campo local `syncState`/`serverVersion`.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Reescribir | `firebase/firestore.rules` |
| Crear | `firebase/rules-test/package.json`, `package-lock.json` (generado), `vitest.config.mjs`, `test/helpers.mjs`, `test/groups.test.mjs`, `test/subcollections.test.mjs`, `test/misc.test.mjs` |
| Modificar | `firebase/firebase.json` (ui del emulador desactivada), `.gitignore` (`node_modules`, logs del emulador), `.github/workflows/ci.yml`, `docs/FIREBASE.md`, `docs/plans/README.md` |

---

### Task 1: Entorno de pruebas

- [ ] **Step 1: Paquete de pruebas**

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
Expected: `package-lock.json` creado, sin vulnerabilidades altas.

- [ ] **Step 2: Emulador sin UI y `.gitignore`**

En `firebase/firebase.json`, dentro de `emulators`, añadir `"ui": { "enabled": false }`. En `.gitignore` añadir:
```
node_modules/
firebase/*-debug.log
```

- [ ] **Step 3: Ayudas de test**

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

### Task 2: Reglas

- [ ] **Step 1: Escribir las reglas**

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

- [ ] **Step 2: Comprobar que compilan**

```bash
cd firebase && npx --yes firebase-tools@15.32.1 emulators:exec --only firestore --project demo-owlsplit "echo rules ok" 2>&1 | tail -3; cd ..
```
Expected: `rules ok` y sin `Error compiling rules`.

- [ ] **Step 3: Commit**

```bash
git add firebase/firestore.rules
git commit -m "feat(rules): Firestore security rules for groups, members, expenses, transfers, history and invites"
```

### Task 3: Tests de grupos

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

- [ ] **Step 1:** Escribir el archivo.
- [ ] **Step 2: Ejecutar**

```bash
cd firebase && npx --yes firebase-tools@15.32.1 emulators:exec --only firestore --project demo-owlsplit "npm --prefix rules-test test" 2>&1 | tail -15; cd ..
```
Expected: `groups.test.mjs` 16 tests en verde. Si alguno falla, leer el motivo que da el emulador (`evaluation error at L…`) y corregir la regla, no el test, salvo que el test contradiga FIREBASE.md.

- [ ] **Step 3: Commit**

```bash
git add firebase/rules-test/test/groups.test.mjs
git commit -m "test(rules): groups — create, read, edit, archive, join and leave"
```

### Task 4: Tests de subcolecciones

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

- [ ] **Step 1:** Escribir el archivo.
- [ ] **Step 2:** Ejecutar el mismo comando que en la Tarea 3. Expected: 11 tests más en verde.
- [ ] **Step 3: Commit**

```bash
git add firebase/rules-test/test/subcollections.test.mjs
git commit -m "test(rules): members, expenses and transfers"
```

### Task 5: Historial, invitaciones y usuarios

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

- [ ] **Step 1:** Escribir el archivo.
- [ ] **Step 2:** Ejecutar. Expected: los tres archivos en verde (16 + 11 + 5 = 32 tests).
- [ ] **Step 3: Commit**

```bash
git add firebase/rules-test/test/misc.test.mjs
git commit -m "test(rules): history, invites and user profiles"
```

### Task 6: CI, despliegue a dev y documentación

- [ ] **Step 1: Job de CI**

En `.github/workflows/ci.yml`, añadir:
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

- [ ] **Step 2: Desplegar a dev**

```bash
cd firebase && npx --yes firebase-tools@15.32.1 deploy --only firestore --project dev; cd ..
./scripts/run-firebase-smoke.sh 2>&1 | grep -E "BUILD|Finished"
```
Expected: `Deploy complete!` y la prueba de humo sigue pasando (la regla `smoke/{uid}` se conserva).

- [ ] **Step 3: Documentación**

- `docs/FIREBASE.md`, sección "Reglas": la unión se escribe con `arrayUnion`/`increment`/`serverTimestamp`; las reglas validan forma, tipos y rangos, **no** las sumas (garantizadas por el cliente y defendidas por `ObserveGroupBalances`); `invites` permite `get` pero no `list`; nada se borra salvo invitaciones; los tests están en `firebase/rules-test` (`npm test` dentro de `emulators:exec`).
- `docs/plans/README.md`: P5 → `Hecho`.

- [ ] **Step 4: Commit, push y PR**

```bash
git add -A
git commit -m "ci: Firestore rules tests on the emulator; docs: rules as implemented"
git push -u origin p5/firestore-rules
gh pr create --title "P5: Firestore rules" --body "Implements docs/plans/2026-10-07-p05-firestore-rules.md"
```

## Verificación final de P5

- 32 tests de reglas en verde en local y en el job `Firestore rules (emulator)`.
- Reglas desplegadas en `owlsplit-dev`; la prueba de humo de P2 sigue en verde.
