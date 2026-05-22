import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

export const cleanupExpiredShares = functions.pubsub.schedule('every 24 hours').onRun(async (context) => {
    const now = Date.now();
    const expiredShares = await admin.firestore().collection('shared_chats')
        .where('expiresAt', '<', now)
        .get();

    const bucket = admin.storage().bucket();

    for (const doc of expiredShares.docs) {
        const shareId = doc.id;

        // Delete media from Storage
        // We delete the entire prefix for the shareId to be safe
        try {
            await bucket.deleteFiles({ prefix: `shared_chat_media/${shareId}/` });
        } catch (e) {
            console.error(`Failed to delete media for share ${shareId}`, e);
        }

        // Delete doc from Firestore
        await doc.ref.delete();
    }
    
    console.log(`Cleaned up ${expiredShares.size} expired shares.`);
});
