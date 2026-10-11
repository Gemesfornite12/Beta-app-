import fs from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { ref, get, set, runTransaction, query, orderByKey } from 'firebase/database';

const rules = fs.readFileSync(new URL('../database.rules.json', import.meta.url), 'utf8');
const projectId = 'demo-rtdb-handle-directory-rules-only';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    database: { host: '127.0.0.1', port: 9000, rules },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); });

const root = (db, path = '') => ref(db, path);
const ownPointer = (db, uid) => root(db, `social_test/memberDirectory/byUid/${uid}`);
const handleRef = (db, handle) => root(db, `social_test/memberDirectory/byHandle/${handle}`);
async function seedProfile(uid, matchingUid = uid, directoryOptIn = true) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    await set(root(db, `social_test/profiles/${uid}`), {
      uid: matchingUid, visibility: 'private',
    });
    if (directoryOptIn !== null) {
      await set(root(db, `social_test/preferences/${uid}/memberDirectoryOptIn`), directoryOptIn);
    }
  });
}
async function createIfAbsent(target, value) {
  return runTransaction(target, (current) => current === null ? value : undefined);
}

test('absent or false directory opt-in denies reservation and canonical creation', async () => {
  await seedProfile('absent', 'absent', null);
  const absent = env.authenticatedContext('absent').database();
  await assertFails(createIfAbsent(ownPointer(absent, 'absent'), 'absent_1'));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await set(ownPointer(ctx.database(), 'absent'), 'absent_1');
  });
  await assertFails(set(handleRef(absent, 'absent_1'), { uid: 'absent', handle: 'absent_1' }));

  await seedProfile('declined', 'declined', false);
  const declined = env.authenticatedContext('declined').database();
  await assertFails(createIfAbsent(ownPointer(declined, 'declined'), 'declined_1'));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await set(ownPointer(ctx.database(), 'declined'), 'declined_1');
  });
  await assertFails(set(handleRef(declined, 'declined_1'), { uid: 'declined', handle: 'declined_1' }));
});

test('true directory opt-in permits the bound owner reservation and canonical create', async () => {
  await seedProfile('opted_in');
  const db = env.authenticatedContext('opted_in').database();
  await assertSucceeds(createIfAbsent(ownPointer(db, 'opted_in'), 'opted_in_1'));
  await assertSucceeds(createIfAbsent(handleRef(db, 'opted_in_1'), { uid: 'opted_in', handle: 'opted_in_1' }));
});

test('an authenticated UID cannot borrow another UID\'s directory opt-in', async () => {
  await seedProfile('consenting');
  await seedProfile('nonconsenting', 'nonconsenting', null);
  const consenting = env.authenticatedContext('consenting').database();
  const nonconsenting = env.authenticatedContext('nonconsenting').database();
  await assertFails(set(root(nonconsenting, 'social_test/preferences/consenting/memberDirectoryOptIn'), true));
  await assertFails(createIfAbsent(ownPointer(nonconsenting, 'nonconsenting'), 'borrowed_1'));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await set(ownPointer(ctx.database(), 'nonconsenting'), 'borrowed_1');
  });
  await assertFails(set(handleRef(nonconsenting, 'borrowed_1'), { uid: 'nonconsenting', handle: 'borrowed_1' }));
  await assertSucceeds(get(root(consenting, 'social_test/preferences/consenting/memberDirectoryOptIn')));
});

test('opt-out still permits ordered revoke and stale reservation cleanup', async () => {
  await seedProfile('alice');
  const alice = env.authenticatedContext('alice').database();
  const consent = root(alice, 'social_test/preferences/alice/memberDirectoryOptIn');
  await assertSucceeds(createIfAbsent(ownPointer(alice, 'alice'), 'alice_optout'));
  await assertSucceeds(createIfAbsent(handleRef(alice, 'alice_optout'), { uid: 'alice', handle: 'alice_optout' }));
  await assertSucceeds(set(consent, false));
  await assertFails(set(ownPointer(alice, 'alice'), null));
  await assertSucceeds(set(handleRef(alice, 'alice_optout'), null));
  await assertSucceeds(set(ownPointer(alice, 'alice'), null));
  await assertFails(createIfAbsent(ownPointer(alice, 'alice'), 'alice_after_optout'));

  await seedProfile('stale');
  const stale = env.authenticatedContext('stale').database();
  await assertSucceeds(createIfAbsent(ownPointer(stale, 'stale'), 'stale_slot'));
  await assertSucceeds(set(root(stale, 'social_test/preferences/stale/memberDirectoryOptIn'), false));
  await assertSucceeds(set(ownPointer(stale, 'stale'), null));
});

