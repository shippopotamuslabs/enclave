# Test Spec: Vault-backed PCENC string encryption hardening

## Test Framework & Run Command
Framework: JUnit 5 with Fakes preferred, MockK only where SDK boundaries make fakes impractical, Turbine for Flow assertions where Flows are involved, and Robolectric only when Android framework APIs are required. Primary ticket slice: `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:data:testDebugUnitTest --no-daemon --stacktrace`. Full gate per `AGENTS.md`: `./gradlew test`.

## Scenario Index
S1 — `parseEnvelope` treats non-envelope plaintext as not enveloped.  
S2 — `parseEnvelope` accepts minimal valid Base64 payload length.  
S3 — `isEncrypted` is false for malformed-but-prefix-looking strings.  
S4 — `decryptForRead` still returns plaintext `"hello"` when stored value is plaintext `"hello"` and vault is unlocked with fake encryptor.  
S5 — Every updated repository test expectation uses the literal prefix `"PCENC:v1:aes-gcm:"` rather than `"ENC:v1:"`.  
S6 — KDoc comment in `CryptoAtRestEncryptionService` states AAD binding without envelope version bump.  
E1 — `parseEnvelope` rejects wrong prefix with `Malformed(BAD_PREFIX)`.  
E2 — `parseEnvelope` rejects invalid Base64 with `Malformed(INVALID_BASE64)`.  
E3 — `parseEnvelope` rejects too-short decoded payload with `Malformed(PAYLOAD_TOO_SHORT)`.  
E4 — `parseEnvelope` rejects too-large decoded payload with `Malformed(PAYLOAD_TOO_LARGE)`.  
E5 — `encryptString` throws `EncryptionLockedException` with message `"Encryption session locked"` when `CryptoManagerPort.hasCachedKey()` is false.  
E6 — `decryptString` throws `EncryptionLockedException` with message `"Encryption session locked"` when `CryptoManagerPort.hasCachedKey()` is false and `stored` equals `"hello-plain"`.  
E7 — `decryptForRead` maps `MalformedEnvelopeException` to `SensitiveRepairAction.REJECTED_MALFORMED_ENVELOPE`.  
E8 — `decryptForRead` maps `DecryptFailedException` to `SensitiveRepairAction.REJECTED_INVALID_CIPHERTEXT`.  
T1 — `CryptoManagerPort.encrypt` round-trip with non-null AAD succeeds.  
T2 — `CryptoManagerPort.decrypt` with wrong AAD fails the `Result` (isFailure equals `true`).  
T3 — `AadContext("ChatEntity","name","chat-1").toBytes()` decodes as UTF-8 to exactly `ChatEntity|name|chat-1`.  
T4 — `CryptoManagerPort.decrypt` with mismatched `entityId` AAD yields `Result.isFailure` equal to `true`.  
T5 — `SettingsRepositoryImpl` encrypt path passes AAD bytes whose UTF-8 string is exactly `SettingsRepositoryImpl|settings_custom_prompt_text|singleton`.  
T6 — `FakeLocalDataEncryptionService.lastEncryptAad` UTF-8 decodes to `DocumentEntity|json|doc-7` when a document JSON field is encrypted under that binding in `ArtifactToolExecutor` tests or dedicated harness test.  
T7 — `FirestoreSensitiveFieldRepairer` produces different `lastEncryptAad` byte arrays for `documents/doc-a/json` versus `documents/doc-b/json` when both fields contain plaintext `"x"` in the same test double.  
T8 — `SensitivePersistenceSelfHealer.decryptForRead` invokes `LocalDataEncryptionService.decryptString` with `aad` bytes whose UTF-8 string equals `MessageEntity|content|msg-99` when `entityId` argument is `"msg-99"`.  
M1 — Distinct ciphertexts for same plaintext when only `entityId` in AAD changes.

## Happy Path Scenarios

### S1: Parse Envelope Classifies Plaintext As Not Enveloped
- **Given:** `stored` equals `"hello-plain"`.
- **When:** `parseEnvelope(stored)` is evaluated.
- **Then:** the result equals `EnvelopeParse.NotEnveloped`.

### S2: Parse Envelope Accepts Minimal Valid Layout
- **Given:** `raw28` is a byte array of length `28` where indices `0..11` are all `0x00` and indices `12..27` are all `0x01`, and `stored` equals `"PCENC:v1:aes-gcm:"` concatenated with the Java default Base64 encoding of `raw28` with no line breaks.
- **When:** `parseEnvelope(stored)` is evaluated.
- **Then:** the result is `EnvelopeParse.Ok` where the `iv` array has length `12` and the `ciphertext` array has length `16`.

### S3: Is Encrypted False For Prefix Garbage
- **Given:** `stored` equals `"PCENC:v1:aes-gcm:@@@@"`.
- **When:** `isEncrypted(stored)` is evaluated.
- **Then:** the boolean result equals `false`.

