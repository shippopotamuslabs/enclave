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

describe('sync event feed (security rules)', () => {
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
            projectId: 'pocket-crew-sync-event-feed-test',
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

    it('SCN-SEC-001 owner can read sync events but cannot write them', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertSucceeds(ownerFirestore.doc('users/alice/sync_events/00000000000000000001').get());
        await assertFails(
            ownerFirestore.doc('users/alice/sync_events/00000000000000000002').set({
                sequence: 2,
                schemaVersion: 1,
            }),
        );
    });

    it('SCN-SEC-002 non-owner cannot read owner sync events', async () => {
        const nonOwnerFirestore = rulesEnv.authenticatedContext('bob').firestore();

        await assertFails(nonOwnerFirestore.doc('users/alice/sync_events/00000000000000000001').get());
    });

    it('allows only owner writes to allowlisted entity collections', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertSucceeds(
            ownerFirestore.doc('users/alice/chats/chat-1').set({
                name: 'cipher',
                _sync_device_id: 'device-a',
                _sync_client_mutation_id: 'mutation-a',
                _sync_written_at: 1700000001000,
            }),
        );
        await assertFails(
            ownerFirestore.doc('users/alice/not_allowlisted/doc-1').set({
                value: true,
            }),
        );
    });

    it('denies invalid sync metadata field types on entity writes', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertFails(
            ownerFirestore.doc('users/alice/chats/chat-1').set({
                name: 'cipher',
                _sync_device_id: ['not-scalar'],
                _sync_written_at: '1700000001000',
            }),
        );
    });
});
