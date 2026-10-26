import { expect } from 'chai';
import {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment,
    RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';
import * as fs from 'fs';
import 'mocha';
import * as path from 'path';

const ACTIVE_STRIPE = {
    active: true,
    stripeCustomerId: 'cus_ent',
    stripeSubscriptionId: 'sub_ent',
    expiryTimeMillis: 9999999999999,
    updatedAt: 1700000000000,
};

const EXPIRED_STRIPE = {
    active: true,
    stripeCustomerId: 'cus_exp',
    stripeSubscriptionId: 'sub_exp',
    expiryTimeMillis: 1,
    updatedAt: 1700000000000,
};

const VALID_META = {
    _sync_device_id: 'device-a',
    _sync_client_mutation_id: 'mutation-a',
    _sync_written_at: 1700000001000,
};

// ENC-238: entitlement gate over the sync surface. `noent` has no entitlement
// docs, `ent` has an active stripe doc, `ovr` has only an override doc, and
// `exp` has an expired stripe doc.
describe('entitlement gate on sync surface (security rules)', () => {
    let rulesEnv: RulesTestEnvironment;

    before(async () => {
        const repoRoot = path.resolve(__dirname, '..', '..');
        const firebaseJson = JSON.parse(fs.readFileSync(path.join(repoRoot, 'firebase.json'), 'utf8')) as {
            firestore?: { rules?: string };
        };
        const rulesPath = firebaseJson.firestore?.rules;
        expect(rulesPath).to.equal('firestore.rules');
        if (!rulesPath) {
            throw new Error('firebase.json missing firestore.rules path');
        }
        rulesEnv = await initializeTestEnvironment({
            projectId: 'enclave-entitlement-gate-test',
            firestore: {
                rules: fs.readFileSync(path.join(repoRoot, rulesPath), 'utf8'),
            },
        });
    });

    beforeEach(async () => {
        await rulesEnv.clearFirestore();
        await rulesEnv.withSecurityRulesDisabled(async (ctx) => {
            const db = ctx.firestore();
            await setDoc(doc(db, 'users/ent/entitlement/stripe'), ACTIVE_STRIPE);
            await setDoc(doc(db, 'users/ovr/entitlement/override'), { freeAccess: true });
            await setDoc(doc(db, 'users/exp/entitlement/stripe'), EXPIRED_STRIPE);
            // Seed entity docs for update-path tests (both gated and carve-out uids).
            await setDoc(doc(db, 'users/noent/chats/chat-1'), { name: 'seed', ...VALID_META });
            await setDoc(doc(db, 'users/ent/chats/chat-1'), { name: 'seed', ...VALID_META });
        });
    });

    after(async () => {
        await rulesEnv.cleanup();
    });

    it('non-entitled owner cannot read allowlisted entities', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        for (const collection of ['chats', 'messages', 'api_keys', 'documents', 'studio_gallery']) {
            await assertFails(db.doc(`users/noent/${collection}/doc-1`).get());
        }
    });

    it('non-entitled owner cannot create allowlisted entities', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        for (const collection of ['chats', 'messages', 'api_keys', 'documents', 'studio_gallery']) {
            await assertFails(db.doc(`users/noent/${collection}/doc-1`).set({ name: 'x', ...VALID_META }));
        }
    });

    it('non-entitled owner cannot update allowlisted entities', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        await assertFails(db.doc('users/noent/chats/chat-1').set({ name: 'changed', ...VALID_META }));
    });

    it('non-entitled owner cannot read sync_events or sync_meta', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        await assertFails(db.doc('users/noent/sync_events/00000000000000000001').get());
        await assertFails(db.doc('users/noent/sync_meta/state').get());
    });

    it('entitled owner can read and write allowlisted entities', async () => {
        const db = rulesEnv.authenticatedContext('ent').firestore();
        for (const collection of ['chats', 'messages', 'api_keys', 'documents', 'studio_gallery']) {
            await assertSucceeds(db.doc(`users/ent/${collection}/doc-1`).set({ name: 'x', ...VALID_META }));
            await assertSucceeds(db.doc(`users/ent/${collection}/doc-1`).get());
        }
        await assertSucceeds(db.doc('users/ent/chats/chat-1').set({ name: 'changed', ...VALID_META }));
    });

    it('entitled owner can read sync_events and sync_meta but cannot write them', async () => {
        const db = rulesEnv.authenticatedContext('ent').firestore();
        await assertSucceeds(db.doc('users/ent/sync_events/00000000000000000001').get());
        await assertSucceeds(db.doc('users/ent/sync_meta/state').get());
        await assertFails(db.doc('users/ent/sync_events/00000000000000000002').set({ sequence: 2 }));
        await assertFails(db.doc('users/ent/sync_meta/state').set({ value: 1 }));
    });

    it('invalid sync metadata is still denied while entitled', async () => {
        const db = rulesEnv.authenticatedContext('ent').firestore();
        await assertFails(
            db.doc('users/ent/chats/chat-1').set({
                name: 'x',
                _sync_device_id: ['not-scalar'],
                _sync_written_at: '1700000001000',
            }),
        );
    });

    it('override freeAccess grants access with no stripe doc', async () => {
        const db = rulesEnv.authenticatedContext('ovr').firestore();
        await assertSucceeds(db.doc('users/ovr/chats/chat-1').set({ name: 'x', ...VALID_META }));
        await assertSucceeds(db.doc('users/ovr/chats/chat-1').get());
        await assertSucceeds(db.doc('users/ovr/sync_events/00000000000000000001').get());
        await assertSucceeds(db.doc('users/ovr/sync_meta/state').get());
    });

    it('expired stripe entitlement denies gated reads and writes', async () => {
        const db = rulesEnv.authenticatedContext('exp').firestore();
        await assertFails(db.doc('users/exp/chats/chat-1').get());
        await assertFails(db.doc('users/exp/chats/chat-1').set({ name: 'x', ...VALID_META }));
        await assertFails(db.doc('users/exp/sync_events/00000000000000000001').get());
        await assertFails(db.doc('users/exp/sync_meta/state').get());
    });

    it('non-entitled owner keeps users/{uid} doc read+write', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        await assertSucceeds(db.doc('users/noent').set({ displayName: 'noent' }));
        await assertSucceeds(db.doc('users/noent').get());
    });

    it('non-entitled owner can read but not write entitlement docs', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        await assertSucceeds(db.doc('users/noent/entitlement/stripe').get());
        await assertSucceeds(db.doc('users/noent/entitlement/override').get());
        await assertFails(db.doc('users/noent/entitlement/stripe').set({ active: true }));
    });

    it('non-entitled owner can delete allowlisted entities (purge carve-out)', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        await assertSucceeds(db.doc('users/noent/chats/chat-1').delete());
    });

    it('non-entitled owner can file moderation reports', async () => {
        const db = rulesEnv.authenticatedContext('noent').firestore();
        await assertSucceeds(
            db.collection('moderation').add({
                prompt: 'is this blocked?',
                outcome: 'blocked',
                timestamp: new Date(),
                version: 1,
            }),
        );
    });
});
