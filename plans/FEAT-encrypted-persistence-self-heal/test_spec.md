# Test Spec: Encrypted Persistence Self-Heal

## Test Framework & Run Command
Framework: JUnit 5 with Fakes preferred, MockK only where SDK boundaries make fakes impractical, Turbine for Flow assertions, and Robolectric for Android persistence APIs. Run: `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:data:testDebugUnitTest --no-daemon --stacktrace`.

## Scenario Index
S1 — Catalog contains every audited sensitive field.
S2 — Plaintext string repairs to encrypted envelope.
S3 — Chat entity encrypts only `name`.
S4 — Message entity encrypts all content/media prompt fields.
S5 — Message file and vision analysis fields encrypt.
S6 — Web search, transcript, and document fields encrypt.
S7 — Provider, model config, credentials, and studio fields encrypt.
S8 — Chat creation persists encrypted chat name.
S9 — Message append repairs legacy plaintext content.
S10 — Full message persistence encrypts generated media, sources, documents, and transcript segments.
S11 — Chat read decrypts values and leaves Room encrypted.
S12 — Message save encrypts message and file attachment payloads.
S13 — Vision analysis persists encrypted image URI, prompt, and analysis.
S14 — Chat summary persists encrypted summary content.
S15 — Memories persist encrypted content and return plaintext domain content.
S16 — Embedding regeneration consumes plaintext only after encrypted source repair.
S17 — API credentials and API model config sensitive fields persist encrypted.
S18 — Local model config sensitive fields persist encrypted.
S19 — Default/local model metadata remains plaintext non-sensitive metadata.
S20 — TTS and media provider sensitive fields persist encrypted.
S21 — Studio album and studio media sensitive fields persist encrypted.
S22 — Studio reads decrypt user-facing values and preserve encrypted rows.
S23 — Artifact document create, merge, and search use encrypted Room rows.
S24 — Settings custom prompt text is encrypted in DataStore.
S25 — Pipeline state JSON is encrypted in DataStore.
S26 — API key sync stores only encrypted local values and encrypted remote blobs.
S27 — Firestore outbound repair encrypts plaintext map fields.
S28 — Firestore inbound repair encrypts plaintext remote fields and returns encrypted Room values.
S29 — Transcript codec never emits plaintext `text`.
S30 — Sync pending changes repairs local plaintext before cloud upload.
S31 — Remote plaintext document is repaired to Firestore and Room when unlocked.
S32 — Remote document artifact plaintext repair replaces previous rejection behavior.
S33 — Message file cloud worker encrypts staged and uploaded remote URLs.
S34 — Studio media cloud worker encrypts staged and uploaded remote URLs.
S35 — Vault, biometric, and download-session stores are explicitly audited.
E1 — Locked vault defers plaintext repair with typed result.
E2 — Invalid encrypted envelope is rejected with typed result.
E3 — Sanitized warning excludes plaintext and ciphertext payloads.
E4 — API key pending sync sets contain aliases only.
E5 — Plaintext Firestore API key field is rejected.
E6 — Firestore sync returns `IllegalStateException("Vault locked")` while locked.
M1 — Mutation defense covers all cataloged fields, not just document artifacts.

## Happy Path Scenarios

