# Test Specification: Centralized Linting System

## 1. Happy Path Scenarios

### Scenario: Centralized KtLint Configuration Applied
- **Given:** Root `build.gradle.kts` is updated with centralized `ktlint` configuration.
- **When:** Running `./gradlew ktlintCheck` from the root.
- **Then:** The task executes for all applicable modules (e.g., `:core:data`, `:feature:chat`, etc.).
- **And:** The task does not fail the build even if lint violations are found (`ignoreFailures = true`).

### Scenario: Centralized Detekt Configuration Applied
- **Given:** Root `build.gradle.kts` is updated with centralized `detekt` configuration.
- **When:** Running `./gradlew detekt` from the root.
- **Then:** The task executes for all applicable modules.
- **And:** The task uses the configuration from `config/detekt/detekt.yml`.
- **And:** The task does not fail the build even if violations are found (`ignoreFailures = true`).

### Scenario: Native Modules Excluded
- **Given:** Centralized linting is applied to `subprojects`.
- **When:** Checking the tasks for `:llama-android` or `:whisper-android`.
- **Then:** `ktlintCheck` and `detekt` tasks should not be configured or should be skipped for these modules.

### Scenario: Redundant Config Removed from App Module
- **Given:** Redundant `ktlint` and `detekt` blocks are removed from `app/build.gradle.kts`.
- **When:** Syncing the project with Gradle.
- **Then:** The `:app` module still has `ktlintCheck` and `detekt` tasks inherited from the root.

## 2. Error Path & Edge Case Scenarios

### Scenario: Module with No Kotlin Files
- **Given:** A module with no Kotlin source files.
- **When:** Running linting tasks.
- **Then:** The tasks should complete successfully (possibly as NO-SOURCE) without error.

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely broken implementation is applying the configuration to ALL modules including the excluded ones, or failing to use `plugins.withId` which might cause issues if a module doesn't have the Kotlin plugin applied (though unlikely in this project). Another risk is forgetting to set `ignoreFailures = true` which would block the build.

### Defense Scenario
- **Given:** The centralized configuration is implemented.
- **When:** Intentionally introducing a linting violation in a core module and running `./gradlew build`.
- **Then:** The build should still pass because `ignoreFailures` is set to `true`.
