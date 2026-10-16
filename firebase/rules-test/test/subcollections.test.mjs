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
    await assertSucceeds(setDoc(doc(as(env, 'luis'), path('members/m3')),
      memberData({ name: 'Zoe', userUid: null, sortKey: 2, ...fresh('luis') })));
  });
  test('a stranger cannot add members', async () => {
    await assertFails(setDoc(doc(as(env, 'zoe'), path('members/m3')), memberData({ userUid: null, ...fresh('zoe') })));
  });
  test('a ghost can be claimed by the caller', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), path('members/ghost')), { userUid: 'luis', ...bump('luis') }));
  });
  test('nobody takes or releases a member claimed by someone else', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { userUid: 'luis', ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { userUid: null, ...bump('luis') }));
  });
  test('only the person behind a member publishes how to pay them', async () => {
    const pay = { bizum: '600111222', paypal: 'ana', revolut: null };
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), path('members/m1')), { payMethods: pay, ...bump('ana') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { payMethods: { bizum: '699000000' }, ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/ghost')), { payMethods: pay, ...bump('luis') }));
    // Anyone may still edit the rest of the member, and releasing clears them.
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), path('members/m1')), { name: 'Ana G.', ...bump('luis') }));
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), path('members/m1')), { userUid: null, payMethods: null, ...bump('ana') }));
  });
  test('pay methods have a fixed shape', async () => {
    const db = as(env, 'ana');
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: { iban: 'ES00' }, ...bump('ana') }));
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: { bizum: '0'.repeat(21) }, ...bump('ana') }));
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: 'bizum', ...bump('ana') }));
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: { swift: 'X' }, ...bump('ana') }));
  });
  test('an IBAN goes compact, in capitals, with an optional holder', async () => {
    const db = as(env, 'ana');
    const iban = { iban: 'ES9121000418450200051332', ibanHolder: 'Ana García López' };
    await assertSucceeds(updateDoc(doc(db, path('members/m1')), { payMethods: iban, ...bump('ana') }));
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: { iban: 'es91 2100 0418 4502 0005 1332' }, ...bump('ana') }));
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: { iban: 'ES00' }, ...bump('ana') }));
    await assertFails(updateDoc(doc(db, path('members/m1')), { payMethods: { ...iban, ibanHolder: 'A'.repeat(71) }, ...bump('ana') }));
    // Only the person behind the member publishes it, like the rest.
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { payMethods: { iban: 'DE89370400440532013000' }, ...bump('luis') }));
  });
  test('leaving adds my own uid to pastUids, and nothing else can change it', async () => {
    const ana = as(env, 'ana');
    // Releasing my member: userUid → null and my uid appended.
    await assertSucceeds(updateDoc(doc(ana, path('members/m1')), { userUid: null, pastUids: ['ana'], ...bump('ana') }));
    // Nobody rewrites or extends it afterwards (not even with their own uid without releasing).
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { pastUids: [], ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/m1')), { pastUids: ['ana', 'luis'], ...bump('luis') }));
    // Claiming it back keeps the list.
    await assertSucceeds(updateDoc(doc(ana, path('members/m1')), { userUid: 'ana', ...bump('ana') }));
  });
  test('releasing cannot write someone else into pastUids', async () => {
    await assertFails(updateDoc(doc(as(env, 'ana'), path('members/m1')), { userUid: null, pastUids: ['luis'], ...bump('ana') }));
    await assertFails(setDoc(doc(as(env, 'luis'), path('members/m9')),
      memberData({ name: 'Zoe', userUid: null, sortKey: 3, pastUids: ['ana'], ...fresh('luis') })));
  });
  test('only the person behind a member marks it as linked to Google', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), path('members/m1')), { linked: true, ...bump('ana') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), path('members/ghost')), { linked: true, ...bump('luis') }));
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), path('members/m1')), { userUid: null, linked: false, pastUids: ['ana'], ...bump('ana') }));
  });
  test('weights stay within 1..100', async () => {
    await assertFails(updateDoc(doc(as(env, 'ana'), path('members/m1')), { defaultWeight: 0, ...bump('ana') }));
  });
});

describe('expenses', () => {
  test('a member creates a valid expense', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'luis'), path('expenses/e2')), expenseData({ createdBy: 'luis', ...fresh('luis') })));
  });
  test('invalid amounts, currencies, categories, dates and empty payers are rejected', async () => {
    const db = as(env, 'ana');
    const bads = [
      { amountMinor: 0 }, { amountMinor: 1.5 }, { currency: 'eur' }, { category: 'BEER' },
      { date: '12/07/2026' }, { payers: {} },
    ];
    for (const bad of bads) {
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
