# Discovery: Centralized Linting System

## 1. Goal Summary
The project currently only enforces `ktlint` and `detekt` rules on the `:app` module. The goal is to centralize these linting configurations in the root `build.gradle.kts` file and apply them to all modules (core, feature, and library) to ensure codebase-wide style consistency and static analysis.

## 2. Target Module Index

### Existing Data Models
*N/A - This is a Build Infrastructure change.*

### Dependencies & API Contracts
- `libs.plugins.ktlint` (version 14.2.0): Kotlin linter/formatter.
- `libs.plugins.detekt` (version 1.23.8): Static code analysis for Kotlin.
- `config/detekt/detekt.yml`: Centralized detekt configuration file.

### Utility/Shared Classes
- `root/build.gradle.kts`: Contains the `subprojects` block where `jacoco` is currently centralized. This is the primary target for centralizing linting. [Identified by Probe: Build Infrastructure]
- `app/build.gradle.kts`: Currently contains the reference implementation for `ktlint` and `detekt` configuration. [Identified by Probe: Build Infrastructure]
- `gradle/libs.versions.toml`: Centralized dependency management.

### Impact Radius
- `build.gradle.kts` (root): Will be modified to include `ktlint` and `detekt` configuration within the `subprojects` block.
- `app/build.gradle.kts`: `ktlint` and `detekt` configuration blocks will be removed as they will be inherited from the root.
- All other modules (15+ modules including `:core:*` and `:feature:*`): Will automatically inherit the linting configuration once applied to `subprojects`.
- CI/CD Pipelines: Any existing linting tasks in CI may need to be updated to run on all modules (e.g., `./gradlew ktlintCheck` vs `./gradlew :app:ktlintCheck`).

## 3. Cross-Probe Analysis
### Overlaps Identified
- Only one probe was executed, focusing on the build infrastructure layer.

### Gaps & Uncertainties
- **Pure Kotlin vs Android Modules:** Some modules like `:core:domain` may be pure Kotlin. We need to ensure the configuration applied in `subprojects` is compatible with both `com.android.*` and `org.jetbrains.kotlin.jvm` plugins.
- **Native Modules:** Modules like `:llama-android` and `:whisper-android` contain JNI/Native code. We should confirm if we want to run Kotlin linting on their Kotlin wrapper code (likely yes).
- **Configuration Exclusions:** We may need to exclude generated files (like Room's `_Impl` classes or Hilt's generated code) from detekt if the shared config doesn't already handle them.

### Conflicts (if any)
- *None identified.*

## 4. High-Impact Clarifying Questions

**INSTRUCTION: Only include questions that are TRUE architectural blockers.**

### Core Logic
* **Failure Policy:** Should linting failures block the build for all modules immediately (`ignoreFailures = false`), or should we start with `true` to avoid breaking current development while fixing existing violations?

### Edge Cases
* **Module Exceptions:** Are there any modules (e.g., third-party forks or native wrappers) that should be explicitly excluded from these rules?

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| . (Root) | Build Infrastructure | Identified root `build.gradle.kts` as the central point for `subprojects` configuration and `app/build.gradle.kts` as the current reference. |
