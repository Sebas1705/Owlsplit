// The web for guests (hosting/js/money.js) must compute what the apps compute: the expected results
// in fixtures/ledger.json come from the app's own code (LedgerFixturesTest in domain/usecases).
import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';
import { byWeights, groupBalances, groupExplanation, myShare } from '../../hosting/js/money.js';

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

describe('where a balance comes from, as the apps explain it', () => {
  const trio = [{ id: 'ana', sortKey: 0 }, { id: 'luis', sortKey: 1 }, { id: 'marta', sortKey: 2 }];
  const expense = (id, amountMinor, payers, splitValues, more = {}) => ({
    id, amountMinor, payers, splitValues, splitType: 'EQUAL', currency: 'EUR', fxRateScaled: 1_000_000_000, ...more,
  });
  const transfer = (id, fromMemberId, toMemberId, amountMinor, more = {}) => ({
    id, fromMemberId, toMemberId, amountMinor, currency: 'EUR', fxRateScaled: 1_000_000_000, ...more,
  });
  const equal = (...ids) => Object.fromEntries(ids.map((id) => [id, 1]));

  test.each(cases.map((c, i) => [i, c]))('case %i gives the same breakdowns and pairwise count as the app', (_, c) => {
    const result = groupExplanation(c.baseCurrency, c.members, c.expenses, c.transfers);
    const plain = groupBalances(c.baseCurrency, c.members, c.expenses, c.transfers);
    const breakdowns = Object.fromEntries(result.members.map(({ memberId, paidMinor, shareMinor, sentMinor, receivedMinor }) =>
      [memberId, { paidMinor, shareMinor, sentMinor, receivedMinor }]));
    expect(breakdowns).toEqual(c.expected.explanation.breakdowns);
    expect(result.pairwiseDebts).toBe(c.expected.explanation.pairwiseDebts);
    expect(result.usesConversion).toBe(c.expected.explanation.usesConversion);
    // The numbers the page shows next to it: the same balances and the same payments.
    expect(result.suggestions).toEqual(plain.suggestions);
    expect(result.invalidEntryIds).toEqual(plain.invalidEntryIds);
    for (const row of result.members) {
      expect(row.balanceMinor).toBe(plain.balances[row.memberId]);
      expect(row.addsUp).toBe(true);
    }
  });

  test("each member's rows add up to the balance the page shows", () => {
    const expenses = [expense('e1', 9000, { ana: 9000 }, equal('ana', 'luis', 'marta')), expense('e2', 3000, { luis: 3000 }, equal('luis', 'marta'))];
    const { members } = groupExplanation('EUR', trio, expenses, [transfer('t1', 'marta', 'ana', 2000)]);
    expect(members.map(({ memberId, paidMinor, shareMinor, sentMinor, receivedMinor, balanceMinor }) =>
      [memberId, paidMinor, shareMinor, sentMinor, receivedMinor, balanceMinor])).toEqual([
      ['ana', 9000, 3000, 0, 2000, 4000],
      ['luis', 3000, 4500, 0, 0, -1500],
      ['marta', 0, 4500, 2000, 0, -2500],
    ]);
  });

  test("rounding cents follow the ledger's own split", () => {
    const { members } = groupExplanation('EUR', trio, [expense('e1', 100, { luis: 100 }, equal('ana', 'luis', 'marta'))], []);
    expect(members.map((m) => m.shareMinor)).toEqual([34, 33, 33]);
    expect(members.map((m) => m.balanceMinor)).toEqual([-34, 67, -33]);
  });

  test('a foreign expense is shown in the group currency and still adds up exactly', () => {
    const expenses = [expense('e1', 1000, { ana: 333, luis: 667 }, equal('ana', 'luis', 'marta'), { currency: 'USD', fxRateScaled: 923_456_789 })];
    const transfers = [transfer('t1', 'marta', 'luis', 500, { currency: 'GBP', fxRateScaled: 1_171_234_567 })];
    const { members, usesConversion } = groupExplanation('EUR', trio, expenses, transfers);
    const sum = (key) => members.reduce((s, m) => s + m[key], 0);
    expect(usesConversion).toBe(true);
    expect(members.every((m) => m.addsUp)).toBe(true);
    expect([sum('paidMinor'), sum('shareMinor'), sum('sentMinor'), sum('receivedMinor')]).toEqual([923, 923, 585, 585]);
  });

  test('tiny converted amounts never show a negative row', () => {
    const expenses = [expense('e1', 3, { ana: 1, luis: 2 }, equal('ana', 'luis', 'marta'), { currency: 'JPY', fxRateScaled: 5_900_000 })];
    const { members } = groupExplanation('EUR', trio, expenses, []);
    expect(members.every((m) => m.addsUp && m.shareMinor >= 0 && m.paidMinor >= 0)).toBe(true);
  });

  test('members who left appear only while they have something to explain', () => {
    const everyone = [...trio, { id: 'pablo', sortKey: 3, active: false }, { id: 'zoe', sortKey: 4, active: false }];
    const { members } = groupExplanation('EUR', everyone, [expense('e1', 4000, { pablo: 4000 }, equal('ana', 'pablo'))], []);
    expect(members.map((m) => m.memberId)).toEqual(['ana', 'luis', 'marta', 'pablo']);
    expect(members.at(-1).balanceMinor).toBe(2000);
  });

  test('deleted members and entries count for nothing, as in the balances', () => {
    const everyone = [...trio, { id: 'gone', sortKey: 3, deleted: true }];
    const expenses = [expense('e1', 2000, { ana: 2000 }, equal('ana', 'luis')), expense('e2', 500, { ana: 500 }, equal('ana', 'luis'), { deleted: true })];
    const { members } = groupExplanation('EUR', everyone, expenses, [transfer('t1', 'luis', 'ana', 300, { deleted: true })]);
    expect(members.map((m) => m.memberId)).toEqual(['ana', 'luis', 'marta']);
    expect(members[0].paidMinor).toBe(2000);
    expect(members[0].receivedMinor).toBe(0);
  });

  test('members come in the order of the balances list', () => {
    const { members } = groupExplanation('EUR', [{ id: 'b', sortKey: 1 }, { id: 'c', sortKey: 0 }, { id: 'a', sortKey: 1 }], [], []);
    expect(members.map((m) => m.memberId)).toEqual(['c', 'a', 'b']);
  });

  test('fewer payments than debts between pairs when simplifying helps', () => {
    const expenses = [expense('e1', 1000, { ana: 1000 }, { luis: 1 }), expense('e2', 1000, { luis: 1000 }, { marta: 1 })];
    const result = groupExplanation('EUR', trio, expenses, []);
    expect(result.pairwiseDebts).toBe(2);
    expect(result.suggestions).toHaveLength(1);
    expect(result.simplifies).toBe(true);
  });

  test('debts between the same two people in both directions cancel out', () => {
    const expenses = [expense('e1', 1000, { ana: 1000 }, { luis: 1 }), expense('e2', 400, { luis: 400 }, { ana: 1 })];
    const result = groupExplanation('EUR', trio, expenses, [transfer('t1', 'luis', 'ana', 600)]);
    expect(result.pairwiseDebts).toBe(0);
    expect(result.settled).toBe(true);
    expect(result.simplifies).toBe(false);
  });

  test('nothing to simplify when every debt is already direct', () => {
    const result = groupExplanation('EUR', trio, [expense('e1', 3000, { ana: 3000 }, equal('ana', 'luis', 'marta'))], []);
    expect(result.pairwiseDebts).toBe(2);
    expect(result.suggestions).toHaveLength(2);
    expect(result.simplifies).toBe(false);
  });

  test('everyone settled gives an empty explanation', () => {
    const result = groupExplanation('EUR', trio, [], []);
    expect(result.settled).toBe(true);
    expect(result.members.every((m) => m.balanceMinor === 0 && m.isEmpty)).toBe(true);
  });

  test('inconsistent entries are left out exactly as the balances leave them out', () => {
    const expenses = [expense('ok', 2000, { ana: 2000 }, equal('ana', 'luis')), expense('bad', 1000, { ana: 999 }, equal('ana', 'luis'))];
    const result = groupExplanation('EUR', trio, expenses, []);
    expect(result.invalidEntryIds).toEqual(['bad']);
    expect(result.members[0].paidMinor).toBe(2000);
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