test('exact authenticated handle read only; parent/list and private reverse slot denied', async () => {
  await seedProfile('alice');
  const alice = env.authenticatedContext('alice').database();
  const bob = env.authenticatedContext('bob').database();
  const unauth = env.unauthenticatedContext().database();
  await assertSucceeds(createIfAbsent(ownPointer(alice, 'alice'), 'alice_1'));
  await assertSucceeds(createIfAbsent(handleRef(alice, 'alice_1'), { uid: 'alice', handle: 'alice_1' }));
  const snapshot = await assertSucceeds(get(handleRef(bob, 'alice_1')));
  assert.deepEqual(snapshot.val(), { uid: 'alice', handle: 'alice_1' });
  await assertFails(get(root(bob, 'social_test/memberDirectory')));
  await assertFails(get(root(bob, 'social_test/memberDirectory/byHandle')));
  await assertFails(get(query(root(bob, 'social_test/memberDirectory/byHandle'), orderByKey())));
  await assertFails(get(root(alice, 'social_test/memberDirectory/byUid')));
  await assertFails(get(root(bob, 'social_test/memberDirectory/byUid')));
  await assertFails(get(query(root(alice, 'social_test/memberDirectory/byUid'), orderByKey())));
  await assertFails(get(handleRef(unauth, 'alice_1')));
  await assertSucceeds(get(ownPointer(alice, 'alice')));
  await assertFails(get(ownPointer(bob, 'alice')));
});

test('UID ownership, profile binding, normalized grammar, and payload shape are enforced', async () => {
  await seedProfile('alice');
  const alice = env.authenticatedContext('alice').database();
  const bob = env.authenticatedContext('bob').database();
  await assertFails(set(ownPointer(bob, 'alice'), 'alice_1'));
  await assertFails(set(ownPointer(alice, 'alice'), 'Bad.Handle'));
  await assertFails(set(ownPointer(alice, 'alice'), 'ab'));
  await assertSucceeds(createIfAbsent(ownPointer(alice, 'alice'), 'alice_1'));
  await assertSucceeds(createIfAbsent(handleRef(alice, 'alice_1'), { uid: 'alice', handle: 'alice_1' }));
  await seedProfile('carol');
  const carol = env.authenticatedContext('carol').database();
  await assertSucceeds(set(ownPointer(carol, 'carol'), 'extra_field'));
  await assertFails(set(handleRef(carol, 'extra_field'), { uid: 'carol', handle: 'extra_field', email: 'private@example.test' }));
  await seedProfile('dave');
  const dave = env.authenticatedContext('dave').database();
  await assertSucceeds(set(ownPointer(dave, 'dave'), 'wrong_field'));
  await assertFails(set(handleRef(dave, 'wrong_field'), { uid: 'dave', handle: 'different_handle' }));
  await assertFails(set(handleRef(bob, 'spoofed'), { uid: 'alice', handle: 'spoofed' }));
});

test('no profile or mismatched profile UID can create a reverse reservation', async () => {
  const db = env.authenticatedContext('alice').database();
  await assertFails(set(ownPointer(db, 'alice'), 'alice_1'));
  await seedProfile('alice', 'someone-else');
  await assertFails(set(ownPointer(db, 'alice'), 'alice_1'));
});

test('one UID slot serializes competing handles; a second active mapping is not possible', async () => {
  await seedProfile('alice');
  const db = env.authenticatedContext('alice').database();
  const slot = ownPointer(db, 'alice');
  const [a, b] = await Promise.all([
    runTransaction(slot, (current) => current === null ? 'alice_1' : undefined),
    runTransaction(slot, (current) => current === null ? 'alice_2' : undefined),
  ]);
  assert.equal(Number(a.committed) + Number(b.committed), 1);
  const current = (await get(slot)).val();
  assert.ok(['alice_1', 'alice_2'].includes(current));
  const second = current === 'alice_1' ? 'alice_2' : 'alice_1';
  await assertFails(set(slot, second));
});

