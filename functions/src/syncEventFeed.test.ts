import { expect } from 'chai';
import 'mocha';
import {
    BACKFILL_COLLECTION_ORDER,
    buildSyncFeedStatus,
    handleBackfillSyncFeed,
    handleSyncEntityWrite,
    InMemorySyncFeedDocumentReader,
    InMemorySyncFeedFirestore,
    normalizeSyncFeedState,
    SYNC_FEED_ALLOWLIST,
} from './syncEventFeed';

describe('sync event feed (pure handlers)', () => {
    it('SCN-FUNC-001 allowlist contains utility_models', () => {
        expect(SYNC_FEED_ALLOWLIST).to.include('utility_models');
    });

    it('SCN-FUNC-001b allowlist contains message_inference_telemetry', () => {
        expect(SYNC_FEED_ALLOWLIST).to.include('message_inference_telemetry');
    });

    it('SCN-FUNC-001c allowlist contains live_voice_providers', () => {
        expect(SYNC_FEED_ALLOWLIST).to.include('live_voice_providers');
    });

    it('upsert trigger writes a monotonic sync event', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta.nextSequence = 0;
        store.documents.set('users/alice/chats/chat-1', {
            updated_at: 1700000001000,
            _sync_device_id: 'device-a',
            _sync_client_mutation_id: 'mutation-1',
        });

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-1',
            after: store.documents.get('users/alice/chats/chat-1'),
        });

        expect(store.syncEvents[0].sequence).to.equal(1);
        expect(store.syncEvents[0].operation).to.equal('upsert');
        expect(store.syncEvents[0].collectionPath).to.equal('chats');
        expect(store.syncEvents[0].entityId).to.equal('chat-1');
        expect(store.syncEvents[0].deviceId).to.equal('device-a');
        expect(store.syncEvents[0].clientMutationId).to.equal('mutation-1');
        expect(store.syncMeta.nextSequence).to.equal(1);
    });

    it('SCN-FUNC-002 duplicate trigger event ID writes no duplicate event', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncEvents.push(syncEvent({ sequence: 1, triggerEventId: 'event-1' }));
        store.processedEvents.set('hash-event-1', 1);
        store.syncMeta.nextSequence = 1;

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-1',
        });

        expect(store.syncEvents).to.have.length(1);
        expect(store.syncMeta.nextSequence).to.equal(1);
    });

    it('SCN-FUNC-003 stale delete after recreate does not emit destructive delete', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.documents.set('users/alice/messages/message-1', { updated_at: 1700000002000 });

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'messages',
            entityId: 'message-1',
            triggerEventId: 'event-delete',
            before: { updated_at: 1700000001000 },
            after: null,
        });

        expect(store.syncEvents[0].operation).to.equal('upsert');
        expect(store.syncEvents[0].sourceUpdatedAt).to.equal(1700000002000);
        expect(store.syncEvents[0].sourceExists).to.equal(true);
    });

    it('SCN-FUNC-004 non-allowlisted collection is ignored', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta.nextSequence = 4;

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'sync_events',
            entityId: '00000000000000000004',
            triggerEventId: 'event-sync-event',
        });

        expect(store.syncEvents).to.have.length(0);
        expect(store.syncMeta.nextSequence).to.equal(4);
    });

    it('upsert sequencing uses current source metadata instead of stale trigger data', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.documents.set('users/alice/chats/chat-1', {
            updated_at: 1700000002000,
            _sync_device_id: 'device-current',
            _sync_client_mutation_id: 'mutation-current',
        });

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-stale-upsert',
            after: {
                updated_at: 1700000001000,
                _sync_device_id: 'device-stale',
                _sync_client_mutation_id: 'mutation-stale',
            },
        });

        expect(store.syncEvents[0].sourceUpdatedAt).to.equal(1700000002000);
        expect(store.syncEvents[0].deviceId).to.equal('device-current');
        expect(store.syncEvents[0].clientMutationId).to.equal('mutation-current');
    });

    it('SCN-PURGE-002 chat purge emits remote delete events for other devices', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta.nextSequence = 11;

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-delete-chat-1',
            before: {},
            after: null,
        });

        expect(store.syncEvents[0].sequence).to.equal(12);
        expect(store.syncEvents[0].operation).to.equal('delete');
        expect(store.syncEvents[0].collectionPath).to.equal('chats');
        expect(store.syncEvents[0].entityId).to.equal('chat-1');
    });

    it('SCN-BACKFILL-001 backfill emits dependency-ordered baseline events', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta = normalizeSyncFeedState({ feedInitialized: false, nextSequence: 0 });
        store.documents.set('users/alice/chats/chat-1', { updated_at: 1700000001000 });
        store.documents.set('users/alice/messages/message-1', {
            updated_at: 1700000001000,
            chat_id: 'chat-1',
        });
        store.documents.set('users/alice/message_files/file-1', {
            updated_at: 1700000001000,
            message_id: 'message-1',
            chat_id: 'chat-1',
        });

        const result = await handleBackfillSyncFeed(
            store,
            new InMemorySyncFeedDocumentReader(store),
            'alice',
            1700000002000,
        );

        expect(result.status).to.equal('initialized');
        expect(result.eventsWritten).to.equal(3);
        expect(store.syncEvents[0].collectionPath).to.equal('chats');
        expect(store.syncEvents[1].collectionPath).to.equal('messages');
        expect(store.syncEvents[2].collectionPath).to.equal('message_files');
        expect(store.syncMeta.feedInitialized).to.equal(true);
        expect(store.syncMeta.backfilledAtMillis).to.equal(1700000002000);
    });

    it('SCN-BACKFILL-003 migration marks feed initialized when events already exist', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta = normalizeSyncFeedState({
            feedInitialized: false,
            nextSequence: 79,
        });
        store.syncEvents.push(syncEvent({ sequence: 1, triggerEventId: 'evt-1' }));

        const result = await handleBackfillSyncFeed(
            store,
            new InMemorySyncFeedDocumentReader(store),
            'alice',
            1700000002000,
        );

        expect(result.status).to.equal('initialized');
        expect(result.eventsWritten).to.equal(0);
        expect(result.nextSequence).to.equal(79);
        expect(store.syncMeta.feedInitialized).to.equal(true);
        expect(store.syncEvents).to.have.length(1);
    });

    it('SCN-BACKFILL-002 backfill is idempotent after feedInitialized is true', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta = normalizeSyncFeedState({
            feedInitialized: true,
            nextSequence: 8,
        });
        store.documents.set('users/alice/chats/chat-1', { updated_at: 1700000001000 });

        const result = await handleBackfillSyncFeed(
            store,
            new InMemorySyncFeedDocumentReader(store),
            'alice',
        );

        expect(result.status).to.equal('already_initialized');
        expect(result.eventsWritten).to.equal(0);
        expect(result.nextSequence).to.equal(8);
        expect(store.syncEvents).to.have.length(0);
    });

    it('buildSyncFeedStatus normalizes feed metadata defaults', () => {
        const status = buildSyncFeedStatus(normalizeSyncFeedState({ nextSequence: 12 }));
        expect(status.nextSequence).to.equal(12);
        expect(status.feedInitialized).to.equal(false);
        expect(status.feedEpoch).to.equal(1);
        expect(status.oldestRetainedSequence).to.equal(1);
        expect(status.schemaVersion).to.equal(1);
    });

    it('BACKFILL_COLLECTION_ORDER only contains allowlisted collections', () => {
        for (const collectionPath of BACKFILL_COLLECTION_ORDER) {
            expect(SYNC_FEED_ALLOWLIST).to.include(collectionPath);
        }
    });

    it('SCN-MUT-001 function without idempotency creates duplicate sequences and fails', async () => {
        const store = new InMemorySyncFeedFirestore();
        store.syncMeta.nextSequence = 0;

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-dup',
        });
        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-dup',
        });

        expect(store.syncEvents).to.have.length(1);
        expect(store.syncEvents[0].triggerEventId).to.equal('event-dup');
        expect(store.syncMeta.nextSequence).to.equal(1);
    });
});

