import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { v4 as uuidv4 } from 'uuid';

admin.initializeApp();

const DEEP_LINK_SCHEME = "pocketcrew";
const APP_DOMAIN = "pocketcrew.app";

export const uploadSharedChat = functions.https.onCall(async (data, context) => {
    // Basic auth check - ensure the user is logged into the app
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'User must be logged in to share chats.');
    }

    const { encryptedPayload, iv, salt } = data;
    if (!encryptedPayload || !iv) {
        throw new functions.https.HttpsError('invalid-argument', 'encryptedPayload and iv must be provided.');
    }

    const shareId = uuidv4();
    const createdAt = Date.now();
    const expiresAt = createdAt + 7 * 24 * 60 * 60 * 1000; // 7 days

    const sharedChat: any = {
        id: shareId,
        ownerId: context.auth.uid,
        createdAt,
        expiresAt,
        shareUrl: `https://${APP_DOMAIN}/share/${shareId}`,
        encryptedPayload,
        iv,
        salt: salt || ""
    };

    await admin.firestore().collection('shared_chats').doc(shareId).set(sharedChat);

    return {
        shareId,
        shareUrl: sharedChat.shareUrl
    };
});

/**
 * Public HTTPS request handler to render the shared chat as HTML.
 */
export const share = functions.https.onRequest(async (req, res) => {
    // Handle both /share/{id} and /share?id={id}
    const shareId = req.path.split('/').pop() || req.query.id as string;
    
    if (!shareId || shareId === 'share') {
        res.status(400).send("Invalid share ID");
        return;
    }

    const doc = await admin.firestore().collection('shared_chats').doc(shareId).get();
    if (!doc.exists) {
        res.status(404).send(`
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Link Expired - Pocket Crew</title>
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
                <a href="https://pocketcrew.app">Download Pocket Crew</a>
            </body>
            </html>
        `);
        return;
    }

    const data = doc.data();
    if (!data || !data.encryptedPayload) {
        res.status(500).send("Chat data is missing or invalid");
        return;
    }

    const scriptHtml = `
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <script>
        (async () => {
            const encryptedPayload = "${data.encryptedPayload}";
            const iv = "${data.iv}";
            
            const hash = window.location.hash;
            const keyBase64 = hash.startsWith('#key=') ? hash.substring(5) : null;
            
            if (!keyBase64) {
                document.getElementById('messages').innerHTML = '<div style="text-align:center; padding: 40px; background: #27272A; border-radius: 12px; border: 2px solid #52525B; box-shadow: 6px 6px 0px #000000;"><p style="color:#DC2626; font-weight: 900; font-size: 1.2em; text-transform: uppercase;">Decryption Key Missing</p><p>This chat is end-to-end encrypted. You need the full link including the key to view it.</p></div>';
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
                const messages = JSON.parse(decryptedText);

                const messagesContainer = document.getElementById('messages');
                messagesContainer.innerHTML = messages.map(m => {
                    if (m.role.toLowerCase() === 'user') {
                        return \`
                            <div class="message user-message">
                                <div class="content">\${marked.parse(m.content)}</div>
                            </div>
                        \`;
                    } else {
                        return \`
                            <div class="message assistant-message">
                                <div class="content">\${marked.parse(m.content)}</div>
                            </div>
                        \`;
                    }
                }).join('');
            } catch (e) {
                console.error(e);
                document.getElementById('messages').innerHTML = '<div style="text-align:center; padding: 40px; background: #27272A; border-radius: 12px; border: 2px solid #52525B; box-shadow: 6px 6px 0px #000000;"><p style="color:#DC2626; font-weight: 900; font-size: 1.2em; text-transform: uppercase;">Decryption Failed</p><p>The key provided in the URL is incorrect or the data is corrupted.</p></div>';
            }
        })();
    </script>
    `;

    const html = `
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Shared Chat - Pocket Crew</title>
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
                background-color: #27272A; 
                border: 2px solid #52525B; 
                padding: 20px; 
                border-radius: 12px; 
                box-shadow: 6px 6px 0px #000000;
                margin-left: 40px;
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
            .header { text-align: center; margin-bottom: 60px; padding-bottom: 30px; border-bottom: 4px solid #000000; }
            .logo { color: #F4A361; font-size: 2.5em; font-weight: 900; letter-spacing: -1px; text-shadow: 3px 3px 0px #000000; }
            @media (max-width: 600px) {
                body { padding: 16px; }
                .user-message { padding: 16px; margin-left: 0; }
                .assistant-message { padding: 0; }
            }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="logo">Pocket Crew</div>
        </div>
        <div id="messages"></div>
        <a href="${DEEP_LINK_SCHEME}://shared/${shareId}" class="button">Open in Pocket Crew</a>
        ${scriptHtml}
    </body>
    </html>
    `;

    res.status(200).send(html);
});
