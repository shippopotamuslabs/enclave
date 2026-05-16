# `:core:agent`

UniFFI JNI bridge for `third_party/agent_core` (same layout as `:core:docgen`).

## Binding policy

Generated Kotlin bindings and native `.so` outputs are checked in with the Android app. CI should verify that
`scripts/build-agent-bindings.sh` can reproduce them, but app builds must not require UniFFI generation at compile time.

## Regenerating bindings

Requires Android NDK **27.0.11718014** and `sdk.dir` in the repo root `local.properties` (or `ANDROID_HOME`).

```bash
./scripts/build-agent-bindings.sh
```

Then:

```bash
./gradlew :core:agent:assembleDebug
```

Run the script whenever any of these change:

- Public UniFFI types or callback interfaces in `third_party/agent_core`
- `agent_core` semver or submodule/tag pointer
- Android target ABIs or NDK version

## Submodule / version bump workflow

1. Update `third_party/agent_core/Cargo.toml` and sync to the intended Rust tag.
2. Run `./scripts/build-agent-bindings.sh`.
3. Commit the Rust source/tag update, generated `agent_core.kt`, and generated native libraries together.
4. Run `cargo test` in `third_party/agent_core`, then `./gradlew :core:agent:assembleDebug`.

## Threading (AGENT-022)

Rust calls Kotlin callbacks on the **caller** thread (often a JNI worker). Heavy work must use `Dispatchers.IO` (never block the main thread). `RustAgentEngineAdapter` dispatches host suspend callbacks onto `Dispatchers.IO` before invoking model, tool, or summary work.

## ABI support

Currently builds for arm64-v8a only; 32-bit ARM and x86_64 emulators are not supported.

## Policy helpers (AGENT-034)

Top-level UniFFI functions in `agent_core.kt` (e.g. `repairTool`, `truncateResult`) are thin bridges to Rust policy — prefer them over duplicating repair/truncation in Kotlin.

## Desktop consumers

Desktop consumers should depend on `third_party/agent_core` directly as a Cargo path dependency and use the native Rust
traits (`AgentTransport`, `AgentToolExecutor`, `AgentSummarizer`). UniFFI is Android-only glue for Pocket Crew.

## Troubleshooting

- **NDK not found:** set `sdk.dir` in `local.properties` or export `ANDROID_HOME`.
- **Contract/version mismatch:** regenerate bindings after Rust FFI type changes; breaking FFI changes require a major
  `agent_core` semver bump.
- **JNA load errors:** verify `:core:agent` packaged the generated `libagent_core.so` for the device ABI.