### S1: Catalog Contains Every Audited Sensitive Field
- **Given:** the `StaticSensitiveFieldCatalog` is created.
- **When:** `allRefs()` and `auditedOwners()` are queried.
- **Then:** the refs include exactly `ChatEntity.name`, `MessageEntity.content`, `MessageEntity.imageUrisJson`, `MessageEntity.thinkingRaw`, `MessageEntity.artifactsJson`, `MessageEntity.generatedMediaUrisJson`, `MessageEntity.generatedMediaAssetIdsJson`, `MessageEntity.generatedMediaPromptsJson`, `ChatSummaryEntity.content`, `MemoriesEntity.content`, `MessageVisionAnalysisEntity.imageUri`, `MessageVisionAnalysisEntity.promptText`, `MessageVisionAnalysisEntity.analysisText`, `MessageFileEntity.name`, `MessageFileEntity.content`, `MessageFileEntity.remoteUrl`, `MessageFileEntity.stagingRemoteUrl`, `WebSearchSourceEntity.title`, `WebSearchSourceEntity.url`, `WebSearchSourceEntity.content`, `MessageTranscriptSegmentEntity.text`, `ApiModelConfigurationEntity.displayName`, `ApiModelConfigurationEntity.customHeaders`, `ApiModelConfigurationEntity.systemPrompt`, `LocalModelConfigurationEntity.displayName`, `LocalModelConfigurationEntity.systemPrompt`, `TtsProviderEntity.displayName`, `TtsProviderEntity.voiceName`, `TtsProviderEntity.modelName`, `TtsProviderEntity.baseUrl`, `MediaProviderEntity.displayName`, `MediaProviderEntity.modelName`, `MediaProviderEntity.baseUrl`, `ApiCredentialsEntity.displayName`, `ApiCredentialsEntity.baseUrl`, `ApiCredentialsEntity.encryptedApiKey`, `StudioAlbumEntity.name`, `StudioMediaEntity.prompt`, `StudioMediaEntity.mediaUri`, `StudioMediaEntity.remoteUrl`, `StudioMediaEntity.remoteThumbnailUrl`, `StudioMediaEntity.stagingRemoteUrl`, `DocumentEntity.json`, `DocumentEntity.title`, `DocumentEntity.author`, `SettingsRepositoryImpl.settings_custom_prompt_text`, `PipelineStateRepositoryImpl.pipeline_state_$chatId`, and `ApiKeyManager.api_key_value`.
- **And:** `auditedOwners()` includes zero-sensitive persisted owners `DefaultModelEntity`, `LocalModelEntity`, `EmbeddingEntity`, `ApiKeyManager.pending_alias_sets`, `LocalVaultMetadataStore`, `AndroidBiometricManager`, `DownloadSessionManager`, and Firestore vault metadata.

### S2: Plaintext String Repairs To Encrypted Envelope
- **Given:** an unlocked fake encryption service where `encryptString("secret text")` returns `"PCENC:v1:aes-gcm:c2VjcmV0"` and the field ref is `MessageEntity.content`.
- **When:** `repairString(ref, "msg-1", "secret text")` is called.
- **Then:** `SensitiveRepairResult.value` equals `"PCENC:v1:aes-gcm:c2VjcmV0"` and `SensitiveRepairResult.action` equals `REPAIRED`.

### S3: Chat Entity Encrypts Only Name
- **Given:** `ChatEntity(id=ChatId("chat-1"), name="Trip plan", pinned=true, pendingSync=1)`.
- **When:** `SensitiveEntityFieldEncryptors.encrypt(entity, healer)` is called.
- **Then:** the returned `name` equals `"PCENC:v1:aes-gcm:VHJpcCBwbGFu"` and `pinned` equals `true`.

### S4: Message Entity Encrypts All Content And Media Prompt Fields
- **Given:** `MessageEntity(id=MessageId("msg-1"), content="hello", imageUrisJson="[\"file://a.png\"]", thinkingRaw="private thought", artifactsJson="{\"body\":\"secret\"}", generatedMediaUrisJson="[\"file://media.png\"]", generatedMediaAssetIdsJson="[\"asset-1\"]", generatedMediaTypesJson="[\"IMAGE\"]", generatedMediaPromptsJson="[\"draw home\"]")`.
- **When:** `SensitiveEntityFieldEncryptors.encrypt(entity, healer)` is called.
- **Then:** `content`, `imageUrisJson`, `thinkingRaw`, `artifactsJson`, `generatedMediaUrisJson`, `generatedMediaAssetIdsJson`, and `generatedMediaPromptsJson` each start with `"PCENC:v1:aes-gcm:"`, and `generatedMediaTypesJson` equals `"[\"IMAGE\"]"`.

