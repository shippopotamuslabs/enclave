import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';
import Stripe from 'stripe';

export const DEFAULT_SUBSCRIBE_URL = 'https://shippopotamuslabs.com/enclave/subscribe';
export const DEFAULT_CHECKOUT_SUCCESS_URL = `${DEFAULT_SUBSCRIBE_URL}?checkout=success`;
export const DEFAULT_CHECKOUT_CANCEL_URL = `${DEFAULT_SUBSCRIBE_URL}?checkout=cancel`;

export const STRIPE_CUSTOMER_MAPPINGS_COLLECTION = 'stripe_customers';

export interface StripeEntitlementWrite {
    active: boolean;
    expiryTimeMillis?: number;
    stripeCustomerId: string;
    stripeSubscriptionId: string;
    updatedAt: number;
}

export interface StoredStripeEntitlement {
    stripeCustomerId?: unknown;
    [key: string]: unknown;
}

export interface BillingRepository {
    writeStripeEntitlement(uid: string, doc: StripeEntitlementWrite): Promise<void>;
    writeStripeCustomerMapping(customerId: string, uid: string, nowMs: number): Promise<void>;
    findUidByCustomerId(customerId: string): Promise<string | undefined>;
    getStripeEntitlement(uid: string): Promise<StoredStripeEntitlement | undefined>;
}

export class InMemoryBillingRepository implements BillingRepository {
    entitlements = new Map<string, Record<string, unknown>>();
    mappings = new Map<string, { uid: string; updatedAt: number }>();

    async writeStripeEntitlement(uid: string, doc: StripeEntitlementWrite): Promise<void> {
        const previous = this.entitlements.get(uid) ?? {};
        this.entitlements.set(uid, { ...previous, ...doc });
    }

    async writeStripeCustomerMapping(
        customerId: string,
        uid: string,
        nowMs: number,
    ): Promise<void> {
        this.mappings.set(customerId, { uid, updatedAt: nowMs });
    }

    async findUidByCustomerId(customerId: string): Promise<string | undefined> {
        return this.mappings.get(customerId)?.uid;
    }

    async getStripeEntitlement(uid: string): Promise<StoredStripeEntitlement | undefined> {
        const doc = this.entitlements.get(uid);
        return doc ? { ...doc } : undefined;
    }
}

export function createFirestoreBillingRepository(
    db: admin.firestore.Firestore = admin.firestore(),
): BillingRepository {
    return {
        async writeStripeEntitlement(uid: string, doc: StripeEntitlementWrite): Promise<void> {
            await db.doc(`users/${uid}/entitlement/stripe`).set(doc, { merge: true });
        },
        async writeStripeCustomerMapping(
            customerId: string,
            uid: string,
            nowMs: number,
        ): Promise<void> {
            await db
                .collection(STRIPE_CUSTOMER_MAPPINGS_COLLECTION)
                .doc(customerId)
                .set({ uid, updatedAt: nowMs }, { merge: true });
        },
        async findUidByCustomerId(customerId: string): Promise<string | undefined> {
            const snapshot = await db
                .collection(STRIPE_CUSTOMER_MAPPINGS_COLLECTION)
                .doc(customerId)
                .get();
            const uid = snapshot.data()?.uid;
            return typeof uid === 'string' && uid.length > 0 ? uid : undefined;
        },
        async getStripeEntitlement(uid: string): Promise<StoredStripeEntitlement | undefined> {
            const snapshot = await db.doc(`users/${uid}/entitlement/stripe`).get();
            const data = snapshot.data();
            return data ? (data as StoredStripeEntitlement) : undefined;
        },
    };
}

export interface BillingEvent {
    type: string;
    data: { object: unknown };
}

export interface CheckoutSessionObject {
    client_reference_id?: string | null;
    subscription?: string | { id?: string } | null;
    customer?: string | { id?: string } | null;
}

