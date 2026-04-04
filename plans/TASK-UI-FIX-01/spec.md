# Technical Specification: Fix ModelDownloadScreen UI overlap on Samsung devices

## 1. Objective
Ensure that the "Pause" button (and other download controls) in `ModelDownloadScreen.kt` do not overlap with the system navigation bar on devices with edge-to-edge mode enabled (like Samsung SM-G998U). The fix should properly handle window insets for the `bottomBar` of the `Scaffold`.

## 2. System Architecture

### Target Files
- `feature/download/src/main/kotlin/com/browntowndev/pocketcrew/feature/download/ModelDownloadScreen.kt`

### Component Boundaries
- The change is internal to the `ModelDownloadScreen.kt` file. It affects how the `DownloadControls` component is rendered within the `Scaffold`'s `bottomBar`.

## 3. Data Models & Schemas
- No data model or schema changes.

## 4. API Contracts & Interfaces
- No API contract changes.

## 5. Permissions & Config Delta
- No permissions or config changes.

## 6. Constitution Audit
- This design adheres to the project's core architectural rules (CFAW, Recomposition Discipline). It correctly handles window insets for a full-screen screen.

## 7. Cross-Spec Dependencies
- No cross-spec dependencies.
