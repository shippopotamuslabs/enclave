import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
const pdf = require('pdf-parse');
const mammoth = require('mammoth');
import * as xlsx from 'xlsx';

/**
 * Cloud Function to extract text from an encrypted document in Firebase Storage.
 */
export const extractDocumentText = functions.https.onCall(async (data, context) => {
    // 1. Auth check
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'User must be logged in.');
    }

    const { path, key, iv } = data;
    if (!path || !key || !iv) {
        throw new functions.https.HttpsError('invalid-argument', 'Path, key, and iv must be provided.');
    }

    // Security: Ensure path is within temp_extractions and belongs to the user
    const userId = context.auth.uid;
    if (!path.startsWith(`temp_extractions/${userId}/`)) {
        throw new functions.https.HttpsError('permission-denied', 'Access to the specified path is not allowed.');
    }

    const bucket = admin.storage().bucket();
    const file = bucket.file(path);

    try {
        // 2. Download ciphertext
        const [exists] = await file.exists();
        if (!exists) {
            throw new functions.https.HttpsError('not-found', 'Document not found.');
        }

        const [buffer] = await file.download();

        // 3. Decrypt in-memory
        const keyBuffer = Buffer.from(key, 'base64');
        const ivBuffer = Buffer.from(iv, 'base64');
        
        const tagLength = 16; // 128 bits
        const ciphertext = buffer.subarray(0, buffer.length - tagLength);
        const tag = buffer.subarray(buffer.length - tagLength);

        const decipher = crypto.createDecipheriv('aes-256-gcm', keyBuffer, ivBuffer);
        decipher.setAuthTag(tag);
        
        const decryptedBuffer = Buffer.concat([
            decipher.update(ciphertext),
            decipher.final()
        ]);

        // 4. Extract text based on extension or mimeType
        let extractedText = '';
        const mimeType = data.mimeType || detectMimeType(decryptedBuffer);

        if (mimeType === 'application/pdf') {
            const pdfData = await pdf(decryptedBuffer);
            extractedText = pdfData.text;
        } else if (mimeType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
            const docxResult = await mammoth.extractRawText({ buffer: decryptedBuffer });
            extractedText = docxResult.value;
        } else if (mimeType === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') {
            const workbook = xlsx.read(decryptedBuffer, { type: 'buffer' });
            extractedText = workbook.SheetNames.map((sheetName: string) => {
                const sheet = workbook.Sheets[sheetName];
                return xlsx.utils.sheet_to_txt(sheet);
            }).join('\n\n');
        } else {
            extractedText = decryptedBuffer.toString('utf8');
        }
        // 5. Cleanup
        await file.delete();

        return { text: extractedText };
    } catch (error: any) {
        console.error('Extraction error:', error);
        // Ensure cleanup even on error
        try { await file.delete(); } catch (e) {}
        
        throw new functions.https.HttpsError('internal', error.message || 'Failed to extract text.');
    }
});

function detectMimeType(buffer: Buffer): string {
    // Basic magic bytes detection
    if (buffer.subarray(0, 4).toString('hex') === '25504446') return 'application/pdf';
    if (buffer.subarray(0, 4).toString('hex') === '504b0304') {
        // Zip-based formats (DOCX, XLSX)
        // For now, we'll return a generic one or let the extractors try
        return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'; 
    }
    return 'text/plain';
}
