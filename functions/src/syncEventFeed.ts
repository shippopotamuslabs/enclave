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
}

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
    syncMeta: SyncFeedState = {
        nextSequence: 0,
        schemaVersion: 1,
        updatedAtMillis: 0,
    };
    processedEvents = new Map<string, number>();
    documents = new Map<string, Record<string, unknown>>();
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
            const meta: SyncFeedState =
                (metaSnap.data() as SyncFeedState | undefined) ?? {
                    nextSequence: 0,
                    schemaVersion: 1,
                    updatedAtMillis: 0,
                };

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
