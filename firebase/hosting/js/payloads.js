// The documents the web for guests writes, exactly as the apps write them (data/remote Dtos.kt): every
// key present (the apps skip a document that lacks one), integers for money and createdAt, explicit
// nulls, and the sync stamp (version, updatedAt from the server, updatedBy, deleted). Pure functions:
// the Firestore sentinels come in [f] (serverTimestamp, arrayUnion, increment), so the rules tests
// (firebase/rules-test/test/web.test.mjs) check these same payloads.

export const RATE_ONE = 1_000_000_000;

const stamp = (f, uid, version) => ({ version, updatedAt: f.serverTimestamp(), updatedBy: uid, deleted: false });

/** Joining with the invite code: only my uid, the code and the stamp (a non-member cannot read the group). */
export function joinGroup(f, uid, code) {
  return {
    memberUids: f.arrayUnion(uid),
    lastJoinCode: code,
    version: f.increment(1),
    updatedAt: f.serverTimestamp(),
    updatedBy: uid,
  };
}

/** A new member for me ("I'm new"), after the others in the group's order. */
export function newMember(f, uid, name, sortKey) {
  return {
    name: name.trim().slice(0, 40),
    avatar: null,
    defaultWeight: 1,
    userUid: uid,
    active: true,
    sortKey,
    ...stamp(f, uid, 1),
    payMethods: null,
    pastUids: [],
    linked: false,
  };
}

/** Taking a member without account: it is mine (and active again), the rest stays. */
export function claimMember(f, uid, member) {
  return { userUid: uid, active: true, version: member.version + 1, updatedAt: f.serverTimestamp(), updatedBy: uid };
}

/**
 * A new expense with one payer for the whole amount. In another currency than the group's,
 * [fxRateScaled] is the rate frozen in it (group currency per unit, ×10⁹), as the apps do.
 */
export function newExpense(f, uid, {
  title, category, date, currency, amountMinor, payerId, splitType, splitValues, now, fxRateScaled = RATE_ONE,
}) {
  return {
    title: title.trim().slice(0, 80),
    category,
    note: null,
    date,
    currency,
    amountMinor,
    fxRateScaled,
    payers: { [payerId]: amountMinor },
    splitType,
    splitValues,
    receiptUrl: null,
    recurrenceId: null,
    createdBy: uid,
    createdAt: now,
    ...stamp(f, uid, 1),
  };
}

/** A payment from one member to another, in the group currency (a suggested payment, recorded). */
export function newTransfer(f, uid, { fromMemberId, toMemberId, amountMinor, currency, date, now }) {
  return {
    fromMemberId,
    toMemberId,
    amountMinor,
    currency,
    fxRateScaled: RATE_ONE,
    date,
    note: null,
    createdBy: uid,
    createdAt: now,
    ...stamp(f, uid, 1),
  };
}

/**
 * The history entry that goes with every change, in the same batch: the apps download a group by its
 * changelog, so without it they would not see the change.
 */
export function changelogEntry(f, uid, { entityType, entityId, action, diff = [], now }) {
  return { entityType, entityId, action, authorUid: uid, diff, createdAt: now, updatedAt: f.serverTimestamp() };
}

/** The split of a new expense: equal, or by weights when the members do not all weigh the same. */
export function defaultSplit(members, includedIds) {
  const included = members.filter((m) => includedIds.includes(m.id));
  const weights = new Set(included.map((m) => m.defaultWeight));
  if (weights.size > 1) return { splitType: 'WEIGHTS', splitValues: Object.fromEntries(included.map((m) => [m.id, m.defaultWeight])) };
  return { splitType: 'EQUAL', splitValues: Object.fromEntries(included.map((m) => [m.id, 1])) };
}

/** "12,50", "12.5" or "12" in minor units with [decimals]; null when it is not an amount. */
export function parseAmount(text, decimals) {
  const match = /^\s*(\d{1,10})(?:[.,](\d*))?\s*$/.exec(text ?? '');
  if (!match) return null;
  const fraction = (match[2] ?? '').slice(0, decimals).padEnd(decimals, '0');
  const minor = Number(match[1]) * 10 ** decimals + (decimals > 0 ? Number(fraction || '0') : 0);
  return minor > 0 ? minor : null;
}

// ── Edits ──────────────────────────────────────────────────────────────────────────────────────────

/** A value as the apps' history writes it (Diffs.kt): maps sorted by key as "k=v,k2=v2", the rest as text. */
function rendered(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'object') {
    return Object.keys(value).sort().map((k) => `${k}=${value[k]}`).join(',');
  }
  return String(value);
}

const EXPENSE_FIELDS = ['title', 'amountMinor', 'currency', 'fxRateScaled', 'date', 'category', 'note', 'payers', 'splitType', 'splitValues'];

