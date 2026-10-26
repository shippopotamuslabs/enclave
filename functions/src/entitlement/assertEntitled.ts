import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';

export const STRIPE_ENTITLEMENT_DOC_ID = 'stripe';
export const OVERRIDE_ENTITLEMENT_DOC_ID = 'override';

export function entitlementDocPath(uid: string, docId: string): string {
    return `users/${uid}/entitlement/${docId}`;
}

export interface StripeEntitlementDoc {
    active?: unknown;
    expiryTimeMillis?: unknown;
    stripeCustomerId?: unknown;
    stripeSubscriptionId?: unknown;
    updatedAt?: unknown;
}

export interface OverrideEntitlementDoc {
    freeAccess?: unknown;
}

export interface EntitlementDocs {
    stripe?: StripeEntitlementDoc | undefined;
    override?: OverrideEntitlementDoc | undefined;
}

export function isEntitledFromDocs(docs: EntitlementDocs, nowMs: number): boolean {
    if (docs.override?.freeAccess === true) {
        return true;
    }
    const stripe = docs.stripe;
    if (!stripe || stripe.active !== true) {
        return false;
    }
    const expiry = stripe.expiryTimeMillis;
    if (expiry === undefined) {
        return true;
    }
    return typeof expiry === 'number' && expiry > nowMs;
}

export type EntitlementDocGetter = (
    uid: string,
    docId: string,
) => Promise<StripeEntitlementDoc | OverrideEntitlementDoc | undefined>;

async function defaultEntitlementDocGetter(
    uid: string,
    docId: string,
): Promise<StripeEntitlementDoc | OverrideEntitlementDoc | undefined> {
    const snapshot = await admin.firestore().doc(entitlementDocPath(uid, docId)).get();
    return snapshot.data() as StripeEntitlementDoc | OverrideEntitlementDoc | undefined;
}

let entitlementFirestoreGetter: EntitlementDocGetter = defaultEntitlementDocGetter;

export function setEntitlementFirestoreGetter(getter: EntitlementDocGetter): void {
    entitlementFirestoreGetter = getter;
}

export function resetEntitlementFirestoreGetter(): void {
    entitlementFirestoreGetter = defaultEntitlementDocGetter;
}

export async function isUserEntitled(uid: string, nowMs: number = Date.now()): Promise<boolean> {
    const [stripe, override] = await Promise.all([
        entitlementFirestoreGetter(uid, STRIPE_ENTITLEMENT_DOC_ID),
        entitlementFirestoreGetter(uid, OVERRIDE_ENTITLEMENT_DOC_ID),
    ]);
    return isEntitledFromDocs(
        {
            stripe: stripe as StripeEntitlementDoc | undefined,
            override: override as OverrideEntitlementDoc | undefined,
        },
        nowMs,
    );
}

export async function assertUserEntitled(
    uid: string,
    nowMs: number = Date.now(),
): Promise<void> {
    if (!(await isUserEntitled(uid, nowMs))) {
        throw new functions.https.HttpsError('permission-denied', 'Enclave subscription required.');
    }
}

export async function isOverrideEntitled(
    uid: string,
    // Accepted for call-site symmetry with isUserEntitled; the override doc
    // carries no expiry, so the timestamp is intentionally unused.
    nowMs: number = Date.now(),
): Promise<boolean> {
    void nowMs;
    const override = (await entitlementFirestoreGetter(
        uid,
        OVERRIDE_ENTITLEMENT_DOC_ID,
    )) as OverrideEntitlementDoc | undefined;
    return override?.freeAccess === true;
}

export async function assertOverrideEntitled(
    uid: string,
    nowMs: number = Date.now(),
): Promise<void> {
    if (!(await isOverrideEntitled(uid, nowMs))) {
        throw new functions.https.HttpsError('permission-denied', 'Enclave subscription required.');
    }
}
