import { expect } from 'chai';
import 'mocha';
import functionsTest from 'firebase-functions-test';
// eslint-disable-next-line @typescript-eslint/no-var-requires
const adminLib = require('firebase-admin');

let firestoreStubbed = false;

function stubAdminFirestore(fake: () => unknown): void {
    Object.defineProperty(adminLib, 'firestore', {
        value: fake,
        configurable: true,
        writable: true,
        enumerable: true,
    });
    firestoreStubbed = true;
}
import {
    resetEntitlementFirestoreGetter,
    setEntitlementFirestoreGetter,
} from '../entitlement/assertEntitled';
import { mintModelDownloadUrl } from './modelDownloadUrl';
import {
    handleMintModelDownloadUrl,
    MODEL_DOWNLOAD_URL_TTL_MILLIS,
    MODEL_DOWNLOAD_URL_TTL_SECONDS,
    readModelDownloadConfig,
    resetModelDownloadPresignerFactory,
    setModelDownloadPresignerFactory,
    validateModelDownloadFilePath,
} from './modelDownloadUrl';

const testEnv = functionsTest();
const wrappedMint = testEnv.wrap(mintModelDownloadUrl);

const authed = (uid: string): unknown => ({ auth: { uid } });

interface RecordedCall {
    bucket: string;
    key: string;
    expiresInSeconds: number;
}

function useFakePresigner(url = 'https://signed.example/object'): {
    calls: RecordedCall[];
} {
    const calls: RecordedCall[] = [];
    setModelDownloadPresignerFactory(() => ({
        presignGetObject: async (bucket: string, key: string, expiresInSeconds: number) => {
            calls.push({ bucket, key, expiresInSeconds });
            return url;
        },
    }));
    return { calls };
}

function useR2Env(): void {
    process.env.R2_ENDPOINT = 'https://example.r2.cloudflarestorage.com';
    process.env.R2_BUCKET = 'models-bucket';
}

async function expectCode(
    call: () => Promise<unknown>,
    code: string,
    message?: string,
): Promise<void> {
    try {
        await call();
        expect.fail('Should have thrown');
    } catch (e: unknown) {
        const error = e as { code?: string; message?: string };
        expect(error.code).to.equal(code);
        if (message !== undefined) {
            expect(error.message).to.equal(message);
        }
    }
}

describe('validateModelDownloadFilePath', () => {
    it('accepts top-level and nested catalog paths', () => {
        expect(validateModelDownloadFilePath('gemma-4-E4B-it.litertlm')).to.equal(
            'gemma-4-E4B-it.litertlm',
        );
        expect(validateModelDownloadFilePath('chat_title_generator/model_q4.onnx')).to.equal(
            'chat_title_generator/model_q4.onnx',
        );
        expect(validateModelDownloadFilePath('a/b/Qwen3.5-2B.Q8_0.GGUF')).to.equal(
            'a/b/Qwen3.5-2B.Q8_0.GGUF',
        );
        expect(validateModelDownloadFilePath('chat_title_generator/tokenizer.json')).to.equal(
            'chat_title_generator/tokenizer.json',
        );
        expect(validateModelDownloadFilePath('p/realvis_xl_v5_dmd2_qnn2.28_8gen3.zip')).to.equal(
            'p/realvis_xl_v5_dmd2_qnn2.28_8gen3.zip',
        );
        expect(validateModelDownloadFilePath('chat_title_generator/model_q4.onnx_data')).to.equal(
            'chat_title_generator/model_q4.onnx_data',
        );
    });

    it('rejects empty, missing, and non-string paths', async () => {
        for (const bad of [undefined, null, '', 42, {}]) {
            await expectCode(async () => validateModelDownloadFilePath(bad), 'invalid-argument');
        }
    });

    it('rejects absolute paths, traversal, and backslashes', async () => {
        for (const bad of [
            '/absolute/model.gguf',
            '../escape.gguf',
            'a/../../escape.gguf',
            'a/../b/model.gguf',
            'a//b.gguf',
            'trailing/',
            '.',
            'a/./b.gguf',
            'back\\slash.gguf',
        ]) {
            await expectCode(async () => validateModelDownloadFilePath(bad), 'invalid-argument',);
        }
    });

    it('rejects unsupported extensions', async () => {
        for (const bad of ['model.exe', 'config.yaml', 'noextension', 'model.gguf.txt.bak']) {
            await expectCode(async () => validateModelDownloadFilePath(bad), 'invalid-argument');
        }
    });
});

