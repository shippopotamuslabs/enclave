import { expect } from 'chai';
import 'mocha';
import {
    BillingEvent,
    BillingRepository,
    CheckoutSessionCreateParams,
    CheckoutSessionObject,
    handleCreateCheckoutSession,
    handleCreatePortalSession,
    handleStripeWebhookEvent,
    InMemoryBillingRepository,
    StripeClient,
    SubscriptionObject,
    verifyAndHandleWebhook,
} from './billing';
import { isEntitledFromDocs } from '../entitlement/assertEntitled';

const NOW_MS = 1_700_000_000_000;
const PERIOD_END_SEC = 1_800_000_000;

class FakeStripeClient implements StripeClient {
    constructCalls: Array<{ signature: string }> = [];
    nextEvent?: BillingEvent;
    signatureValid = true;
    subscriptionsById = new Map<string, SubscriptionObject>();
    checkoutParams: CheckoutSessionCreateParams[] = [];
    checkoutUrl: string | null = 'https://checkout.stripe.test/session-1';
    portalParams: Array<{ customer: string; return_url: string }> = [];
    portalUrl: string | null = 'https://billing.stripe.test/portal-1';

    constructEvent(payload: string | Buffer, signature: string, secret: string): BillingEvent {
        this.constructCalls.push({ signature });
        void payload;
        void secret;
        if (!this.signatureValid) {
            throw new Error('Invalid signature');
        }
        if (!this.nextEvent) {
            throw new Error('No event staged');
        }
        return this.nextEvent;
    }

    subscriptions = {
        retrieve: async (id: string): Promise<SubscriptionObject> => {
            const sub = this.subscriptionsById.get(id);
            if (!sub) {
                throw new Error(`Unknown subscription ${id}`);
            }
            return sub;
        },
    };

    checkout = {
        sessions: {
            create: async (
                params: CheckoutSessionCreateParams,
            ): Promise<{ url: string | null }> => {
                this.checkoutParams.push(params);
                return { url: this.checkoutUrl };
            },
        },
    };

    billingPortal = {
        sessions: {
            create: async (params: {
                customer: string;
                return_url: string;
            }): Promise<{ url: string | null }> => {
                this.portalParams.push(params);
                return { url: this.portalUrl };
            },
        },
    };
}

function checkoutCompletedEvent(session: CheckoutSessionObject): BillingEvent {
    return { type: 'checkout.session.completed', data: { object: session } };
}

function subscriptionEvent(type: string, sub: SubscriptionObject): BillingEvent {
    return { type, data: { object: sub } };
}

function liveSubscription(overrides: Partial<SubscriptionObject> = {}): SubscriptionObject {
    return {
        id: 'sub_123',
        customer: 'cus_123',
        status: 'active',
        metadata: { uid: 'alice' },
        current_period_end: PERIOD_END_SEC,
        ...overrides,
    };
}

describe('verifyAndHandleWebhook signature verification', () => {
    it('rejects a bad signature with 400 and zero repo writes', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.signatureValid = false;

        const result = await verifyAndHandleWebhook(Buffer.from('raw-body'), 'bad-sig', 'secret', {
            repo,
            stripe,
            nowMs: NOW_MS,
        });

        expect(result.httpStatus).to.equal(400);
        expect(result.outcome).to.equal('invalid-signature');
        expect(repo.entitlements.size).to.equal(0);
        expect(repo.mappings.size).to.equal(0);
    });

    it('rejects a missing signature with 400 and zero repo writes', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await verifyAndHandleWebhook(Buffer.from('raw-body'), undefined, 'secret', {
            repo,
            stripe,
            nowMs: NOW_MS,
        });

        expect(result.httpStatus).to.equal(400);
        expect(repo.entitlements.size).to.equal(0);
        expect(stripe.constructCalls).to.have.length(0);
    });
});