test('same-handle concurrent claim has one canonical winner; loser can clear its stale private reservation', async () => {
  await seedProfile('alice');
  await seedProfile('bob');
  const alice = env.authenticatedContext('alice').database();
  const bob = env.authenticatedContext('bob').database();
  await assertSucceeds(createIfAbsent(ownPointer(alice, 'alice'), 'shared_handle'));
  await assertSucceeds(createIfAbsent(ownPointer(bob, 'bob'), 'shared_handle'));
  const results = await Promise.allSettled([
    createIfAbsent(handleRef(alice, 'shared_handle'), { uid: 'alice', handle: 'shared_handle' }),
    createIfAbsent(handleRef(bob, 'shared_handle'), { uid: 'bob', handle: 'shared_handle' }),
  ]);
  // A losing RTDB transaction may resolve with committed:false when its
  // callback returns undefined after seeing the winner; it need not reject.
  assert.equal(results.filter((x) => x.status === 'fulfilled' && x.value.committed).length, 1);
  assert.equal(results.filter((x) => x.status === 'rejected').length +
    results.filter((x) => x.status === 'fulfilled' && !x.value.committed).length, 1);
  const owner = (await get(handleRef(alice, 'shared_handle'))).val().uid;
  const loser = owner === 'alice' ? bob : alice;
  const loserUid = owner === 'alice' ? 'bob' : 'alice';
  await assertSucceeds(set(ownPointer(loser, loserUid), null));
  assert.equal((await get(handleRef(alice, 'shared_handle'))).val().uid, owner);
});

test('cannot revoke UID lock before mapping; owner revokes mapping then slot, handle becomes reusable', async () => {
  await seedProfile('alice');
  await seedProfile('bob');
  const alice = env.authenticatedContext('alice').database();
  const bob = env.authenticatedContext('bob').database();
  await assertSucceeds(createIfAbsent(ownPointer(alice, 'alice'), 'alice_1'));
  await assertSucceeds(createIfAbsent(handleRef(alice, 'alice_1'), { uid: 'alice', handle: 'alice_1' }));
  await assertFails(set(ownPointer(alice, 'alice'), null));
  await assertFails(set(handleRef(bob, 'alice_1'), null));
  await assertSucceeds(set(handleRef(alice, 'alice_1'), null));
  await assertSucceeds(set(ownPointer(alice, 'alice'), null));
  await assertSucceeds(createIfAbsent(ownPointer(bob, 'bob'), 'alice_1'));
  await assertSucceeds(createIfAbsent(handleRef(bob, 'alice_1'), { uid: 'bob', handle: 'alice_1' }));
  assert.deepEqual((await get(handleRef(alice, 'alice_1'))).val(), { uid: 'bob', handle: 'alice_1' });
});

test('changing handle is staged: old mapping must be revoked before new reservation', async () => {
  await seedProfile('alice');
  const db = env.authenticatedContext('alice').database();
  await assertSucceeds(createIfAbsent(ownPointer(db, 'alice'), 'old_handle'));
  await assertSucceeds(createIfAbsent(handleRef(db, 'old_handle'), { uid: 'alice', handle: 'old_handle' }));
  await assertFails(set(ownPointer(db, 'alice'), 'new_handle'));
  await assertSucceeds(set(handleRef(db, 'old_handle'), null));
  await assertSucceeds(set(ownPointer(db, 'alice'), null));
  await assertSucceeds(createIfAbsent(ownPointer(db, 'alice'), 'new_handle'));
  await assertSucceeds(createIfAbsent(handleRef(db, 'new_handle'), { uid: 'alice', handle: 'new_handle' }));
  assert.equal((await get(handleRef(db, 'old_handle'))).val(), null);
  assert.equal((await get(handleRef(db, 'new_handle'))).val().uid, 'alice');
});