### S5: Message File And Vision Analysis Fields Encrypt
- **Given:** a `MessageFileEntity(name="notes.txt", content="private file", remoteUrl="https://storage/file", stagingRemoteUrl="https://storage/staging")` and a `MessageVisionAnalysisEntity(imageUri="file://face.png", promptText="describe image", analysisText="person at desk")`.
- **When:** both entities are encrypted with `SensitiveEntityFieldEncryptors.encrypt`.
- **Then:** all seven sensitive string fields start with `"PCENC:v1:aes-gcm:"`.

### S6: Web Search, Transcript, And Document Fields Encrypt
- **Given:** `WebSearchSourceEntity(title="Result", url="https://example.test", content="page text")`, `MessageTranscriptSegmentEntity(text="reasoning text")`, and `DocumentEntity(json="{\"doc\":\"secret\"}", title="Report", author="Sean")`.
- **When:** each entity is encrypted with `SensitiveEntityFieldEncryptors.encrypt`.
- **Then:** `title`, `url`, `content`, `text`, `json`, and `author` start with `"PCENC:v1:aes-gcm:"`.

### S7: Provider, Model Config, Credentials, And Studio Fields Encrypt
- **Given:** entities for `ApiModelConfigurationEntity`, `LocalModelConfigurationEntity`, `TtsProviderEntity`, `MediaProviderEntity`, `ApiCredentialsEntity`, `StudioAlbumEntity`, and `StudioMediaEntity` with literal sensitive values `"Display"`, `"{}"`, `"System prompt"`, `"Voice"`, `"https://base"`, `"Album"`, `"Prompt"`, and `"file://media"`.
- **When:** each entity is encrypted with `SensitiveEntityFieldEncryptors.encrypt`.
- **Then:** every cataloged sensitive string field on those entities starts with `"PCENC:v1:aes-gcm:"`, and `credentialAlias` equals its original plaintext alias.

### S8: Chat Creation Persists Encrypted Chat Name
- **Given:** `ChatRepositoryImpl` with unlocked encryption and a domain `Chat(id=ChatId("chat-1"), name="Health plan")`.
- **When:** `createChat(chat)` is called.
- **Then:** the Room `chat.name` value equals `"PCENC:v1:aes-gcm:SGVhbHRoIHBsYW4"` and the returned `ChatId.value` equals `"chat-1"`.

### S9: Message Append Repairs Legacy Plaintext Content
- **Given:** Room contains `MessageEntity(id=MessageId("msg-1"), content="hello", pendingSync=0)`.
- **When:** `appendMessageContent(MessageId("msg-1"), " world")` is called.
- **Then:** Room contains `content` equal to `"PCENC:v1:aes-gcm:aGVsbG8gd29ybGQ"` and `pendingSync` equals `1`.

### S10: Full Message Persistence Encrypts Generated Media, Sources, Documents, And Transcript Segments
- **Given:** `persistAllMessageData` receives `content="answer"`, one web source with `url="https://example.test"`, one artifact with `content="{\"body\":\"draft\"}"`, `generatedMediaPrompts=["draw a house"]`, and one transcript segment with `text="visible answer"`.
- **When:** `persistAllMessageData(...)` is called.
- **Then:** the message row `content`, web source `url`, document `json`, message `generated_media_prompts_json`, and transcript segment `text` all start with `"PCENC:v1:aes-gcm:"`.

### S11: Chat Read Decrypts Values And Leaves Room Encrypted
- **Given:** Room contains encrypted values for chat `name="PCENC:v1:aes-gcm:Q2hhdA"`, message `content="PCENC:v1:aes-gcm:SGVsbG8"`, and document `title="PCENC:v1:aes-gcm:UmVwb3J0"`.
- **When:** `getMessagesForChat(ChatId("chat-1"))` emits its first list.
- **Then:** the emitted message content text equals `"Hello"` and the Room message `content` still starts with `"PCENC:v1:aes-gcm:"`.

