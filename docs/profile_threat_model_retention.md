# Psychological Profile Analysis — Threat Model & Data Retention

This document outlines the security threat model and data retention policies for the user's ingested chat conversation history used during psychological profile analysis.

## Threat Model

### 1. Data Classification
- **Profile Corpus (Conversations and Messages)**: Plaintext chat exports imported from providers (OpenAI, Anthropic, Google, xAI, etc.).
- **Intermediate Findings**: Behavioural moments, patterns, self-review notes, and draft syntheses recorded during the multi-pass analysis.
- **Persistent Profile (PSYCH_PROFILE)**: The final generated profile memory stored in the local memories repository.

### 2. Storage Location & Security Boundaries
- **Local Application Sandbox**: All files (unzipped exports, intermediate DB records, findings, and final memories) reside inside the private application sandbox directory (`/data/data/com.browntowndev.enclave/`).
- **Android Sandbox Security**: On non-rooted devices, Android enforces UID isolation to prevent other applications from accessing this directory. On rooted devices, root access can expose sandbox contents. This matches the security model of all other local database/app data.
- **No Cloud/Sync Persistence**: Raw conversation exports, messages, and intermediate findings are **never** synced to Firestore or external servers. The final saved profile is stored in `memories` and is also kept local to the device by default.

### 3. Exfiltration Risks (BYOK API Integration)
- The raw conversation corpus remains local to the device.
- During the 4-pass analysis, when using a custom "Bring Your Own Key" (BYOK) cloud model configuration (e.g. OpenAI, Anthropic, Gemini API), **only selected conversation text excerpts** are transmitted to the configured API endpoint over secure HTTPS for analysis.
- Excerpts are subject to the privacy terms of the user's selected API provider.

---

## Retention Policy

To minimize the exposure of plaintext personal chat history, a strict data retention and automatic cleanup policy is enforced:

| Scenario | Trigger | Action |
|----------|---------|--------|
| **Successful Save** | User reviews, edits, and saves the psychological profile memory. | The full corpus, messages, findings, and temporary directories are **immediately deleted** from the database and storage via `clearAll(corpusRunId)`. |
| **New Session / Re-import** | User selects different files or re-ingests a corpus. | The existing corpus run data is **purged** before starting ingestion to prevent data build-up. |
| **Flow Cancellation / Dismissal** | The user exits the Profile Builder flow or the ViewModel is cleared (e.g. going back, closing settings). | If the profile has not been saved, all associated corpus databases and unzipped temporary files are **purged** on ViewModel termination. |
| **Analysis Failure** | The analysis fails due to a terminal model or network failure. | The corpus remains on-disk transiently so the user can review and retry, but is fully cleaned up if they exit the flow or start a new run. |
