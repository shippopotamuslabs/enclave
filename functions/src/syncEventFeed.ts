import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

export interface SyncEntityWriteInput {
    uid: string;
    collectionPath: string;
    entityId: string;
    triggerEventId: string;
    before?: Record<string, unknown> | null;
    after?: Record<string, unknown> | null;
}

export interface SyncEventDocument {
    sequence: number;
    schemaVersion: 1;
    operation: 'upsert' | 'delete';
    collectionPath: string;
    entityType: string;
    entityId: string;
    entityPath: string;
    sourceExists: boolean;
    sourceUpdatedAt: number;
    sourceDeleted: boolean;
    deviceId: string | null;
    clientMutationId: string | null;
    triggerEventId: string;
    emittedAtMillis: number;
}

export interface SyncFeedState {
    nextSequence: number;
    schemaVersion: 1;
    updatedAtMillis: number;
    feedInitialized?: boolean;
    feedEpoch?: number;
    oldestRetainedSequence?: number;
    backfilledAtMillis?: number | null;
}

export interface SyncFeedStatus {
    nextSequence: number;
    feedInitialized: boolean;
    feedEpoch: number;
    oldestRetainedSequence: number;
    schemaVersion: 1;
}

export interface BackfillSyncFeedResult {
    status: 'initialized' | 'already_initialized';
    eventsWritten: number;
    nextSequence: number;
}

/**
 * Dependency-ordered collection scan for baseline backfill and client-side entity
 * reconciliation. Parents always precede children.
 */
export const BACKFILL_COLLECTION_ORDER: ReadonlyArray<string> = Object.freeze([
    'chats',
    'memories',
    'api_credentials',
    'local_models',
    'utility_models',
    'tts_providers',
    'live_voice_providers',
    'media_providers',
    'studio_albums',
    'messages',
    'chat_summaries',
    'message_files',
    'web_search_sources',
    'message_transcript_segments',
    'message_inference_telemetry',
    'vision_analysis',
    'documents',
    'local_model_configs',
    'api_model_configs',
    'default_models',
    'api_keys',
    'studio_gallery',
]);

export interface RuleRequest {
    authenticatedUid: string | null;
    operation: 'get' | 'create';
    path: string;
}

/**
 * Allowlist of direct user subcollections that emit sync events. Any write to a
 * collection outside this set is ignored.
 */
export const SYNC_FEED_ALLOWLIST: ReadonlyArray<string> = Object.freeze([
    'chats',
    'messages',
    'chat_summaries',
    'memories',
    'vision_analysis',
    'message_files',
    'web_search_sources',
    'message_transcript_segments',
    'message_inference_telemetry',
    'documents',
    'api_credentials',
    'api_keys',
    'api_model_configs',
    'local_models',
    'utility_models',
    'local_model_configs',
    'default_models',
    'tts_providers',
    'live_voice_providers',
    'media_providers',
    'studio_albums',
    'studio_gallery',
]);

export class InMemorySyncFeedFirestore {
    syncEvents: SyncEventDocument[] = [];
    syncMeta: SyncFeedState = normalizeSyncFeedState(undefined);
    processedEvents = new Map<string, number>();
    documents = new Map<string, Record<string, unknown>>();
}

export function normalizeSyncFeedState(raw: Partial<SyncFeedState> | undefined): SyncFeedState {
    return {
        nextSequence: raw?.nextSequence ?? 0,
        schemaVersion: 1,
        updatedAtMillis: raw?.updatedAtMillis ?? 0,
        feedInitialized: raw?.feedInitialized ?? false,
        feedEpoch: raw?.feedEpoch ?? 1,
        oldestRetainedSequence: raw?.oldestRetainedSequence ?? 1,
        backfilledAtMillis: raw?.backfilledAtMillis ?? null,
    };
}

export function buildSyncFeedStatus(meta: SyncFeedState): SyncFeedStatus {
    const normalized = normalizeSyncFeedState(meta);
    return {
        nextSequence: normalized.nextSequence,
        feedInitialized: normalized.feedInitialized ?? false,
        feedEpoch: normalized.feedEpoch ?? 1,
        oldestRetainedSequence: normalized.oldestRetainedSequence ?? 1,
        schemaVersion: 1,
    };
}

export function paddedSequenceDocId(sequence: number): string {
    return paddedSequence(sequence);
}

function paddedSequence(sequence: number): string {
    return sequence.toString().padStart(20, '0');
}

function hashEventId(triggerEventId: string): string {
    return `hash-${triggerEventId}`;
}

