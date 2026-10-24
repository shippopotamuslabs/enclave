# Enclave

Enclave is an Android app that runs AI models on the phone itself. Chat, dictation, image generation, and an agent runtime, with the model files stored on the device and the inference done locally. Cloud providers plug in through your own API keys when you want them: chat, image generation, and video generation at eight providers. Sean Brown builds it under Shippopotamus Labs. The source is public because the app is the portfolio piece and because anyone who wants it can build it.

## What it does

- Chat with models that run on the device. The download feature pulls GGUF model files from Hugging Face, checks each file against a recorded SHA-256 hash, and stores it on the phone. The catalog lives in `model_config.json` at the repo root.
- Two inference engines. LiteRT-LM serves the models it supports. A llama.cpp build with Arm KleidiAI kernels serves GGUF models and picks NEON or SVE code paths for the CPU it finds.
- Dictation through Whisper. The app records audio and transcribes it on the device through whisper.cpp, wired in through the whisper-android library.
- Image generation in the Studio screens. Two on-device backends do the work: a diffusion engine written for Qualcomm QNN hardware, and an MNN CPU pipeline (CLIP, UNet, and VAE) that runs on any supported phone. Generation runs in a foreground service, so a long job keeps running with the screen off.
- Multi-model pipelines. A Mixture-of-Agents worker runs several models against one prompt in stages, with a foreground inference service reporting progress.
- An agent loop with tool calls, written in Rust. It repairs malformed tool calls, compacts context when a session fills up, and keeps session accounting in one implementation.
- Text-to-speech playback through a media foreground service.
- Document generation, extraction, and validation through a second Rust core.
- Accounts and sync for anyone who wants them. Firebase Auth handles sign-in, Firestore holds synced data, and TypeScript functions in `functions/` run the server side. Crash reports go to Crashlytics. The inference contract in `contracts/INFERENCE_RULES.md` sets on-device inference as the default and treats cloud model providers as opt-in, with the user supplying their own API keys.
- Bring your own key (BYOK) for cloud models. Eight providers wire in: Anthropic, OpenAI, OpenRouter, xAI, Google, Z.ai, and DeepSeek for chat, and fal.ai for media. You paste a provider key in Settings; the app fetches that provider's model list, and you assign default models per job. OpenAI, Google, and xAI keys also carry realtime voice.
- Studio media generation through the same keys. Image and video jobs dispatch to cloud providers (fal.ai carries image and video models; Google carries Veo video) or to the on-device image backends, per assignment. Generated assets land in the app's media library.

## Status and distribution

There is no Play Store listing for Enclave, and there will not be one. This repo is the distribution channel. You build the APK from source and install it on your own phone.

The build files say version 1.0, versionCode 1. One developer builds and maintains the app, and work is active.

The APK targets arm64 hardware running Android 14 (API 34) or newer. The build ships the arm64-v8a ABI only, so x86_64 emulators cannot run it.

## Architecture

The build has 20 Gradle modules plus one composite build. Dependencies point inward to `:core:domain`, which holds the domain models and the ports other modules implement. Feature, data, and native modules adapt storage, network, hardware, and UI around those ports, and `:core:domain` imports no Android framework types. `contracts/ARCHITECTURE_RULES.md` states the rule set in full.

Module map, by layer:

- App shell: `:app` (applicationId `com.browntowndev.enclave`), with two distribution flavors, `normal` and `playStore`.
- Core: `:core:domain`, `:core:data` (Room), `:core:ui`, `:core:testing`, `:core:agent`, `:core:docgen`, `:detekt-rules` (custom detekt rules).
- Features: `:feature:chat`, `:feature:inference`, `:feature:chat-inference-service`, `:feature:studio`, `:feature:studio-generation-service`, `:feature:moa-pipeline-worker`, `:feature:download`, `:feature:history`, `:feature:settings`, `:feature:onboarding`.
- Native: `:llama-android` (llama.cpp JNI bridge), `:local-image-gen-android` (QNN and MNN image generation engines).
- Composite build: `third_party/whisper-android`, substituted for the published Maven coordinates during development.

Three Rust codebases sit under the Kotlin:

- `agent_core` holds the agent loop: the turn state machine, context compaction at 80 percent full (Micro, then FIFO, then ArtifactSquash, then an LLM summary), tool-call repair with fuzzy name matching, guardrails, and session accounting. The plan in `plans/FEAT-agent-core-rust/` states the goal plainly: one agent loop implementation, Android first, with plain Rust traits exposed so a desktop host can run the same loop later.
- `docgen_core` handles document generation, extraction, and schema validation. `:core:docgen` wraps it.
- `classifier_core` classifies search intent from an embedding vector. Its source lives in this repo at `third_party/classifier_core`.

