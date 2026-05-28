# Schema Authorization — FEAT-inference-telemetry-stats

**Status:** Authorized for implementation (destructive migration posture accepted).

## Decision

Add durable table `message_inference_telemetry` and bump `EnclaveDatabase` to version **2**.

## Rationale

- Response stats, chat stats, and settings Usage & Spend require historical telemetry across app restarts.
- In-memory-only telemetry fails `TEL-010`, `TEL-011`, and settings period filtering scenarios.

## Migration posture

The app uses `fallbackToDestructiveMigration()` for this schema bump. Existing local databases on upgrade will be recreated without a manual SQL migration script.

## Firestore

Collection path: `users/{uid}/message_inference_telemetry/{id}` — synced as a message child entity (plaintext metadata; not in the sensitive-field catalog).