export interface SubscriptionItemsObject {
    data?: Array<{ current_period_end?: number }>;
}

export interface SubscriptionObject {
    id: string;
    customer: string | { id?: string } | null;
    status: string;
    metadata?: Record<string, string> | null;
    current_period_end?: number | null;
    items?: SubscriptionItemsObject | null;
}

export interface CheckoutSessionCreateParams {
    mode: 'subscription';
    client_reference_id: string;
    line_items: Array<{ price: string; quantity: number }>;
    metadata: { uid: string };
    subscription_data: { metadata: { uid: string } };
    success_url: string;
    cancel_url: string;
    customer?: string;
}

export interface StripeClient {
    constructEvent(
        payload: string | Buffer,
        signature: string,
        secret: string,
    ): BillingEvent;
    subscriptions: {
        retrieve(id: string): Promise<SubscriptionObject>;
    };
    checkout: {
        sessions: {
            create(params: CheckoutSessionCreateParams): Promise<{ url: string | null }>;
        };
    };
    billingPortal: {
        sessions: {
            create(params: {
                customer: string;
                return_url: string;
            }): Promise<{ url: string | null }>;
        };
    };
}

function idOf(value: string | { id?: string } | null | undefined): string | undefined {
    if (typeof value === 'string') {
        return value.length > 0 ? value : undefined;
    }
    if (value && typeof value.id === 'string' && value.id.length > 0) {
        return value.id;
    }
    return undefined;
}

function customerIdOf(customer: SubscriptionObject['customer']): string | undefined {
    return idOf(customer);
}

/**
 * Resolve the subscription period end (unix seconds). Older Stripe API versions
 * expose `current_period_end` on the subscription; current versions expose it on
 * each subscription item, so prefer the top-level field and fall back to the max
 * across items.
 */
export function subscriptionPeriodEndSec(sub: SubscriptionObject): number | undefined {
    if (typeof sub.current_period_end === 'number') {
        return sub.current_period_end;
    }
    const items = sub.items?.data;
    if (Array.isArray(items)) {
        let max: number | undefined;
        for (const item of items) {
            const value = item?.current_period_end;
            if (typeof value === 'number' && (max === undefined || value > max)) {
                max = value;
            }
        }
        return max;
    }
    return undefined;
}

const ACTIVE_SUBSCRIPTION_STATUSES = new Set(['active', 'trialing', 'past_due']);

export type WebhookOutcomeKind = 'applied' | 'unresolvable' | 'noop' | 'invalid-signature';

export interface WebhookResult {
    httpStatus: number;
    outcome: WebhookOutcomeKind;
}

function asCheckoutSession(value: unknown): CheckoutSessionObject {
    return (value ?? {}) as CheckoutSessionObject;
}

function asSubscription(value: unknown): SubscriptionObject {
    return (value ?? {}) as SubscriptionObject;
}

function metadataUid(metadata: SubscriptionObject['metadata']): string | undefined {
    const uid = metadata?.uid;
    return typeof uid === 'string' && uid.length > 0 ? uid : undefined;
}

