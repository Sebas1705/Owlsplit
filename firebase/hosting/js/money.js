// Owlsplit's money, as the apps compute it (core/money and domain/ledger), so a guest on the web sees
// the same balances and suggested payments. Amounts are integer minor units; all the arithmetic is
// BigInt, exact like the Kotlin Longs. Checked against fixtures/ledger.json, which the Kotlin tests
// also check (LedgerFixturesTest).

export const RATE_SCALE = 1_000_000_000n;
export const FULL_PERCENT = 10_000n;
export const MAX_EXPENSE_MINOR = 1_000_000_000_000n;

/** The currencies the apps offer (core/money CurrencyTable). */
export const CURRENCIES = ['AED', 'ARS', 'AUD', 'BGN', 'BHD', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY', 'COP', 'CZK', 'DKK', 'EGP', 'EUR', 'GBP', 'HKD', 'HUF', 'IDR', 'ILS', 'INR', 'ISK', 'JOD', 'JPY', 'KRW', 'KWD', 'MAD', 'MXN', 'MYR', 'NOK', 'NZD', 'OMR', 'PEN', 'PHP', 'PLN', 'QAR', 'RON', 'RSD', 'SAR', 'SEK', 'SGD', 'THB', 'TND', 'TRY', 'TWD', 'UAH', 'USD', 'UYU', 'VND', 'ZAR'];

const ZERO_DECIMALS = ["CLP", "ISK", "JPY", "KRW", "VND"];
const THREE_DECIMALS = ["BHD", "JOD", "KWD", "OMR", "TND"];

/** Minor-unit digits of an ISO currency (core/money CurrencyTable). */
export function decimalsOf(code) {
  if (ZERO_DECIMALS.includes(code)) return 0;
  if (THREE_DECIMALS.includes(code)) return 3;
  return 2;
}

const big = (v) => BigInt(v);
const pow10 = (n) => 10n ** BigInt(n);

/** Members in the ledger's order: sortKey, then id. */
export function memberOrder(a, b) {
  if (a.sortKey !== b.sortKey) return a.sortKey < b.sortKey ? -1 : 1;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

/** Exact (a·b)/c truncated toward zero; the remainder carries the sign of a·b. */
function mulDivRem(a, b, c) {
  const product = a * b;
  return { quotient: product / c, remainder: product % c };
}

/** Largest remainder: parts proportional to weights, summing exactly to total; ties to the earlier one. */
export function byWeights(total, weights) {
  const t = big(total);
  const w = weights.map(big);
  const sum = w.reduce((s, x) => s + x, 0n);
  if (t < 0n || w.length === 0 || w.some((x) => x < 0n) || sum <= 0n) throw new Error("Invalid allocation");
  const parts = w.map((x) => mulDivRem(t, x, sum).quotient);
  const remainders = w.map((x) => mulDivRem(t, x, sum).remainder);
  let leftover = t - parts.reduce((s, x) => s + x, 0n);
  const order = w.map((_, i) => i).sort((i, j) => (remainders[j] > remainders[i] ? 1 : remainders[j] < remainders[i] ? -1 : i - j));
  for (const i of order) {
    if (leftover <= 0n) break;
    parts[i] += 1n;
    leftover -= 1n;
  }
  return parts;
}

class InvalidEntry extends Error {}

/** Each member's part of an expense in its currency (ShareCalculator), as BigInt by member id. */
export function shares(amountMinor, splitType, splitValues, members) {
  const amount = big(amountMinor);
  if (amount < 1n || amount > MAX_EXPENSE_MINOR) throw new InvalidEntry("amount");
  const known = new Set(members.map((m) => m.id));
  for (const id of Object.keys(splitValues)) if (!known.has(id)) throw new InvalidEntry("unknown member");
  if (Object.values(splitValues).some((v) => big(v) < 0n)) throw new InvalidEntry("negative");
  const included = members.filter((m) => big(splitValues[m.id] ?? 0) > 0n).sort(memberOrder);
  if (included.length === 0) throw new InvalidEntry("no beneficiary");
  const value = (m) => big(splitValues[m.id]);
  const allocate = (weight) => {
    const parts = byWeights(amount, included.map(weight));
    return new Map(included.map((m, i) => [m.id, parts[i]]));
  };
  switch (splitType) {
    case "EQUAL":
      return allocate(() => 1n);
    case "WEIGHTS":
      return allocate(value);
    case "PERCENT": {
      const sum = included.reduce((s, m) => s + value(m), 0n);
      if (sum !== FULL_PERCENT) throw new InvalidEntry("percent sum");
      return allocate(value);
    }
    case "EXACT": {
      const sum = included.reduce((s, m) => s + value(m), 0n);
      if (sum !== amount) throw new InvalidEntry("exact sum");
      return new Map(included.map((m) => [m.id, value(m)]));
    }
    default:
      throw new InvalidEntry("split type");
  }
}

/** A net (summing to 0) in another currency, in the base one: truncated, then the leftover by remainder. */
function toBase(net, from, base, fxRateScaled, members) {
  if (from === base) return net;
  const ordered = [...members].sort(memberOrder).map((m) => m.id).filter((id) => net.has(id));
  const factor = big(fxRateScaled) * pow10(decimalsOf(base));
  const denominator = RATE_SCALE * pow10(decimalsOf(from));
  const converted = new Map(ordered.map((id) => [id, mulDivRem(net.get(id), factor, denominator)]));
  const result = new Map(ordered.map((id) => [id, converted.get(id).quotient]));
  let leftover = -[...result.values()].reduce((s, x) => s + x, 0n);
  if (leftover !== 0n) {
    const sign = leftover > 0n ? 1n : -1n;
    // Stable: ties keep member order, as Kotlin's sortedByDescending.
    const byRemainder = [...ordered].sort((a, b) => {
      const ra = sign * converted.get(a).remainder;
      const rb = sign * converted.get(b).remainder;
      return rb > ra ? 1 : rb < ra ? -1 : 0;
    });
    let count = leftover < 0n ? -leftover : leftover;
    for (const id of byRemainder) {
      if (count === 0n) break;
      result.set(id, result.get(id) + sign);
      count -= 1n;
    }
  }
  return result;
}

/** An expense's effect on each balance, in the base currency (NetCalculator.expenseNet). */
export function expenseNet(expense, members, base) {
  const parts = shares(expense.amountMinor, expense.splitType, expense.splitValues, members);
  const known = new Set(members.map((m) => m.id));
  const payers = Object.entries(expense.payers);
  if (payers.some(([id]) => !known.has(id))) throw new InvalidEntry("unknown payer");
  if (payers.some(([, v]) => big(v) < 0n)) throw new InvalidEntry("negative payer");
  if (payers.reduce((s, [, v]) => s + big(v), 0n) !== big(expense.amountMinor)) throw new InvalidEntry("payers sum");
  const net = new Map();
  for (const [id, paid] of payers) net.set(id, (net.get(id) ?? 0n) + big(paid));
  for (const [id, share] of parts) net.set(id, (net.get(id) ?? 0n) - share);
  return toBase(net, expense.currency, base, expense.fxRateScaled, members);
}

/** A transfer's effect: whoever paid is owed it back (NetCalculator.transferNet). */
export function transferNet(transfer, members, base) {
  const known = new Set(members.map((m) => m.id));
  const amount = big(transfer.amountMinor);
  if (amount < 1n || amount > MAX_EXPENSE_MINOR) throw new InvalidEntry("amount");
  if (transfer.fromMemberId === transfer.toMemberId) throw new InvalidEntry("same member");
  if (!known.has(transfer.fromMemberId) || !known.has(transfer.toMemberId)) throw new InvalidEntry("unknown member");
  const net = new Map([[transfer.fromMemberId, amount], [transfer.toMemberId, -amount]]);
  return toBase(net, transfer.currency, base, transfer.fxRateScaled, members);
}

/** Greedy settlement: the largest debtor pays the largest creditor; ties follow member order. */
export function simplify(balances, members) {
  const rank = new Map([...members].sort(memberOrder).map((m, i) => [m.id, i]));
  const byAmountThenRank = (a, b) => (b.amount > a.amount ? 1 : b.amount < a.amount ? -1 : rank.get(a.id) - rank.get(b.id));
  const creditors = [];
  const debtors = [];
  for (const [id, v] of balances) {
    if (v > 0n) creditors.push({ id, amount: v });
    if (v < 0n) debtors.push({ id, amount: -v });
  }
  const payments = [];
  while (debtors.length > 0) {
    debtors.sort(byAmountThenRank);
    creditors.sort(byAmountThenRank);
    const debtor = debtors[0];
    const creditor = creditors[0];
    const amount = debtor.amount < creditor.amount ? debtor.amount : creditor.amount;
    payments.push({ fromMemberId: debtor.id, toMemberId: creditor.id, amountMinor: amount });
    debtor.amount -= amount;
    creditor.amount -= amount;
    if (debtor.amount === 0n) debtors.shift();
    if (creditor.amount === 0n) creditors.shift();
  }
  return payments;
}

/**
 * A group's balances and suggested payments, like ObserveGroupBalances: members not deleted (inactive
 * ones too), expenses and transfers not deleted; an inconsistent entry is left out and reported.
 * Amounts come back as Numbers (safe: they stay far below 2^53).
 */
export function groupBalances(baseCurrency, members, expenses, transfers) {
  const visible = members.filter((m) => !m.deleted).map((m) => ({ id: m.id, sortKey: m.sortKey }));
  const totals = new Map([...visible].sort(memberOrder).map((m) => [m.id, 0n]));
  const invalidEntryIds = [];
  const add = (entry, net) => {
    try {
      for (const [id, v] of net(entry)) totals.set(id, totals.get(id) + v);
    } catch (e) {
      if (!(e instanceof InvalidEntry)) throw e;
      invalidEntryIds.push(entry.id);
    }
  };
  for (const e of expenses.filter((x) => !x.deleted)) add(e, (x) => expenseNet(x, visible, baseCurrency));
  for (const t of transfers.filter((x) => !x.deleted)) add(t, (x) => transferNet(x, visible, baseCurrency));
  const suggestions = simplify(totals, visible).map((p) => ({ ...p, amountMinor: Number(p.amountMinor) }));
  return {
    balances: Object.fromEntries([...totals].map(([id, v]) => [id, Number(v)])),
    suggestions,
    invalidEntryIds,
  };
}

/** My part of an expense in its own currency, or 0 (for the list's "your share"). */
export function myShare(expense, members, memberId) {
  try {
    const visible = members.filter((m) => !m.deleted);
    return Number(shares(expense.amountMinor, expense.splitType, expense.splitValues, visible).get(memberId) ?? 0n);
  } catch {
    return 0;
  }
}
