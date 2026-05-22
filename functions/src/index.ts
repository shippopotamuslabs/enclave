import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { v4 as uuidv4 } from 'uuid';
import { APP_DOMAIN, handleUploadSharedChat } from './sharedChatUpload';
export { cleanupExpiredShares } from './scheduledCleanup';
export { handleSyncEntityWrite, handleSyncEntityWriteTrigger } from './syncEventFeed';

admin.initializeApp();

const DEEP_LINK_SCHEME = "enclave";

/** Public HTTPS download URL for a Storage object (bucket must allow read). */
export function buildPublicStorageMediaUrl(bucketName: string, storagePath: string): string {
    return `https://firebasestorage.googleapis.com/v0/b/${bucketName}/o/${encodeURIComponent(storagePath)}?alt=media`;
}

function resolveDefaultStorageBucketName(): string {
    return admin.storage().bucket().name;
}

function extractShareId(req: functions.https.Request): string | null {
    const queryId = req.query.id;
    if (typeof queryId === 'string' && queryId.trim().length > 0) {
        return queryId.trim();
    }

    const pathParts = req.path.split('/').filter(Boolean);
    const shareIndex = pathParts.indexOf('share');
    if (shareIndex >= 0 && pathParts[shareIndex + 1]) {
        return pathParts[shareIndex + 1].split('?')[0];
    }

    const lastSegment = pathParts[pathParts.length - 1];
    if (lastSegment && lastSegment !== 'share') {
        return lastSegment.split('?')[0];
    }

    return null;
}

function renderShareNotFoundPage(): string {
    return `
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Link Expired - Enclave</title>
                <style>
                    body { 
                        background-color: #18181B; 
                        color: #E4E4E7; 
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; 
                        text-align: center; 
                        padding-top: 100px; 
                    }
                    h1 { 
                        color: #F4A361; 
                        font-weight: 900; 
                        text-shadow: 3px 3px 0px #000000; 
                        font-size: 2.5em;
                        letter-spacing: -1px;
                    }
                    a { 
                        display: inline-block;
                        margin-top: 30px;
                        padding: 16px 32px; 
                        background-color: #F4A361; 
                        color: #18181B; 
                        text-decoration: none; 
                        border-radius: 8px; 
                        font-weight: 900;
                        font-size: 1.1em;
                        text-transform: uppercase;
                        border: 2px solid #18181B;
                        box-shadow: 6px 6px 0px #000000;
                        transition: transform 0.1s, box-shadow 0.1s;
                    }
                    a:active { 
                        transform: translate(4px, 4px); 
                        box-shadow: 2px 2px 0px #000000; 
                    }
                </style>
            </head>
            <body>
                <h1>Shared chat not found</h1>
                <p>It may have expired (7-day limit).</p>
                <a href="https://${APP_DOMAIN}">Download Enclave</a>
            </body>
            </html>
        `;
}

export const uploadSharedChat = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'User must be logged in to share chats.');
    }

    return handleUploadSharedChat(
        data,
        context.auth.uid,
        {
            save: async (document) => {
                await admin.firestore().collection('shared_chats').doc(document.id).set(document);
            },
        },
        { generateShareId: () => uuidv4() },
    );
});

/**
 * Public HTTPS request handler to render the shared chat as HTML.
 */
