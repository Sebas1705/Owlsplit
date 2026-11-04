// Group admin, member permissions and the author of expenses and payments
// (docs/designs/2026-11-03-group-admin.md §3).
//
// Cast: ana (m1) created the group; luis (m2) and zoe (m3) joined; "ghost" (m4) has no account.
// Two flavours of group: G (no adminMemberId: ana, the creator, is the admin) and G2 (adminMemberId = m2: luis).
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { arrayRemove, doc, increment, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { GROUP, as, createEnv, expenseData, groupData, memberData, seed, transferData } from './helpers.mjs';

const G2 = 'g2';
const ALL = ['ADD_EXPENSES', 'EDIT_EXPENSES', 'RECORD_PAYMENTS'];

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });

const bump = (by) => ({ version: increment(1), updatedAt: serverTimestamp(), updatedBy: by });
const fresh = (by) => ({ version: 1, updatedAt: serverTimestamp(), updatedBy: by });
const replaced = (by) => ({ version: 2, updatedAt: serverTimestamp(), updatedBy: by });

const members = (g, extra = {}) => ({
  [`groups/${g}/members/m1`]: memberData({ name: 'Ana', userUid: 'ana', sortKey: 0, ...(extra.m1 ?? {}) }),
  [`groups/${g}/members/m2`]: memberData({ name: 'Luis', userUid: 'luis', sortKey: 1, ...(extra.m2 ?? {}) }),
  [`groups/${g}/members/m3`]: memberData({ name: 'Zoe', userUid: 'zoe', sortKey: 2, ...(extra.m3 ?? {}) }),
  [`groups/${g}/members/m4`]: memberData({ name: 'Ghost', userUid: null, sortKey: 3, ...(extra.m4 ?? {}) }),
});

async function setup(extra = {}) {
  await env.clearFirestore();
  await seed(env, {
    [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis', 'zoe'] }),
    [`groups/${G2}`]: groupData({ memberUids: ['ana', 'luis', 'zoe'], adminMemberId: 'm2' }),
    ...members(GROUP, extra),
    ...members(G2, extra),
  });
}
beforeEach(async () => { await setup(); });

const g = (db, id, p = '') => doc(db, `groups/${id}${p ? '/' + p : ''}`);

describe('who is the admin', () => {
  test('without adminMemberId the creator is the admin and nobody else', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP), { name: 'Ibiza', ...bump('ana') }));
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { name: 'Otro', ...bump('luis') }));
  });
  test('with adminMemberId its member is the admin, not the creator', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'luis'), G2), { name: 'Ibiza', ...bump('luis') }));
    await assertFails(updateDoc(g(as(env, 'ana'), G2), { name: 'Otro', ...bump('ana') }));
    await assertFails(updateDoc(g(as(env, 'zoe'), G2), { name: 'Otro', ...bump('zoe') }));
  });
  test('adminMemberId null is the same as absent', async () => {
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis', 'zoe'], adminMemberId: null }) });
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP), { name: 'Ibiza', ...bump('ana') }));
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { name: 'Otro', ...bump('luis') }));
  });
});

describe('group settings', () => {
  const settings = [
    { name: 'Otro' }, { icon: 'tent' }, { accent: 'RED' }, { baseCurrency: 'USD' }, { startDate: '2026-08-01' },
    { endDate: '2026-08-09' }, { inviteCode: 'ZZZZ2345' },
  ];
  test('the admin changes each setting and the invite code', async () => {
    for (const change of settings) {
      await setup();
      await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP), { ...change, ...bump('ana') }));
    }
  });
  test('a member changes none of them', async () => {
    for (const change of settings) {
      await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { ...change, ...bump('luis') }));
    }
  });
  test('a member still archives the group for themselves, with or without the new key', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'luis'), GROUP), { archivedBy: ['luis'], ...bump('luis') }));
    await assertSucceeds(updateDoc(g(as(env, 'zoe'), GROUP), { archivedBy: ['luis', 'zoe'], adminMemberId: null, ...bump('zoe') }));
    await assertSucceeds(updateDoc(g(as(env, 'zoe'), G2), { archivedBy: ['zoe'], ...bump('zoe') }));
  });
  test('the admin edits a group document that lacks the new key', async () => {
    await assertSucceeds(setDoc(g(as(env, 'ana'), GROUP),
      groupData({ memberUids: ['ana', 'luis', 'zoe'], name: 'Mío', adminMemberId: null, ...replaced('ana') })));
  });
  test('a group is created with or without an admin member, with the key null or a text', async () => {
    const db = as(env, 'ana');
    await assertSucceeds(setDoc(g(db, 'n1'), groupData({ adminMemberId: null, ...fresh('ana') })));
    await assertSucceeds(setDoc(g(db, 'n2'), groupData({ adminMemberId: 'm1', ...fresh('ana') })));
    await assertFails(setDoc(g(db, 'n3'), groupData({ adminMemberId: 5, ...fresh('ana') })));
  });
});