### S4: Plaintext Read Still Returns Plaintext
- **Given:** `FakeLocalDataEncryptionService` is unlocked, `ref.owner` equals `"MessageEntity"`, `ref.field` equals `"content"`, `entityId` equals `"m1"`, and `value` equals `"hello"`.
- **When:** `SensitivePersistenceSelfHealer.decryptForRead(ref, "m1", value)` is called.
- **Then:** the returned `String?` equals `"hello"`.

### S5: Tests Use Production Prefix Literal
- **Given:** a repository unit test updated by Tasks 7–9 has an assertion comparing a stored encrypted string.
- **When:** the assertion expected string prefix is inspected as a literal in source.
- **Then:** the literal begins with exactly `"PCENC:v1:aes-gcm:"` and never contains the substring `"ENC:v1:"`.

### S6: Version Bump Called Out In KDoc
- **Given:** `CryptoAtRestEncryptionService.kt` after Task 28.
- **When:** the primary KDoc block on `CryptoAtRestEncryptionService` class is read as plain text.
- **Then:** that text contains the exact sentence `No envelope version bump because the app is unreleased and legacy ciphertext is intentionally unsupported.`

## Error & Edge Case Scenarios

### E1: Wrong Prefix Is Malformed Bad Prefix
- **Given:** `stored` equals `"PCENC:v1:aes-gcmX"`.
- **When:** `parseEnvelope(stored)` is evaluated.
- **Then:** the result equals `MalformedEnvelopeReason.BAD_PREFIX` wrapped as `EnvelopeParse.Malformed`.

### E2: Invalid Base64 After Prefix
- **Given:** `stored` equals `"PCENC:v1:aes-gcm:@@@@"`.
- **When:** `parseEnvelope(stored)` is evaluated.
- **Then:** the result uses `MalformedEnvelopeReason.INVALID_BASE64`.

### E3: Too Short Decoded Payload
- **Given:** `stored` equals `"PCENC:v1:aes-gcm:"` concatenated with the Base64 encoding of twenty-seven `0x05` bytes.
- **When:** `parseEnvelope(stored)` is evaluated.
- **Then:** the result uses `MalformedEnvelopeReason.PAYLOAD_TOO_SHORT`.

### E4: Too Large Decoded Payload
- **Given:** `stored` equals `"PCENC:v1:aes-gcm:"` concatenated with Base64 of a byte array whose length is `16777217`.
- **When:** `parseEnvelope(stored)` is evaluated.
- **Then:** the result uses `MalformedEnvelopeReason.PAYLOAD_TOO_LARGE`.

### E5: Encrypt While Locked Uses EncryptionLockedException
- **Given:** `CryptoAtRestEncryptionService` constructed with a `CryptoManagerPort` stub where `hasCachedKey()` returns `false` and `plaintext` equals `"x"`.
- **When:** `encryptString(plaintext)` is invoked while the project is still on Theme 1 single-argument signatures, **or** `encryptString(plaintext, byteArrayOf(1))` after Theme 2 lands.
- **Then:** the thrown exception class name equals `"EncryptionLockedException"` and `message` equals `"Encryption session locked"`.

### E6: Decrypt While Locked Uses EncryptionLockedException
- **Given:** `CryptoAtRestEncryptionService` constructed with a `CryptoManagerPort` stub where `hasCachedKey()` returns `false` and `stored` equals `"hello-plain"`.
- **When:** `decryptString(stored)` is invoked on Theme 1 single-argument signatures, **or** `decryptString(stored, byteArrayOf())` after Theme 2 lands.
- **Then:** the thrown exception class name equals `"EncryptionLockedException"` and `message` equals `"Encryption session locked"`.

### E7: Malformed Envelope Maps To Distinct Repair Action
- **Given:** a `SensitivePersistenceSelfHealer` wired to a `LocalDataEncryptionService` stub where `isEncrypted` returns `true` and `decryptString` throws `MalformedEnvelopeException(MalformedEnvelopeReason.INVALID_BASE64)`, `ref` targets `MessageEntity.content`, `entityId` equals `"m1"`, and `value` equals `"PCENC:v1:aes-gcm:@@@@"`.
- **When:** `decryptForRead(ref, "m1", value)` is called.
- **Then:** the emitted `SensitiveRepairEvent.action` equals `SensitiveRepairAction.REJECTED_MALFORMED_ENVELOPE`.

### E8: Decrypt Failed Maps To Invalid Ciphertext Action
- **Given:** a `SensitivePersistenceSelfHealer` wired to a `LocalDataEncryptionService` stub where `isEncrypted` returns `true` for `value` and `decryptString` throws `DecryptFailedException("tag mismatch", null)`, `ref.owner` equals `"MessageEntity"`, `ref.field` equals `"content"`, `entityId` equals `"m1"`, and `value` equals `"PCENC:v1:aes-gcm:unused"`.
- **When:** `decryptForRead(ref, "m1", value)` is called.
- **Then:** the emitted `SensitiveRepairEvent.action` equals `SensitiveRepairAction.REJECTED_INVALID_CIPHERTEXT`.