describe('handleStripeWebhookEvent checkout.session.completed', () => {
    it('writes the exact stripe doc shape plus the customer mapping', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.subscriptionsById.set('sub_123', liveSubscription());

        const result = await handleStripeWebhookEvent(
            checkoutCompletedEvent({
                client_reference_id: 'alice',
                subscription: 'sub_123',
                customer: 'cus_123',
            }),
            repo,
            stripe,
            NOW_MS,
        );

        expect(result).to.deep.equal({ httpStatus: 200, outcome: 'applied' });
        expect(repo.entitlements.get('alice')).to.deep.equal({
            active: true,
            expiryTimeMillis: PERIOD_END_SEC * 1000,
            stripeCustomerId: 'cus_123',
            stripeSubscriptionId: 'sub_123',
            updatedAt: NOW_MS,
        });
        expect(repo.mappings.get('cus_123')).to.deep.equal({ uid: 'alice', updatedAt: NOW_MS });
    });

    it('handles an expanded subscription object on the session', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.subscriptionsById.set('sub_123', liveSubscription());

        const result = await handleStripeWebhookEvent(
            checkoutCompletedEvent({ client_reference_id: 'alice', subscription: { id: 'sub_123' } }),
            repo,
            stripe,
            NOW_MS,
        );

        expect(result.outcome).to.equal('applied');
        expect(repo.entitlements.get('alice')).to.include({ stripeSubscriptionId: 'sub_123' });
    });

    it('derives expiry from subscription items when the top-level period end is absent', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.subscriptionsById.set(
            'sub_123',
            liveSubscription({
                current_period_end: null,
                items: { data: [{ current_period_end: 10 }, { current_period_end: 42 }] },
            }),
        );

        await handleStripeWebhookEvent(
            checkoutCompletedEvent({ client_reference_id: 'alice', subscription: 'sub_123' }),
            repo,
            stripe,
            NOW_MS,
        );

        expect(repo.entitlements.get('alice')).to.include({ expiryTimeMillis: 42 * 1000 });
    });

    it('writes nothing when client_reference_id is missing', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await handleStripeWebhookEvent(
            checkoutCompletedEvent({ subscription: 'sub_123' }),
            repo,
            stripe,
            NOW_MS,
        );

        expect(result).to.deep.equal({ httpStatus: 200, outcome: 'unresolvable' });
        expect(repo.entitlements.size).to.equal(0);
        expect(repo.mappings.size).to.equal(0);
    });

    it('converges on duplicate delivery of the same event', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.subscriptionsById.set('sub_123', liveSubscription());
        const event = checkoutCompletedEvent({
            client_reference_id: 'alice',
            subscription: 'sub_123',
            customer: 'cus_123',
        });

        await handleStripeWebhookEvent(event, repo, stripe, NOW_MS);
        const afterFirst = {
            entitlements: [...repo.entitlements.entries()],
            mappings: [...repo.mappings.entries()],
        };
        await handleStripeWebhookEvent(event, repo, stripe, NOW_MS);

        expect([...repo.entitlements.entries()]).to.deep.equal(afterFirst.entitlements);
        expect([...repo.mappings.entries()]).to.deep.equal(afterFirst.mappings);
    });

    it('produces an entitlement doc that passes isEntitledFromDocs', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.subscriptionsById.set('sub_123', liveSubscription());

        await handleStripeWebhookEvent(
            checkoutCompletedEvent({ client_reference_id: 'alice', subscription: 'sub_123' }),
            repo,
            stripe,
            NOW_MS,
        );

        const doc = repo.entitlements.get('alice');
        expect(isEntitledFromDocs({ stripe: doc }, NOW_MS)).to.equal(true);
    });
});

describe('handleStripeWebhookEvent subscription lifecycle', () => {
    it('keeps past_due subscriptions active', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await handleStripeWebhookEvent(
            subscriptionEvent(
                'customer.subscription.updated',
                liveSubscription({ status: 'past_due' }),
            ),
            repo,
            stripe,
            NOW_MS,
        );

        expect(result.outcome).to.equal('applied');
        expect(repo.entitlements.get('alice')).to.include({
            active: true,
            expiryTimeMillis: PERIOD_END_SEC * 1000,
        });
    });

    it('marks trialing subscriptions active', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        await handleStripeWebhookEvent(
            subscriptionEvent(
                'customer.subscription.updated',
                liveSubscription({ status: 'trialing' }),
            ),
            repo,
            stripe,
            NOW_MS,
        );

        expect(repo.entitlements.get('alice')).to.include({ active: true });
    });

    it('marks canceled subscriptions inactive', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        await handleStripeWebhookEvent(
            subscriptionEvent(
                'customer.subscription.updated',
                liveSubscription({ status: 'canceled' }),
            ),
            repo,
            stripe,
            NOW_MS,
        );

        expect(repo.entitlements.get('alice')).to.include({ active: false });
    });

    it('marks deleted subscriptions inactive while keeping ids and prior expiry', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();
        stripe.subscriptionsById.set('sub_123', liveSubscription());
        await handleStripeWebhookEvent(
            checkoutCompletedEvent({ client_reference_id: 'alice', subscription: 'sub_123' }),
            repo,
            stripe,
            NOW_MS,
        );

        const result = await handleStripeWebhookEvent(
            subscriptionEvent('customer.subscription.deleted', liveSubscription()),
            repo,
            stripe,
            NOW_MS + 1000,
        );

        expect(result.outcome).to.equal('applied');
        expect(repo.entitlements.get('alice')).to.deep.equal({
            active: false,
            expiryTimeMillis: PERIOD_END_SEC * 1000,
            stripeCustomerId: 'cus_123',
            stripeSubscriptionId: 'sub_123',
            updatedAt: NOW_MS + 1000,
        });
    });

    it('resolves uid via the customer mapping when metadata is absent', async () => {
        const repo = new InMemoryBillingRepository();
        await repo.writeStripeCustomerMapping('cus_123', 'alice', NOW_MS);
        const stripe = new FakeStripeClient();

        const result = await handleStripeWebhookEvent(
            subscriptionEvent(
                'customer.subscription.updated',
                liveSubscription({ metadata: null }),
            ),
            repo,
            stripe,
            NOW_MS,
        );

        expect(result.outcome).to.equal('applied');
        expect(repo.entitlements.get('alice')).to.include({ active: true });
    });

    it('writes nothing when uid is unresolvable', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await handleStripeWebhookEvent(
            subscriptionEvent(
                'customer.subscription.updated',
                liveSubscription({ metadata: null, customer: 'cus_unknown' }),
            ),
            repo,
            stripe,
            NOW_MS,
        );

        expect(result).to.deep.equal({ httpStatus: 200, outcome: 'unresolvable' });
        expect(repo.entitlements.size).to.equal(0);
        expect(repo.mappings.size).to.equal(0);
    });
});

