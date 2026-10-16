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

describe('exchange rates, as the apps read them', () => {
  test('plain decimals from JSON numbers (FrankfurterRatesTest)', async () => {
    const { plainDecimal } = await import('../../hosting/js/payloads.js');
    expect(plainDecimal('3.75e-05')).toBe('0.0000375');
    expect(plainDecimal('0.92345')).toBe('0.92345');
    expect(plainDecimal('1.5E3')).toBe('1500');
    expect(plainDecimal('161.1234567891234')).toBe('161.123456789');
  });
  test('fixed point (FxRateTest) and a Frankfurter response', async () => {
    const { parseRate, rateFromFrankfurter, convertMinor } = await import('../../hosting/js/payloads.js');
    expect(parseRate('0.923456789')).toBe(923_456_789);
    expect(parseRate('1,5')).toBe(1_500_000_000);
    expect(parseRate('163.5')).toBe(163_500_000_000);
    expect(parseRate('abc')).toBeNull();
    const body = '{"amount":1.0,"base":"USD","date":"2026-10-15","rates":{"EUR":0.89222}}';
    expect(rateFromFrankfurter(body, 'EUR')).toEqual({ scaled: 892_220_000, day: '2026-10-15' });
    expect(rateFromFrankfurter(body, 'GBP')).toBeNull();
    // 10.00 USD at 0.89222 → 8.92 EUR (truncated); 1 500 JPY at 0.0059 → 8.85 EUR.
    expect(convertMinor(1000, 2, 2, 892_220_000)).toBe(892);
    expect(convertMinor(1500, 0, 2, 5_900_000)).toBe(885);
  });
});