### S12: Message Save Encrypts Message And File Attachment Payloads
- **Given:** a domain `Message` with text `"message secret"` and a file attachment named `"secret.txt"` containing `"file secret"`.
- **When:** `MessageRepositoryImpl.saveMessage(message)` is called.
- **Then:** Room message `content`, message file `name`, and message file `content` each start with `"PCENC:v1:aes-gcm:"`.

### S13: Vision Analysis Persists Encrypted Image URI, Prompt, And Analysis
- **Given:** `imageUri="file://image.png"`, `promptText="what is shown"`, and `analysisText="a private room"`.
- **When:** `saveVisionAnalysis(MessageId("msg-1"), imageUri, promptText, analysisText, ModelType.VISION)` is called.
- **Then:** Room `image_uri`, `prompt_text`, and `analysis_text` all start with `"PCENC:v1:aes-gcm:"`.

### S14: Chat Summary Persists Encrypted Summary Content
- **Given:** `ChatSummary(chatId=ChatId("chat-1"), content="private summary", lastSummarizedMessageId=MessageId("msg-1"))`.
- **When:** `saveChatSummary(summary)` is called.
- **Then:** Room `chat_summary.content` starts with `"PCENC:v1:aes-gcm:"`.

### S15: Memories Persist Encrypted Content And Return Plaintext Domain Content
- **Given:** `content="likes quiet mornings"` and category `PREFERENCES`.
- **When:** `insertMemory(PREFERENCES, "likes quiet mornings")` is called and then `getMemoryById(id)` is called.
- **Then:** Room `memories.content` starts with `"PCENC:v1:aes-gcm:"` and the domain memory `content` equals `"likes quiet mornings"`.

### S16: Embedding Regeneration Consumes Plaintext Only After Encrypted Source Repair
- **Given:** Room contains `DocumentEntity(id="doc-1", json="{\"blocks\":[{\"text\":\"secret block\"}]}", title="Plain")`.
- **When:** `EmbeddingRegenerationWorker` regenerates document embeddings for `"doc-1"`.
- **Then:** the fake embedding engine receives input `"secret block"` and Room `documents.json` starts with `"PCENC:v1:aes-gcm:"`.

### S17: API Credentials And API Model Config Sensitive Fields Persist Encrypted
- **Given:** API credentials with `displayName="OpenAI Main"`, `baseUrl="https://api.openai.com"`, and API key `"sk-live"`, plus API config `displayName="Fast"`, `customHeaders="{\"X-Team\":\"A\"}"`, and `systemPrompt="be concise"`.
- **When:** `saveCredentials(...)` and `saveConfiguration(...)` are called.
- **Then:** Room `api_credentials.display_name`, `api_credentials.base_url`, `api_model_configurations.display_name`, `custom_headers`, and `system_prompt` start with `"PCENC:v1:aes-gcm:"`, and Room `api_credentials.encrypted_api_key` is a non-empty `ByteArray`.

### S18: Local Model Config Sensitive Fields Persist Encrypted
- **Given:** a local model configuration with `displayName="Private preset"` and `systemPrompt="speak gently"`.
- **When:** `saveConfiguration(config)` is called.
- **Then:** Room `local_model_configurations.display_name` and `system_prompt` both start with `"PCENC:v1:aes-gcm:"`.

### S19: Default And Local Model Metadata Remain Plaintext Non-Sensitive Metadata
- **Given:** a default model assignment with `modelType=LLM` and local config id `"local-config-1"`, plus local model metadata with `sha256="abc123"` and `remoteFilename="model.gguf"`.
- **When:** the repository persists both records.
- **Then:** Room `default_models.model_type` equals `"LLM"`, `default_models.local_config_id` equals `"local-config-1"`, `local_models.sha256` equals `"abc123"`, and `local_models.remote_filename` equals `"model.gguf"`.

