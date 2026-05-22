import * as functions from 'firebase-functions/v1';

export const APP_DOMAIN = 'enclave-app.com';
export const MAX_SHARED_CHAT_MEDIA = 50;
export const SHARE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export interface UploadSharedChatRequest {
    shareId?: string;
    encryptedPayload?: string;
    iv?: string;
    salt?: string;
    mediaPaths?: string[];
}

export interface SharedChatDocument {
    id: string;
    ownerId: string;
    createdAt: number;
    expiresAt: number;
    shareUrl: string;
    encryptedPayload: string;
    iv: string;
    salt: string;
    mediaPaths: string[];
}

export interface SharedChatRepository {
    save(document: SharedChatDocument): Promise<void>;
}

export class InMemorySharedChatRepository implements SharedChatRepository {
    saved: SharedChatDocument[] = [];

    async save(document: SharedChatDocument): Promise<void> {
        this.saved.push(document);
    }
}

export function validateMediaPaths(shareId: string, mediaPaths: string[] | undefined): string[] {
    return (mediaPaths || []).map((path: string) => {
        if (!path.startsWith(`shared_chat_media/${shareId}/`)) {
            throw new functions.https.HttpsError('invalid-argument', `Invalid media path: ${path}`);
        }
        return path;
    });
}

export function buildSharedChatDocument(
    shareId: string,
    ownerId: string,
    data: UploadSharedChatRequest,
    nowMs: number,
): SharedChatDocument {
    const validatedMediaPaths = validateMediaPaths(shareId, data.mediaPaths);

    if (validatedMediaPaths.length > MAX_SHARED_CHAT_MEDIA) {
        throw new functions.https.HttpsError('invalid-argument', 'Too many media items (max 50).');
    }

    const expiresAt = nowMs + SHARE_TTL_MS;

    return {
        id: shareId,
        ownerId,
        createdAt: nowMs,
        expiresAt,
        shareUrl: `https://${APP_DOMAIN}/share/${shareId}`,
        encryptedPayload: data.encryptedPayload!,
        iv: data.iv!,
        salt: data.salt || '',
        mediaPaths: validatedMediaPaths,
    };
}

export async function handleUploadSharedChat(
    data: UploadSharedChatRequest,
    ownerId: string,
    repository: SharedChatRepository,
    options: { generateShareId?: () => string; nowMs?: number } = {},
): Promise<{ shareId: string; shareUrl: string }> {
    if (!data.encryptedPayload || !data.iv) {
        throw new functions.https.HttpsError('invalid-argument', 'encryptedPayload and iv must be provided.');
    }

    const shareId = data.shareId ?? options.generateShareId?.();
    if (!shareId) {
        throw new functions.https.HttpsError('invalid-argument', 'shareId must be provided.');
    }

    const sharedChat = buildSharedChatDocument(shareId, ownerId, data, options.nowMs ?? Date.now());
    await repository.save(sharedChat);

    return {
        shareId,
        shareUrl: sharedChat.shareUrl,
    };
}