describe('handing over', () => {
  test('the admin hands the role to a member with an account', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP), { adminMemberId: 'm2', ...bump('ana') }));
    // Now luis is the admin and ana is not.
    await assertSucceeds(updateDoc(g(as(env, 'luis'), GROUP), { name: 'Ibiza', ...bump('luis') }));
    await assertFails(updateDoc(g(as(env, 'ana'), GROUP), { name: 'Otra', ...bump('ana') }));
    await assertFails(updateDoc(g(as(env, 'ana'), GROUP), { adminMemberId: 'm1', ...bump('ana') }));
  });
  test('the new admin hands it on, or to nobody', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'luis'), G2), { adminMemberId: 'm3', ...bump('luis') }));
    await assertSucceeds(updateDoc(g(as(env, 'zoe'), G2), { adminMemberId: null, ...bump('zoe') }));
  });
  test('not to a member without account, deleted, unknown, nor of somebody who is not in the group', async () => {
    const ana = as(env, 'ana');
    await assertFails(updateDoc(g(ana, GROUP), { adminMemberId: 'm4', ...bump('ana') }));
    await assertFails(updateDoc(g(ana, GROUP), { adminMemberId: 'nobody', ...bump('ana') }));
    await seed(env, {
      [`groups/${GROUP}/members/m5`]: memberData({ name: 'Gone', userUid: 'max', sortKey: 5 }),
      [`groups/${GROUP}/members/m6`]: memberData({ name: 'Old', userUid: 'zoe', sortKey: 6, deleted: true }),
    });
    await assertFails(updateDoc(g(ana, GROUP), { adminMemberId: 'm5', ...bump('ana') }));
    await assertFails(updateDoc(g(ana, GROUP), { adminMemberId: 'm6', ...bump('ana') }));
  });
  test('a member cannot take or set the role', async () => {
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { adminMemberId: 'm2', ...bump('luis') }));
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { adminMemberId: 'm2', archivedBy: ['luis'], ...bump('luis') }));
    await assertFails(updateDoc(g(as(env, 'zoe'), G2), { adminMemberId: 'm3', ...bump('zoe') }));
    await assertFails(updateDoc(g(as(env, 'zoe'), G2), { adminMemberId: null, ...bump('zoe') }));
  });
});

describe('leaving and the admin', () => {
  test('the admin leaving may hand over in the same write', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP), {
      memberUids: arrayRemove('ana'), adminMemberId: 'm2', ...bump('ana'),
    }));
  });
  test('the admin leaving without touching the role is accepted as before', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'luis'), G2), { memberUids: arrayRemove('luis'), ...bump('luis') }));
  });
  test('the hand over on leaving is to somebody else, with an account, existing and not deleted', async () => {
    const ana = as(env, 'ana');
    const leave = (adminMemberId) => updateDoc(g(ana, GROUP), { memberUids: arrayRemove('ana'), adminMemberId, ...bump('ana') });
    await assertFails(leave('m1'));
    await assertFails(leave('m4'));
    await assertFails(leave('nobody'));
    await seed(env, { [`groups/${GROUP}/members/m6`]: memberData({ name: 'Old', userUid: 'zoe', sortKey: 6, deleted: true }) });
    await assertFails(leave('m6'));
  });
  test('a member who is not the admin cannot touch adminMemberId when leaving', async () => {
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { memberUids: arrayRemove('luis'), adminMemberId: 'm2', ...bump('luis') }));
    await assertFails(updateDoc(g(as(env, 'zoe'), G2), { memberUids: arrayRemove('zoe'), adminMemberId: 'm3', ...bump('zoe') }));
    await assertSucceeds(updateDoc(g(as(env, 'zoe'), G2), { memberUids: arrayRemove('zoe'), ...bump('zoe') }));
  });
});

