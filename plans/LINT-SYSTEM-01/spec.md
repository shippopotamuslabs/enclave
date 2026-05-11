# Technical Specification: Centralized Linting System

## 1. Objective
Centralize `ktlint` and `detekt` configurations in the root `build.gradle.kts` file to ensure codebase-wide style consistency and static analysis across all modules.

### Acceptance Criteria:
- Root `build.gradle.kts` contains the centralized configuration for `ktlint` and `detekt`.
- All modules (except excluded ones) automatically inherit the linting configuration.
- `ignoreFailures` is set to `true` for both `ktlint` and `detekt` to prevent blocking the build.
- Native wrapper modules `:llama-android` and `:whisper-android` are excluded from the linting configuration.
- `app/build.gradle.kts` no longer contains redundant linting configuration.

## 2. System Architecture

### Target Files
- `build.gradle.kts` (Root): Add centralized `ktlint` and `detekt` configuration in the `subprojects` block.
- `app/build.gradle.kts`: Remove module-specific `ktlint` and `detekt` configuration.

### Component Boundaries
The centralized configuration will be applied within the `subprojects` block in the root `build.gradle.kts`. This ensures that any module added to the project in the future will automatically inherit these rules. The logic will use `plugins.withId` to apply configurations only when the relevant plugins are present, ensuring compatibility with both Android and pure Kotlin modules (though most are currently Android modules).

## 3. Data Models & Schemas
No data model or schema changes.

## 4. API Contracts & Interfaces
No API contract changes.

## 5. Permissions & Config Delta
### build.gradle.kts (Root)
Add the following to the `subprojects` block:
- `ktlint` configuration with `android.set(true)` and `ignoreFailures.set(true)`.
- `detekt` configuration with `config.setFrom("$rootDir/config/detekt/detekt.yml")`, `buildUponDefaultConfig = true`, `allRules = false`, and `ignoreFailures = true`.
- Logic to exclude `:llama-android` and `:whisper-android` modules.

### app/build.gradle.kts
- Remove `alias(libs.plugins.ktlint)` and `alias(libs.plugins.detekt)` from the `plugins` block.
- Remove `ktlint { ... }` and `detekt { ... }` blocks.

## 6. Constitution Audit
This design adheres to the project's core architectural rules. Specifically, it follows the principle of centralized build configuration and consistency across modules.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.
