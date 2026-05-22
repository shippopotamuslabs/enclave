import { expect } from 'chai';
import 'mocha';
import { buildPublicStorageMediaUrl } from './index';

describe('buildPublicStorageMediaUrl', () => {
    it('uses the Firebase Storage bucket name, not legacy appspot.com', () => {
        const url = buildPublicStorageMediaUrl(
            'enclave-497101.firebasestorage.app',
            'shared_chat_media/share-1/media-1',
        );
        expect(url).to.equal(
            'https://firebasestorage.googleapis.com/v0/b/enclave-497101.firebasestorage.app/o/shared_chat_media%2Fshare-1%2Fmedia-1?alt=media',
        );
    });
});