function readField<T>(source: Record<string, unknown> | null | undefined, key: string): T | undefined {
    if (!source) return undefined;
    return source[key] as T | undefined;
}

export async function handleSyncEntityWrite(
    store: InMemorySyncFeedFirestore,
    input: SyncEntityWriteInput,
): Promise<void> {
    if (!SYNC_FEED_ALLOWLIST.includes(input.collectionPath)) {
        return;
    }

    const eventHash = hashEventId(input.triggerEventId);
    if (store.processedEvents.has(eventHash)) {
        return;
    }

    const sourcePath = `users/${input.uid}/${input.collectionPath}/${input.entityId}`;
    const currentSource = store.documents.get(sourcePath) ?? input.after ?? null;

    let operation: 'upsert' | 'delete';
    let sourceExists: boolean;
    let sourceUpdatedAt: number;
    let sourceDeleted: boolean;

    const isDeleteTrigger = input.after == null;

    if (isDeleteTrigger && currentSource != null) {
        // Stale-delete guard: source still exists with newer/different data — emit upsert.
        operation = 'upsert';
        sourceExists = true;
        sourceUpdatedAt = (readField<number>(currentSource, 'updated_at') ?? 0) as number;
        sourceDeleted = false;
    } else if (isDeleteTrigger) {
        operation = 'delete';
        sourceExists = false;
        sourceUpdatedAt = (readField<number>(input.before, 'updated_at') ?? 0) as number;
        sourceDeleted = true;
    } else {
        operation = 'upsert';
        sourceExists = currentSource != null;
        sourceUpdatedAt = (readField<number>(currentSource, 'updated_at')
            ?? readField<number>(input.after, 'updated_at')
            ?? 0) as number;
        sourceDeleted = false;
    }

    const metadataSource = currentSource ?? input.after ?? input.before ?? null;
    const deviceId = (readField<string>(metadataSource, '_sync_device_id') ?? null) as string | null;
    const clientMutationId = (readField<string>(metadataSource, '_sync_client_mutation_id')
        ?? null) as string | null;

    const sequence = store.syncMeta.nextSequence + 1;
    const event: SyncEventDocument = {
        sequence,
        schemaVersion: 1,
        operation,
        collectionPath: input.collectionPath,
        entityType: input.collectionPath,
        entityId: input.entityId,
        entityPath: sourcePath,
        sourceExists,
        sourceUpdatedAt,
        sourceDeleted,
        deviceId,
        clientMutationId,
        triggerEventId: input.triggerEventId,
        emittedAtMillis: Date.now(),
    };

    store.syncEvents.push(event);
    store.processedEvents.set(eventHash, sequence);
    store.syncMeta.nextSequence = sequence;
    store.syncMeta.updatedAtMillis = event.emittedAtMillis;
    // paddedSequence is used by the production Firestore writer when persisting event IDs.
    void paddedSequence(sequence);
}

export function evaluateSyncEventRule(request: RuleRequest): 'allow' | 'deny' {
    if (request.authenticatedUid == null) return 'deny';
    // Path shape: users/{uid}/sync_events/{eventId}
    const match = /^users\/([^/]+)\/sync_events\/[^/]+$/.exec(request.path);
    if (!match) return 'deny';
    const owner = match[1];
    if (owner !== request.authenticatedUid) return 'deny';
    if (request.operation === 'get') return 'allow';
    return 'deny';
}

/**
 * Cloud Functions wiring. The pure handlers above are exported for unit tests; the
 * trigger below adapts Firestore event payloads to those handlers using the Admin SDK.
 */
