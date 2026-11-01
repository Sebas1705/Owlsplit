// scripts/audit-expense-payers.mjs: its logic, on a fake database that only knows how to read. Any
// write would throw, so this also keeps the script read-only.
import { describe, expect, test } from 'vitest';
import { findMismatches, payersMismatch } from '../../../scripts/audit-expense-payers.mjs';

function collectionOf(docs) {
  return {
    select: () => ({
      get: async () => ({
        size: docs.length,
        docs: docs.map(({ id, data, expenses }) => ({
          id,
          data: () => data,
          ref: { collection: () => collectionOf(expenses ?? []) },
        })),
      }),
    }),
  };
}

const database = (groups) => ({ collection: () => collectionOf(groups) });

describe('payersMismatch', () => {
  test('payers that add up are fine, with one payer or several', () => {
    expect(payersMismatch({ amountMinor: 100, payers: { a: 100 } })).toBeNull();
    expect(payersMismatch({ amountMinor: 100, payers: { a: 30, b: 70 } })).toBeNull();
  });
  test('a merged edit pays twice', () => {
    expect(payersMismatch({ amountMinor: 100, payers: { a: 100, b: 100 } })).toEqual({ paid: 200, differenceMinor: 100 });
  });
  test('too little, and no payers at all, are reported too', () => {
    expect(payersMismatch({ amountMinor: 100, payers: { a: 60 } })).toEqual({ paid: 60, differenceMinor: -40 });
    expect(payersMismatch({ amountMinor: 100 })).toEqual({ paid: 0, differenceMinor: -100 });
    expect(payersMismatch({ amountMinor: 100, payers: null })).toEqual({ paid: 0, differenceMinor: -100 });
  });
});

describe('findMismatches', () => {
  test('lists only the expenses that do not add up, deleted ones included', async () => {
    const result = await findMismatches(database([
      {
        id: 'g1',
        expenses: [
          { id: 'ok', data: { amountMinor: 100, currency: 'EUR', payers: { a: 100 } } },
          { id: 'bad', data: { amountMinor: 100, currency: 'EUR', payers: { a: 100, b: 100 }, version: 2, deleted: false } },
        ],
      },
      { id: 'empty' },
      { id: 'g2', expenses: [{ id: 'gone', data: { amountMinor: 50, currency: 'USD', payers: { c: 25 }, deleted: true } }] },
    ]));
    expect(result.groups).toBe(3);
    expect(result.expenses).toBe(3);
    expect(result.found.map((e) => `${e.groupId}/${e.expenseId}`)).toEqual(['g1/bad', 'g2/gone']);
    expect(result.found[0]).toMatchObject({ amountMinor: 100, paid: 200, differenceMinor: 100, version: 2, updatedAt: null });
    expect(result.found[1].deleted).toBe(true);
  });
});
