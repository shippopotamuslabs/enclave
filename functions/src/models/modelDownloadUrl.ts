import * as functions from 'firebase-functions/v1';
import { GetObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { assertUserEntitled } from '../entitlement/assertEntitled';

/** Presigned-URL TTL in seconds (S3 expiresIn) and milliseconds (response field). */
export const MODEL_DOWNLOAD_URL_TTL_SECONDS = 900;
export const MODEL_DOWNLOAD_URL_TTL_MILLIS = 900 * 1000;

/**
 * File extensions the model catalog produces for R2-hosted artifacts.
 * Derived from model_config.json asset fileNames plus mmproj/catalog handling:
 * .gguf (quantized LLM + SD diffusion), .litertlm, .onnx, .onnx_data,
 * .json (tokenizer), .txt (vocab), .bin (whisper/ggml weights), .zip (QNN packs).
 * Compared case-insensitively against the final path segment.
 */
export const MODEL_DOWNLOAD_ALLOWED_EXTENSIONS: ReadonlySet<string> = new Set([
    '.gguf',
    '.litertlm',
    '.onnx',
    '.onnx_data',
    '.json',
    '.txt',
    '.bin',
    '.zip',
]);

export interface MintModelDownloadUrlResult {
    url: string;
    expiresAtMillis: number;
}

export interface ModelDownloadUrlConfig {
    endpoint: string;
    bucket: string;
}

export interface ModelDownloadPresigner {
    presignGetObject(bucket: string, key: string, expiresInSeconds: number): Promise<string>;
}

export interface MintModelDownloadUrlDeps {
    presigner: ModelDownloadPresigner;
    config: ModelDownloadUrlConfig;
    nowMs: number;
}

/**
 * Pure validation for the R2 object key. Mirrors how DynamicModelUrlProvider
 * builds CLOUDFLARE_R2 paths (path after https://config.enclave-app.com/):
 * optional '<huggingFacePath>/' prefix segments plus a safe file name.
 * Returns the trimmed key on success; throws invalid-argument otherwise.
 */
export function validateModelDownloadFilePath(filePath: unknown): string {
    if (typeof filePath !== 'string' || filePath.length === 0) {
        throw new functions.https.HttpsError('invalid-argument', 'filePath must be a non-empty string.');
    }
    if (filePath.startsWith('/')) {
        throw new functions.https.HttpsError('invalid-argument', 'filePath must be a relative object key.');
    }
    if (filePath.includes('\\')) {
        throw new functions.https.HttpsError('invalid-argument', 'filePath must not contain backslashes.');
    }
    const segments = filePath.split('/');
    for (const segment of segments) {
        if (segment === '' || segment === '.' || segment === '..') {
            throw new functions.https.HttpsError(
                'invalid-argument',
                'filePath must not contain empty, dot, or parent segments.',
            );
        }
    }
    const fileName = segments[segments.length - 1].toLowerCase();
    const hasAllowedExtension = [...MODEL_DOWNLOAD_ALLOWED_EXTENSIONS].some((extension) =>
        fileName.endsWith(extension),
    );
    if (!hasAllowedExtension) {
        throw new functions.https.HttpsError(
            'invalid-argument',
            'filePath must end in a supported model file extension.',
        );
    }
    return filePath;
}

export function readModelDownloadConfig(env: NodeJS.ProcessEnv = process.env): ModelDownloadUrlConfig {
    const endpoint = env.R2_ENDPOINT ?? '';
    const bucket = env.R2_BUCKET ?? '';
    if (endpoint === '' || bucket === '') {
        throw new functions.https.HttpsError('failed-precondition', 'Model downloads are not configured.');
    }
    return { endpoint, bucket };
}

/**
 * Pure handler: takes an already-validated filePath and seams, presigns a
 * single S3 GetObject URL, and stamps expiry as nowMs + TTL. No secrets logged.
 */
export async function handleMintModelDownloadUrl(
    validatedFilePath: string,
    deps: MintModelDownloadUrlDeps,
): Promise<MintModelDownloadUrlResult> {
    const url = await deps.presigner.presignGetObject(
        deps.config.bucket,
        validatedFilePath,
        MODEL_DOWNLOAD_URL_TTL_SECONDS,
    );
    return { url, expiresAtMillis: deps.nowMs + MODEL_DOWNLOAD_URL_TTL_MILLIS };
}

export class S3ModelDownloadPresigner implements ModelDownloadPresigner {
    async presignGetObject(bucket: string, key: string, expiresInSeconds: number): Promise<string> {
        const client = new S3Client({
            endpoint: process.env.R2_ENDPOINT,
            region: 'auto',
            credentials: {
                accessKeyId: process.env.R2_ACCESS_KEY_ID ?? '',
                secretAccessKey: process.env.R2_SECRET_ACCESS_KEY ?? '',
            },
        });
        try {
            return await getSignedUrl(
                client,
                new GetObjectCommand({ Bucket: bucket, Key: key }),
                { expiresIn: expiresInSeconds },
            );
        } finally {
            client.destroy();
        }
    }
}

type PresignerFactory = () => ModelDownloadPresigner;

let presignerFactory: PresignerFactory = () => new S3ModelDownloadPresigner();

/** Test seam: swap the presigner constructed by the callable wrapper. */
export function setModelDownloadPresignerFactory(factory: PresignerFactory): void {
    presignerFactory = factory;
}

export function resetModelDownloadPresignerFactory(): void {
    presignerFactory = () => new S3ModelDownloadPresigner();
}

const R2_SECRETS = ['R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY'];

export const mintModelDownloadUrl = functions
    .runWith({ secrets: R2_SECRETS })
    .https.onCall(async (data, context) => {
        if (!context.auth) {
            throw new functions.https.HttpsError(
                'unauthenticated',
                'User must be logged in to download models.',
            );
        }
        await assertUserEntitled(context.auth.uid);
        const filePath = validateModelDownloadFilePath(
            (data as { filePath?: unknown } | null | undefined)?.filePath,
        );
        return handleMintModelDownloadUrl(filePath, {
            presigner: presignerFactory(),
            config: readModelDownloadConfig(),
            nowMs: Date.now(),
        });
    });
