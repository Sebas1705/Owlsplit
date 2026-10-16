// The web for guests (hosting/js/money.js) must compute what the apps compute: the expected results
// in fixtures/ledger.json come from the app's own code (LedgerFixturesTest in domain/usecases).
import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';
import { byWeights, groupBalances, myShare } from '../../hosting/js/money.js';

const { cases } = JSON.parse(readFileSync(new URL('../fixtures/ledger.json', import.meta.url), 'utf8'));

describe('money.js', () => {
  test.each(cases.map((c, i) => [i, c]))('case %i gives the same balances and payments as the app', (_, c) => {
    const result = groupBalances(c.baseCurrency, c.members, c.expenses, c.transfers);
    expect(result.balances).toEqual(c.expected.balances);
    expect(result.suggestions).toEqual(c.expected.suggestions);
    expect(result.invalidEntryIds).toEqual(c.expected.invalidEntryIds);
  });

  test('largest remainder: ties go to the earlier one', () => {
    expect(byWeights(100, [1, 1, 1])).toEqual([34n, 33n, 33n]);
    expect(byWeights(5, [1, 2])).toEqual([2n, 3n]);
  });

  test('my share of an expense, or 0', () => {
    const members = [{ id: 'a', sortKey: 0 }, { id: 'b', sortKey: 1 }];
    const dinner = { amountMinor: 1001, splitType: 'EQUAL', splitValues: { a: 1, b: 1 } };
    expect(myShare(dinner, members, 'a')).toBe(501);
    expect(myShare(dinner, members, 'b')).toBe(500);
    expect(myShare({ ...dinner, splitType: 'EXACT' }, members, 'a')).toBe(0);
  });
});
