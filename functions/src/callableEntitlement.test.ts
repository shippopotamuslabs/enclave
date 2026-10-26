import { expect } from 'chai';
import 'mocha';
import functionsTest from 'firebase-functions-test';
// NOTE: firebase-admin exposes `firestore` as a getter-only accessor on its
// namespace prototype, so `sinon.stub(admin, 'firestore')` silently fails to
// intercept (sloppy-mode assignment to a setter-less inherited property).
// Define an own data property on the shared module exports instead; every
// compiled caller resolves through this same object. `delete` restores the
// prototype getter.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const adminLib = require('firebase-admin');

let firestoreStubbed = false;

function stubAdminFirestore(fake: () => unknown): void {
    Object.defineProperty(adminLib, 'firestore', {
        value: fake,
        configurable: true,
        writable: true,
        enumerable: true,
    });
    firestoreStubbed = true;
}
import {
    resetEntitlementFirestoreGetter,
    setEntitlementFirestoreGetter,
} from './entitlement/assertEntitled';
import { uploadSharedChat } from './index';
import { backfillSyncFeedCallable } from './syncEventFeed';
import { rebuildProviderDailyRollups } from './telemetry/providerDailyRollup';

const testEnv = functionsTest();
const wrappedUpload = testEnv.wrap(uploadSharedChat);
const wrappedBackfill = testEnv.wrap(backfillSyncFeedCallable);
const wrappedRollup = testEnv.wrap(rebuildProviderDailyRollups);

const authed = (uid: string): unknown => ({ auth: { uid } });

async function expectUnauthenticated(call: () => Promise<unknown>): Promise<void> {
    try {
        await call();
        expect.fail('Should have thrown');
    } catch (e: unknown) {
        const error = e as { code?: string };
        expect(error.code).to.equal('unauthenticated');
    }
}

async function expectSubscriptionRequired(call: () => Promise<unknown>): Promise<void> {
    try {
        await call();
        expect.fail('Should have thrown');
    } catch (e: unknown) {
        const error = e as { code?: string; message?: string };
        expect(error.code).to.equal('permission-denied');
        expect(error.message).to.equal('Enclave subscription required.');
    }
}

describe('callable entitlement boundary (wrap)', () => {
    afterEach(() => {
        resetEntitlementFirestoreGetter();
        if (firestoreStubbed) {
            delete adminLib.firestore;
            firestoreStubbed = false;
        }
    });

    after(() => {
        testEnv.cleanup();
    });

    it('uploadSharedChat rejects unauthenticated callers', async () => {
        await expectUnauthenticated(() =>
            wrappedUpload({ shareId: 's', encryptedPayload: 'p', iv: 'v' }, {} as never),
        );
    });

    it('backfillSyncFeedCallable rejects unauthenticated callers', async () => {
        await expectUnauthenticated(() => wrappedBackfill({}, {} as never));
    });

    it('rebuildProviderDailyRollups rejects unauthenticated callers', async () => {
        await expectUnauthenticated(() => wrappedRollup({}, {} as never));
    });

    it('uploadSharedChat rejects authenticated callers without entitlement', async () => {
        setEntitlementFirestoreGetter(async () => undefined);
        await expectSubscriptionRequired(() =>
            wrappedUpload(
                { shareId: 's', encryptedPayload: 'p', iv: 'v' },
                authed('alice') as never,
            ),
        );
    });

    it('backfillSyncFeedCallable rejects authenticated callers without entitlement', async () => {
        setEntitlementFirestoreGetter(async () => undefined);
        await expectSubscriptionRequired(() =>
            wrappedBackfill({}, authed('alice') as never),
        );
    });

    it('rebuildProviderDailyRollups rejects stripe-active callers without an override', async () => {
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'stripe' ? { active: true } : undefined,
        );
        await expectSubscriptionRequired(() =>
            wrappedRollup({}, authed('alice') as never),
        );
    });

    it('uploadSharedChat proceeds for stripe-entitled callers without touching entitlement writes', async () => {
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'stripe' ? { active: true } : undefined,
        );
        const saved: Array<{ collection: string; id: string }> = [];
        stubAdminFirestore(() => ({
            collection: (collection: string) => ({
                doc: (id: string) => ({
                    set: async () => {
                        saved.push({ collection, id });
                    },
                }),
            }),
        }));

        const result = (await wrappedUpload(
            { shareId: 'share-1', encryptedPayload: 'p', iv: 'v' },
            authed('alice') as never,
        )) as { shareId: string };
        expect(result.shareId).to.equal('share-1');
        expect(saved).to.deep.equal([{ collection: 'shared_chats', id: 'share-1' }]);
    });

    it('uploadSharedChat proceeds for override-entitled callers', async () => {
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'override' ? { freeAccess: true } : undefined,
        );
        stubAdminFirestore(() => ({
            collection: () => ({
                doc: () => ({
                    set: async () => undefined,
                }),
            }),
        }));

        const result = (await wrappedUpload(
            { shareId: 'share-2', encryptedPayload: 'p', iv: 'v' },
            authed('alice') as never,
        )) as { shareId: string };
        expect(result.shareId).to.equal('share-2');
    });

    it('backfillSyncFeedCallable proceeds for stripe-entitled callers', async () => {
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'stripe' ? { active: true } : undefined,
        );
        stubAdminFirestore(() => ({
            doc: () => ({
                get: async () => ({
                    data: () => ({ feedInitialized: true, nextSequence: 7 }),
                }),
                set: async () => undefined,
            }),
        }));

        const result = await wrappedBackfill({}, authed('alice') as never);
        expect(result).to.deep.equal({
            status: 'already_initialized',
            eventsWritten: 0,
            nextSequence: 7,
        });
    });

    it('rebuildProviderDailyRollups proceeds for override-entitled callers', async () => {
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'override' ? { freeAccess: true } : undefined,
        );
        const result = await wrappedRollup({}, authed('alice') as never);
        expect(result).to.deep.equal({ status: 'noop' });
    });
});
