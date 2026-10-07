import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { Timestamp, doc, setDoc } from 'firebase/firestore';

export const GROUP = 'g1';
export const CODE = 'ABCD2345';

export async function createEnv() {
  return initializeTestEnvironment({
    projectId: 'demo-owlsplit',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
}

/** Firestore client acting as [uid]. */
export const as = (env, uid) => env.authenticatedContext(uid).firestore();

export const synced = (version = 1, by = 'ana') => ({
  version,
  updatedAt: Timestamp.fromMillis(1_000),
  updatedBy: by,
  deleted: false,
});

export function groupData(overrides = {}) {
  return {
    name: 'Ibiza 2026', icon: 'plane', accent: 'BLUE', baseCurrency: 'EUR',
    startDate: null, endDate: null, inviteCode: CODE, memberUids: ['ana'], archivedBy: [],
    createdBy: 'ana', createdAt: 1_000, lastJoinCode: null,
    ...synced(),
    ...overrides,
  };
}

export function memberData(overrides = {}) {
  return {
    name: 'Ana', avatar: null, defaultWeight: 1, userUid: 'ana', active: true, sortKey: 0,
    ...synced(),
    ...overrides,
  };
}

export function expenseData(overrides = {}) {
  return {
    title: 'Cena', category: 'FOOD', note: null, date: '2026-07-12', currency: 'EUR',
    amountMinor: 4860, fxRateScaled: 1_000_000_000, payers: { m1: 4860 }, splitType: 'EQUAL',
    splitValues: { m1: 1, m2: 1 }, receiptUrl: null, recurrenceId: null, createdBy: 'ana', createdAt: 1_000,
    ...synced(),
    ...overrides,
  };
}

export function transferData(overrides = {}) {
  return {
    fromMemberId: 'm2', toMemberId: 'm1', amountMinor: 1215, currency: 'EUR', fxRateScaled: 1_000_000_000,
    date: '2026-07-13', note: null, createdBy: 'ana', createdAt: 1_000,
    ...synced(),
    ...overrides,
  };
}

/** Seeds documents bypassing the rules. */
export async function seed(env, writes) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [path, data] of Object.entries(writes)) await setDoc(doc(db, path), data);
  });
}
