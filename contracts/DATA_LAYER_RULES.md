# DATA_LAYER_RULES.md
**Pocket Crew – Data Layer Contract v2.4**

Rules for the `:data` module: Room database, repositories, entities, mappers, and persistence.

Always enforce together with `ARCHITECTURE_RULES.md`.

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Return `Flow<List<Entity>>` for observable queries. | Modify the database schema for transient UI state (e.g., `is_typing`). |
| Map Entity ↔ Domain strictly inside the `:data` module. | Expose Room `@Entity` classes or Room annotations to `:app` or `:domain`. |
| Use `@Transaction` for multi-table operations. | Return domain models directly from DAOs. |
| Use `withContext(Dispatchers.IO)` for heavy file/hash ops. | Wrap Room DAO calls in manual `withContext(Dispatchers.IO)`. |
| Store timestamps as `Long` (epoch millis). | Use `List`, `Map`, or complex objects as columns without `@TypeConverter`. |
| Implement Logical or Vector Clocks for CRDT columns. | Rely purely on server timestamps for conflict resolution in offline-first mode. |

## 1. Room Database & Anti-Hallucination Lock


- Single `PocketCrewDatabase` class annotated `@Database`.
- Database instance provided via Hilt `@Singleton` module.
- **SCHEMA LOCK:** Never invent new tables or columns (e.g., `is_typing`, `is_thinking`, `is_loading`) for transient presentation state. Transient state belongs in the UI layer. Database schema changes require explicit developer authorization.

## 2. Entity Rules

- Entities live in `:data` only. Never exposed to `:app` or UI.
- One entity per logical data table, annotated `@Entity` with explicit `tableName`.
- **Primary key:** Prefer explicit UUID string for sync-ready data.
- **CRDT Support:** Incorporate logical clocks or vector clock columns into entity schemas to support robust offline-first sync operations.
- All fields non-null unless genuinely optional. Use Kotlin defaults where sensible.
- **Timestamps:** Stored as `Long` (epoch millis). Formatting happens exclusively in the `:app` layer.
- Prefer normalized tables over `@TypeConverter`.

## 3. DAO Rules

- One DAO per entity or closely related entity group, annotated `@Dao`.
- Return `Flow<List<Entity>>` for observable queries (chat messages, history).
- Return `suspend fun` for single-shot operations (insert, delete, update).
- Never return domain models from DAOs — return entities only.
- Queries: Prefer `@Query` with explicit SQL over `@Insert`/`@Update` when behavior matters (e.g., `ON CONFLICT` strategy).

## 4. Repository Pattern, Mapping & Threading

- Repositories implement `:domain` interfaces.
- Data sources (DAOs, DataStore) injected via constructor.
- **Mapping Boundaries:**
  - Entity ↔ Domain mapping happens entirely in `:data` (repository or dedicated mapper functions). Mappers are pure functions.
  - Domain → Presentation mapping happens in `:app` (ViewModel).
  - Presentation models must never reference Entity types or Room annotations.
- **Threading Boundaries:**
  - All DAO calls are `suspend` or return `Flow`. Room handles the dispatcher internally. Do **not** use manual `withContext(Dispatchers.IO)` for Room DAO calls.
  - Background work (e.g., model file hash verification, bulk imports) MUST use `withContext(Dispatchers.IO)` in the repository.

## 5. Chat Session Data Model (Reference Schema)

Minimum required tables for v1:

**`chat_sessions`**
- `id` (PK, Long, auto-generate)
- `title` (String)
- `mode` (String/Enum)
- `created_at` (Long)
- `updated_at` (Long)

**`chat_messages`**
- `id` (PK, Long, auto-generate)
- `session_id` (FK to chat_sessions)
- `role` (String: user/assistant)
- `content` (String)
- `timestamp` (Long)
- `is_blocked` (Boolean)

**`shield_events`** (optional v1.1)
- `id` (PK)
- `message_id` (FK to chat_messages)
- `reason` (String)
- `timestamp` (Long)

**Indexes:** `session_id` on `chat_messages`, `created_at` on `chat_sessions`.

---

## 6. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Verify Isolation:** Run `gradlew.bat :data:assemble`. Ensure no compilation errors from bleeding Android/Compose dependencies.
2. **Compile Schema:** Run `gradlew.bat :app:kspDebugKotlin` to ensure the Room compiler generates schemas without errors or missing TypeConverters.
3. **Check Style:** Run `gradlew.bat :data:ktlintCheck`.