export async function handleStripeWebhookEvent(
    event: BillingEvent,
    repo: BillingRepository,
    stripe: Pick<StripeClient, 'subscriptions'> & Pick<StripeClient, 'constructEvent'>,
    nowMs: number,
): Promise<WebhookResult> {
    switch (event.type) {
        case 'checkout.session.completed': {
            const session = asCheckoutSession(event.data.object);
            const uid = session.client_reference_id;
            if (typeof uid !== 'string' || uid.length === 0) {
                functions.logger.warn('stripe webhook: checkout.session.completed without uid');
                return { httpStatus: 200, outcome: 'unresolvable' };
            }
            const subscriptionId = idOf(session.subscription);
            if (!subscriptionId) {
                functions.logger.warn('stripe webhook: checkout.session.completed without subscription');
                return { httpStatus: 200, outcome: 'unresolvable' };
            }
            const subscription = await stripe.subscriptions.retrieve(subscriptionId);
            const customerId = customerIdOf(subscription.customer) ?? idOf(session.customer);
            if (!customerId) {
                functions.logger.warn('stripe webhook: checkout.session.completed without customer');
                return { httpStatus: 200, outcome: 'unresolvable' };
            }
            const periodEndSec = subscriptionPeriodEndSec(subscription);
            await repo.writeStripeEntitlement(uid, {
                active: true,
                ...(periodEndSec !== undefined ? { expiryTimeMillis: periodEndSec * 1000 } : {}),
                stripeCustomerId: customerId,
                stripeSubscriptionId: subscription.id,
                updatedAt: nowMs,
            });
            await repo.writeStripeCustomerMapping(customerId, uid, nowMs);
            functions.logger.info('stripe webhook: checkout.session.completed applied');
            return { httpStatus: 200, outcome: 'applied' };
        }
        case 'customer.subscription.updated':
        case 'customer.subscription.deleted': {
            const subscription = asSubscription(event.data.object);
            const customerId = customerIdOf(subscription.customer);
            const uid =
                metadataUid(subscription.metadata) ??
                (customerId ? await repo.findUidByCustomerId(customerId) : undefined);
            if (!uid || !customerId) {
                functions.logger.warn('stripe webhook: subscription event without resolvable uid');
                return { httpStatus: 200, outcome: 'unresolvable' };
            }
            if (event.type === 'customer.subscription.deleted') {
                await repo.writeStripeEntitlement(uid, {
                    active: false,
                    stripeCustomerId: customerId,
                    stripeSubscriptionId: subscription.id,
                    updatedAt: nowMs,
                });
            } else {
                const periodEndSec = subscriptionPeriodEndSec(subscription);
                await repo.writeStripeEntitlement(uid, {
                    active: ACTIVE_SUBSCRIPTION_STATUSES.has(subscription.status),
                    ...(periodEndSec !== undefined
                        ? { expiryTimeMillis: periodEndSec * 1000 }
                        : {}),
                    stripeCustomerId: customerId,
                    stripeSubscriptionId: subscription.id,
                    updatedAt: nowMs,
                });
            }
            await repo.writeStripeCustomerMapping(customerId, uid, nowMs);
            functions.logger.info('stripe webhook: subscription event applied');
            return { httpStatus: 200, outcome: 'applied' };
        }
        case 'invoice.payment_failed': {
            functions.logger.info('stripe webhook: invoice.payment_failed logged');
            return { httpStatus: 200, outcome: 'noop' };
        }
        default: {
            functions.logger.info('stripe webhook: unknown event type ignored');
            return { httpStatus: 200, outcome: 'noop' };
        }
    }
}

/**
 * Verify the Stripe signature via the injected client seam, then handle the
 * event. A verification throw maps to HTTP 400 with zero repo writes; handler
 * errors propagate so the HTTPS wrapper can return HTTP 500.
 */
export async function verifyAndHandleWebhook(
    payload: string | Buffer,
    signature: string | undefined,
    secret: string,
    deps: { repo: BillingRepository; stripe: StripeClient; nowMs: number },
): Promise<WebhookResult> {
    if (!signature) {
        return { httpStatus: 400, outcome: 'invalid-signature' };
    }
    let event: BillingEvent;
    try {
        event = deps.stripe.constructEvent(payload, signature, secret);
    } catch {
        return { httpStatus: 400, outcome: 'invalid-signature' };
    }
    return handleStripeWebhookEvent(event, deps.repo, deps.stripe, deps.nowMs);
}

export interface CheckoutConfig {
    priceId: string;
    successUrl: string;
    cancelUrl: string;
}

export interface PortalConfig {
    returnUrl: string;
}

