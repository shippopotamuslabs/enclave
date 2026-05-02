# Studio & Gallery Feature Roadmap

## Objective

Complete the remaining feature set for `StudioScreen` and `GalleryScreen`. This document defines every remaining feature as an implementation-ready ticket with enough context for an AI agent to begin work immediately. Tickets are grouped into **tiers** — each tier contains work that can be done concurrently without merge conflicts.

---

## Tier Definitions

| Tier | Description | Can Run Concurrently? |
|------|-------------|----------------------|
| **Tier 1** | Pure data-layer and domain-layer changes. No UI overlap. | Yes — each ticket touches separate domain/data files. |
| **Tier 2** | Shared infrastructure components (fullscreen viewer, share port). Foundation for Tier 3 UI work. | Yes — viewer and share port touch different module boundaries. |
| **Tier 3** | UI features that consume Tier 1 + Tier 2 outputs. Gallery UI and Studio UI changes are independent. | Yes — Gallery tickets touch `GalleryScreen.kt`/`GalleryViewModel.kt`; Studio tickets touch `StudioScreen.kt`/`StudioDetailScreen.kt`/`MultimodalViewModel.kt`. No file overlap. |
| **Tier 4** | Deep integration features (music generation, video editing) requiring new API clients and port implementations. | Partially — music gen and video edit are separate port/impl tracks but both touch `MultimodalViewModel.kt`, so they should be sequenced within the tier. |

---

## Tier 1 — Data & Domain Layer Additions

### T1-A: Add Album Rename to Data Layer

**Summary:** Add the ability to rename an existing album through the full data stack.

**Files to modify:**
- `core/data/src/main/kotlin/.../local/StudioAlbumDao.kt` — add `updateAlbumName` query
- `core/domain/src/main/kotlin/.../port/repository/StudioRepositoryPort.kt` — add `renameAlbum(id, name)` to interface
- `core/data/src/main/kotlin/.../repository/StudioRepositoryImpl.kt` — implement `renameAlbum`
- `core/domain/src/test/kotlin/...` — add domain-level tests for the new method
- `core/data/src/test/kotlin/...` — add repository-level tests

**Implementation details:**

- [ ] Add `@Query("UPDATE studio_albums SET name = :name WHERE id = :id") suspend fun updateAlbumName(id: Long, name: String)` to `StudioAlbumDao` (`StudioAlbumDao.kt:10-18`)
- [ ] Add `suspend fun renameAlbum(id: String, name: String)` to `StudioRepositoryPort` (`StudioRepositoryPort.kt:23-34`)
- [ ] Implement `renameAlbum` in `StudioRepositoryImpl` — convert String id to Long, call `studioAlbumDao.updateAlbumName`, handle invalid id gracefully (`StudioRepositoryImpl.kt:18-142`)
- [ ] Update `FakeStudioRepository` in `GalleryViewModelTest.kt` to implement the new interface method (`GalleryViewModelTest.kt:169-209`)
- [ ] Write unit tests: rename succeeds, rename with empty name is rejected, rename nonexistent album is a no-op

**Contracts:** `DATA_LAYER_RULES.md`, `ARCHITECTURE_RULES.md` (domain must remain pure Kotlin, no Android deps)

---

### T1-B: Add Album Deletion to Data Layer

**Summary:** Add the ability to delete albums with a policy decision on what happens to contained media.

**Files to modify:**
- `core/domain/src/main/kotlin/.../port/repository/StudioRepositoryPort.kt` — add `deleteAlbum(id, reassignMediaToDefault)` to interface
- `core/data/src/main/kotlin/.../repository/StudioRepositoryImpl.kt` — implement `deleteAlbum` with media reassignment logic
- `core/data/src/main/kotlin/.../local/StudioMediaDao.kt` — add `getMediaByAlbumId` query if not present, and `reassignMediaToAlbum` query
- `core/data/src/main/kotlin/.../local/StudioAlbumDao.kt` — `deleteAlbum` already exists at line 17, no change needed
- Test files for both layers

**Implementation details:**

