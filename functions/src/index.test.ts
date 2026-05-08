import functionsTest from 'firebase-functions-test';
import { expect } from 'chai';
import 'mocha';
import * as admin from 'firebase-admin';
import * as sinon from 'sinon';

// Mocking firestore before importing functions
const setStub = sinon.stub().resolves();
const docStub = sinon.stub().returns({ set: setStub });
const collectionStub = sinon.stub().returns({ doc: docStub });
const firestoreStub = sinon.stub(admin, 'firestore').returns({
    collection: collectionStub
} as any);

// We must initialize the test environment before importing the functions
const test = functionsTest();

import * as myFunctions from './index';

describe('uploadSharedChat', () => {

    after(() => {
        firestoreStub.restore();
        test.cleanup();
    });

    it('should save encryptedPayload and mediaPaths to firestore', async () => {
        const data = {
            shareId: 'temp-id',
            encryptedPayload: 'some-encrypted-string',
            iv: 'some-iv',
            salt: 'some-salt',
            mediaPaths: ['shared_chat_media/temp-id/media1']
        };
        const context = {
            auth: {
                uid: 'test-user-id'
            }
        };

        const wrapped = test.wrap(myFunctions.uploadSharedChat);
        const result = await wrapped(data, context);

        expect(result).to.have.property('shareId');
        const shareId = result.shareId;
        
        expect(setStub.calledOnce).to.be.true;
        const savedData = setStub.getCall(0).args[0];
        expect(savedData.mediaPaths).to.deep.equal([`shared_chat_media/${shareId}/media1`]);
    });

    it('should throw if mediaPaths exceed limit', async () => {
        const data = {
            encryptedPayload: 'some-encrypted-string',
            iv: 'some-iv',
            mediaPaths: new Array(51).fill('path')
        };
        const context = { auth: { uid: 'uid' } };
        const wrapped = test.wrap(myFunctions.uploadSharedChat);
        
        try {
            await wrapped(data, context);
            expect.fail('Should have thrown');
        } catch (e: any) {
            expect(e.code).to.equal('invalid-argument');
        }
    });
});