Kotlin reaches the Rust through UniFFI-generated bindings. The generated Kotlin bindings and the compiled libraries for `agent_core` and `classifier_core` are checked into `:core:agent`, so a normal app build never runs Cargo. The binding policy is written down in `core/agent/README.md`.

Long work runs outside the UI. Foreground services carry the chat inference session, the Mixture-of-Agents pipeline, Studio generation, and TTS playback, and WorkManager carries resumable background state between the services and the screens. Android gives a dataSync foreground service a 6-hour cumulative budget, so the workers checkpoint and resume.

`contracts/` holds the binding rules for this repo: architecture, data layer, inference, UI design, code style, unit testing, and voice. `AGENTS.md` routes any piece of work, by a person or by an agent, to the contract that covers it before code gets written.

## What you need to build it

- JDK 17. The build files set a Java 17 toolchain throughout.
- Android SDK with the API 37 platform. The app compiles against SDK 37 and targets SDK 36.
- Android NDK 28.2.13676358. `:llama-android` and `:local-image-gen-android` pin that version.
- CMake 3.22.1, installable through the Android SDK manager. The native modules name that version.
- Git, for the clone and the submodules.
- An arm64 phone or tablet running Android 14 or newer, if you want to install the result.

Gradle comes from the wrapper (Gradle 9.8.1, Android Gradle Plugin 9.4.1, Kotlin 2.3.21). Rust is not required to build the app. Regenerating the UniFFI bindings takes a Rust toolchain (the crates set rust-version 1.78, edition 2021) and NDK 27.0.11718014. That workflow sits at the end of this document.

### The Qualcomm SDK, for Studio image generation

The primary diffusion engine in `:local-image-gen-android` runs on Qualcomm QNN hardware, and its SDK comes from Qualcomm under a Qualcomm account. The app builds and runs with the SDK absent; the engine compiles with its QNN path dark, and the MNN CPU backend plus any BYOK cloud providers still serve Studio generation. The MNN backend needs model packages only, no vendor SDK. To give the QNN engine its runtime:

1. Create a free account (a Qualcomm ID) at developer.qualcomm.com, and accept the Qualcomm AI Engine Direct license agreement when the site offers it. Downloads stay closed until the license is accepted.
2. Download the Qualcomm AI Runtime (QAIRT) SDK through the Qualcomm Package Manager or the Qualcomm software center. The build files name version **2.47.0.260601**. A default Linux install extracts to `/opt/qcom/aistack/qairt/2.47.0.260601`.
3. Point the build at the SDK: set the `QNN_SDK_ROOT` environment variable, or add `qnn.sdk.dir=<path>` to `local.properties`. The CMake files also try the default `/opt` path on their own.
4. Copy the QNN runtime libraries into the app with `scripts/sync-qnn-jniLibs.sh`. The script fills `local-image-gen-android/src/main/assets/qnnlibs/` from your SDK install. That directory holds only a `.gitkeep` in this repo; the libraries total about 221 MB and load via dlopen at runtime, and Qualcomm's SDK license governs what you may pass on.

## Build from a cold clone

1. Clone the repo and look at the submodules before you run Gradle. The cold clone section below lists what `.gitmodules` still points at and which native trees are absent. Read it first; it will save you a confusing configuration error.

2. Put a `google-services.json` at `app/google-services.json`. The app module applies the Google Services and Crashlytics plugins, and the build stops without that file. A file from your own Firebase project, with the package name `com.browntowndev.enclave`, satisfies the build. Firebase-backed features reach whatever backend that file names, and reach nothing if the project behind it is empty.

3. Build the debug APK:

   ```
   ./gradlew :app:assembleNormalDebug
   ```

4. The APK lands at `app/build/outputs/apk/normal/debug/app-normal-debug.apk`. Install it with `adb install -r` and that path, or copy it to the phone and open it there. Android may ask you to allow installs from your file manager or from adb.

