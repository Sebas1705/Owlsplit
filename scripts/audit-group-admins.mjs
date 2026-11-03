#!/usr/bin/env node
// READ-ONLY report of who runs each group once the admin role exists (docs/designs/2026-11-03-group-admin.md).
// Groups from before the role have no `adminMemberId`: their admin is the member of `createdBy`. This
// lists the groups where that does not resolve, so you can decide whether any needs a hand. It never
// writes anything and prints only ids and counts (no names, titles or amounts; they are not downloaded).
//
//   npm install --no-save --prefix scripts firebase-admin@14
//   GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json node scripts/audit-group-admins.mjs [--project owlsplit] [--json]
//
// Credentials and cost: as scripts/audit-expense-payers.mjs (roles/datastore.viewer is enough; one read
// per group and one per member).
import { pathToFileURL } from 'node:url';

const DEFAULT_PROJECT = 'owlsplit';
const GROUP_FIELDS = ['createdBy', 'memberUids', 'adminMemberId', 'deleted'];
const MEMBER_FIELDS = ['userUid', 'deleted'];

/**
 * How the admin of [group] resolves given its [members] (`{ id, userUid, deleted }`):
 * - `explicit`: `adminMemberId` points to a live member.
 * - `explicit-missing`: `adminMemberId` points to nothing (the creator then counts, as in the rules).
 * - `creator`: no `adminMemberId`; the creator still has a member (the usual old group).
 * - `claimable`: no admin and the creator left the group: any member can take it over.
 * - `stuck`: no admin, yet the creator's uid is still in the group without a member (nobody can claim it).
 */
export function classify(group, members) {
  const live = members.filter((m) => !m.deleted);
  if (group.adminMemberId) {
    return live.some((m) => m.id === group.adminMemberId) ? 'explicit' : 'explicit-missing';
  }
  if (live.some((m) => m.userUid === group.createdBy)) return 'creator';
  return (group.memberUids ?? []).includes(group.createdBy) ? 'stuck' : 'claimable';
}

export async function audit(db) {
  const groups = await db.collection('groups').select(...GROUP_FIELDS).get();
  const counts = {};
  const needAttention = [];
  for (const doc of groups.docs) {
    const group = doc.data();
    if (group.deleted) continue;
    const members = (await doc.ref.collection('members').select(...MEMBER_FIELDS).get()).docs
      .map((m) => ({ id: m.id, ...m.data() }));
    const kind = classify(group, members);
    counts[kind] = (counts[kind] ?? 0) + 1;
    if (kind === 'stuck' || kind === 'explicit-missing') needAttention.push({ groupId: doc.id, kind });
  }
  return { groups: groups.size, counts, needAttention };
}

async function main() {
  const argv = process.argv.slice(2);
  let project = DEFAULT_PROJECT;
  let json = false;
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--json') json = true;
    else if (argv[i] === '--project' && argv[i + 1]) project = argv[++i];
    else throw new Error(`Unknown argument: ${argv[i]}`);
  }
  const { applicationDefault, initializeApp } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');
  console.error(`Reading project ${project}. Nothing is written.`);
  const result = await audit(getFirestore(initializeApp({ credential: applicationDefault(), projectId: project })));
  if (json) {
    console.log(JSON.stringify(result, null, 2));
  } else {
    console.log(`${result.groups} groups:`, result.counts);
    result.needAttention.forEach((g) => console.log(`  groups/${g.groupId}: ${g.kind}`));
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
