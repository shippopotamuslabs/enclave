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

// ENC-239: entitlement gate over Storage user paths + shared_chat_media.
// `noent` has no entitlement docs, `ent` has an active stripe doc, `ovr` has
// only an override doc, and `exp` has an expired stripe doc. Storage objects
// and Firestore entitlement docs are seeded with rules disabled; all gated
// ops run through authenticated/unauthenticated storage contexts.
describe('storage entitlement gate (security rules)', function () {
    this.timeout(60000);
    let rulesEnv: RulesTestEnvironment;

    before(async () => {
        const repoRoot = path.resolve(__dirname, '..', '..');
        const firebaseJson = JSON.parse(fs.readFileSync(path.join(repoRoot, 'firebase.json'), 'utf8')) as {
            firestore?: { rules?: string };
            storage?: { rules?: string };
        };
        expect(firebaseJson.firestore?.rules).to.equal('firestore.rules');
        expect(firebaseJson.storage?.rules).to.equal('storage.rules');
        const firestoreRules = firebaseJson.firestore?.rules;
        const storageRules = firebaseJson.storage?.rules;
        if (!firestoreRules || !storageRules) {
            throw new Error('firebase.json missing firestore/storage rules paths');
        }
        rulesEnv = await initializeTestEnvironment({
            projectId: 'enclave-storage-entitlement-test',
            firestore: {
                rules: fs.readFileSync(path.join(repoRoot, firestoreRules), 'utf8'),
            },
            storage: {
                rules: fs.readFileSync(path.join(repoRoot, storageRules), 'utf8'),
            },
        });
    });

    beforeEach(async () => {
        await rulesEnv.clearFirestore();
        await rulesEnv.clearStorage();
        await rulesEnv.withSecurityRulesDisabled(async (ctx) => {
            const db = ctx.firestore();
            await setDoc(doc(db, 'users/ent/entitlement/stripe'), ACTIVE_STRIPE);
            await setDoc(doc(db, 'users/ovr/entitlement/override'), { freeAccess: true });
            await setDoc(doc(db, 'users/exp/entitlement/stripe'), EXPIRED_STRIPE);
            await setDoc(doc(db, 'shared_chats/owned-by-other'), { ownerId: 'someone-else' });
            const storage = ctx.storage();
            await storage.ref('users/noent/media/seed').putString('seed-media');
            await storage.ref('users/noent/files/seed').putString('seed-files');
            await storage.ref('users/ent/media/seed').putString('seed-ent-media');
            await storage.ref('users/ovr/media/seed').putString('seed-ovr-media');
            await storage.ref('users/exp/media/seed').putString('seed-exp-media');
            await storage.ref('users/noent/media/deletable').putString('delete-me');
            await storage.ref('shared_chat_media/share-1/blob').putString('shared-seed');
        });
    });

    after(async () => {
        await rulesEnv.cleanup();
    });

    it('non-entitled owner upload denied on users/{uid}/media and files paths', async () => {
        const storage = rulesEnv.authenticatedContext('noent').storage();
        await assertFails(storage.ref('users/noent/media/x').putString('hello').then(() => 'ok'));
        await assertFails(storage.ref('users/noent/files/x').putString('hello').then(() => 'ok'));
    });

    it('non-entitled owner download denied on users/{uid}/media and files paths', async () => {
        const storage = rulesEnv.authenticatedContext('noent').storage();
        await assertFails(storage.ref('users/noent/media/seed').getMetadata());
        await assertFails(storage.ref('users/noent/files/seed').getMetadata());
    });

    it('non-entitled owner can delete own object (purge carve-out)', async () => {
        const storage = rulesEnv.authenticatedContext('noent').storage();
        await assertSucceeds(storage.ref('users/noent/media/deletable').delete());
    });

    it('entitled stripe owner can upload and download', async () => {
        const storage = rulesEnv.authenticatedContext('ent').storage();
        await assertSucceeds(storage.ref('users/ent/media/x').putString('hello').then(() => 'ok'));
        await assertSucceeds(storage.ref('users/ent/media/x').getMetadata());
        await assertSucceeds(storage.ref('users/ent/files/y').putString('hello').then(() => 'ok'));
        await assertSucceeds(storage.ref('users/ent/media/seed').getMetadata());
    });

    it('override freeAccess grants upload and download with no stripe doc', async () => {
        const storage = rulesEnv.authenticatedContext('ovr').storage();
        await assertSucceeds(storage.ref('users/ovr/media/x').putString('hello').then(() => 'ok'));
        await assertSucceeds(storage.ref('users/ovr/media/x').getMetadata());
    });

    it('expired stripe entitlement denies upload and download', async () => {
        const storage = rulesEnv.authenticatedContext('exp').storage();
        await assertFails(storage.ref('users/exp/media/x').putString('hello').then(() => 'ok'));
        await assertFails(storage.ref('users/exp/media/seed').getMetadata());
    });

    it('100MB cap denies oversized upload for entitled owner', async function () {
        this.timeout(180000);
        const storage = rulesEnv.authenticatedContext('ent').storage();
        const big = Buffer.alloc(101 * 1024 * 1024, 1);
        await assertFails(storage.ref('users/ent/media/big').put(big).then(() => 'ok'));
    });

    it('non-entitled write to shared_chat_media denied', async () => {
        const storage = rulesEnv.authenticatedContext('noent').storage();
        await assertFails(storage.ref('shared_chat_media/fresh-share/blob').putString('hello').then(() => 'ok'));
    });

    it('entitled write to shared_chat_media succeeds with no shared_chats doc', async () => {
        const storage = rulesEnv.authenticatedContext('ent').storage();
        await assertSucceeds(storage.ref('shared_chat_media/fresh-share/blob').putString('hello').then(() => 'ok'));
    });

    it('entitled write to shared_chat_media denied when shared_chats doc has different owner', async () => {
        const storage = rulesEnv.authenticatedContext('ent').storage();
        await assertFails(storage.ref('shared_chat_media/owned-by-other/blob').putString('hello').then(() => 'ok'));
    });

    it('unauthenticated public read of seeded shared object succeeds', async () => {
        const storage = rulesEnv.unauthenticatedContext().storage();
        await assertSucceeds(storage.ref('shared_chat_media/share-1/blob').getMetadata());
    });

    it('temp_extractions and temp_generations writes denied for entitled owner (catch-all)', async () => {
        const storage = rulesEnv.authenticatedContext('ent').storage();
        await assertFails(storage.ref('temp_extractions/ent/f').putString('x').then(() => 'ok'));
        await assertFails(storage.ref('temp_generations/ent/f').putString('x').then(() => 'ok'));
    });
});
