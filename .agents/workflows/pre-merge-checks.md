---
description: "Run all CI-aligned checks before merging (Lint, Detekt, Build, Tests)."
---

# Pre-Merge Checks

This workflow runs the standard CI-aligned checks for the Enclave repository.

## Steps

1.  **KtLint Check**: `./gradlew ktlintCheck`
2.  **Detekt Static Analysis**: `./gradlew detekt`
3.  **Assemble Debug Build**: `./gradlew assembleDebug`
4.  **Unit Tests**: `./gradlew testDebugUnitTest`

## Completion Criteria

- All steps must exit with code 0.
- Detekt must have `maxIssues: 0`.