describe('readModelDownloadConfig', () => {
    const savedEndpoint = process.env.R2_ENDPOINT;
    const savedBucket = process.env.R2_BUCKET;

    afterEach(() => {
        if (savedEndpoint === undefined) delete process.env.R2_ENDPOINT;
        else process.env.R2_ENDPOINT = savedEndpoint;
        if (savedBucket === undefined) delete process.env.R2_BUCKET;
        else process.env.R2_BUCKET = savedBucket;
    });

    it('returns endpoint and bucket when configured', () => {
        const config = readModelDownloadConfig({
            R2_ENDPOINT: 'https://example.r2.cloudflarestorage.com',
            R2_BUCKET: 'b',
        } as NodeJS.ProcessEnv);
        expect(config).to.deep.equal({
            endpoint: 'https://example.r2.cloudflarestorage.com',
            bucket: 'b',
        });
    });

    it('throws failed-precondition when R2 config is missing', async () => {
        await expectCode(
            async () =>
                readModelDownloadConfig({} as NodeJS.ProcessEnv),
            'failed-precondition',
        );
    });
});

describe('handleMintModelDownloadUrl', () => {
    it('presigns with bucket/key/900 and stamps expiry', async () => {
        const calls: RecordedCall[] = [];
        const result = await handleMintModelDownloadUrl('chat_title_generator/model_q4.onnx', {
            presigner: {
                presignGetObject: async (bucket: string, key: string, expires: number) => {
                    calls.push({ bucket, key, expiresInSeconds: expires });
                    return 'https://signed.example/o';
                },
            },
            config: { endpoint: 'https://e', bucket: 'models-bucket' },
            nowMs: 1_000_000,
        });
        expect(result).to.deep.equal({
            url: 'https://signed.example/o',
            expiresAtMillis: 1_000_000 + MODEL_DOWNLOAD_URL_TTL_MILLIS,
        });
        expect(calls).to.deep.equal([
            {
                bucket: 'models-bucket',
                key: 'chat_title_generator/model_q4.onnx',
                expiresInSeconds: MODEL_DOWNLOAD_URL_TTL_SECONDS,
            },
        ]);
        expect(MODEL_DOWNLOAD_URL_TTL_SECONDS).to.equal(900);
    });
});

describe('mintModelDownloadUrl callable boundary (wrap)', () => {
    const savedEndpoint = process.env.R2_ENDPOINT;
    const savedBucket = process.env.R2_BUCKET;

    afterEach(() => {
        resetEntitlementFirestoreGetter();
        resetModelDownloadPresignerFactory();
        if (savedEndpoint === undefined) delete process.env.R2_ENDPOINT;
        else process.env.R2_ENDPOINT = savedEndpoint;
        if (savedBucket === undefined) delete process.env.R2_BUCKET;
        else process.env.R2_BUCKET = savedBucket;
        if (firestoreStubbed) {
            delete adminLib.firestore;
            firestoreStubbed = false;
        }
    });

    after(() => {
        testEnv.cleanup();
    });

    it('rejects unauthenticated callers', async () => {
        useR2Env();
        useFakePresigner();
        await expectCode(
            () => wrappedMint({ filePath: 'm.gguf' }, {} as never),
            'unauthenticated',
        );
    });

    it('rejects non-entitled callers with the subscription message', async () => {
        useR2Env();
        useFakePresigner();
        setEntitlementFirestoreGetter(async () => undefined);
        await expectCode(
            () => wrappedMint({ filePath: 'm.gguf' }, authed('alice') as never),
            'permission-denied',
            'Enclave subscription required.',
        );
    });

    it('returns url and expiry for entitled callers', async () => {
        useR2Env();
        const { calls } = useFakePresigner('https://signed.example/o');
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'stripe' ? { active: true } : undefined,
        );
        stubAdminFirestore(() => ({
            collection: () => ({
                doc: () => ({ set: async () => undefined }),
            }),
        }));
        const before = Date.now();
        const result = (await wrappedMint(
            { filePath: 'chat_title_generator/model_q4.onnx' },
            authed('alice') as never,
        )) as { url: string; expiresAtMillis: number };
        const after = Date.now();
        expect(result.url).to.equal('https://signed.example/o');
        expect(result.expiresAtMillis).to.be.within(
            before + MODEL_DOWNLOAD_URL_TTL_MILLIS,
            after + MODEL_DOWNLOAD_URL_TTL_MILLIS,
        );
        expect(calls).to.deep.equal([
            {
                bucket: 'models-bucket',
                key: 'chat_title_generator/model_q4.onnx',
                expiresInSeconds: 900,
            },
        ]);
    });

    it('rejects invalid paths with invalid-argument', async () => {
        useR2Env();
        useFakePresigner();
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'stripe' ? { active: true } : undefined,
        );
        await expectCode(
            () => wrappedMint({ filePath: '../escape.gguf' }, authed('alice') as never),
            'invalid-argument',
        );
    });

    it('throws failed-precondition when R2 config is missing', async () => {
        delete process.env.R2_ENDPOINT;
        delete process.env.R2_BUCKET;
        useFakePresigner();
        setEntitlementFirestoreGetter(async (_uid: string, docId: string) =>
            docId === 'stripe' ? { active: true } : undefined,
        );
        await expectCode(
            () => wrappedMint({ filePath: 'm.gguf' }, authed('alice') as never),
            'failed-precondition',
        );
    });
});
