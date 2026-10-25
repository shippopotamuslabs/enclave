import * as functions from 'firebase-functions/v1';
import {
    CheckoutConfig,
    createFirestoreBillingRepository,
    createStripeClient,
    DEFAULT_CHECKOUT_CANCEL_URL,
    DEFAULT_CHECKOUT_SUCCESS_URL,
    DEFAULT_SUBSCRIBE_URL,
    handleCreateCheckoutSession,
    handleCreatePortalSession,
    PortalConfig,
} from './billing';

const STRIPE_SECRET = ['STRIPE_SECRET_KEY'];

function readCheckoutConfig(): CheckoutConfig {
    return {
        priceId: process.env.STRIPE_PRICE_ID ?? '',
        successUrl: process.env.ENCLAVE_CHECKOUT_SUCCESS_URL || DEFAULT_CHECKOUT_SUCCESS_URL,
        cancelUrl: process.env.ENCLAVE_CHECKOUT_CANCEL_URL || DEFAULT_CHECKOUT_CANCEL_URL,
    };
}

function readPortalConfig(): PortalConfig {
    return {
        returnUrl: process.env.ENCLAVE_PORTAL_RETURN_URL || DEFAULT_SUBSCRIBE_URL,
    };
}

function readStripeSecretKey(): string {
    const key = process.env.STRIPE_SECRET_KEY;
    if (!key) {
        throw new functions.https.HttpsError(
            'failed-precondition',
            'Stripe is not configured.',
        );
    }
    return key;
}

export const createCheckoutSession = functions
    .runWith({ secrets: STRIPE_SECRET })
    .https.onCall(async (data, context) => {
        if (!context.auth) {
            throw new functions.https.HttpsError(
                'unauthenticated',
                'User must be logged in to start checkout.',
            );
        }
        return handleCreateCheckoutSession(
            context.auth.uid,
            createFirestoreBillingRepository(),
            createStripeClient(readStripeSecretKey()),
            readCheckoutConfig(),
        );
    });

export const createPortalSession = functions
    .runWith({ secrets: STRIPE_SECRET })
    .https.onCall(async (data, context) => {
        if (!context.auth) {
            throw new functions.https.HttpsError(
                'unauthenticated',
                'User must be logged in to manage billing.',
            );
        }
        return handleCreatePortalSession(
            context.auth.uid,
            createFirestoreBillingRepository(),
            createStripeClient(readStripeSecretKey()),
            readPortalConfig(),
        );
    });