### S20: TTS And Media Provider Sensitive Fields Persist Encrypted
- **Given:** a TTS provider with `displayName="Narrator"`, `voiceName="alloy"`, `modelName="tts-1"`, `baseUrl="https://tts.example"`, and a media provider with `displayName="Image Gen"`, `modelName="image-1"`, `baseUrl="https://image.example"`.
- **When:** both provider repositories save the providers.
- **Then:** all provider `displayName`, `voiceName`, `modelName`, and `baseUrl` columns start with `"PCENC:v1:aes-gcm:"`.

### S21: Studio Album And Studio Media Sensitive Fields Persist Encrypted
- **Given:** `createAlbum("Family")` and `saveMedia(localUri="file://pic.png", prompt="draw family", mediaType="IMAGE")`.
- **When:** both calls complete.
- **Then:** Room `studio_albums.name`, `studio_gallery.prompt`, and `studio_gallery.mediaUri` start with `"PCENC:v1:aes-gcm:"`.

### S22: Studio Reads Decrypt User-Facing Values And Preserve Encrypted Rows
- **Given:** Room contains `StudioMediaEntity(prompt="PCENC:v1:aes-gcm:ZHJhdyBmYW1pbHk", mediaUri="PCENC:v1:aes-gcm:ZmlsZTovL3BpYy5wbmc")`.
- **When:** `getAllMedia()` emits its first list.
- **Then:** the emitted asset `prompt` equals `"draw family"`, the emitted asset local URI equals `"file://pic.png"`, and Room `studio_gallery.prompt` still starts with `"PCENC:v1:aes-gcm:"`.

### S23: Artifact Document Create, Merge, And Search Use Encrypted Room Rows
- **Given:** artifact parameters with `title="Budget"`, `author="Sean"`, and raw JSON `{"blocks":[{"text":"salary"}]}`.
- **When:** `ArtifactToolExecutor` creates the document, merges a patch containing `"bonus"`, and `SearchArtifactToolExecutor` searches for `"bonus"`.
- **Then:** Room `documents.json`, `documents.title`, and `documents.author` start with `"PCENC:v1:aes-gcm:"`, and the search result title equals `"Budget"`.

### S24: Settings Custom Prompt Text Is Encrypted In DataStore
- **Given:** settings DataStore is empty.
- **When:** `updateCustomPromptText("answer like me")` is called.
- **Then:** the raw DataStore value for key `"settings_custom_prompt_text"` starts with `"PCENC:v1:aes-gcm:"` and `settingsFlow` emits `customPromptText` equal to `"answer like me"`.

### S25: Pipeline State JSON Is Encrypted In DataStore
- **Given:** a pipeline state whose serialized JSON equals `{"chatId":"chat-1","stage":"MERGE"}`.
- **When:** `persistPipelineState("chat-1", state)` is called.
- **Then:** the raw DataStore value for key `"pipeline_state_chat-1"` starts with `"PCENC:v1:aes-gcm:"` and `getPipelineState("chat-1")` returns a non-null state with stage `"MERGE"`.

### S26: API Key Sync Stores Only Encrypted Local Values And Encrypted Remote Blobs
- **Given:** `ApiKeyManager.save("tavily_web_search", "sk-live")` is called.
- **When:** `ApiKeySyncAdapter.toFirestore("tavily_web_search" to "sk-live")` is called.
- **Then:** the Firestore map contains key `"encrypted_key"` with a non-empty Blob, does not contain key `"api_key"`, and does not contain value `"sk-live"`.

### S27: Firestore Outbound Repair Encrypts Plaintext Map Fields
- **Given:** collection `"messages"`, entity id `"msg-1"`, and outbound map `{"content":"hello","generated_media_types_json":"[\"IMAGE\"]"}`.
- **When:** `FirestoreSensitiveFieldRepairer.repairOutbound("messages", "msg-1", map)` is called.
- **Then:** returned `"content"` starts with `"PCENC:v1:aes-gcm:"` and returned `"generated_media_types_json"` equals `"[\"IMAGE\"]"`.