describe('a group whose creator left', () => {
  beforeEach(async () => {
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['luis', 'zoe'] }) });
  });
  test('a member takes the role for their own member, and nothing else changes', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'luis'), GROUP), { adminMemberId: 'm2', ...bump('luis') }));
    // They are the admin now.
    await assertSucceeds(updateDoc(g(as(env, 'luis'), GROUP), { name: 'Nuevo', ...bump('luis') }));
    await assertFails(updateDoc(g(as(env, 'zoe'), GROUP), { name: 'Otro', ...bump('zoe') }));
  });
  test('not for somebody else\'s member, a ghost, a deleted one or with other changes', async () => {
    const zoe = as(env, 'zoe');
    await assertFails(updateDoc(g(zoe, GROUP), { adminMemberId: 'm2', ...bump('zoe') }));
    await assertFails(updateDoc(g(zoe, GROUP), { adminMemberId: 'm4', ...bump('zoe') }));
    await assertFails(updateDoc(g(zoe, GROUP), { adminMemberId: 'nobody', ...bump('zoe') }));
    await assertFails(updateDoc(g(zoe, GROUP), { adminMemberId: 'm3', name: 'Mío', ...bump('zoe') }));
    await assertFails(updateDoc(g(zoe, GROUP), { adminMemberId: 'm3', inviteCode: 'ZZZZ2345', ...bump('zoe') }));
    await seed(env, { [`groups/${GROUP}/members/m3`]: memberData({ name: 'Zoe', userUid: 'zoe', deleted: true }) });
    await assertFails(updateDoc(g(zoe, GROUP), { adminMemberId: 'm3', ...bump('zoe') }));
  });
  test('a stranger cannot', async () => {
    await assertFails(updateDoc(g(as(env, 'max'), GROUP), { adminMemberId: 'm1', ...bump('max') }));
  });
  test('not while the creator is still in the group, nor when there already is an admin member', async () => {
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis', 'zoe'] }) });
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP), { adminMemberId: 'm2', ...bump('luis') }));
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['luis', 'zoe'], adminMemberId: 'm2' }) });
    await assertFails(updateDoc(g(as(env, 'zoe'), GROUP), { adminMemberId: 'm3', ...bump('zoe') }));
  });
});

