# String Identity Refactor Implementation Plan

## Objective
Migrate all remaining `Long` primary keys on `Room` entities to `String` wrapped in `JvmInline` value classes within the `domain` layer. This aligns with the recent refactor done for `LocalModelConfigurationEntity` and `LocalModelConfigurationId`.

## Background & Motivation
The system is shifting away from SQLite's `autoGenerate = true` integer IDs in favor of deterministic `String` UUIDs. This simplifies offline creation, cross-platform synchronization, and domain-layer encapsulation.

## Key Files & Context
- **Domain IDs to Create**: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/`
  - `chat/ChatId.kt`
  - `chat/MessageId.kt`
  - `model/config/LocalModelId.kt`
  - `model/config/ApiCredentialsId.kt`
- **Room Entities to Update**: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/`
  - `ChatEntity.kt` (id)
  - `MessageEntity.kt` (id, chatId)
  - `MessageSearch.kt` (id)
  - `LocalModelEntity.kt` (id)
  - `LocalModelConfigurationEntity.kt` (localModelId)
  - `ApiCredentialsEntity.kt` (id)
  - `ApiModelConfigurationEntity.kt` (apiCredentialsId)
- **Room Type Converters to Add**: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/`
  - `IdTypeConverters.kt` (or similar new file to house new converters)
- **Repositories & Mappers**: Update all mappers from Entity <-> Domain mapping, as well as DAOs and their query arguments to match the domain model types.

## Implementation Steps
1. **Define Domain Value Classes**:
   - Add `ChatId`, `MessageId`, `LocalModelId`, and `ApiCredentialsId` as `@JvmInline value class X(override val value: String) : IdentityInterface`.
2. **Update Entity Primary and Foreign Keys**:
   - For `ChatEntity`, `MessageEntity`, `LocalModelEntity`, `ApiCredentialsEntity`:
     - Change `@PrimaryKey(autoGenerate = true) val id: Long = 0` to `@PrimaryKey val id: [NewIdClass]`.
   - Update foreign key annotations (`@ForeignKey` columns) and columns like `chatId`, `localModelId`, `apiCredentialsId` to match the new ID types.
   - Update `MessageSearch` `id` from `Long` to `MessageId` (if supported by FTS4, otherwise `String`).
3. **Implement Room Type Converters**:
   - Create functions to map each new ID type to and from `String`. Add these to the `@TypeConverters` annotation on `PocketCrewDatabase`.
4. **Update DAOs & Mappers**:
   - Adjust `ChatDao`, `MessageDao`, `LocalModelDao`, `ApiCredentialsDao` method signatures to use the new identity types instead of `Long`.
   - Update mappers to instantiate a `UUID.randomUUID().toString()` for new entities (if they are being mapped from a domain layer creation event without a previous identity).
5. **Update Domain & UI Usage**:
   - Propagate the ID type changes through Repositories, Use Cases, ViewModels, and Compose UI states.
   - Ensure explicit UI argument passing utilizes `String` (e.g. Navigation routes) and parses back to the respective ID wrapper.

## Verification
- Project compiles cleanly.
- Tests (if any) are updated to compile with `String` UUIDs instead of `Long` IDs.
- Explicitly verify that no `autoGenerate = true` attributes remain in the entity `@PrimaryKey` annotations for these specific models.
- No database migrations will be written, per user request.