export const handleSyncEntityWriteTrigger = functions.firestore
    .document('users/{uid}/{collectionPath}/{entityId}')
    .onWrite(async (change, context) => {
        const uid = context.params.uid as string;
        const collectionPath = context.params.collectionPath as string;
        const entityId = context.params.entityId as string;
        if (!SYNC_FEED_ALLOWLIST.includes(collectionPath)) {
            return;
        }
        const triggerEventId = context.eventId;
        const before = change.before.exists ? (change.before.data() as Record<string, unknown>) : null;
        const after = change.after.exists ? (change.after.data() as Record<string, unknown>) : null;
        const sourcePath = `users/${uid}/${collectionPath}/${entityId}`;
        const eventHash = hashEventId(triggerEventId);
        const firestore = admin.firestore();

        await firestore.runTransaction(async (transaction) => {
            const processedRef = firestore.doc(`users/${uid}/sync_processed_events/${eventHash}`);
            const metaRef = firestore.doc(`users/${uid}/sync_meta/state`);
            const sourceRef = firestore.doc(sourcePath);

            const processedSnap = await transaction.get(processedRef);
            if (processedSnap.exists) {
                return;
            }

            const metaSnap = await transaction.get(metaRef);
            const meta: SyncFeedState = normalizeSyncFeedState(
                metaSnap.data() as Partial<SyncFeedState> | undefined,
            );

            const store = new InMemorySyncFeedFirestore();
            store.syncMeta = { ...meta };
            const sourceSnap = await transaction.get(sourceRef);
            if (sourceSnap.exists) {
                store.documents.set(sourcePath, sourceSnap.data() as Record<string, unknown>);
            }

            const eventsBefore = store.syncEvents.length;
            await handleSyncEntityWrite(store, {
                uid,
                collectionPath,
                entityId,
                triggerEventId,
                before,
                after,
            });
            if (store.syncEvents.length === eventsBefore) {
                return;
            }

            const event = store.syncEvents[store.syncEvents.length - 1];
            const eventDocId = paddedSequence(event.sequence);
            transaction.set(firestore.doc(`users/${uid}/sync_events/${eventDocId}`), event);
            transaction.set(processedRef, {
                triggerEventId,
                sequence: event.sequence,
                processedAtMillis: Date.now(),
            });
            transaction.set(metaRef, store.syncMeta, { merge: true });
        });
    });

export interface SyncFeedDocumentReader {
    listCollectionDocuments(
        uid: string,
        collectionPath: string,
    ): Promise<Array<{ entityId: string; data: Record<string, unknown> }>>;
}

export class InMemorySyncFeedDocumentReader implements SyncFeedDocumentReader {
    constructor(private readonly store: InMemorySyncFeedFirestore) {}

    async listCollectionDocuments(
        uid: string,
        collectionPath: string,
    ): Promise<Array<{ entityId: string; data: Record<string, unknown> }>> {
        const prefix = `users/${uid}/${collectionPath}/`;
        const results: Array<{ entityId: string; data: Record<string, unknown> }> = [];
        for (const [path, data] of this.store.documents.entries()) {
            if (!path.startsWith(prefix)) continue;
            const entityId = path.substring(prefix.length);
            if (!entityId || entityId.includes('/')) continue;
            results.push({ entityId, data });
        }
        results.sort((left, right) => left.entityId.localeCompare(right.entityId));
        return results;
    }
}

export class FirestoreSyncFeedDocumentReader implements SyncFeedDocumentReader {
    constructor(private readonly firestore: admin.firestore.Firestore) {}

    async listCollectionDocuments(
        uid: string,
        collectionPath: string,
    ): Promise<Array<{ entityId: string; data: Record<string, unknown> }>> {
        const snapshot = await this.firestore.collection(`users/${uid}/${collectionPath}`).get();
        return snapshot.docs.map((doc) => ({
            entityId: doc.id,
            data: doc.data() as Record<string, unknown>,
        }));
    }
}

async function emitBaselineEntityEvent(
    store: InMemorySyncFeedFirestore,
    uid: string,
    collectionPath: string,
    entityId: string,
    sourceData: Record<string, unknown>,
): Promise<boolean> {
    const triggerEventId = `backfill:${collectionPath}:${entityId}`;
    const eventsBefore = store.syncEvents.length;
    await handleSyncEntityWrite(store, {
        uid,
        collectionPath,
        entityId,
        triggerEventId,
        after: sourceData,
    });
    return store.syncEvents.length > eventsBefore;
}

export async function handleBackfillSyncFeed(
    store: InMemorySyncFeedFirestore,
    reader: SyncFeedDocumentReader,
    uid: string,
    nowMillis: number = Date.now(),
): Promise<BackfillSyncFeedResult> {
    const meta = normalizeSyncFeedState(store.syncMeta);
    if (meta.feedInitialized) {
        return {
            status: 'already_initialized',
            eventsWritten: 0,
            nextSequence: meta.nextSequence,
        };
    }

    if (meta.nextSequence > 0) {
        store.syncMeta = {
            ...meta,
            feedInitialized: true,
            backfilledAtMillis: nowMillis,
            updatedAtMillis: nowMillis,
        };
        return {
            status: 'initialized',
            eventsWritten: 0,
            nextSequence: meta.nextSequence,
        };
    }

    let eventsWritten = 0;
    for (const collectionPath of BACKFILL_COLLECTION_ORDER) {
        if (!SYNC_FEED_ALLOWLIST.includes(collectionPath)) continue;
        const documents = await reader.listCollectionDocuments(uid, collectionPath);
        for (const document of documents) {
            const sourcePath = `users/${uid}/${collectionPath}/${document.entityId}`;
            store.documents.set(sourcePath, document.data);
            const wrote = await emitBaselineEntityEvent(
                store,
                uid,
                collectionPath,
                document.entityId,
                document.data,
            );
            if (wrote) eventsWritten += 1;
        }
    }

    store.syncMeta = {
        ...normalizeSyncFeedState(store.syncMeta),
        feedInitialized: true,
        backfilledAtMillis: nowMillis,
        updatedAtMillis: nowMillis,
    };

    return {
        status: 'initialized',
        eventsWritten,
        nextSequence: store.syncMeta.nextSequence,
    };
}

