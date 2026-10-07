import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore';
import { CODE, GROUP, as, createEnv, groupData, seed } from './helpers.mjs';

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, {
    [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }),
    [`invites/${CODE}`]: {
      groupId: GROUP, groupName: 'Ibiza 2026', groupIcon: 'plane', memberCount: 2, createdBy: 'ana', createdAt: 1,
    },
  });
});

const entry = (author) => ({
  entityType: 'EXPENSE', entityId: 'e1', action: 'CREATED', authorUid: author, diff: [], createdAt: 1,
});

describe('changelog', () => {
  test('members append history as themselves, never edit it', async () => {
    const ref = doc(as(env, 'luis'), `groups/${GROUP}/changelog/c1`);
    await assertSucceeds(setDoc(ref, entry('luis')));
    await assertFails(updateDoc(ref, { action: 'DELETED' }));
    await assertFails(deleteDoc(ref));
  });
  test('history cannot be written in someone else\'s name', async () => {
    await assertFails(setDoc(doc(as(env, 'luis'), `groups/${GROUP}/changelog/c2`), entry('ana')));
  });
});

describe('invites', () => {
  test('any signed-in user can open an invite by code but not list them', async () => {
    await assertSucceeds(getDoc(doc(as(env, 'zoe'), `invites/${CODE}`)));
    await assertFails(getDocs(collection(as(env, 'zoe'), 'invites')));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `invites/${CODE}`)));
  });
  test('only members create the invite of the group current code', async () => {
    const data = {
      groupId: GROUP, groupName: 'Ibiza 2026', groupIcon: 'plane', memberCount: 2, createdBy: 'luis', createdAt: 2,
    };
    await env.clearFirestore();
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }) });
    await assertSucceeds(setDoc(doc(as(env, 'luis'), `invites/${CODE}`), data));
    await assertFails(setDoc(doc(as(env, 'luis'), 'invites/OTHER234'), data));
    await assertFails(setDoc(doc(as(env, 'zoe'), `invites/${CODE}`), { ...data, createdBy: 'zoe' }));
  });
});

describe('users', () => {
  test('a user reads and writes only their own profile', async () => {
    await assertSucceeds(setDoc(doc(as(env, 'ana'), 'users/ana'), { displayName: 'Ana' }));
    await assertFails(getDoc(doc(as(env, 'luis'), 'users/ana')));
  });
});
