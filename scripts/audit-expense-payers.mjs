#!/usr/bin/env node
// READ-ONLY report: expenses whose payers do not add up to amountMinor. They are the visible trace of
// the writes that deep-merged `payers` (docs/SYNC.md, "Expenses are replaced, not merged"). It only
// lists documents, it never writes or repairs anything; the listed ids and amounts are all it prints
// (no titles or names, and they are not even downloaded).
//
//   npm install --no-save --prefix scripts firebase-admin@14
//   GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json node scripts/audit-expense-payers.mjs [--project owlsplit] [--json]
//
// Credentials: a service account with read access to Firestore (roles/datastore.viewer is enough), or
// `gcloud auth application-default login`. The CI account (scripts/setup-ci-access.mjs) has no access
// to documents, so its key does not work here.
//
// Cost: one read per group and one per expense (at least one per group, even with no expenses). The
// Spark plan allows 50K reads a day.
//
// Not covered: a member removed from an EQUAL or WEIGHTS split who stays charged. Such a document is
// consistent on its own, so nothing in it tells it apart from a member who really takes part.
import { pathToFileURL } from 'node:url';

const DEFAULT_PROJECT = 'owlsplit';
// Only what the report needs: titles, notes and the rest never reach this machine.
const FIELDS = ['amountMinor', 'currency', 'payers', 'deleted', 'version', 'updatedAt'];

/** The sum of [expense]'s payers and how far it is from its amount; null when they match. */
export function payersMismatch(expense) {
  const payers = expense.payers !== null && typeof expense.payers === 'object' ? expense.payers : {};
  const paid = Object.values(payers).reduce((sum, value) => sum + Number(value), 0);
  return paid === expense.amountMinor ? null : { paid, differenceMinor: paid - expense.amountMinor };
}

/** Every expense of every group in [db] (a firebase-admin Firestore) whose payers do not add up. */
export async function findMismatches(db) {
  const groups = await db.collection('groups').select().get();
  const found = [];
  let expenses = 0;
  for (const group of groups.docs) {
    const snapshot = await group.ref.collection('expenses').select(...FIELDS).get();
    expenses += snapshot.size;
    for (const doc of snapshot.docs) {
      const expense = doc.data();
      const mismatch = payersMismatch(expense);
      if (mismatch === null) continue;
      found.push({
        groupId: group.id,
        expenseId: doc.id,
        amountMinor: expense.amountMinor,
        currency: expense.currency,
        ...mismatch,
        payers: expense.payers,
        deleted: expense.deleted,
        version: expense.version,
        updatedAt: expense.updatedAt?.toDate?.().toISOString() ?? null,
      });
    }
  }
  return { groups: groups.size, expenses, found };
}

function parseArgs(argv) {
  const args = { project: DEFAULT_PROJECT, json: false };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--json') args.json = true;
    else if (argv[i] === '--project' && argv[i + 1]) args.project = argv[++i];
    else throw new Error(`Unknown argument: ${argv[i]}`);
  }
  return args;
}

function print({ groups, expenses, found }) {
  for (const e of found) {
    const state = e.deleted ? ' (deleted)' : '';
    console.log(`groups/${e.groupId}/expenses/${e.expenseId}${state}`);
    console.log(`  amount ${e.amountMinor} ${e.currency}, payers add up to ${e.paid} (off by ${e.differenceMinor})`);
    console.log(`  payers ${JSON.stringify(e.payers)}, version ${e.version}, updated ${e.updatedAt}`);
  }
  console.log(`${found.length} of ${expenses} expenses in ${groups} groups do not add up.`);
}

async function main() {
  const { project, json } = parseArgs(process.argv.slice(2));
  const { applicationDefault, initializeApp } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');
  console.error(`Reading project ${project}. Nothing is written.`);
  const app = initializeApp({ credential: applicationDefault(), projectId: project });
  const result = await findMismatches(getFirestore(app));
  if (json) console.log(JSON.stringify(result, null, 2));
  else print(result);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