### S28: Firestore Inbound Repair Encrypts Plaintext Remote Fields And Returns Encrypted Room Values
- **Given:** collection `"web_search_sources"`, entity id `"src-1"`, and inbound map `{"title":"Result","url":"https://example.test","content":"body"}`.
- **When:** `FirestoreSensitiveFieldRepairer.repairInbound("web_search_sources", "src-1", map)` is called.
- **Then:** returned `"title"`, `"url"`, and `"content"` all start with `"PCENC:v1:aes-gcm:"`.

### S29: Transcript Codec Never Emits Plaintext Text
- **Given:** `MessageTranscriptSegmentEntity(text="PCENC:v1:aes-gcm:dmlzaWJsZQ")`.
- **When:** `messageTranscriptSegmentToFirestoreMap(local, "chat-1", local.text)` is called.
- **Then:** the returned `"text"` value equals `"PCENC:v1:aes-gcm:dmlzaWJsZQ"`.

### S30: Sync Pending Changes Repairs Local Plaintext Before Cloud Upload
- **Given:** Room contains claimed `MessageEntity(id=MessageId("msg-1"), content="legacy plaintext", pendingSync=2)` and the vault is unlocked.
- **When:** `syncPendingChanges()` is called.
- **Then:** the Firestore batch path ends with `"/users/user-1/messages/msg-1"`, the uploaded `"content"` starts with `"PCENC:v1:aes-gcm:"`, and Room `message.content` starts with `"PCENC:v1:aes-gcm:"`.

### S31: Remote Plaintext Document Is Repaired To Firestore And Room When Unlocked
- **Given:** a Firestore document change in collection `"chats"` with id `"chat-1"` and field `"name"="Plain Chat"`.
- **When:** `processDocumentChange` handles the added document while the vault is unlocked.
- **Then:** Room `chat.name` starts with `"PCENC:v1:aes-gcm:"` and Firestore receives a merge update where `"name"` starts with `"PCENC:v1:aes-gcm:"`.

### S32: Remote Document Artifact Plaintext Repair Replaces Previous Rejection Behavior
- **Given:** a Firestore document in collection `"documents"` with id `"doc-1"`, `json="{\"blocks\":[]}"`, `title="Plain Title"`, and `message_id="msg-1"`.
- **When:** the document is imported while unlocked.
- **Then:** Room contains `DocumentEntity(id="doc-1")`, Room `json` starts with `"PCENC:v1:aes-gcm:"`, and the import result is not `null`.

### S33: Message File Cloud Worker Encrypts Staged And Uploaded Remote URLs
- **Given:** a pending message file upload returns remote URL `"https://storage.example/file.txt"`.
- **When:** `MessageFileCloudSyncWorker.doWork()` completes successfully.
- **Then:** Room `message_file.remote_url` starts with `"PCENC:v1:aes-gcm:"`, `staging_remote_url` is `null`, and `sync_status` equals `"UPLOADED"`.

### S34: Studio Media Cloud Worker Encrypts Staged And Uploaded Remote URLs
- **Given:** a pending studio media upload returns remote URL `"https://storage.example/media.png"`.
- **When:** `MediaCloudSyncWorker.doWork()` completes successfully.
- **Then:** Room `studio_gallery.remoteUrl` starts with `"PCENC:v1:aes-gcm:"`, `stagingRemoteUrl` is `null`, and `syncStatus` equals `"UPLOADED"`.

### S35: Vault, Biometric, And Download-Session Stores Are Explicitly Audited
- **Given:** `LocalVaultMetadataStore` has `vault_local_salt`, `vault_local_verification`, and `vault_local_wrapped_dek`; `AndroidBiometricManager` has `${key}_data` and `${key}_iv`; and `DownloadSessionManager` has `download_session_id`.
- **When:** the persistence audit catalog is queried and each owner is exercised by its existing store/read path.
- **Then:** vault verification and wrapped-DEK values are preserved as existing bootstrap crypto payloads, biometric data remains Android Keystore ciphertext/IV material, download session IDs remain plaintext non-sensitive UUID metadata, and none of these values are logged or wrapped with `LocalDataEncryptionService`.