describe('members: admin only', () => {
  const edit = (db, id, change, by) => updateDoc(g(db, GROUP, `members/${id}`), { ...change, ...bump(by) });

  test('the admin adds a ghost, renames, reweights, deactivates, reorders and deletes anybody', async () => {
    const ana = as(env, 'ana');
    await assertSucceeds(setDoc(g(ana, GROUP, 'members/m9'), memberData({ name: 'Max', userUid: null, sortKey: 9, ...fresh('ana') })));
    await assertSucceeds(edit(ana, 'm2', { name: 'Luis G.' }, 'ana'));
    await assertSucceeds(edit(ana, 'm2', { defaultWeight: 2 }, 'ana'));
    await assertSucceeds(edit(ana, 'm2', { active: false }, 'ana'));
    await assertSucceeds(edit(ana, 'm2', { sortKey: 7 }, 'ana'));
    await assertSucceeds(edit(ana, 'm4', { deleted: true }, 'ana'));
  });
  test('a member does none of that, to others or to themselves', async () => {
    const luis = as(env, 'luis');
    await assertFails(setDoc(g(luis, GROUP, 'members/m9'), memberData({ name: 'Max', userUid: null, sortKey: 9, ...fresh('luis') })));
    for (const id of ['m1', 'm2', 'm4']) {
      await assertFails(edit(luis, id, { defaultWeight: 2 }, 'luis'));
      await assertFails(edit(luis, id, { active: false }, 'luis'));
      await assertFails(edit(luis, id, { sortKey: 7 }, 'luis'));
      await assertFails(edit(luis, id, { deleted: true }, 'luis'));
    }
    await assertFails(edit(luis, 'm1', { name: 'Ana G.' }, 'luis'));
    await assertFails(edit(luis, 'm4', { name: 'Marta' }, 'luis'));
    await assertFails(edit(luis, 'm1', { avatar: 'x' }, 'luis'));
  });
  test('a member changes their own personal fields', async () => {
    const luis = as(env, 'luis');
    await assertSucceeds(edit(luis, 'm2', { name: 'Luis G.' }, 'luis'));
    await assertSucceeds(edit(luis, 'm2', { avatar: 'cat' }, 'luis'));
    await assertSucceeds(edit(luis, 'm2', { payMethods: { bizum: '600111222' } }, 'luis'));
    await assertSucceeds(edit(luis, 'm2', { linked: true }, 'luis'));
    await assertSucceeds(edit(luis, 'm2', { userUid: null, pastUids: ['luis'], linked: false }, 'luis'));
  });
  test('claiming a ghost and releasing it go through for a member', async () => {
    const luis = as(env, 'luis');
    await assertSucceeds(edit(luis, 'm4', { userUid: 'luis' }, 'luis'));
    await assertSucceeds(edit(luis, 'm4', { userUid: null, pastUids: ['luis'] }, 'luis'));
  });
  test('a member joins by creating their own member; not a ghost, nor with denied permissions', async () => {
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis', 'zoe', 'max'] }) });
    const max = as(env, 'max');
    await assertSucceeds(setDoc(g(max, GROUP, 'members/m7'), memberData({ name: 'Max', userUid: 'max', sortKey: 7, ...fresh('max') })));
    await assertSucceeds(setDoc(g(max, GROUP, 'members/m8'),
      memberData({ name: 'Max', userUid: 'max', sortKey: 8, deniedPermissions: [], ...fresh('max') })));
    await assertFails(setDoc(g(max, GROUP, 'members/m9'), memberData({ name: 'Ghost', userUid: null, sortKey: 9, ...fresh('max') })));
    await assertFails(setDoc(g(max, GROUP, 'members/m10'),
      memberData({ name: 'Max', userUid: 'max', sortKey: 10, deniedPermissions: ['ADD_EXPENSES'], ...fresh('max') })));
  });
  test('in a group whose admin is another member, the same applies', async () => {
    const edit2 = (db, id, change, by) => updateDoc(g(db, G2, `members/${id}`), { ...change, ...bump(by) });
    await assertSucceeds(edit2(as(env, 'luis'), 'm3', { defaultWeight: 3 }, 'luis'));
    await assertFails(edit2(as(env, 'ana'), 'm3', { defaultWeight: 4 }, 'ana'));
    await assertFails(setDoc(g(as(env, 'ana'), G2, 'members/m9'), memberData({ userUid: null, sortKey: 9, ...fresh('ana') })));
    await assertSucceeds(setDoc(g(as(env, 'luis'), G2, 'members/m9'), memberData({ userUid: null, sortKey: 9, ...fresh('luis') })));
  });
});

describe('members: deniedPermissions', () => {
  const edit = (db, id, change, by, group = GROUP) => updateDoc(g(db, group, `members/${id}`), { ...change, ...bump(by) });

  test('the admin grants and takes away each permission, any subset', async () => {
    const ana = as(env, 'ana');
    for (const denied of [['ADD_EXPENSES'], ['EDIT_EXPENSES'], ['RECORD_PAYMENTS'], ALL, []]) {
      await assertSucceeds(edit(ana, 'm2', { deniedPermissions: denied }, 'ana'));
    }
  });
  test('only the three flags are valid', async () => {
    const ana = as(env, 'ana');
    await assertFails(edit(ana, 'm2', { deniedPermissions: ['ADMIN'] }, 'ana'));
    await assertFails(edit(ana, 'm2', { deniedPermissions: ['ADD_EXPENSES', 'nope'] }, 'ana'));
    await assertFails(edit(ana, 'm2', { deniedPermissions: [5] }, 'ana'));
    await assertFails(edit(ana, 'm2', { deniedPermissions: 'ADD_EXPENSES' }, 'ana'));
    await assertFails(edit(ana, 'm2', { deniedPermissions: [...ALL, 'ADD_EXPENSES'] }, 'ana'));
  });
  test('a member cannot change them: not their own, nor somebody else\'s', async () => {
    const luis = as(env, 'luis');
    await assertFails(edit(luis, 'm2', { deniedPermissions: ['ADD_EXPENSES'] }, 'luis'));
    await assertFails(edit(luis, 'm3', { deniedPermissions: ['ADD_EXPENSES'] }, 'luis'));
    await assertFails(edit(as(env, 'zoe'), 'm3', { deniedPermissions: ['EDIT_EXPENSES'] }, 'zoe', G2));
  });
  test('a member cannot lift what the admin took away, even by uploading the member whole', async () => {
    await setup({ m2: { deniedPermissions: ['ADD_EXPENSES'] } });
    const luis = as(env, 'luis');
    await assertFails(edit(luis, 'm2', { deniedPermissions: [] }, 'luis'));
    await assertFails(setDoc(g(luis, GROUP, 'members/m2'), memberData({ name: 'Luis', userUid: 'luis', sortKey: 1, ...replaced('luis') })));
    // Their own personal edits keep working with the list untouched.
    await assertSucceeds(edit(luis, 'm2', { avatar: 'cat' }, 'luis'));
    await assertSucceeds(setDoc(g(luis, GROUP, 'members/m2'),
      memberData({ name: 'Luis G.', userUid: 'luis', sortKey: 1, deniedPermissions: ['ADD_EXPENSES'], version: 3, updatedAt: serverTimestamp(), updatedBy: 'luis' })));
  });
  test('a member document without the key and one with an empty list are the same', async () => {
    const luis = as(env, 'luis');
    await assertSucceeds(edit(luis, 'm2', { deniedPermissions: [] }, 'luis'));
    await assertSucceeds(setDoc(g(as(env, 'ana'), GROUP, 'members/m1'),
      memberData({ name: 'Ana G.', userUid: 'ana', deniedPermissions: [], ...replaced('ana') })));
  });
});

