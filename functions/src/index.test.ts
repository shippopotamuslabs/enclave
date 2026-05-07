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

    it('should save encryptedPayload to firestore', async () => {
        const data = {
            encryptedPayload: 'some-encrypted-string',
            iv: 'some-iv',
            salt: 'some-salt'
        };
        const context = {
            auth: {
                uid: 'test-user-id'
            }
        };

        const wrapped = test.wrap(myFunctions.uploadSharedChat);
        const result = await wrapped(data, context);

        expect(result).to.have.property('shareId');
        expect(result).to.have.property('shareUrl');
        
        expect(setStub.calledOnce).to.be.true;
        const savedData = setStub.getCall(0).args[0];
        expect(savedData.encryptedPayload).to.equal('some-encrypted-string');
        expect(savedData.iv).to.equal('some-iv');
        expect(savedData.salt).to.equal('some-salt');
        expect(savedData.ownerId).to.equal('test-user-id');
    });
});