- [ ] Add `@Query("SELECT * FROM studio_gallery WHERE albumId = :albumId") suspend fun getMediaByAlbumId(albumId: Long): List<StudioMediaEntity>` to `StudioMediaDao` (`StudioMediaDao.kt:10-25`)
- [ ] Add `suspend fun deleteAlbum(id: String)` to `StudioRepositoryPort` — when an album is deleted, its media should be reassigned to the default album (null albumId) rather than deleted. This matches the existing pattern where `albumId` is nullable and null means "Default Album" (`StudioRepositoryPort.kt:23-34`)
- [ ] Implement `deleteAlbum` in `StudioRepositoryImpl`: (1) reassign all media with that albumId to null albumId, (2) then delete the album row (`StudioRepositoryImpl.kt`)
- [ ] Update `FakeStudioRepository` in `GalleryViewModelTest.kt`
- [ ] Write unit tests: delete empty album, delete album with media (verify media reassigned to default), delete nonexistent album

**Contracts:** `DATA_LAYER_RULES.md`, `ARCHITECTURE_RULES.md`

**Design decision:** Media is NOT cascade-deleted when an album is removed. It moves back to the Default Album. This prevents accidental data loss. If the user wants to delete media, they use the existing per-item delete flow.

---

### T1-C: Add ShareMediaPort to Domain Layer

**Summary:** Define the domain port for sharing media files via Android's share sheet.

**Files to modify:**
- New file: `core/domain/src/main/kotlin/.../port/media/ShareMediaPort.kt` — interface definition

**Implementation details:**

- [ ] Create `ShareMediaPort` interface with method `fun shareMedia(uris: List<String>, mimeType: String)` — takes a list of local file URIs and a MIME type prefix (`image/*` or `video/*`). Single item uses `ACTION_SEND`, multiple uses `ACTION_SEND_MULTIPLE`. Returns `Unit` since sharing is fire-and-forget (user picks target app) (`core/domain/src/main/kotlin/.../port/media/`)
- [ ] The interface should be pure Kotlin with no Android imports — the `uris` parameter is `List<String>` (file path strings), not `android.net.Uri`

**Contracts:** `ARCHITECTURE_RULES.md` (domain must be pure Kotlin), `DATA_LAYER_RULES.md`

---

## Tier 2 — Shared Infrastructure Components

### T2-A: Implement AndroidShareMediaAdapter

**Summary:** Create the Android-side implementation of `ShareMediaPort` using `Intent.ACTION_SEND`.

**Files to modify:**
- New file: `core/data/src/main/kotlin/.../media/AndroidShareMediaAdapter.kt`
- `core/data/src/main/kotlin/.../DataModule.kt` — bind the new port

**Implementation details:**