describe('expenses: author and permissions', () => {
  const create = (db, by, extra = {}, id = 'e9') =>
    setDoc(g(db, GROUP, `expenses/${id}`), expenseData({ createdBy: by.uid, ...extra, ...fresh(by.uid) }));
  const ana = { uid: 'ana' }, luis = { uid: 'luis' }, zoe = { uid: 'zoe' };

  test('old clients: no byMember or null is not checked', async () => {
    await setup({ m2: { deniedPermissions: ALL } });
    await assertSucceeds(create(as(env, 'luis'), luis));
    await assertSucceeds(create(as(env, 'luis'), luis, { byMember: null }, 'e8'));
  });
  test('a member records themselves as the author', async () => {
    await assertSucceeds(create(as(env, 'luis'), luis, { byMember: 'm2' }));
  });
  test('but not somebody else, a missing member or a non-text', async () => {
    const db = as(env, 'luis');
    await assertFails(create(db, luis, { byMember: 'm1' }));
    await assertFails(create(db, luis, { byMember: 'm4' }));
    await assertFails(create(db, luis, { byMember: 'nobody' }));
    await assertFails(create(db, luis, { byMember: 7 }));
  });
  test('ADD_EXPENSES decides the creation, the others do not', async () => {
    await setup({ m2: { deniedPermissions: ['ADD_EXPENSES'] }, m3: { deniedPermissions: ['EDIT_EXPENSES', 'RECORD_PAYMENTS'] } });
    await assertFails(create(as(env, 'luis'), luis, { byMember: 'm2' }));
    await assertSucceeds(create(as(env, 'zoe'), zoe, { byMember: 'm3' }));
  });
  test('EDIT_EXPENSES decides editing and soft deleting, ADD_EXPENSES and RECORD_PAYMENTS do not', async () => {
    await setup({ m2: { deniedPermissions: ['EDIT_EXPENSES'] }, m3: { deniedPermissions: ['ADD_EXPENSES', 'RECORD_PAYMENTS'] } });
    await seed(env, { [`groups/${GROUP}/expenses/e1`]: expenseData() });
    const edit = (db, change) => updateDoc(g(db, GROUP, 'expenses/e1'), change);
    await assertFails(edit(as(env, 'luis'), { title: 'Otra', byMember: 'm2', ...bump('luis') }));
    await assertFails(edit(as(env, 'luis'), { deleted: true, byMember: 'm2', ...bump('luis') }));
    await assertSucceeds(edit(as(env, 'zoe'), { title: 'Otra', byMember: 'm3', ...bump('zoe') }));
    await assertSucceeds(edit(as(env, 'zoe'), { deleted: true, byMember: 'm3', version: 3, updatedAt: serverTimestamp(), updatedBy: 'zoe' }));
    // Restoring is an edit too.
    await assertSucceeds(edit(as(env, 'zoe'), { deleted: false, byMember: 'm3', version: 4, updatedAt: serverTimestamp(), updatedBy: 'zoe' }));
  });
  test('an edit cannot name another member as the actor', async () => {
    await seed(env, { [`groups/${GROUP}/expenses/e1`]: expenseData() });
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP, 'expenses/e1'), { title: 'Otra', byMember: 'm1', ...bump('luis') }));
  });
  test('an old client replacing a document whole drops byMember and is not checked (it cannot be forced)', async () => {
    await setup({ m2: { deniedPermissions: ['EDIT_EXPENSES'] } });
    await seed(env, { [`groups/${GROUP}/expenses/e1`]: expenseData({ byMember: 'm1' }) });
    await assertSucceeds(setDoc(g(as(env, 'luis'), GROUP, 'expenses/e1'), expenseData({ title: 'Otra', ...replaced('luis') })));
  });
  test('the admin has every permission, even a stale denied list on their member', async () => {
    await setup({ m1: { deniedPermissions: ALL } });
    await assertSucceeds(create(as(env, 'ana'), ana, { byMember: 'm1' }));
    await seed(env, { [`groups/${GROUP}/expenses/e1`]: expenseData() });
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP, 'expenses/e1'), { title: 'Otra', byMember: 'm1', ...bump('ana') }));
  });
  test('in a group with an admin member, the admin is that member and the creator has no privilege', async () => {
    await setup({ m1: { deniedPermissions: ['ADD_EXPENSES'] }, m2: { deniedPermissions: ['ADD_EXPENSES'] } });
    const make = (db, uid, by) => setDoc(g(db, G2, `expenses/e-${uid}`), expenseData({ createdBy: uid, byMember: by, ...fresh(uid) }));
    await assertSucceeds(make(as(env, 'luis'), 'luis', 'm2'));
    await assertFails(make(as(env, 'ana'), 'ana', 'm1'));
  });
});