describe('handleStripeWebhookEvent non-write events', () => {
    it('logs invoice.payment_failed with no writes', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await handleStripeWebhookEvent(
            { type: 'invoice.payment_failed', data: { object: { id: 'in_1' } } },
            repo,
            stripe,
            NOW_MS,
        );

        expect(result).to.deep.equal({ httpStatus: 200, outcome: 'noop' });
        expect(repo.entitlements.size).to.equal(0);
        expect(repo.mappings.size).to.equal(0);
    });

    it('ignores unknown event types with 200 and no writes', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await handleStripeWebhookEvent(
            { type: 'charge.refunded', data: { object: {} } },
            repo,
            stripe,
            NOW_MS,
        );

        expect(result).to.deep.equal({ httpStatus: 200, outcome: 'noop' });
        expect(repo.entitlements.size).to.equal(0);
    });
});

describe('handleCreateCheckoutSession', () => {
    const config = {
        priceId: 'price_123',
        successUrl: 'https://example.test/success',
        cancelUrl: 'https://example.test/cancel',
    };

    it('passes price, client_reference_id, and uid metadata on session and subscription', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        const result = await handleCreateCheckoutSession('alice', repo, stripe, config);

        expect(result).to.deep.equal({ url: 'https://checkout.stripe.test/session-1' });
        expect(stripe.checkoutParams).to.have.length(1);
        expect(stripe.checkoutParams[0]).to.deep.equal({
            mode: 'subscription',
            client_reference_id: 'alice',
            line_items: [{ price: 'price_123', quantity: 1 }],
            metadata: { uid: 'alice' },
            subscription_data: { metadata: { uid: 'alice' } },
            success_url: 'https://example.test/success',
            cancel_url: 'https://example.test/cancel',
        });
    });

    it('reuses the stored stripe customer when present', async () => {
        const repo: BillingRepository = new InMemoryBillingRepository();
        await repo.writeStripeEntitlement('alice', {
            active: true,
            stripeCustomerId: 'cus_123',
            stripeSubscriptionId: 'sub_123',
            updatedAt: NOW_MS,
        });
        const stripe = new FakeStripeClient();

        await handleCreateCheckoutSession('alice', repo, stripe, config);

        expect(stripe.checkoutParams[0].customer).to.equal('cus_123');
    });

    it('fails failed-precondition when the price id is missing', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        try {
            await handleCreateCheckoutSession('alice', repo, stripe, { ...config, priceId: '' });
            expect.fail('Should have thrown');
        } catch (e: unknown) {
            const error = e as { code?: string };
            expect(error.code).to.equal('failed-precondition');
        }
        expect(stripe.checkoutParams).to.have.length(0);
    });
});

describe('handleCreatePortalSession', () => {
    const config = { returnUrl: 'https://example.test/subscribe' };

    it('fails failed-precondition when no stripe customer exists', async () => {
        const repo = new InMemoryBillingRepository();
        const stripe = new FakeStripeClient();

        try {
            await handleCreatePortalSession('alice', repo, stripe, config);
            expect.fail('Should have thrown');
        } catch (e: unknown) {
            const error = e as { code?: string; message?: string };
            expect(error.code).to.equal('failed-precondition');
            expect(error.message).to.include('No Stripe customer for this account.');
        }
        expect(stripe.portalParams).to.have.length(0);
    });

    it('creates a portal session for the stored customer', async () => {
        const repo: BillingRepository = new InMemoryBillingRepository();
        await repo.writeStripeEntitlement('alice', {
            active: true,
            stripeCustomerId: 'cus_123',
            stripeSubscriptionId: 'sub_123',
            updatedAt: NOW_MS,
        });
        const stripe = new FakeStripeClient();

        const result = await handleCreatePortalSession('alice', repo, stripe, config);

        expect(result).to.deep.equal({ url: 'https://billing.stripe.test/portal-1' });
        expect(stripe.portalParams).to.deep.equal([
            { customer: 'cus_123', return_url: 'https://example.test/subscribe' },
        ]);
    });
});