describe('sync event feed entitlement exclusion', () => {
    it('entitlement writes emit zero sync events', async () => {
        const store = new InMemorySyncFeedFirestore();

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'entitlement',
            entityId: 'stripe',
            triggerEventId: 'event-entitlement-stripe',
            after: { active: true },
        });

        expect(store.syncEvents).to.have.length(0);
        expect(store.syncMeta.nextSequence).to.equal(0);
    });

    it('allowlisted write emits exactly one sync event (entitlement control)', async () => {
        const store = new InMemorySyncFeedFirestore();

        await handleSyncEntityWrite(store, {
            uid: 'alice',
            collectionPath: 'chats',
            entityId: 'chat-1',
            triggerEventId: 'event-control',
            after: { updated_at: 1700000001000 },
        });

        expect(store.syncEvents).to.have.length(1);
        expect(store.syncMeta.nextSequence).to.equal(1);
    });
});

function syncEvent(overrides: Partial<ReturnType<typeof baseSyncEvent>>): ReturnType<typeof baseSyncEvent> {
    return {
        ...baseSyncEvent(),
        ...overrides,
    };
}

function baseSyncEvent() {
    return {
        sequence: 1,
        schemaVersion: 1 as const,
        operation: 'upsert' as const,
        collectionPath: 'chats',
        entityType: 'chat',
        entityId: 'chat-1',
        entityPath: 'users/alice/chats/chat-1',
        sourceExists: true,
        sourceUpdatedAt: 1700000001000,
        sourceDeleted: false,
        deviceId: null,
        clientMutationId: null,
        triggerEventId: 'event-1',
        emittedAtMillis: 1700000001001,
    };
}
