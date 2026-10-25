import { expect } from 'chai';
import 'mocha';
import {
    assertUserEntitled,
    entitlementDocPath,
    EntitlementDocGetter,
    isEntitledFromDocs,
    isUserEntitled,
    OVERRIDE_ENTITLEMENT_DOC_ID,
    resetEntitlementFirestoreGetter,
    setEntitlementFirestoreGetter,
    STRIPE_ENTITLEMENT_DOC_ID,
} from './assertEntitled';

describe('entitlementDocPath', () => {
    it('builds the stripe and override doc paths under users/{uid}/entitlement', () => {
        expect(entitlementDocPath('alice', STRIPE_ENTITLEMENT_DOC_ID)).to.equal(
            'users/alice/entitlement/stripe',
        );
        expect(entitlementDocPath('alice', OVERRIDE_ENTITLEMENT_DOC_ID)).to.equal(
            'users/alice/entitlement/override',
        );
    });
});

describe('isEntitledFromDocs', () => {
    const NOW_MS = 1_700_000_000_000;

    it('grants when the override sets freeAccess', () => {
        expect(isEntitledFromDocs({ override: { freeAccess: true } }, NOW_MS)).to.equal(true);
    });

    it('grants via override even when stripe is inactive', () => {
        expect(
            isEntitledFromDocs(
                { stripe: { active: false }, override: { freeAccess: true } },
                NOW_MS,
            ),
        ).to.equal(true);
    });

    it('grants when stripe is active with no expiry', () => {
        expect(isEntitledFromDocs({ stripe: { active: true } }, NOW_MS)).to.equal(true);
    });

    it('grants when stripe is active with a future expiry', () => {
        expect(
            isEntitledFromDocs(
                { stripe: { active: true, expiryTimeMillis: NOW_MS + 1000 } },
                NOW_MS,
            ),
        ).to.equal(true);
    });

    it('denies when stripe is active with a past expiry', () => {
        expect(
            isEntitledFromDocs(
                { stripe: { active: true, expiryTimeMillis: NOW_MS - 1000 } },
                NOW_MS,
            ),
        ).to.equal(false);
    });

    it('denies when stripe expiry equals now', () => {
        expect(
            isEntitledFromDocs({ stripe: { active: true, expiryTimeMillis: NOW_MS } }, NOW_MS),
        ).to.equal(false);
    });

    it('denies when stripe is inactive', () => {
        expect(isEntitledFromDocs({ stripe: { active: false } }, NOW_MS)).to.equal(false);
    });

    it('denies when docs are missing', () => {
        expect(isEntitledFromDocs({}, NOW_MS)).to.equal(false);
        expect(isEntitledFromDocs({ stripe: undefined, override: undefined }, NOW_MS)).to.equal(
            false,
        );
    });

    it('denies when override freeAccess is not exactly true', () => {
        expect(isEntitledFromDocs({ override: { freeAccess: 'yes' } }, NOW_MS)).to.equal(false);
    });
});

describe('isUserEntitled / assertUserEntitled', () => {
    const NOW_MS = 1_700_000_000_000;

    afterEach(() => {
        resetEntitlementFirestoreGetter();
    });

    function stubGetter(docs: Record<string, object | undefined>): EntitlementDocGetter {
        return async (uid: string, docId: string) => {
            expect(uid).to.equal('alice');
            return docs[docId];
        };
    }

    it('reads stripe and override docs and grants on active stripe', async () => {
        setEntitlementFirestoreGetter(
            stubGetter({ stripe: { active: true }, override: undefined }),
        );
        expect(await isUserEntitled('alice', NOW_MS)).to.equal(true);
    });

    it('grants on override freeAccess', async () => {
        setEntitlementFirestoreGetter(
            stubGetter({ stripe: { active: false }, override: { freeAccess: true } }),
        );
        expect(await isUserEntitled('alice', NOW_MS)).to.equal(true);
    });

    it('denies when both docs are missing', async () => {
        setEntitlementFirestoreGetter(stubGetter({}));
        expect(await isUserEntitled('alice', NOW_MS)).to.equal(false);
    });

    it('assertUserEntitled resolves when entitled', async () => {
        setEntitlementFirestoreGetter(
            stubGetter({ stripe: { active: true }, override: undefined }),
        );
        await assertUserEntitled('alice', NOW_MS);
    });

    it('assertUserEntitled throws permission-denied when not entitled', async () => {
        setEntitlementFirestoreGetter(stubGetter({}));
        try {
            await assertUserEntitled('alice', NOW_MS);
            expect.fail('Should have thrown');
        } catch (e: unknown) {
            const error = e as { code?: string; message?: string };
            expect(error.code).to.equal('permission-denied');
            expect(error.message).to.include('Enclave subscription required.');
        }
    });
});