export async function handleCreateCheckoutSession(
    uid: string,
    repo: BillingRepository,
    stripe: Pick<StripeClient, 'checkout'>,
    config: CheckoutConfig,
): Promise<{ url: string }> {
    if (!config.priceId) {
        throw new functions.https.HttpsError(
            'failed-precondition',
            'Stripe price is not configured.',
        );
    }
    const existing = await repo.getStripeEntitlement(uid);
    const customer = existing?.stripeCustomerId;
    const session = await stripe.checkout.sessions.create({
        mode: 'subscription',
        client_reference_id: uid,
        line_items: [{ price: config.priceId, quantity: 1 }],
        metadata: { uid },
        subscription_data: { metadata: { uid } },
        success_url: config.successUrl,
        cancel_url: config.cancelUrl,
        ...(typeof customer === 'string' && customer.length > 0 ? { customer } : {}),
    });
    if (!session.url) {
        throw new functions.https.HttpsError(
            'internal',
            'Stripe did not return a checkout URL.',
        );
    }
    return { url: session.url };
}

export async function handleCreatePortalSession(
    uid: string,
    repo: BillingRepository,
    stripe: Pick<StripeClient, 'billingPortal'>,
    config: PortalConfig,
): Promise<{ url: string }> {
    const existing = await repo.getStripeEntitlement(uid);
    const customer = existing?.stripeCustomerId;
    if (typeof customer !== 'string' || customer.length === 0) {
        throw new functions.https.HttpsError(
            'failed-precondition',
            'No Stripe customer for this account.',
        );
    }
    const session = await stripe.billingPortal.sessions.create({
        customer,
        return_url: config.returnUrl,
    });
    if (!session.url) {
        throw new functions.https.HttpsError(
            'internal',
            'Stripe did not return a portal URL.',
        );
    }
    return { url: session.url };
}

function toSubscriptionObject(sub: Stripe.Subscription): SubscriptionObject {
    const customer =
        typeof sub.customer === 'string' ? sub.customer : (sub.customer?.id ?? null);
    const legacy = sub as unknown as { current_period_end?: unknown };
    const items = sub.items?.data?.map((item) => {
        const raw = item as unknown as { current_period_end?: unknown };
        return typeof raw.current_period_end === 'number'
            ? { current_period_end: raw.current_period_end }
            : {};
    });
    return {
        id: sub.id,
        customer,
        status: sub.status,
        metadata: { ...(sub.metadata ?? {}) },
        ...(typeof legacy.current_period_end === 'number'
            ? { current_period_end: legacy.current_period_end }
            : {}),
        ...(items ? { items: { data: items } } : {}),
    };
}

/** Real Stripe SDK implementation of the StripeClient seam. */
export function createStripeClient(secretKey: string): StripeClient {
    const stripe = new Stripe(secretKey);
    return {
        constructEvent(payload: string | Buffer, signature: string, secret: string) {
            const event = stripe.webhooks.constructEvent(payload, signature, secret);
            return { type: event.type, data: { object: event.data.object } };
        },
        subscriptions: {
            async retrieve(id: string): Promise<SubscriptionObject> {
                return toSubscriptionObject(await stripe.subscriptions.retrieve(id));
            },
        },
        checkout: {
            sessions: {
                async create(
                    params: CheckoutSessionCreateParams,
                ): Promise<{ url: string | null }> {
                    const session = await stripe.checkout.sessions.create({
                        mode: params.mode,
                        client_reference_id: params.client_reference_id,
                        line_items: params.line_items,
                        metadata: params.metadata,
                        subscription_data: params.subscription_data,
                        success_url: params.success_url,
                        cancel_url: params.cancel_url,
                        ...(params.customer ? { customer: params.customer } : {}),
                    });
                    return { url: session.url };
                },
            },
        },
        billingPortal: {
            sessions: {
                async create(params: {
                    customer: string;
                    return_url: string;
                }): Promise<{ url: string | null }> {
                    const session = await stripe.billingPortal.sessions.create(params);
                    return { url: session.url };
                },
            },
        },
    };
}