describe('transfers: author, permissions and pair', () => {
  const create = (db, uid, extra = {}, id = 't9') =>
    setDoc(g(db, GROUP, `transfers/${id}`), transferData({ createdBy: uid, ...extra, ...fresh(uid) }));

  test('old clients: no byMember or null can record any pair', async () => {
    await setup({ m2: { deniedPermissions: ALL } });
    await assertSucceeds(create(as(env, 'luis'), 'luis', { fromMemberId: 'm1', toMemberId: 'm3' }));
    await assertSucceeds(create(as(env, 'luis'), 'luis', { fromMemberId: 'm1', toMemberId: 'm3', byMember: null }, 't8'));
  });
  test('a member records payments in which they pay or receive', async () => {
    const luis = as(env, 'luis');
    await assertSucceeds(create(luis, 'luis', { fromMemberId: 'm2', toMemberId: 'm1', byMember: 'm2' }));
    await assertSucceeds(create(luis, 'luis', { fromMemberId: 'm1', toMemberId: 'm2', byMember: 'm2' }, 't8'));
  });
  test('not between two others', async () => {
    await assertFails(create(as(env, 'luis'), 'luis', { fromMemberId: 'm1', toMemberId: 'm3', byMember: 'm2' }));
  });
  test('nor as somebody else, nor as a missing member', async () => {
    const luis = as(env, 'luis');
    await assertFails(create(luis, 'luis', { fromMemberId: 'm1', toMemberId: 'm2', byMember: 'm1' }));
    await assertFails(create(luis, 'luis', { fromMemberId: 'm2', toMemberId: 'm1', byMember: 'nobody' }));
  });
  test('the admin records any pair, whoever it is', async () => {
    await assertSucceeds(create(as(env, 'ana'), 'ana', { fromMemberId: 'm2', toMemberId: 'm3', byMember: 'm1' }));
    await assertSucceeds(setDoc(g(as(env, 'luis'), G2, 'transfers/t1'),
      transferData({ fromMemberId: 'm1', toMemberId: 'm3', byMember: 'm2', createdBy: 'luis', ...fresh('luis') })));
    await assertFails(setDoc(g(as(env, 'ana'), G2, 'transfers/t2'),
      transferData({ fromMemberId: 'm2', toMemberId: 'm3', byMember: 'm1', createdBy: 'ana', ...fresh('ana') })));
  });
  test('RECORD_PAYMENTS decides creating, soft deleting and restoring; the others do not', async () => {
    await setup({ m2: { deniedPermissions: ['RECORD_PAYMENTS'] }, m3: { deniedPermissions: ['ADD_EXPENSES', 'EDIT_EXPENSES'] } });
    await seed(env, {
      [`groups/${GROUP}/transfers/t1`]: transferData({ fromMemberId: 'm2', toMemberId: 'm1' }),
      [`groups/${GROUP}/transfers/t2`]: transferData({ fromMemberId: 'm3', toMemberId: 'm1' }),
    });
    const luis = as(env, 'luis');
    await assertFails(create(luis, 'luis', { fromMemberId: 'm2', toMemberId: 'm1', byMember: 'm2' }));
    await assertFails(updateDoc(g(luis, GROUP, 'transfers/t1'), { deleted: true, byMember: 'm2', ...bump('luis') }));
    const zoe = as(env, 'zoe');
    await assertSucceeds(create(zoe, 'zoe', { fromMemberId: 'm3', toMemberId: 'm1', byMember: 'm3' }));
    await assertSucceeds(updateDoc(g(zoe, GROUP, 'transfers/t2'), { deleted: true, byMember: 'm3', ...bump('zoe') }));
    await assertSucceeds(updateDoc(g(zoe, GROUP, 'transfers/t2'),
      { deleted: false, byMember: 'm3', version: 3, updatedAt: serverTimestamp(), updatedBy: 'zoe' }));
  });
  test('a non-admin does not delete a payment between two others; the admin does', async () => {
    await seed(env, { [`groups/${GROUP}/transfers/t1`]: transferData({ fromMemberId: 'm1', toMemberId: 'm3' }) });
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP, 'transfers/t1'), { deleted: true, byMember: 'm2', ...bump('luis') }));
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP, 'transfers/t1'), { deleted: true, byMember: 'm1', ...bump('ana') }));
  });
  test('an old client replacing a payment whole to delete it is not checked', async () => {
    await setup({ m2: { deniedPermissions: ['RECORD_PAYMENTS'] } });
    await seed(env, { [`groups/${GROUP}/transfers/t1`]: transferData({ fromMemberId: 'm1', toMemberId: 'm3', byMember: 'm1' }) });
    await assertSucceeds(setDoc(g(as(env, 'luis'), GROUP, 'transfers/t1'), transferData({ fromMemberId: 'm1', toMemberId: 'm3', deleted: true, ...replaced('luis') })));
  });
});

