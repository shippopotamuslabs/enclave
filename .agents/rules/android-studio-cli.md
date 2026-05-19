# Android CLI and Studio Bridge (Pocket Crew)

Android CLI 1.0+ and a running **Android Studio Quail** instance are available for this
project. Use them proactively on Android/Kotlin/Compose work — do not wait for the user
to remind you.

## When to use (mandatory checks)

At the start of tasks involving **Compose UI**, **navigation/refactors**, **Gradle
dependency bumps**, or **multi-module symbol search**:

1. Run `android studio check`. If `Pocket Crew` is `READY`, use Studio bridge commands.
2. If Studio is not running, say so once and continue with Gradle/grep; retry `studio check`
   after the user may have opened Studio.

## Tool split (complements AndroJack)

| Tool | Use for |
|------|---------|
| **AndroJack (MCP)** | Pre/post codegen validation, API levels, Material 3, dependency checks — still **mandatory** per `AGENTS.md` §3.2 |
| **`android studio …`** | IDE index: find declaration/usages, file analysis (lint/inspections), resolved library versions, Compose preview PNGs |
| **`android` CLI (non-studio)** | `describe`, `run`, `layout`, `docs search/fetch`, emulator lifecycle |

Do not replace AndroJack with Studio commands. Use both.

## Studio project identifier

- `--project="Pocket Crew"` (or run commands from repo root; Studio resolves the open project)
- Repo path: `/home/sean/Code/pocket-crew`

## Preferred Studio commands

```bash
android studio check
android studio find-declaration --project="Pocket Crew" --short <SymbolName>
android studio find-usages --project="Pocket Crew" --short <SymbolName>
android studio analyze-file --project="Pocket Crew" <path-relative-to-repo-root>
android studio version-lookup --project="Pocket Crew" <artifact-or-keyword>
```

For Compose previews, read `.agents/skills/pocket-crew-studio-bridge/SKILL.md`.

## Compose preview prerequisite (debug)

Preview rendering uses Studio's **debug** tooling, not release artifacts.

Before `android studio render-compose-preview`:

1. Ensure Studio has synced the project (user keeps Studio open on Pocket Crew).
2. Build debug for the touched module, e.g. `./gradlew :feature:chat:assembleDebug` or
   `./gradlew :app:assembleDebug`.
3. If render fails with `Missing ComposeViewAdapter`, open the `@Preview` once in Studio's
   preview panel, rebuild debug, then retry.

## Deploy and UI inspection (CLI)

After `./gradlew :app:assembleDebug`:

```bash
android describe --project_dir=/home/sean/Code/pocket-crew
android run --apks=app/build/outputs/apk/debug/app-debug.apk
android layout   # connected device/emulator
```

## Skills

- Global: `~/.cursor/skills/android-cli` (`android init`)
- Repo: `.agents/skills/pocket-crew-studio-bridge`, `navigation-3`, `edge-to-edge`
