// The web for guests writes with these payloads (hosting/js/payloads.js): the rules must accept them,
// with the history entry in the same batch, for a guest who joins with the code.
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { arrayUnion, doc, getDoc, increment, serverTimestamp, updateDoc, writeBatch } from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { CODE, GROUP, as, createEnv, groupData, memberData, seed } from './helpers.mjs';
import {
  changelogEntry, claimMember, defaultSplit, joinGroup, newExpense, newMember, newTransfer,
} from '../../hosting/js/payloads.js';

const f = { serverTimestamp, arrayUnion, increment };
let env;

beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, {
    [`groups/${GROUP}`]: groupData(),
    [`groups/${GROUP}/members/m1`]: memberData(),
    // Added by Ana for Zoe, without account: Zoe claims it from the web.
    [`groups/${GROUP}/members/m2`]: memberData({ name: 'Zoe', userUid: null, sortKey: 1 }),
    [`invites/${CODE}`]: { groupId: GROUP, groupName: 'Ibiza 2026', groupIcon: 'plane', memberCount: 2, createdBy: 'ana', createdAt: 1 },
  });
});

async function join(uid) {
  const db = as(env, uid);
  await assertSucceeds(updateDoc(doc(db, `groups/${GROUP}`), joinGroup(f, uid, CODE)));
  return db;
}

describe('web guests', () => {
  test('a guest reads the invite, joins and claims the member left for them', async () => {
    const db = as(env, 'zoe');
    await assertSucceeds(getDoc(doc(db, `invites/${CODE}`)));
    await join('zoe');
    const batch = writeBatch(db);
    batch.update(doc(db, `groups/${GROUP}/members/m2`), claimMember(f, 'zoe', { version: 1 }));
    batch.set(doc(db, `groups/${GROUP}/changelog/c1`), changelogEntry(f, 'zoe', {
      entityType: 'MEMBER', entityId: 'm2', action: 'JOINED', diff: [{ field: 'userUid', before: null, after: 'zoe' }], now: 2,
    }));
    await assertSucceeds(batch.commit());
  });

  test('or adds themselves as new', async () => {
    const db = await join('max');
    const batch = writeBatch(db);
    batch.set(doc(db, `groups/${GROUP}/members/m3`), newMember(f, 'max', ' Max ', 2));
    batch.set(doc(db, `groups/${GROUP}/changelog/c1`), changelogEntry(f, 'max', { entityType: 'MEMBER', entityId: 'm3', action: 'JOINED', now: 2 }));
    await assertSucceeds(batch.commit());
  });

  test('adds an expense and records a payment, each with its history entry', async () => {
    const db = await join('zoe');
    const split = defaultSplit([{ id: 'm1', defaultWeight: 1 }, { id: 'm2', defaultWeight: 1 }], ['m1', 'm2']);
    const expense = writeBatch(db);
    expense.set(doc(db, `groups/${GROUP}/expenses/e1`), newExpense(f, 'zoe', {
      title: 'Taxi', category: 'TRANSPORT', date: '2026-10-16', currency: 'EUR', amountMinor: 2450, payerId: 'm2', ...split, now: 3,
    }));
    expense.set(doc(db, `groups/${GROUP}/changelog/c2`), changelogEntry(f, 'zoe', { entityType: 'EXPENSE', entityId: 'e1', action: 'CREATED', now: 3 }));
    await assertSucceeds(expense.commit());

    const payment = writeBatch(db);
    payment.set(doc(db, `groups/${GROUP}/transfers/t1`), newTransfer(f, 'zoe', {
      fromMemberId: 'm1', toMemberId: 'm2', amountMinor: 1225, currency: 'EUR', date: '2026-10-16', now: 4,
    }));
    payment.set(doc(db, `groups/${GROUP}/changelog/c3`), changelogEntry(f, 'zoe', { entityType: 'TRANSFER', entityId: 't1', action: 'CREATED', now: 4 }));
    await assertSucceeds(payment.commit());
  });

  test('a guest who has not joined writes nothing', async () => {
    const db = as(env, 'eve');
    await assertFails(updateDoc(doc(db, `groups/${GROUP}/members/m2`), claimMember(f, 'eve', { version: 1 })));
    await assertFails(updateDoc(doc(db, `groups/${GROUP}`), joinGroup(f, 'eve', 'WRONG234')));
  });

  test('weights decide the split when they differ', () => {
    const split = defaultSplit([{ id: 'a', defaultWeight: 3 }, { id: 'b', defaultWeight: 2 }], ['a', 'b']);
    if (split.splitType !== 'WEIGHTS' || split.splitValues.a !== 3) throw new Error(JSON.stringify(split));
  });
});
