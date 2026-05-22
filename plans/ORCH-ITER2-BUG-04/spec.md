# ORCH-ITER2-BUG-04 — Application vs use-case sync race

## Problem
`PocketCrewApplication` and `StartCloudSyncListeningUseCase` both call `cloudSyncPort.startListening()`, causing races on unlock/adopt.

## Design
- **Application (`CloudSyncAuthListener`)**: stop only when authenticated+locked or unauthenticated; never start.
- **Start paths**: `StartCloudSyncListeningUseCase` only (MainViewModel, SettingsGeneralDelegate).

## Tests
- `CloudSyncAuthListenerTest`: no start on unlock; stop when locked/signed out.
- Ordering: unlock via use case starts listener without Application having started first.