## Error & Edge Case Scenarios

### E1: Locked Vault Defers Plaintext Repair With Typed Result
- **Given:** a locked encryption service and field ref `DocumentEntity.json`.
- **When:** `repairString(ref, "doc-1", "{\"plain\":true}")` is called.
- **Then:** `SensitiveRepairResult.value` equals `"{\"plain\":true}"` and `SensitiveRepairResult.action` equals `DEFERRED_LOCKED`.

### E2: Invalid Encrypted Envelope Is Rejected With Typed Result
- **Given:** an unlocked encryption service where decrypting `"PCENC:v1:aes-gcm:not-base64"` fails.
- **When:** `decryptForRead(ref, "msg-1", "PCENC:v1:aes-gcm:not-base64")` is called.
- **Then:** `SensitivePersistenceLockedException` is not thrown and the returned value is `null`.

### E3: Sanitized Warning Excludes Plaintext And Ciphertext Payloads
- **Given:** a repair of `ApiCredentialsEntity.baseUrl` for entity `"creds-1"` from plaintext `"https://secret.example"`.
- **When:** the warning log is captured.
- **Then:** the warning contains `"ApiCredentialsEntity"`, `"baseUrl"`, `"creds-1"`, and `"REPAIRED"`, and does not contain `"https://secret.example"`, `"PCENC:"`, `"ciphertext"`, or `"sk-live"`.

### E4: API Key Pending Sync Sets Contain Aliases Only
- **Given:** `ApiKeyManager.save("x_search", "sk-live")` is called.
- **When:** the raw DataStore pending sync set is read.
- **Then:** the set equals `["x_search"]` and does not contain `"sk-live"`.

### E5: Plaintext Firestore API Key Field Is Rejected
- **Given:** collection `"api_keys"` document id `"x_search"` contains `{"api_key":"sk-live"}` and no `"encrypted_key"` blob.
- **When:** `FirestoreSensitiveFieldRepairer.repairInbound("api_keys", "x_search", map)` is called.
- **Then:** `SensitiveRepairResult.action` for the API key value equals `REJECTED_UNSUPPORTED_FIELD` and the returned map does not contain `"api_key"`.

### E6: Firestore Sync Returns Vault Locked While Locked
- **Given:** `FirestoreDataSource` has user id `"user-1"` and `cryptoManager.hasCachedKey()` returns `false`.
- **When:** `syncPendingChanges()` is called.
- **Then:** the result is failure with `IllegalStateException` message `"Vault locked"`.

## Mutation Defense

### Lazy Implementation Risk
A partial implementation could repair only `DocumentEntity` fields or only outbound Firestore maps while leaving other Room, DataStore, provider, credential, worker, inbound Firestore, and bootstrap/shared-preference stores unaudited or plaintext.

### M1: Defense Scenario
- **Given:** one plaintext value exists in each cataloged owner: `MessageEntity.content="plain-message"`, `MemoriesEntity.content="plain-memory"`, `ApiModelConfigurationEntity.systemPrompt="plain-system"`, `TtsProviderEntity.voiceName="plain-voice"`, `StudioMediaEntity.prompt="plain-prompt"`, `SettingsRepositoryImpl.settings_custom_prompt_text="plain-settings"`, and `DocumentEntity.json="{\"plain\":\"doc\"}"`.
- **When:** the owning repository or repairer for each value runs its normal read/write/sync repair path while the vault is unlocked.
- **Then:** all seven stored source values start with `"PCENC:v1:aes-gcm:"`, and none of the seven stored source values equals `"plain-message"`, `"plain-memory"`, `"plain-system"`, `"plain-voice"`, `"plain-prompt"`, `"plain-settings"`, or `"{\"plain\":\"doc\"}"`.