- [ ] Create `AndroidShareMediaAdapter` class implementing `ShareMediaPort`, injected with `@ApplicationContext Context`
- [ ] For single item: create `Intent(ACTION_SEND)` with `type = mimeType`, add `EXTRA_STREAM` as a content URI (convert file:// URI to a FileProvider content URI for security), set `Intent.FLAG_GRANT_READ_URI_PERMISSION`
- [ ] For multiple items: create `Intent(ACTION_SEND_MULTIPLE)` with `EXTRA_STREAM` as an `ArrayList<Uri>` of content URIs
- [ ] Wrap in `Intent.createChooser()` and add `FLAG_ACTIVITY_NEW_TASK`
- [ ] Add `@Binds` in `DataModule.kt` to bind `ShareMediaPort` to `AndroidShareMediaAdapter`
- [ ] Ensure a `FileProvider` is configured in `AndroidManifest.xml` if not already present (check `core/data/src/main/AndroidManifest.xml` or `app/src/main/AndroidManifest.xml`)
- [ ] Write unit tests using Robolectric to verify intent construction (single vs multiple, MIME types, flags)

**Contracts:** `DATA_LAYER_RULES.md`, `ARCHITECTURE_RULES.md`

---

### T2-B: Extract Reusable Fullscreen Media Viewer

**Summary:** Extract the existing `StudioVideoPlayer` and `ZoomableAsyncImage` detail viewing logic into a shared, reusable `FullscreenMediaViewer` composable that both Studio and Gallery can use.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../StudioVideoPlayer.kt` — keep as-is, but the viewer will compose it
- New file: `core/ui/src/main/kotlin/.../component/FullscreenMediaViewer.kt`
- `feature/studio/src/main/kotlin/.../StudioDetailScreen.kt` — refactor `DetailMedia` to use the new viewer

**Implementation details:**

- [ ] Create `FullscreenMediaViewer` composable in `core/ui` module. Signature: `FullscreenMediaViewer(localUri: String, mediaType: MediaCapability, contentDescription: String, modifier: Modifier)`
- [ ] For `IMAGE` type: use `ZoomableAsyncImage` from telephoto library (already a dependency — see `StudioDetailScreen.kt:41-42`) with `rememberZoomableImageState()`, `ContentScale.Fit`
- [ ] For `VIDEO` type: use `StudioVideoPlayer` (move or copy to `core/ui`, or keep in `feature/studio` and have `core/ui` define a `VideoPlayerSlot` composable parameter — evaluate which approach is cleaner given the architecture rules). If keeping `StudioVideoPlayer` in `feature/studio`, the viewer in `core/ui` can accept a `videoPlayerContent: @Composable () -> Unit` slot parameter.
- [ ] Default video to muted (`muted = true`), auto-play off (`autoPlay = false`)
- [ ] Add a mute/unmute toggle icon button in the bottom-right corner of video content, overlaying the video. Use `Icons.Default.VolumeOff` / `Icons.Default.VolumeUp`. State is local to the viewer composable.
- [ ] Refactor `DetailMedia` in `StudioDetailScreen.kt:164-219` to delegate to `FullscreenMediaViewer`

**Contracts:** `UI_DESIGN_SPEC.md`, `CODE_STYLE_RULES.md` (modifier conventions, state hoisting)

**Dependency note:** This ticket depends on understanding whether `StudioVideoPlayer` can be moved to `core/ui`. If it has no `:inference` or `:data` dependencies (it doesn't — it's pure Compose + Media3), it can be moved. The alternative slot-based approach avoids the move entirely.

---

### T2-C: Add Mute/Unmute State Preference to StudioVideoPlayer

**Summary:** Add the missing mute toggle to `StudioVideoPlayer`. Currently it accepts a `muted` parameter but has no UI toggle and no default-muted behavior.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../StudioVideoPlayer.kt`

**Implementation details:**

- [ ] Change `muted` default from `false` to `true` (`StudioVideoPlayer.kt:51`) — videos should be muted by default per user requirement
- [ ] Add internal `var isMuted by remember(localUri, muted) { mutableStateOf(muted) }` state
- [ ] Add a volume icon button overlay in the bottom-right corner of the `Box` (`StudioVideoPlayer.kt:94-152`), similar pattern to the existing play/pause button but smaller (e.g., `Modifier.size(40.dp)`)
- [ ] On toggle: update `isMuted` and set `player.volume = if (isMuted) 0f else 1f`
- [ ] Use `Icons.Default.VolumeOff` when muted, `Icons.Default.VolumeUp` when unmuted
- [ ] Position the mute button at `Alignment.BottomEnd` with padding, separate from the centered play/pause button
- [ ] Write Compose UI test: verify mute button appears, toggling changes volume state

**Contracts:** `UI_DESIGN_SPEC.md`, `CODE_STYLE_RULES.md`

---

## Tier 3 — UI Features (Gallery & Studio in Parallel)

### T3-A: Gallery — Fullscreen Media Detail Screen

**Summary:** When a user taps a media item in an album, open a fullscreen detail viewer (not just toggle selection). Support pinch-to-zoom on images and play/pause/mute on videos.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../GalleryScreen.kt` — add tap-to-open-detail behavior in `AlbumMediaItem` and `AlbumItemGrid`
- `feature/studio/src/main/kotlin/.../GalleryViewModel.kt` — add state for selected detail item
- `app/src/main/kotlin/.../navigation/PocketCrewNavGraph.kt` — add navigation to a gallery detail route
- Potentially reuse `StudioDetailScreen` or create `GalleryDetailScreen`

**Implementation details:**

- [ ] Decide approach: (A) navigate to a new `GALLERY_DETAIL` route with its own composable, or (B) show fullscreen viewer as an in-screen overlay/dialog within `GalleryScreen`. Approach A is recommended since it matches the Studio pattern and allows the HorizontalPager for swiping between items.
- [ ] Add `onMediaItemClick: (String) -> Unit` callback to `GalleryScreen` and `AlbumItemGrid` (`GalleryScreen.kt:125-132`). Currently tapping an item only toggles selection — add a condition: if `selectedMediaItemIds` is empty, emit `onMediaItemClick`; if non-empty, toggle selection (existing behavior).
- [ ] In `AlbumMediaItem` (`GalleryScreen.kt:503-509`), add `clickable` modifier that calls the new callback when no selection is active
- [ ] Add navigation route `GALLERY_DETAIL/{assetId}` in `PocketCrewNavGraph.kt` (`PocketCrewNavGraph.kt:217-247`). The gallery detail screen should use `FullscreenMediaViewer` (from T2-B) wrapped in a `Scaffold` with a close button and action row (share, delete, send to studio).
- [ ] Create `GalleryDetailScreen` composable — simpler than `StudioDetailScreen`, focused on viewing + actions rather than editing. Use `HorizontalPager` for swipe navigation between album items.
- [ ] Thread album items list into the detail screen via the `GalleryViewModel` or pass as nav arguments (evaluate approach based on list size)

**Contracts:** `UI_DESIGN_SPEC.md`, `ARCHITECTURE_RULES.md`, `CODE_STYLE_RULES.md`

**Dependency:** T2-B (FullscreenMediaViewer), T2-C (mute toggle)

---

### T3-B: Gallery — Share Media

**Summary:** Add share action to the Gallery detail view and batch share from multi-select.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../GalleryScreen.kt` — add share button to action bar and detail view
- `feature/studio/src/main/kotlin/.../GalleryViewModel.kt` — add `shareMedia` function

**Implementation details:**

- [ ] Inject `ShareMediaPort` into `GalleryViewModel` (`GalleryViewModel.kt:19-21`)
- [ ] Add `fun shareMedia(mediaIds: Set<String>)` to `GalleryViewModel` — look up the URIs from the current `uiState` media items, call `shareMediaPort.shareMedia(uris, mimeType)`. Determine MIME type from `mediaType` field.
- [ ] In `GalleryScreen`, add a share `IconButton` to `GalleryTopBar` actions when items are selected (`GalleryScreen.kt:316-336`). Place it before the existing move and delete icons.
- [ ] In the fullscreen detail screen (from T3-A), add a "Share" action to the `DetailActionRow`
- [ ] Single item share: pass `listOf(uri)` with the item's MIME type
- [ ] Batch share: pass all selected URIs — if mixed types (images + videos), use `*/*` as MIME type

**Contracts:** `UI_DESIGN_SPEC.md`, `ARCHITECTURE_RULES.md`

**Dependency:** T1-C (ShareMediaPort), T2-A (AndroidShareMediaAdapter), T3-A (detail screen for single-item share)

---

### T3-C: Gallery — Rename Albums

**Summary:** Add the ability to rename an album from the Gallery screen.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../GalleryScreen.kt` — add rename UI (dialog or inline edit)
- `feature/studio/src/main/kotlin/.../GalleryViewModel.kt` — add `renameAlbum` function

**Implementation details:**

- [ ] Add `fun renameAlbum(albumId: String, newName: String)` to `GalleryViewModel` — validate non-empty name, call `studioRepository.renameAlbum(albumId, newName)` (`GalleryViewModel.kt`)
- [ ] Add a rename action to the album view. Two options: (A) long-press context menu on album card in the grid, or (B) a rename icon in the top bar when viewing an album. Approach B is recommended — when `selectedAlbumId` is non-null and no media items are selected, show a rename/edit icon in `GalleryTopBar` actions.
- [ ] Create `RenameAlbumDialog` composable — `AlertDialog` with a `TextField` pre-populated with current album name. Similar pattern to existing `AddAlbumDialog` (`GalleryScreen.kt:222-229`).
- [ ] Add state `isRenameAlbumDialogOpen` and `albumToRename` in `GalleryScreen`
- [ ] When rename icon is tapped, open dialog. On confirm, call `viewModel.renameAlbum(albumId, newName)`.

**Contracts:** `UI_DESIGN_SPEC.md`, `CODE_STYLE_RULES.md`

**Dependency:** T1-A (renameAlbum in data layer)

---

### T3-D: Gallery — Long-Press Delete Albums

**Summary:** Add long-press on album cards to enter album selection mode, then delete one or many albums with a confirmation dialog.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../GalleryScreen.kt` — add album selection mode and delete dialog
- `feature/studio/src/main/kotlin/.../GalleryViewModel.kt` — add `deleteAlbums` function

**Implementation details:**

- [ ] Add `fun deleteAlbums(albumIds: Set<String>)` to `GalleryViewModel` — call `studioRepository.deleteAlbum(id)` for each. Media inside is reassigned to default per T1-B design. (`GalleryViewModel.kt`)
- [ ] Add state `selectedAlbumIds: Set<String>` to `GalleryScreen` (local state via `rememberSaveable`, same pattern as `selectedMediaItemIds` at line 137)
- [ ] In `AlbumCard` composable, add `onLongClick` callback. On long-press, add the album ID to `selectedAlbumIds`.
- [ ] When `selectedAlbumIds` is non-empty, `GalleryTopBar` should show selection count and a delete action icon (same pattern as media selection mode at `GalleryScreen.kt:306-337`)
- [ ] Create `DeleteAlbumsDialog` composable — confirmation dialog similar to existing `DeleteDialog` (`GalleryScreen.kt:737-763`). Text: "Delete {count} album(s)? Media inside will be moved to the Default Album."
- [ ] On confirm, call `viewModel.deleteAlbums(selectedAlbumIds)`, clear selection
- [ ] Tapping an album card when `selectedAlbumIds` is non-empty should toggle selection (not navigate into the album)
- [ ] Pressing back when `selectedAlbumIds` is non-empty should clear selection (same pattern as media selection at `GalleryScreen.kt:153-160`)
- [ ] Do NOT allow deleting the Default Album (id = `DEFAULT_GALLERY_ALBUM_ID`) — filter it from selection

**Contracts:** `UI_DESIGN_SPEC.md`, `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`

**Dependency:** T1-B (deleteAlbum in data layer)

---

### T3-E: Gallery — Send Media to Studio

**Summary:** Allow users to send a media item from the Gallery to the Studio for editing or animation.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../GalleryScreen.kt` — add "Send to Studio" action
- `app/src/main/kotlin/.../navigation/PocketCrewNavGraph.kt` — wire navigation callback

**Implementation details:**

- [ ] Add `onSendToStudio: (assetId: String, mode: "edit" | "animate") -> Unit` callback to `GalleryScreen` and the gallery detail screen
- [ ] In the gallery detail view (from T3-A), add "Edit" and "Animate" action buttons in the action row (same icons as `StudioDetailScreen.kt:314-334`)
- [ ] In `PocketCrewNavGraph.kt`, when `onSendToStudio` is called: navigate to `Routes.STUDIO` and trigger `MultimodalViewModel.onEditMedia(assetId)` or `MultimodalViewModel.onAnimateMedia(assetId)`. This requires the studio `MultimodalViewModel` to already be available on the backstack.
- [ ] Implementation approach: use a shared ViewModel pattern or a navigation result. The simplest approach is to navigate to Studio and pass the assetId + mode as nav arguments, then in the Studio composable's `LaunchedEffect`, call the appropriate method.
- [ ] Add a new nav route or arguments to `Routes.STUDIO` like `Routes.STUDIO + "?editAssetId={editAssetId}&animateAssetId={animateAssetId}"` with optional arguments
- [ ] In StudioScreen composable (`PocketCrewNavGraph.kt:204-214`), read the optional arguments and call `studioViewModel.onEditMedia` or `studioViewModel.onAnimateMedia` in a `LaunchedEffect` (run once, then clear the argument)

**Contracts:** `ARCHITECTURE_RULES.md` (navigation via pure callbacks, no NavController in composables)

**Dependency:** None for UI — the edit/animate methods already exist in `MultimodalViewModel`. Navigation wiring depends on T3-A for the detail screen.

---

### T3-F: Studio — Share Media (Detail View + Batch)

**Summary:** Add share action to the Studio detail view and batch share from multi-select in StudioScreen.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../StudioDetailScreen.kt` — add share button to action row
- `feature/studio/src/main/kotlin/.../StudioScreen.kt` — add share to selection mode actions
- `feature/studio/src/main/kotlin/.../MultimodalViewModel.kt` — add `shareMedia` function

**Implementation details:**

- [ ] Inject `ShareMediaPort` into `MultimodalViewModel` (`MultimodalViewModel.kt:29-41`)
- [ ] Add `fun shareMedia(assetIds: Set<String>)` — look up URIs from `_sessionMedia`, call `shareMediaPort.shareMedia`
- [ ] Add `fun shareSingleMedia(assetId: String)` — convenience wrapper for single item from detail view
- [ ] In `StudioDetailScreen.kt`, add a "Share" `ActionItem` to `DetailActionRow` (`StudioDetailScreen.kt:322-333`). Place it between "Save" and "Delete". Icon: `Icons.Default.Share`.
- [ ] Wire the share action through `StudioDetailScreen` composable signature — add `onShareMedia: (String) -> Unit` callback
- [ ] In `PocketCrewNavGraph.kt:274-281`, pass `studioViewModel::shareSingleMedia` as the callback
- [ ] For batch share in `StudioScreen`, the selection mode already exists (`StudioScreen.kt:253-268`). Add a share icon to the `StudioTopBar` actions when items are selected. Call `viewModel.shareMedia(selectedIds)`.
- [ ] Note: Studio's `StudioTopBar` currently shows `selectedCount`, `onClearSelection`, `onSaveClick` — add `onShareClick` parameter

**Contracts:** `UI_DESIGN_SPEC.md`, `CODE_STYLE_RULES.md`

**Dependency:** T1-C (ShareMediaPort), T2-A (AndroidShareMediaAdapter)

---

### T3-G: Studio — Custom Animation Description

**Summary:** When a user taps "Animate" on an image, let them optionally modify the animation prompt before generation starts, rather than always using the original image prompt.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../MultimodalViewModel.kt` — modify `onAnimateMedia`
- `feature/studio/src/main/kotlin/.../StudioDetailScreen.kt` — add prompt input in animate flow

**Implementation details:**

- [ ] Current behavior (`MultimodalViewModel.kt:389-410`): `onAnimateMedia` sets `_prompt.value = asset.prompt` and `_mediaType.value = MediaCapability.VIDEO`, then navigates back to StudioScreen with the prompt pre-filled in the input dock.
- [ ] This already partially works — the user lands on StudioScreen with the prompt in the input field and can edit it before tapping generate. The issue is that `onNavigateBack()` is called immediately in `StudioDetailScreen.kt:80-81`, so the user never gets to see or modify the prompt on the detail screen.
- [ ] Approach: Keep the current flow (navigate to StudioScreen with pre-filled prompt) since the input dock is already there. The UX improvement is: (1) when tapping "Animate", navigate to StudioScreen but do NOT auto-generate. The user sees the image as a reference and the prompt in the input field. They can modify the prompt, then tap generate. (2) Add a secondary quick-action: a "Quick Animate" button or long-press on "Animate" that auto-generates without navigating back.
- [ ] Modify `StudioDetailScreen.kt:79-81`: remove `onNavigateBack()` from the `onAnimate` callback. Instead, just call `onAnimateMedia(asset.id)` and let the parent handle navigation. In `PocketCrewNavGraph.kt:279`, `onAnimateMedia` should navigate to Studio AND call the ViewModel method.
- [ ] Add `autoAnimate: Boolean = false` parameter to `onAnimateMedia`. When `false` (default), set the state but don't trigger generation. When `true`, trigger generation immediately after setting state.
- [ ] In `StudioDetailScreen.kt:329`, rename the "Animate" button label to "Animate" (keep as-is). The user modifies the prompt on the StudioScreen input dock. This is the primary flow.
- [ ] Optionally: add a second button "Quick Animate" that calls `onAnimateMedia(assetId, autoAnimate = true)` for one-tap animation.

**Contracts:** `UI_DESIGN_SPEC.md`, `CODE_STYLE_RULES.md`

**Dependency:** None — this modifies existing flow only.

---

### T3-H: Studio — Edit Video Support

**Summary:** Extend the existing "Edit" action to work on videos, not just images. Currently `onEditMedia` hard-codes `_mediaType.value = MediaCapability.IMAGE`.

**Files to modify:**
- `feature/studio/src/main/kotlin/.../MultimodalViewModel.kt` — modify `onEditMedia`
- `feature/studio/src/main/kotlin/.../StudioDetailScreen.kt` — show "Edit" button for videos too

**Implementation details:**

- [ ] Modify `onEditMedia` (`MultimodalViewModel.kt:364-386`): instead of always setting `_mediaType.value = MediaCapability.IMAGE`, check the asset's `mediaType`. If it's `VIDEO`, set `_mediaType.value = MediaCapability.VIDEO` and use `VideoGenerationSettings(referenceImageUri = asset.localUri)`. If `IMAGE`, keep current behavior.
- [ ] In `StudioDetailScreen.kt:327-334`, the "Edit" button already shows for all media types (line 327: `ActionItem(Icons.Default.Edit, "Edit", onEdit)` is outside the `if (mediaType == MediaCapability.IMAGE)` check). No UI change needed — it's already visible for videos.
- [ ] The real work is in `MultimodalViewModel`: the video generation pipeline must support reference video input. Currently `VideoGenerationSettings` has `referenceImageUri: String?` which is image-focused. Consider renaming to `referenceMediaUri` or adding a `referenceVideoUri` field. However, since the field is just a URI string, it can carry either an image or video URI — the backend interprets it. Keep the field name as-is for now to avoid a schema change.
- [ ] Update `startDetailVideoGeneration` (`MultimodalViewModel.kt:566-606`) to also accept a reference video URI (currently it only reads `asset.localUri` for images). The method should construct settings from the current `_settings.value` which already has the reference URI set by `onEditMedia`.
- [ ] Write tests: verify `onEditMedia` for video asset sets correct media type and settings

**Contracts:** `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`

**Dependency:** None for the ViewModel logic. The video generation backend (`VideoGenerationPortImpl`) is still a stub — this ticket makes the plumbing work so that when the backend is implemented, video editing flows end-to-end.

---

## Tier 4 — Deep Integration (API Backends)

### T4-A: Music Generation via Google API

**Summary:** Implement end-to-end music/song generation using Google's music generation API (Lyria via Vertex AI or MusicFX). This is a new capability — no port or implementation exists.

**Files to modify:**
- New file: `core/domain/src/main/kotlin/.../port/media/MusicGenerationPort.kt` — domain port
- New file: `core/domain/src/main/kotlin/.../usecase/media/GenerateMusicUseCase.kt` — use case
- New file: `feature/inference/src/main/kotlin/.../media/MusicGenerationPortImpl.kt` — API client
- `feature/inference/src/main/kotlin/.../di/MediaInferenceModule.kt` — bind port
- `feature/studio/src/main/kotlin/.../MultimodalViewModel.kt` — wire music generation into `generate()` flow
- `feature/studio/src/main/kotlin/.../StudioUiState.kt` — add music generation state if needed

**Implementation details:**

- [ ] **Research phase:** Investigate Google's music generation API availability. Check Vertex AI Generative AI documentation for Lyria or MusicFX endpoints. Determine: API surface (REST vs gRPC), authentication (API key vs service account), input format (text prompt + settings), output format (audio bytes or streaming), rate limits, pricing. Document findings in a brief research note.
- [ ] Create `MusicGenerationPort` interface in `:domain`: `suspend fun generateMusic(prompt: String, provider: MediaProviderAsset, settings: GenerationSettings): Result<ByteArray>` — mirrors `VideoGenerationPort` pattern (`VideoGenerationPort.kt:9-22`)
- [ ] Create `GenerateMusicUseCase` in `:domain` — follows same pattern as `GenerateVideoUseCase` (`GenerateVideoUseCase.kt:13-30`): resolve `ModelType.MUSIC_GENERATION` default assignment, get provider, call port
- [ ] Create `MusicGenerationPortImpl` in `:inference` — implement the actual API call to Google's endpoint. Handle: authentication, request construction, polling for async generation (if applicable), downloading result audio bytes, error handling with structured `Result.failure`.
- [ ] Add `@Binds` for `MusicGenerationPort` in `MediaInferenceModule.kt:14-27`
- [ ] In `MultimodalViewModel.generate()`, add a branch for `MediaCapability.MUSIC` that calls `generateMusicUseCase(prompt, settings)` instead of image/video generation. Save the resulting audio bytes via `studioRepository.saveMedia(bytes, prompt, "MUSIC", albumId)`.
- [ ] Extend `StudioVideoPlayer` or create `StudioAudioPlayer` composable for playing back generated music in the detail view. Alternatively, reuse the existing `AudioPlayerPort` for playback.
- [ ] Add `AUDIO` or `MUSIC` to `MediaCapability` enum if not already present (check — it may already be `MUSIC` since `MediaCapability.MUSIC` is used in the UI toggle at `MediaModeToggle.kt:48-53`)
- [ ] Write integration tests with mocked API responses

**Contracts:** `INFERENCE_RULES.md` (on-device only for inference, but music gen is cloud API — this is a media generation port, not an inference model, so it's allowed), `ARCHITECTURE_RULES.md`

**Risk:** Google's music generation API may have limited availability, require allowlisting, or be in preview. This ticket may be partially blocked on API access.

---

### T4-B: Video Generation Backend Implementation

**Summary:** Replace the stub `VideoGenerationPortImpl` with a real implementation using the configured media provider's video generation API (e.g., Google Veo, Stability AI, etc.).

**Files to modify:**
- `feature/inference/src/main/kotlin/.../media/VideoGenerationPortImpl.kt` — replace stub
- Potentially new files for API client helpers

**Implementation details:**

- [ ] **Research phase:** Investigate which video generation APIs the app's media provider system supports. Check `MediaProviderAsset` model (`core/domain/src/main/kotlin/.../model/config/`) to understand provider configuration. Determine the target API (Google Veo, Stability Video, etc.) and its video generation endpoint, auth, input format, output format, and async polling requirements.
- [ ] Replace `VideoGenerationPortImpl.generateVideo` (`VideoGenerationPortImpl.kt:8-15`) with real implementation
- [ ] Handle: authentication via provider's API key (from `MediaProviderAsset.credentialAlias`), request construction with prompt + settings (aspect ratio, duration, resolution), async operation (video gen is typically async — submit job, poll for completion, download result), timeout handling, structured error responses
- [ ] Use the existing `OkHttpClient` or `Ktor` client (check what the image generation port uses — `ImageGenerationPortImpl` — and follow the same pattern)
- [ ] Write unit tests with mocked HTTP responses covering: success, timeout, invalid prompt, rate limit, auth failure
- [ ] This unblocks T3-H (video editing) and T3-G (animation) for actual video generation

**Contracts:** `INFERENCE_RULES.md`, `ARCHITECTURE_RULES.md`

**Risk:** Similar to T4-A — API availability and access may be a blocker. The UI plumbing from T3-H/T3-G works independently of this.

---

## Summary: Tier Dependency Graph

```
Tier 1 (data/domain)          Tier 2 (shared infra)
├── T1-A: Album rename        ├── T2-A: Share adapter
├── T1-B: Album delete        ├── T2-B: Fullscreen viewer
└── T1-C: ShareMediaPort      └── T2-C: Mute toggle
         │                              │
         ▼                              ▼
Tier 3 (UI features — all parallel except noted)
├── T3-A: Gallery fullscreen detail     (needs T2-B, T2-C)
├── T3-B: Gallery share                 (needs T1-C, T2-A, T3-A)
├── T3-C: Gallery rename albums         (needs T1-A)
├── T3-D: Gallery delete albums         (needs T1-B)
├── T3-E: Gallery send to studio        (needs T3-A)
├── T3-F: Studio share                  (needs T1-C, T2-A)
├── T3-G: Custom animation prompt       (standalone)
└── T3-H: Edit video support            (standalone)
         │
         ▼
Tier 4 (API backends — sequential within tier)
├── T4-A: Music generation (Google API)
└── T4-B: Video generation backend
```

## Estimated Scope

| Tier | Tickets | Complexity | Parallelizable? |
|------|---------|------------|-----------------|
| Tier 1 | 3 | Low (data layer CRUD) | Fully |
| Tier 2 | 3 | Medium (shared components) | Fully |
| Tier 3 | 8 | Medium (UI + integration) | Mostly (T3-B and T3-E depend on T3-A) |
| Tier 4 | 2 | High (external API integration) | No (both touch MultimodalViewModel + MediaInferenceModule) |
