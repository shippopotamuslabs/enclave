import { expect } from 'chai';
import {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment,
    RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import * as fs from 'fs';
import 'mocha';
import * as path from 'path';

describe('entitlement docs (security rules)', () => {
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
            projectId: 'enclave-entitlement-test',
            firestore: {
                rules: fs.readFileSync(path.join(repoRoot, rulesPath), 'utf8'),
            },
        });
    });

    beforeEach(async () => {
        await rulesEnv.clearFirestore();
    });

    after(async () => {
        await rulesEnv.cleanup();
    });

    it('owner can read own stripe and override entitlement docs', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertSucceeds(ownerFirestore.doc('users/alice/entitlement/stripe').get());
        await assertSucceeds(ownerFirestore.doc('users/alice/entitlement/override').get());
    });

    it('owner cannot write entitlement docs', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertFails(
            ownerFirestore.doc('users/alice/entitlement/stripe').set({
                active: true,
                stripeCustomerId: 'cus_123',
                stripeSubscriptionId: 'sub_123',
                updatedAt: 1700000001000,
            }),
        );
        await assertFails(
            ownerFirestore.doc('users/alice/entitlement/override').set({
                freeAccess: true,
            }),
        );
    });

    it('non-owner cannot read entitlement docs', async () => {
        const nonOwnerFirestore = rulesEnv.authenticatedContext('bob').firestore();

        await assertFails(nonOwnerFirestore.doc('users/alice/entitlement/stripe').get());
        await assertFails(nonOwnerFirestore.doc('users/alice/entitlement/override').get());
    });

    it('unauthenticated client cannot read entitlement docs', async () => {
        const unauthedFirestore = rulesEnv.unauthenticatedContext().firestore();

        await assertFails(unauthedFirestore.doc('users/alice/entitlement/stripe').get());
    });
});
