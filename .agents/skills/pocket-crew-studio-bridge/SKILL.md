---
name: pocket-crew-studio-bridge
description: >-
  Enclave Android Studio bridge via Android CLI — find symbols, run IDE
  inspections, resolve dependency versions, and render Compose @Preview PNGs.
  Use for Compose UI, refactors, navigation, and Gradle dependency work when
  Studio is open on this project.
---

# Enclave — Android Studio Bridge

## Preconditions

1. **Android Studio Quail** open with project **Enclave** at repo root.
2. **Gemini in Android Studio** enabled and signed in (required for `android studio` commands).
3. Verify:

```bash
android studio check
# Expect: READY  Enclave  /home/sean/Code/pocket-crew
```

## Compose preview workflow (debug)

Previews require a **debug** classpath, not release.

```bash
# 1. Build debug for the module you are editing
./gradlew :feature:chat:assembleDebug
# or full app:
./gradlew :app:assembleDebug

# 2. Render preview to PNG (path + composable name are separate arguments)
android studio render-compose-preview \
  --project="Enclave" \
  --output-image-file=/tmp/pocket-crew-preview.png \
  feature/chat/src/main/kotlin/.../BubblePreviews.kt \
  PreviewMessageBubbleUser
```

If you see `Missing ComposeViewAdapter unable to render`:

- Open that `@Preview` in Studio's design panel once.
- Re-run `./gradlew :<module>:assembleDebug`.
- Retry `render-compose-preview`.

## Symbol navigation

```bash
android studio find-declaration --project="Enclave" --short ChatScreen
android studio find-usages --project="Enclave" --short EnclaveNavGraph
```

Prefer these over repo-wide grep for Kotlin symbols across modules.

## IDE analysis before/after edits

```bash
android studio analyze-file --project="Enclave" \
  feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatScreen.kt
```

Surfaces Studio inspections (lint, Kotlin warnings) on the file under edit.

## Resolved versions (dependency work)

```bash
android studio version-lookup --project="Enclave" compose agp kotlin
android studio version-lookup --project="Enclave" androidx.navigation:navigation-compose
```

## APK paths and deploy

```bash
android describe --project_dir=/home/sean/Code/pocket-crew
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

## Documentation (Knowledge Base)

```bash
android docs search 'Navigation 3 multiple back stacks'
android docs fetch kb://...
```

## Related repo docs

- Rule: `.agents/rules/android-studio-cli.md`
- Contract: `AGENTS.md` §7 (Android CLI + Studio bridge)
- AndroJack: `.cursor/mcp.json` — still required for generated Kotlin validation
