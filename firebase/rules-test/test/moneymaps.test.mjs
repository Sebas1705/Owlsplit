// payers and splitValues are maps of member id -> whole number. The rules cannot loop over a map, so they
// check it as text (isMemberMap in firestore.rules); these tests pin what it accepts and what it refuses.
// Floats, negatives, strings and bigger-than-allowed values would otherwise break every balance that
// reads the expense (hosting/js/money.js turned a float into a RangeError).
import { afterAll, beforeAll, beforeEach, describe, expect, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';
import { GROUP, as, createEnv, expenseData, groupData, memberData, seed, transferData } from './helpers.mjs';
import { groupBalances } from '../../hosting/js/money.js';

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, {
    [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }),
    [`groups/${GROUP}/members/m1`]: memberData(),
    [`groups/${GROUP}/members/m2`]: memberData({ name: 'Luis', userUid: 'luis', sortKey: 1 }),
    [`groups/${GROUP}/expenses/e1`]: expenseData(),
  });
});

const fresh = (by = 'ana') => ({ version: 1, updatedAt: serverTimestamp(), updatedBy: by });
const path = (p) => `groups/${GROUP}/${p}`;
let next = 0;
const create = (overrides) => setDoc(doc(as(env, 'ana'), path('expenses/n' + next++)), expenseData({ ...overrides, ...fresh() }));
// `count` ids shaped like the app's (UUIDs), each with `value`.
const ids = (count, value = 1) => Object.fromEntries(
  Array.from({ length: count }, (_, i) => [`${String(i).padStart(8, '0')}-0000-4000-8000-000000000000`, value]));

describe('payers and splitValues: what is accepted', () => {
  test('one payer, several payers and zero shares', async () => {
    await assertSucceeds(create({ payers: { m1: 4860 } }));
    await assertSucceeds(create({ payers: { m1: 3000, m2: 1860 } }));
    await assertSucceeds(create({ splitValues: { m1: 1, m2: 0 } }));
  });
  test('every split type with its usual values', async () => {
    await assertSucceeds(create({ splitType: 'WEIGHTS', splitValues: { m1: 60, m2: 40 } }));
    await assertSucceeds(create({ splitType: 'PERCENT', splitValues: { m1: 6000, m2: 4000 } }));
    await assertSucceeds(create({ splitType: 'EXACT', splitValues: { m1: 3000, m2: 1860 } }));
  });
  test('the largest amount, 10^12, as payer and as exact share', async () => {
    await assertSucceeds(create({
      amountMinor: 1_000_000_000_000, payers: { m1: 1_000_000_000_000 },
      splitType: 'EXACT', splitValues: { m1: 1_000_000_000_000 },
    }));
  });
  test('50 payers and 50 shares, the most a group has', async () => {
    await assertSucceeds(create({ amountMinor: 50, payers: ids(50), splitValues: ids(50) }));
  });
  test('ids with letters, digits, dash and underscore', async () => {
    await assertSucceeds(create({ payers: { 'Ab_9-z': 4860 }, splitValues: { 'Ab_9-z': 1 } }));
  });
  test('a full replace of an existing expense keeps working', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'luis'), path('expenses/e1')),
      expenseData({ payers: { m2: 4860 }, splitValues: { m2: 1 }, version: 2, updatedAt: serverTimestamp(), updatedBy: 'luis' })));
  });
});