## Theme 2 — AAD Binding Scenarios

### T1: Crypto Port Round Trip Honors AAD
- **Given:** `CryptoManagerImpl` has a cached DEK, `plaintext` equals `"secret"`, `aadA` equals `"row|field|1".toByteArray(Charsets.UTF_8)`.
- **When:** `encrypt(plaintext, aadA)` is chained into `decrypt` on the returned ciphertext bytes with the same `aadA`.
- **Then:** the decrypted `String` equals `"secret"`.

### T2: Wrong AAD Fails Decrypt Result
- **Given:** `CryptoManagerImpl` has a cached DEK, `plaintext` equals `"same"`, `aadA` equals `"row|field|1".toByteArray(Charsets.UTF_8)`, `aadB` equals `"row|field|2".toByteArray(Charsets.UTF_8)`, and `ciphertext` is the success value of `encrypt(plaintext, aadA)`.
- **When:** `decrypt(ciphertext, aadB)` is evaluated.
- **Then:** `isFailure` on that `Result` equals `true`.

### T3: AadContext Canonical String
- **Given:** `owner` equals `"ChatEntity"`, `field` equals `"name"`, `entityId` equals `"chat-1"`.
- **When:** `AadContext(owner, field, entityId).toBytes()` is decoded as UTF-8.
- **Then:** the decoded string equals `"ChatEntity|name|chat-1"`.

### T4: Entity Id Swap Breaks Decrypt
- **Given:** a harness encrypts `MessageEntity.content` for `entityId` `"a"` using production AAD rules, producing ciphertext bytes `C`, and the harness attempts `CryptoManagerPort.decrypt` on `C` with AAD bytes for `entityId` `"b"` for the same field literal.
- **When:** that decrypt `Result` is evaluated.
- **Then:** `isFailure` equals `true`.

### T5: Settings AAD Literal
- **Given:** `SettingsRepositoryImpl` is under test with `FakeLocalDataEncryptionService` recording AAD, vault unlocked, and any non-empty custom prompt string.
- **When:** the code path that persists encrypted custom prompt executes.
- **Then:** `FakeLocalDataEncryptionService.lastEncryptAad` decoded as UTF-8 equals `"SettingsRepositoryImpl|settings_custom_prompt_text|singleton"`.

### T6: Document Json AAD Literal
- **Given:** `FakeLocalDataEncryptionService` recording AAD and a `documentId` of `"doc-7"`.
- **When:** the `ArtifactToolExecutor` (or equivalent focused harness) encrypts JSON field `json` for that document id.
- **Then:** `FakeLocalDataEncryptionService.lastEncryptAad` decoded as UTF-8 equals `"DocumentEntity|json|doc-7"`.

### T7: Firestore Repairer Distinct AAD Per Doc
- **Given:** a test double `FakeLocalDataEncryptionService` recording each `aad` passed to `encryptString`, two Firestore document ids `"doc-a"` and `"doc-b"` for collection path `"documents"`, and field name `"json"`.
- **When:** `FirestoreSensitiveFieldRepairer` repairs plaintext `"x"` outbound for each doc sequentially.
- **Then:** the first recorded AAD UTF-8 string equals `"DocumentEntity|json|doc-a"` and the second equals `"DocumentEntity|json|doc-b"`.

### T8: Self Healer Forwards Entity Id Into AAD
- **Given:** `FakeLocalDataEncryptionService` where `isEncrypted` returns `true` for values starting with `"PCENC:v1:aes-gcm:"`, `decryptString` returns `"body"` and records the `aad` argument, `ref.field` equals `"content"`, `ref.owner` equals `"MessageEntity"`, and stored value is any string starting with `"PCENC:v1:aes-gcm:"` that `isEncrypted` treats as true.
- **When:** `decryptForRead(ref, "msg-99", stored)` is called.
- **Then:** the recorded `aad` UTF-8 string equals `"MessageEntity|content|msg-99"`.

## Mutation Defense

### Lazy Implementation Risk
A lazy implementation passes `null` AAD to `Cipher.updateAAD` for all calls while still round-tripping decrypt within the same call because the JVM default still authenticates the ciphertext for identical keys.

### M1: Defense Scenario
- **Given:** `CryptoManagerImpl` with cached DEK, fixed `plaintext` equals `"payload"`, `aad1` equals `"e|f|1".toByteArray(Charsets.UTF_8)`, `aad2` equals `"e|f|2".toByteArray(Charsets.UTF_8)`.
- **When:** `c1` is computed from `encrypt(plaintext, aad1)` and `c2` from `encrypt(plaintext, aad2)` using successful `Result` values only.
- **Then:** `c1` byte array is not equal to `c2` byte array using `java.util.Arrays.equals` returning `false`.