Debug builds sign with the standard debug key, so they install anywhere. Release builds sign with a keystore that stays off this repo (`.keystore/` is gitignored, and the passwords sit in the author's `local.properties`), so the release variant stops at signing for anyone else. The sideload path is the debug build.

On first launch the app offers model downloads. Sizes run large. The first entry in `model_config.json`, MiniCPM-V-4.6 1.3B, is 811,591,648 bytes, and the hash check runs on download and on cold start.

## Cold clone status

This repo builds the author's phone builds today. A public cold clone hits gaps that the author's working tree papers over. They are listed here so nobody has to discover them from stack traces.

- `.gitmodules` lists GitLab URLs for three submodules: `third_party/agent_core`, `third_party/docgen_core`, and `third_party/whisper-android`. Those GitLab projects are private, so a public clone cannot fetch them. The whisper-android composite build is wired into `settings.gradle.kts` without a condition, and `:app` depends on its artifacts, so Gradle needs that submodule in place. All three projects also live in the `shippopotamuslabs` GitHub org, and the docgen_core and whisper-android pins have tree-identical commits there. The agent_core pin names a commit that exists in neither the GitLab history nor the GitHub one. Repointing `.gitmodules` at GitHub and repairing the pins is tracked work.
- `third_party/llama.cpp` and `third_party/kleidiai-1.22.0` are absent from the repo and are not submodules. `:llama-android` skips its CMake build when llama.cpp is missing, and its own build file calls that case expected in CI. The same build file registers a packaging check that requires both llama JNI libraries, and neither library is checked in. As the files stand, a cold clone needs the llama.cpp and KleidiAI trees placed by hand before that check can pass.
- `build-kleidiai.sh` builds the SVE and NEON llama variants into `llama-android/src/main/jniLibs/`. Its header calls the script optional for development builds. It reads the SDK location from `ANDROID_HOME` or `local.properties` and wants the same NDK version the module pins.
- The diffusion engine in `:local-image-gen-android` reads `QNN_SDK_ROOT`, or `qnn.sdk.dir` in `local.properties`, and falls back to a hardcoded path from the author's machine. The QNN runtime libraries the engine loads at runtime sit in `src/main/assets/qnnlibs/`, which holds only a `.gitkeep` in this repo. The Qualcomm SDK section above covers the account, the download, and the sync script that fills the directory. The MNN CPU backend carries no SDK dependency; it needs its model package in app-private storage.
- `:core:docgen` ships its UniFFI bindings as source, and its native library rides along in no form. `scripts/build-docgen-bindings.sh` writes `libdocgen_core.so` into `core/docgen/src/main/jniLibs/arm64-v8a/`, and the global `*.so` ignore rule covers that path, so the author's tree has the library and a clone never receives it. Docgen calls in a build from this tree fail at load time.
- `vulkan_sdk.sh` at the repo root is an empty file.
- `.github/workflows/` holds a CodeQL workflow whose Analyze job currently fails, and a GitHub Actions port of the old GitLab pipeline waiting in a pull request. `app/build.gradle.kts` also applies the Google Services and Crashlytics plugins with no condition on the file existing, so the `google-services.json` step above is mandatory today.

## Tests

The repo carries 867 unit test files. Suites concentrate in `:core:domain` (267 files), `:core:data` (199), `:feature:inference` (184), and `:feature:chat` (73). Tests run on JUnit 5, with Robolectric for the Android-facing ones and Turbine for flows, per `contracts/UNIT_TESTING.md`.

Run the unit tests with:

```
./gradlew test
```

Per module, the task names follow the flavor and build type, for example `./gradlew :app:testNormalDebugUnitTest`.

Instrumented tests exist in `:app`, and the app build file disables the `connected*AndroidTest` tasks, so device test runs take manual re-enabling.

## Regenerating the Rust bindings

Skip this section unless you changed a Rust crate's public UniFFI types. The checked-in bindings cover normal development.

- `scripts/build-agent-bindings.sh` regenerates the `agent_core` bindings and library.
- `scripts/build-docgen-bindings.sh` does the same for `docgen_core`.
- `scripts/build-classifier-bindings.sh` does the same for `classifier_core`.

`core/agent/README.md` gives the requirements and the order of operations: NDK 27.0.11718014, `sdk.dir` set, `cargo test` in the crate, then the module build, with the Rust source change, the generated Kotlin, and the generated libraries committed together.

## Repository layout

- `app/` holds the application module and the manifest that declares the foreground services.
- `core/` holds the domain, data, UI, testing, agent bridge, and docgen bridge modules, plus the custom detekt rules.
- `feature/` holds one module per feature area, including the three service modules.
- `llama-android/` and `local-image-gen-android/` hold the two native inference engines and their JNI bridges.
- `third_party/` holds the Rust crates and the submodule pointers: `agent_core`, `classifier_core`, `docgen_core`, `whisper-android`, `whisper.cpp`.
- `contracts/` holds the binding architecture, data, inference, UI, style, testing, and voice rules.
- `functions/` holds the TypeScript Firebase functions, with `firebase.json` and the Firestore and Storage rules beside it at the root.
- `scripts/` holds the binding generators, the image and classifier tooling, and the QNN sync scripts.
- `docs/` and `plans/` hold design documents and feature plans, including the Rust agent core plan and the threat and retention notes in `docs/profile_threat_model_retention.md`.

## License

ISC. The full text is in `LICENSE` at the repo root.