export async function persistBackfillResult(
    firestore: admin.firestore.Firestore,
    uid: string,
    store: InMemorySyncFeedFirestore,
): Promise<BackfillSyncFeedResult> {
    const meta = normalizeSyncFeedState(store.syncMeta);
    if (!meta.feedInitialized) {
        throw new Error('backfill store must be marked initialized before persistence');
    }

    const eventsWritten = await commitBackfillWrites(firestore, uid, store.syncEvents, meta);
    return {
        status: 'initialized',
        eventsWritten,
        nextSequence: meta.nextSequence,
    };
}

const BACKFILL_WRITE_BATCH_LIMIT = 400;

async function commitBackfillWrites(
    firestore: admin.firestore.Firestore,
    uid: string,
    events: SyncEventDocument[],
    meta: SyncFeedState,
): Promise<number> {
    let batch = firestore.batch();
    let operations = 0;
    let eventsWritten = 0;

    const flush = async () => {
        if (operations === 0) return;
        await batch.commit();
        batch = firestore.batch();
        operations = 0;
    };

    const enqueue = async (write: (current: admin.firestore.WriteBatch) => void) => {
        if (operations >= BACKFILL_WRITE_BATCH_LIMIT) {
            await flush();
        }
        write(batch);
        operations += 1;
    };

    await enqueue((current) => {
        current.set(firestore.doc(`users/${uid}/sync_meta/state`), meta, { merge: true });
    });

    for (const event of events) {
        await enqueue((current) => {
            current.set(
                firestore.doc(`users/${uid}/sync_events/${paddedSequence(event.sequence)}`),
                event,
            );
        });
        await enqueue((current) => {
            current.set(
                firestore.doc(`users/${uid}/sync_processed_events/${hashEventId(event.triggerEventId)}`),
                {
                    triggerEventId: event.triggerEventId,
                    sequence: event.sequence,
                    processedAtMillis: Date.now(),
                },
            );
        });
        eventsWritten += 1;
    }

    await flush();
    return eventsWritten;
}

export async function runProductionBackfillSyncFeed(
    firestore: admin.firestore.Firestore,
    uid: string,
    options: { migrationOnly?: boolean } = {},
): Promise<BackfillSyncFeedResult> {
    const metaRef = firestore.doc(`users/${uid}/sync_meta/state`);
    const metaSnap = await metaRef.get();
    const existingMeta = normalizeSyncFeedState(metaSnap.data() as Partial<SyncFeedState> | undefined);
    if (existingMeta.feedInitialized) {
        return {
            status: 'already_initialized',
            eventsWritten: 0,
            nextSequence: existingMeta.nextSequence,
        };
    }

    if (options.migrationOnly || existingMeta.nextSequence > 0) {
        const nowMillis = Date.now();
        const migratedMeta: SyncFeedState = {
            ...existingMeta,
            feedInitialized: true,
            backfilledAtMillis: nowMillis,
            updatedAtMillis: nowMillis,
        };
        await metaRef.set(migratedMeta, { merge: true });
        return {
            status: 'initialized',
            eventsWritten: 0,
            nextSequence: existingMeta.nextSequence,
        };
    }

    const store = new InMemorySyncFeedFirestore();
    store.syncMeta = existingMeta;
    const reader = new FirestoreSyncFeedDocumentReader(firestore);
    const result = await handleBackfillSyncFeed(store, reader, uid);
    if (result.status === 'already_initialized') {
        return result;
    }
    return persistBackfillResult(firestore, uid, store);
}

export const backfillSyncFeedCallable = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'User must be logged in.');
    }
    const migrationOnly = data?.migrationOnly === true;
    return runProductionBackfillSyncFeed(admin.firestore(), context.auth.uid, { migrationOnly });
});