export const share = functions.https.onRequest(async (req, res) => {
    const shareId = extractShareId(req);
    
    if (!shareId) {
        res.status(400).send("Invalid share ID");
        return;
    }

    const doc = await admin.firestore().collection('shared_chats').doc(shareId).get();
    if (!doc.exists) {
        res.status(404).send(renderShareNotFoundPage());
        return;
    }

    const data = doc.data();
    if (!data || !data.encryptedPayload) {
        res.status(500).send("Chat data is missing or invalid");
        return;
    }

    const storageBucket = resolveDefaultStorageBucketName();

    const scriptHtml = `
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <script>
        (async () => {
            const encryptedPayload = "${data.encryptedPayload}";
            const iv = "${data.iv}";
            const storageBucket = "${storageBucket}";
            
            const queryKey = new URLSearchParams(window.location.search).get('key');
            const hash = window.location.hash;
            const hashKey = hash.startsWith('#key=') ? hash.substring(5) : null;
            const keyBase64 = queryKey || hashKey;
            
            if (!keyBase64) {
                document.getElementById('messages').innerHTML = '<div class="error-box"><p class="error-title">Decryption Key Missing</p><p>This chat is end-to-end encrypted. You need the full link including the key to view it.</p></div>';
                return;
            }
            
            function base64ToUint8Array(base64) {
                const binaryString = atob(base64);
                const bytes = new Uint8Array(binaryString.length);
                for (let i = 0; i < binaryString.length; i++) {
                    bytes[i] = binaryString.charCodeAt(i);
                }
                return bytes;
            }

            async function fetchAndDecryptMedia(storagePath, cryptoKey) {
                try {
                    const url = \`https://firebasestorage.googleapis.com/v0/b/\${storageBucket}/o/\${encodeURIComponent(storagePath)}?alt=media\`;
                    const response = await fetch(url);
                    if (!response.ok) throw new Error('Failed to fetch media');
                    const encryptedBytes = new Uint8Array(await response.arrayBuffer());
                    const iv = encryptedBytes.slice(0, 12);
                    const ciphertext = encryptedBytes.slice(12);
                    const decryptedBuffer = await window.crypto.subtle.decrypt(
                        { name: "AES-GCM", iv: iv },
                        cryptoKey,
                        ciphertext
                    );
                    return URL.createObjectURL(new Blob([decryptedBuffer]));
                } catch (e) {
                    console.error('Media decryption failed', e);
                    return null;
                }
            }

            try {
                const keyData = base64ToUint8Array(keyBase64.replace(/-/g, '+').replace(/_/g, '/'));
                const ivData = base64ToUint8Array(iv);
                const payloadData = base64ToUint8Array(encryptedPayload);

                const cryptoKey = await window.crypto.subtle.importKey(
                    "raw",
                    keyData,
                    { name: "AES-GCM" },
                    false,
                    ["decrypt"]
                );

                const decryptedBuffer = await window.crypto.subtle.decrypt(
                    { name: "AES-GCM", iv: ivData },
                    cryptoKey,
                    payloadData
                );

                const decoder = new TextDecoder();
                const decryptedText = decoder.decode(decryptedBuffer);
                const sharedChat = JSON.parse(decryptedText);
                const messages = Array.isArray(sharedChat) ? sharedChat : sharedChat.messages;
                const media = sharedChat.media || [];
                const mediaMap = {};
                media.forEach(m => mediaMap[m.id] = m);

                const messagesContainer = document.getElementById('messages');
                
                for (const m of messages) {
                    const messageDiv = document.createElement('div');
                    messageDiv.className = \`message \${m.role.toLowerCase() === 'user' ? 'user-message' : 'assistant-message'}\`;
                    
                    let mediaHtml = '';
                    const attachedIds = m.attachedImageMediaIds || (m.attachedImageMediaId ? [m.attachedImageMediaId] : []);
                    for (const mediaId of attachedIds) {
                        if (!mediaMap[mediaId]) continue;
                        const mediaItem = mediaMap[mediaId];
                        const blobUrl = await fetchAndDecryptMedia(mediaItem.storagePath, cryptoKey);
                        if (blobUrl) {
                            mediaHtml += \`<img src="\${blobUrl}" class="shared-image" />\`;
                        }
                    }
                    
                    if (m.generatedMediaIds) {
                        for (const genId of m.generatedMediaIds) {
                            if (mediaMap[genId]) {
                                const mediaItem = mediaMap[genId];
                                const blobUrl = await fetchAndDecryptMedia(mediaItem.storagePath, cryptoKey);
                                if (blobUrl) {
                                    mediaHtml += \`<img src="\${blobUrl}" class="shared-image" />\`;
                                }
                            }
                        }
                    }

                    if (m.fileAttachments && m.fileAttachments.length > 0) {
                        mediaHtml += '<div class="file-attachments">';
                        m.fileAttachments.forEach(f => {
                            mediaHtml += \`<div class="file-pill"><span>\${f.name}</span></div>\`;
                        });
                        mediaHtml += '</div>';
                    }

                    messageDiv.innerHTML = \`
                        <div class="content">
                            \${mediaHtml}
                            \${marked.parse(m.content)}
                        </div>
                    \`;
                    messagesContainer.appendChild(messageDiv);
                }
            } catch (e) {
                console.error(e);
                document.getElementById('messages').innerHTML = '<div class="error-box"><p class="error-title">Decryption Failed</p><p>The key provided in the URL is incorrect or the data is corrupted.</p></div>';
            }
        })();
    </script>
    `;

    const html = `
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Shared Chat - Enclave</title>
        <style>
            body { 
                background-color: #18181B; 
                color: #E4E4E7; 
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; 
                padding: 20px; 
                max-width: 800px; 
                margin: 0 auto; 
            }
            .message { 
                margin-bottom: 32px; 
                position: relative; 
            }
            .user-message { 
                width: 85%;
                max-width: 85%;
                margin-left: auto;
                margin-right: 0;
                background-color: #27272A; 
                border: 2px solid #52525B; 
                padding: 20px; 
                border-radius: 20px 20px 4px 20px;
                box-shadow: 6px 6px 0px #000000;
            }
            .assistant-message { 
                padding: 0 20px; 
            }
            .content { line-height: 1.6; font-size: 1.05em; }
            .content p { margin: 0 0 16px 0; }
            .content p:last-child { margin-bottom: 0; }
            .content pre { 
                background-color: #000; 
                padding: 16px; 
                border-radius: 8px; 
                overflow-x: auto; 
                border: 2px solid #52525B; 
                box-shadow: 4px 4px 0px #000000;
            }
            .content code { font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace; font-size: 0.9em; color: #F4A361; }
            .content pre code { color: #E4E4E7; padding: 0; background-color: transparent; }
            .content blockquote { border-left: 4px solid #F4A361; padding-left: 16px; margin: 0; font-style: italic; color: #A1A1AA; }
            .button { 
                display: block; 
                width: fit-content; 
                min-width: 220px; 
                margin: 60px auto; 
                padding: 16px 32px; 
                background-color: #F4A361; 
                color: #18181B; 
                text-align: center; 
                text-decoration: none; 
                border-radius: 8px; 
                font-weight: 900;
                font-size: 1.1em;
                text-transform: uppercase;
                border: 2px solid #18181B;
                box-shadow: 6px 6px 0px #000000;
                transition: transform 0.1s, box-shadow 0.1s;
            }
            .button:active { 
                transform: translate(4px, 4px); 
                box-shadow: 2px 2px 0px #000000; 
            }
            .shared-image { 
                width: 100%; 
                border-radius: 8px; 
                margin-bottom: 16px; 
                border: 2px solid #52525B;
                box-shadow: 4px 4px 0px #000000;
            }
            .file-attachments { 
                display: flex; 
                flex-wrap: wrap; 
                gap: 8px; 
                margin-bottom: 16px; 
            }
            .file-pill { 
                background: #3F3F46; 
                padding: 6px 12px; 
                border-radius: 20px; 
                font-size: 0.9em; 
                border: 1px solid #52525B;
                display: flex;
                align-items: center;
                gap: 6px;
            }
            .file-pill::before {
                content: "📄";
            }
            .error-box {
                text-align:center; 
                padding: 40px; 
                background: #27272A; 
                border-radius: 12px; 
                border: 2px solid #52525B; 
                box-shadow: 6px 6px 0px #000000;
            }
            .error-title {
                color:#DC2626; 
                font-weight: 900; 
                font-size: 1.2em; 
                text-transform: uppercase;
                margin-bottom: 10px;
            }
            .header { text-align: center; margin-bottom: 60px; padding-bottom: 30px; border-bottom: 4px solid #000000; }
            .logo { color: #F4A361; font-size: 2.5em; font-weight: 900; letter-spacing: -1px; text-shadow: 3px 3px 0px #000000; }
            @media (max-width: 600px) {
                body { padding: 16px; }
                .user-message { padding: 16px; }
                .assistant-message { padding: 0; }
            }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="logo">Enclave</div>
        </div>
        <div id="messages"></div>
        <a href="${DEEP_LINK_SCHEME}://shared/${shareId}" class="button">Open in Enclave</a>
        ${scriptHtml}
    </body>
    </html>
    `;

    res.status(200).send(html);
});