describe('merging members: the admin adds the accounts of the member merged in', () => {
  const merged = (by, pastUids) => ({ pastUids, ...bump(by) });
  test('the admin can add past accounts to another member', async () => {
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP, 'members/m4'), merged('ana', ['old-uid'])));
  });
  test('the list can only grow, and only the admin grows it for someone else', async () => {
    await setup({ m4: { pastUids: ['a', 'b'] } });
    await assertSucceeds(updateDoc(g(as(env, 'ana'), GROUP, 'members/m4'), merged('ana', ['a', 'b', 'c'])));
    await assertFails(updateDoc(g(as(env, 'ana'), GROUP, 'members/m4'), merged('ana', ['a'])));
    await assertFails(updateDoc(g(as(env, 'luis'), GROUP, 'members/m4'), merged('luis', ['a', 'b', 'c', 'd'])));
  });
  test('it stays within 20 accounts', async () => {
    const many = Array.from({ length: 21 }, (_, i) => `u${i}`);
    await assertFails(updateDoc(g(as(env, 'ana'), GROUP, 'members/m4'), merged('ana', many)));
  });
});

describe('merging members: the admin moves payments to the member kept', () => {
  const T = `groups/${GROUP}/transfers/t1`;
  beforeEach(async () => {
    await seed(env, { [T]: transferData({ fromMemberId: 'm4', toMemberId: 'm1', createdBy: 'ana' }) });
  });
  const moved = (by, fields) => ({ ...fields, ...bump(by) });
  test('the admin can replace a member on a payment', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), T), moved('ana', { fromMemberId: 'm3' })));
  });
  test('nobody else can change who pays whom, nor the amount', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), T), moved('luis', { fromMemberId: 'm3' })));
    await assertFails(updateDoc(doc(as(env, 'ana'), T), moved('ana', { amountMinor: 5 })));
  });
  test('a payment cannot end up between the same member', async () => {
    await assertFails(updateDoc(doc(as(env, 'ana'), T), moved('ana', { fromMemberId: 'm1' })));
  });
  test('a payment between the two merged members is deleted as it was', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), T), moved('ana', { deleted: true })));
  });
});
