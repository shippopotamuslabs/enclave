package com.browntowndev.enclave.testing

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeBiometricPort
import com.browntowndev.enclave.core.testing.FakeCleanupRepository
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeQnnPackInstaller
import com.browntowndev.enclave.core.testing.FakeSnapdragonTier
import com.browntowndev.enclave.core.testing.FakeTrustedDomainRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelRepository
import com.browntowndev.enclave.core.testing.download.FakeModelDownloadOrchestrator
import com.browntowndev.enclave.core.testing.FakeModelFileScanner
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome
import com.browntowndev.enclave.domain.port.security.BiometricCipherPort
import com.browntowndev.enclave.domain.usecase.download.CheckModelEligibilityUseCase
import com.browntowndev.enclave.domain.usecase.download.CheckModelsUseCase
import com.browntowndev.enclave.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.enclave.domain.usecase.download.ResolveSd15QnnPackCatalogAssetUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncResult
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultUseCase
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Recording fakes for MainViewModel's concrete use-case dependencies (ENC-28).
 * Each subclasses the real use case (opened for testability) with dummy
 * collaborators and overrides invoke with test-controlled behavior, replacing
 * the MockK stubs these tests used before.
 */

fun emptyModelsResult(): DownloadModelsResult =
    DownloadModelsResult(
        allModels = emptyMap(),
        modelsToDownload = emptyList(),
        scanResult =
            ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true,
            ),
    )

class FakeInitializeModelsUseCase(
    private val events: MutableList<String>? = null,
) : InitializeModelsUseCase(
        FakeModelConfigFetcher(),
        FakeActiveModelProvider(),
        SyncLocalModelRegistryUseCase(
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeTransactionProvider(),
            FakeLoggingPort(),
        ),
        FakeLocalModelRepository(),
        FakeModelDownloadOrchestrator(),
        CheckModelsUseCase(
            FakeModelFileScanner(),
            CheckModelEligibilityUseCase(FakeLoggingPort()),
            FakeLoggingPort(),
        ),
        SyncUtilityModelRegistryUseCase(FakeUtilityModelRepository(), FakeLoggingPort()),
        FakeTrustedDomainRepository(),
        FakeGpuCapability(),
        FakeLocalImageGenerationCapability(),
        ResolveSd15QnnPackCatalogAssetUseCase(FakeSnapdragonTier()),
        FakeQnnPackInstaller(),
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
        FakeLoggingPort(),
    ) {
    var result: DownloadModelsResult = emptyModelsResult()
    var delayMs: Long = 0
    var callCount = 0

    override suspend fun invoke(): DownloadModelsResult {
        callCount++
        events?.add("initModels")
        if (delayMs > 0) delay(delayMs)
        return result
    }
}

class FakeCreateVaultUseCase : CreateVaultUseCase(FakeCryptoManagerPort()) {
    val calls = mutableListOf<Pair<String, ByteArray>>()
    var result: Result<ByteArray> = Result.success(byteArrayOf(1, 2, 3))

    override suspend fun invoke(password: String, salt: ByteArray): Result<ByteArray> {
        calls += password to salt
        return result
    }
}

class FakeUnlockVaultUseCase :
    UnlockVaultUseCase(FakeCryptoManagerPort(), FakeApiModelRepository(), FakeApiKeyProvider()) {
    var callCount = 0
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        password: String,
        salt: ByteArray,
        wrappedDek: ByteArray,
    ): Result<Unit> {
        callCount++
        return result
    }
}

class FakeDetectVaultReconciliationStateUseCase(
    private val events: MutableList<String>? = null,
) : DetectVaultReconciliationStateUseCase(
        FakeCryptoManagerPort(),
        FakeVaultMetadataPort(),
        FakeCloudSyncPort(),
    ) {
    var result: Result<VaultReconciliationOutcome> =
        Result.success(VaultReconciliationOutcome.NoCloudVault)

    /** Runs inside invoke before the result is returned (for mid-flight state assertions). */
    var invokeHook: ((String) -> Unit)? = null
    val calls = mutableListOf<String>()

    override suspend fun invoke(password: String): Result<VaultReconciliationOutcome> {
        calls += password
        invokeHook?.invoke(password)
        return result
    }
}

class FakeAdoptCloudVaultUseCase(
    private val events: MutableList<String>? = null,
) : AdoptCloudVaultUseCase(
        FakeCryptoManagerPort(),
        FakeVaultMetadataPort(),
        FakeCleanupRepository(),
        FakeBiometricPort(),
    ) {
    val calls = mutableListOf<VaultReconciliationOutcome.MismatchRequiresClobber>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        payload: VaultReconciliationOutcome.MismatchRequiresClobber,
    ): Result<Unit> {
        calls += payload
        events?.add("adopt")
        return result
    }
}

class FakeBootstrapCloudVaultFromLocalUseCase(
    private val events: MutableList<String>? = null,
) : BootstrapCloudVaultFromLocalUseCase(FakeVaultMetadataPort(), FakeCloudSyncPort()) {
    var callCount = 0
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(): Result<Unit> {
        callCount++
        events?.add("bootstrap")
        return result
    }
}

class FakeStartCloudSyncListeningUseCase(
    private val events: MutableList<String>? = null,
) : StartCloudSyncListeningUseCase(
        FakeCloudSyncPort(),
        com.browntowndev.enclave.core.testing.FakeEntitlementPort(),
    ) {
    val calls = mutableListOf<Boolean>()
    var result: StartCloudSyncResult = StartCloudSyncResult.Started

    override suspend fun invoke(
        uploadLocalFirst: Boolean,
        onProgress: (suspend (com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress) -> Unit)?,
    ): StartCloudSyncResult {
        calls += uploadLocalFirst
        events?.add("startSync")
        return result
    }
}

/** Recording [BiometricCipherPort] double; ciphers are opaque tokens in these tests. */
class FakeBiometricCipherPort : BiometricCipherPort {
    var encryptCipher: Cipher? = null
    var decryptCipher: Cipher? = null
    var storeResult: Result<Unit> = Result.success(Unit)
    var decryptResult: ByteArray? = null
    val storeBytesCalls = mutableListOf<Triple<String, ByteArray, Cipher>>()
    val decryptCalls = mutableListOf<Pair<String, Cipher>>()

    override fun getEncryptCipher(key: String): Cipher? = encryptCipher

    override fun getDecryptCipher(key: String): Cipher? = decryptCipher

    override suspend fun storeSecureBytesWithCipher(
        key: String,
        value: ByteArray,
        cipher: Cipher,
    ): Result<Unit> {
        storeBytesCalls += Triple(key, value, cipher)
        return storeResult
    }

    override fun decryptBytesWithCipher(key: String, cipher: Cipher): ByteArray? {
        decryptCalls += key to cipher
        return decryptResult
    }
}

/** Recording [ViewModelErrorHandler] double returning a real no-op handler. */
class FakeViewModelErrorHandler : ViewModelErrorHandler {
    override val errorEvents: SharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 16)
    val handledErrors = mutableListOf<String>()

    override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
        handledErrors += "$tag:$message"
    }

    override fun coroutineExceptionHandler(
        tag: String,
        message: String,
        userMessage: String,
    ): CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }
}