/** The changed fields of an expense, in the apps' order, for its history entry. */
export function expenseDiff(before, after) {
  return EXPENSE_FIELDS
    .map((field) => ({ field, before: rendered(before[field]), after: rendered(after[field]) }))
    .filter((c) => c.before !== c.after);
}

/** An edit of [old]: the changed fields, the next version and the stamp (the rules check the version). */
export function expenseUpdate(f, uid, old, changes) {
  return { ...changes, version: old.version + 1, updatedAt: f.serverTimestamp(), updatedBy: uid };
}

/** Deleting (or restoring) an expense or a payment: it stays, marked, so it can be restored. */
export function markDeleted(f, uid, old, deleted = true) {
  return { deleted, version: old.version + 1, updatedAt: f.serverTimestamp(), updatedBy: uid };
}

/**
 * Whether the web can edit the whole of an expense: one payer, an equal or weights split, in the group
 * currency (what the web itself creates). The rest is edited in the app; here only its words and date.
 */
export function fullyEditable(expense, baseCurrency) {
  return Object.keys(expense.payers).length === 1
    && (expense.splitType === 'EQUAL' || expense.splitType === 'WEIGHTS')
    && expense.currency === baseCurrency;
}

// ── Exchange rates (data/fx FrankfurterRates and core/money FxRate) ───────────────────────────────

/**
 * A JSON number as a plain decimal with at most 9 decimals ("3.75e-05" → "0.0000375"); extra digits
 * are cut, never rounded up. Read from the text, never through a float.
 */
export function plainDecimal(number) {
  const [mantissa, exp] = number.toLowerCase().split('e');
  const exponent = exp === undefined ? 0 : parseInt(exp, 10);
  const [integerDigits, fractionDigits = ''] = mantissa.split('.');
  const digits = integerDigits + fractionDigits;
  const point = integerDigits.length + exponent;
  const padded = point <= 0 ? '0'.repeat(-point + 1) + digits
    : point > digits.length ? digits + '0'.repeat(point - digits.length) : digits;
  const split = Math.max(point, 1);
  const integer = padded.slice(0, split).replace(/^0+/, '') || '0';
  const fraction = padded.slice(split).slice(0, 9).replace(/0+$/, '');
  return fraction ? `${integer}.${fraction}` : integer;
}

/** "0.923456789" or "1,5" as the fixed-point rate (×10⁹); null when it is not one. */
export function parseRate(decimal) {
  const match = /^(\d{1,6})(?:[.,](\d{1,9}))?$/.exec((decimal ?? '').trim());
  if (!match) return null;
  const scaled = Number(match[1]) * 1_000_000_000 + Number((match[2] ?? '').padEnd(9, '0'));
  return scaled > 0 ? scaled : null;
}

/** The rate [to] in a Frankfurter response, read from its text: { scaled, day } or null. */
export function rateFromFrankfurter(body, to) {
  const rate = new RegExp(`"${to}"\\s*:\\s*([0-9.eE+-]+)`).exec(body)?.[1];
  const day = /"date"\s*:\s*"([0-9-]+)"/.exec(body)?.[1] ?? '';
  const scaled = rate ? parseRate(plainDecimal(rate)) : null;
  return scaled ? { scaled, day } : null;
}

/** [amountMinor] of [from] in minor units of [to] at [fxRateScaled], truncated (Conversion.convert). */
export function convertMinor(amountMinor, fromDecimals, toDecimals, fxRateScaled) {
  const value = (BigInt(amountMinor) * BigInt(fxRateScaled) * 10n ** BigInt(toDecimals))
    / (1_000_000_000n * 10n ** BigInt(fromDecimals));
  return Number(value);
}

// ── Saving the guest with Google ───────────────────────────────────────────────────────────────────

/** Giving my member back (it stays, without account, with its expenses), before another account takes it. */
export function releaseMember(f, uid, member) {
  return {
    userUid: null,
    payMethods: null,
    linked: false,
    pastUids: [...(member.pastUids ?? []), uid],
    version: member.version + 1,
    updatedAt: f.serverTimestamp(),
    updatedBy: uid,
  };
}

/** Leaving the group: only my uid goes, and the stamp. */
export function leaveGroup(f, uid) {
  return { memberUids: f.arrayRemove(uid), version: f.increment(1), updatedAt: f.serverTimestamp(), updatedBy: uid };
}

/** "Linked to Google" on my member, as the app shows it (no history entry, like publishMyLinked). */
export function markLinked(f, uid, member) {
  return { linked: true, version: member.version + 1, updatedAt: f.serverTimestamp(), updatedBy: uid };
}

/** users/{uid}: what the app restores when signing in with this Google account (UserDto). */
export function profileDoc(f, { name, currency }) {
  return {
    displayName: name,
    avatar: 'accent:BLUE',
    preferredCurrency: currency,
    linkedProviders: ['google.com'],
    updatedAt: f.serverTimestamp(),
    payMethods: {},
  };
}
