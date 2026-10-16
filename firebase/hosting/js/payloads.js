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

/** A new expense in the group currency, with one payer for the whole amount. */
export function newExpense(f, uid, { title, category, date, currency, amountMinor, payerId, splitType, splitValues, now }) {
  return {
    title: title.trim().slice(0, 80),
    category,
    note: null,
    date,
    currency,
    amountMinor,
    fxRateScaled: RATE_ONE,
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
