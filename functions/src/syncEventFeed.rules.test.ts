import { expect } from 'chai';
import {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment,
    RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import * as fs from 'fs';
import { doc, setDoc } from 'firebase/firestore';
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
        // ENC-238: entity + sync feed reads/writes are entitlement-gated.
        // Seed an active stripe entitlement for the owner uid used below so
        // existing assertions keep testing ownership/allowlist/metadata, not
        // entitlement denial. No assertion weakened.
        await rulesEnv.withSecurityRulesDisabled(async (ctx) => {
            await setDoc(doc(ctx.firestore(), 'users/alice/entitlement/stripe'), {
                active: true,
                stripeCustomerId: 'cus_alice',
                stripeSubscriptionId: 'sub_alice',
                expiryTimeMillis: 9999999999999,
                updatedAt: 1700000000000,
            });
        });
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

    it('allows owner writes to message_inference_telemetry', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertSucceeds(
            ownerFirestore.doc('users/alice/message_inference_telemetry/tel-1').set({
                messageId: 'message-1',
                chatId: 'chat-1',
                sequenceIndex: 0,
                providerKey: 'openai',
                providerLabel: 'OpenAI',
                modelId: 'gpt-4o',
                modelLabel: 'GPT-4o',
                sourceKind: 'chat',
                requestStartedAtMs: 1700000000000,
                completedAtMs: 1700000001000,
                costStatus: 'estimated',
                costSource: 'saved_pricing',
                telemetrySchemaVersion: 1,
                updated_at: 1700000001000,
                deleted: false,
                _sync_device_id: 'device-a',
                _sync_client_mutation_id: 'mutation-a',
                _sync_written_at: 1700000001000,
            }),
        );
    });

    it('allows owner writes to live_voice_providers', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertSucceeds(
            ownerFirestore.doc('users/alice/live_voice_providers/lvp-1').set({
                display_name: 'OpenAI Voice',
                provider: 'OPENAI',
                voice_name: 'alloy',
                credential_alias: 'openai-default',
                updated_at: 1700000001000,
                deleted: false,
                _sync_device_id: 'device-a',
                _sync_client_mutation_id: 'mutation-a',
                _sync_written_at: 1700000001000,
            }),
        );
    });

    it('allows owner writes to utility_models', async () => {
        const ownerFirestore = rulesEnv.authenticatedContext('alice').firestore();

        await assertSucceeds(
            ownerFirestore.doc('users/alice/utility_models/util-1').set({
                utility_type: 'onnx_model',
                model_file_format: 'ONNX',
                huggingface_model_name: 'test',
                remote_filename: 'model.onnx',
                local_filename: 'model.onnx',
                sha256: 'abc123',
                size_in_bytes: 100,
                updated_at: 1700000001000,
                deleted: false,
                _sync_device_id: 'device-a',
                _sync_client_mutation_id: 'mutation-a',
                _sync_written_at: 1700000001000,
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
