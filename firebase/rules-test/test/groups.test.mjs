import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp, arrayRemove, arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, increment, query,
  serverTimestamp, setDoc, updateDoc, where,
} from 'firebase/firestore';
import { CODE, GROUP, as, createEnv, groupData, seed } from './helpers.mjs';

let env;
beforeAll(async () => { env = await createEnv(); });
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'] }) });
});

const bump = (by) => ({ version: increment(1), updatedAt: serverTimestamp(), updatedBy: by });

describe('create', () => {
  test('a user creates a group with only themselves as member', async () => {
    const data = groupData({ version: 1, updatedAt: serverTimestamp(), updatedBy: 'ana' });
    await assertSucceeds(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
  test('cannot create a group for someone else', async () => {
    const data = groupData({ memberUids: ['ana', 'zoe'], updatedAt: serverTimestamp() });
    await assertFails(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
  test('the client cannot choose updatedAt', async () => {
    const data = groupData({ updatedAt: Timestamp.now() });
    await assertFails(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
  test('extra fields are rejected', async () => {
    const data = groupData({ updatedAt: serverTimestamp(), owner: 'ana' });
    await assertFails(setDoc(doc(as(env, 'ana'), 'groups/new'), data));
  });
});

describe('read', () => {
  test('members read, strangers do not', async () => {
    await assertSucceeds(getDoc(doc(as(env, 'luis'), `groups/${GROUP}`)));
    await assertFails(getDoc(doc(as(env, 'zoe'), `groups/${GROUP}`)));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `groups/${GROUP}`)));
  });
  test('a member can list their groups with array-contains', async () => {
    const q = query(collection(as(env, 'ana'), 'groups'), where('memberUids', 'array-contains', 'ana'));
    await assertSucceeds(getDocs(q));
  });
});

describe('edit', () => {
  test('the app has to merge group edits: lastJoinCode is not in its document', async () => {
    await seed(env, { [`groups/${GROUP}`]: groupData({ memberUids: ['ana', 'luis'], lastJoinCode: CODE }) });
    const { lastJoinCode, ...withoutCode } = groupData({
      name: 'Ibiza', memberUids: ['ana', 'luis'], version: 2, updatedAt: serverTimestamp(), updatedBy: 'ana',
    });
    const ref = doc(as(env, 'ana'), `groups/${GROUP}`);
    await assertFails(setDoc(ref, withoutCode));
    await assertSucceeds(setDoc(ref, withoutCode, { merge: true }));
  });
  test('the admin renames with a chained version; another member cannot', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), `groups/${GROUP}`), { name: 'Ibiza', ...bump('ana') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { name: 'Otro', ...bump('luis') }));
  });
  test('a stale version is rejected', async () => {
    await assertFails(updateDoc(doc(as(env, 'ana'), `groups/${GROUP}`), {
      name: 'Ibiza', version: 1, updatedAt: serverTimestamp(), updatedBy: 'ana',
    }));
  });
  test('updatedBy must be the caller', async () => {
    await assertFails(updateDoc(doc(as(env, 'ana'), `groups/${GROUP}`), { name: 'Ibiza', ...bump('luis') }));
  });
  test('a member cannot remove someone else', async () => {
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { memberUids: ['luis'], ...bump('luis') }));
  });
  test('archiving touches only your own uid', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { archivedBy: arrayUnion('luis'), ...bump('luis') }));
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { archivedBy: arrayUnion('ana'), ...bump('luis') }));
  });
  test('groups are never deleted', async () => {
    await assertFails(deleteDoc(doc(as(env, 'ana'), `groups/${GROUP}`)));
  });
});

describe('join and leave', () => {
  test('a stranger joins with the current code', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('zoe'), lastJoinCode: CODE, ...bump('zoe'),
    }));
  });
  test('a wrong code is rejected', async () => {
    await assertFails(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('zoe'), lastJoinCode: 'WRONG234', ...bump('zoe'),
    }));
  });
  test('a joiner cannot add someone else or change anything else', async () => {
    await assertFails(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('max'), lastJoinCode: CODE, ...bump('zoe'),
    }));
    await assertFails(updateDoc(doc(as(env, 'zoe'), `groups/${GROUP}`), {
      memberUids: arrayUnion('zoe'), lastJoinCode: CODE, name: 'Mío', ...bump('zoe'),
    }));
  });
  test('a member leaves by removing only themselves', async () => {
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { memberUids: arrayRemove('luis'), ...bump('luis') }));
  });
  test('leaving cannot carry other edits: the client uploads them first, as a separate version', async () => {
    // The outbox compacts an offline rename and a later leave into one write; the app splits it again.
    await assertFails(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { name: 'Mío', memberUids: arrayRemove('luis'), ...bump('luis') }));
    await assertSucceeds(updateDoc(doc(as(env, 'ana'), `groups/${GROUP}`), { name: 'Mío', ...bump('ana') }));
    await assertSucceeds(updateDoc(doc(as(env, 'luis'), `groups/${GROUP}`), { memberUids: arrayRemove('luis'), ...bump('luis') }));
  });
});
