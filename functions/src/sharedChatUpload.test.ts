import { expect } from 'chai';
import 'mocha';
import {
    handleUploadSharedChat,
    InMemorySharedChatRepository,
} from './sharedChatUpload';

describe('handleUploadSharedChat', () => {
    it('should save encryptedPayload and mediaPaths to the repository', async () => {
        const repository = new InMemorySharedChatRepository();
        const data = {
            shareId: 'temp-id',
            encryptedPayload: 'some-encrypted-string',
            iv: 'some-iv',
            salt: 'some-salt',
            mediaPaths: ['shared_chat_media/temp-id/media1'],
        };

        const result = await handleUploadSharedChat(data, 'test-user-id', repository, {
            nowMs: 1_700_000_000_000,
        });

        expect(result.shareId).to.equal('temp-id');
        expect(repository.saved).to.have.length(1);

        const savedData = repository.saved[0];
        expect(savedData.mediaPaths).to.deep.equal(['shared_chat_media/temp-id/media1']);
        expect(savedData.ownerId).to.equal('test-user-id');
        expect(savedData.encryptedPayload).to.equal('some-encrypted-string');
        expect(savedData.createdAt).to.equal(1_700_000_000_000);
        expect(savedData.expiresAt).to.equal(1_700_000_000_000 + 7 * 24 * 60 * 60 * 1000);
    });

    it('should throw if mediaPaths exceed limit', async () => {
        const repository = new InMemorySharedChatRepository();
        const data = {
            shareId: 'temp-id',
            encryptedPayload: 'some-encrypted-string',
            iv: 'some-iv',
            mediaPaths: new Array(51).fill('shared_chat_media/temp-id/media'),
        };

        try {
            await handleUploadSharedChat(data, 'uid', repository);
            expect.fail('Should have thrown');
        } catch (e: unknown) {
            const error = e as { code?: string };
            expect(error.code).to.equal('invalid-argument');
        }

        expect(repository.saved).to.have.length(0);
    });

    it('should reject media paths that do not belong to the share id', async () => {
        const repository = new InMemorySharedChatRepository();
        const data = {
            shareId: 'temp-id',
            encryptedPayload: 'some-encrypted-string',
            iv: 'some-iv',
            mediaPaths: ['shared_chat_media/other-id/media1'],
        };

        try {
            await handleUploadSharedChat(data, 'uid', repository);
            expect.fail('Should have thrown');
        } catch (e: unknown) {
            const error = e as { code?: string; message?: string };
            expect(error.code).to.equal('invalid-argument');
            expect(error.message).to.include('Invalid media path');
        }
    });
});
