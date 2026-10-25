import * as functions from 'firebase-functions/v1';
import {
    createFirestoreBillingRepository,
    createStripeClient,
    verifyAndHandleWebhook,
} from './billing';

const BILLING_SECRETS = ['STRIPE_SECRET_KEY', 'STRIPE_WEBHOOK_SECRET'];

/**
 * Stripe webhook receiver. Verifies the `stripe-signature` header against the
 * webhook secret before any Firestore write; bad/missing signatures return 400
 * with zero writes. Unresolvable or unknown events return 200 without writes
 * to avoid retry storms. Unexpected errors return 500.
 */
export const stripeWebhook = functions
    .runWith({ secrets: BILLING_SECRETS })
    .https.onRequest(async (req, res) => {
        const secret = process.env.STRIPE_WEBHOOK_SECRET;
        if (!secret) {
            functions.logger.error('stripe webhook: webhook secret is not configured');
            res.status(500).send('Stripe webhook is not configured.');
            return;
        }
        const signature = req.headers['stripe-signature'];
        try {
            const result = await verifyAndHandleWebhook(
                req.rawBody,
                typeof signature === 'string' ? signature : undefined,
                secret,
                {
                    repo: createFirestoreBillingRepository(),
                    stripe: createStripeClient(process.env.STRIPE_SECRET_KEY ?? ''),
                    nowMs: Date.now(),
                },
            );
            res.status(result.httpStatus).send(result.outcome);
        } catch (error) {
            functions.logger.error('stripe webhook: handler failed');
            res.status(500).send('Stripe webhook handler failed.');
        }
    });