describe('payers and splitValues: what is refused', () => {
  const bad = {
    'a fraction': 1.5,
    'a fraction just above a whole number': 4860.000001,
    'a negative number': -5,
    'a text of digits': '4860',
    'a text': 'lots',
    'a boolean': true,
    'null': null,
    'a list': [4860],
    'a map': { amount: 4860 },
    'more than 10^12': 1_000_000_000_001,
    'a number with an exponent': 1e21,
  };
  for (const [name, value] of Object.entries(bad)) {
    test(`${name} as a payer`, async () => {
      await assertFails(create({ payers: { m1: value } }));
    });
    test(`${name} as a split value`, async () => {
      await assertFails(create({ splitValues: { m1: value } }));
    });
  }
  test('one bad value among good ones, in either map', async () => {
    await assertFails(create({ payers: { m1: 4000, m2: 860.5 } }));
    await assertFails(create({ splitValues: { m1: 1, m2: -1 } }));
    await assertFails(create({ splitValues: { m1: 1, m2: '1' } }));
  });
  test('a bad value on an edit of a good expense', async () => {
    const edit = (data) => setDoc(doc(as(env, 'luis'), path('expenses/e1')),
      expenseData({ ...data, version: 2, updatedAt: serverTimestamp(), updatedBy: 'luis' }));
    await assertFails(edit({ payers: { m1: 1.5 } }));
    await assertFails(edit({ splitValues: { m1: 1, m2: 0.5 } }));
  });
  test('more than 50 entries', async () => {
    await assertFails(create({ amountMinor: 51, payers: ids(51) }));
    await assertFails(create({ splitValues: ids(51) }));
  });
  test('empty maps', async () => {
    await assertFails(create({ payers: {} }));
    await assertFails(create({ splitValues: {} }));
  });
  test('keys that are not member ids: too long, with spaces, commas, slashes or other characters', async () => {
    for (const key of ['x'.repeat(101), 'two words', 'a,b', 'a/b', 'añ', 'a.b', '<script>']) {
      await assertFails(create({ payers: { [key]: 4860 } }));
      await assertFails(create({ splitValues: { [key]: 1 } }));
    }
  });
  test('a value that is not a map at all', async () => {
    // Firestore map keys are always strings, so a non-string key cannot be written; the shape can differ.
    for (const notMap of [[4860], 4860, '4860', null]) {
      await assertFails(create({ payers: notMap }));
      await assertFails(create({ splitValues: notMap }));
    }
  });
});

describe('the other money fields of expenses and payments', () => {
  test('amount and rate must be whole numbers within range', async () => {
    for (const bad of [{ amountMinor: 1.5 }, { amountMinor: '4860' }, { amountMinor: 1_000_000_000_001 },
      { fxRateScaled: 1.5 }, { fxRateScaled: 0 }, { fxRateScaled: 900_000_000_000_001 }]) {
      await assertFails(create(bad));
    }
  });
  test('a payment: amount and rate must be whole numbers within range', async () => {
    const pay = (overrides) => setDoc(doc(as(env, 'ana'), path('transfers/t9')),
      transferData({ ...overrides, ...fresh() }));
    await assertSucceeds(pay({}));
    for (const bad of [{ amountMinor: 12.5 }, { amountMinor: '1215' }, { amountMinor: 0 },
      { amountMinor: 1_000_000_000_001 }, { fxRateScaled: 0.5 }, { fxRateScaled: '1000000000' }]) {
      await assertFails(pay(bad));
    }
  });
});

describe('money.js and a bad expense that was written before the rules checked it', () => {
  const members = [{ id: 'm1', sortKey: 0 }, { id: 'm2', sortKey: 1 }];
  const entry = (id, payers, splitValues) => ({
    id, amountMinor: 100, payers, splitValues, splitType: 'EQUAL', currency: 'EUR', fxRateScaled: 1_000_000_000,
  });
  test('it leaves the entry out instead of throwing, and the rest still counts', () => {
    const good = entry('good', { m1: 100 }, { m1: 1, m2: 1 });
    for (const badEntry of [
      entry('float', { m1: 1.5 }, { m1: 1, m2: 1 }),
      entry('text', { m1: '100' }, { m1: 1, m2: 1 }),
      entry('fraction share', { m1: 100 }, { m1: 1, m2: 0.5 }),
      entry('nan', { m1: Number.NaN }, { m1: 1, m2: 1 }),
    ]) {
      const result = groupBalances('EUR', members, [good, badEntry], []);
      expect(result.invalidEntryIds).toEqual([badEntry.id]);
      expect(result.balances).toEqual({ m1: 50, m2: -50 });
    }
  });
  test('a stored expense with accepted values reads back as numbers', async () => {
    const ref = doc(as(env, 'ana'), path('expenses/stored'));
    await assertSucceeds(setDoc(ref, expenseData({ payers: { m1: 3000, m2: 1860 }, ...fresh() })));
    const stored = (await getDoc(ref)).data();
    expect(Object.values(stored.payers).every(Number.isInteger)).toBe(true);
  });
});
