# Spec: DOCX math as Typst-rendered PNG + LaTeX source caption

## Objective

Replace the current DOCX `Block::Math` pipeline (placeholder paragraph + ZIP post-process OMML injection) with **rasterized equation images** produced by the same Typst + `latex_to_typst` stack used for PDF, plus a **plain-text caption** under each equation so users can copy the original LaTeX. Remove OMML/XML injection for math to avoid Word compatibility issues. Handle render failures gracefully without failing the whole document.

## File Structure

| Action | Path | Responsibility |
|--------|------|------------------|
| Create | `third_party/docgen_core/src/math_png.rs` | Build minimal Typst source from LaTeX + `latex_to_typst_math`, compile via `PocketCrewWorld`, rasterize first page to PNG bytes using `typst-render` (aligned to `typst` / `typst-pdf` versions in `Cargo.toml`). |
| Modify | `third_party/docgen_core/Cargo.toml` | Add `typst-render` with the **same exact version** as existing `typst` / `typst-pdf` / `typst-assets` entries. |
| Modify | `third_party/docgen_core/Cargo.lock` | Refresh lockfile after adding `typst-render`; no unrelated dependency updates. |
| Modify | `third_party/docgen_core/src/lib.rs` | Add `mod math_png;` during TDD red so module tests compile and run; replace the existing OMML math regression test with PNG/caption assertions; extend `DocGenError` only if new error variants are required (prefer reusing `GenerationError(String)`). |
| Modify | `third_party/docgen_core/src/docx_impl.rs` | Replace `Block::Math` handling: call `math_png::render_math_latex_to_png`, embed `Pic` like `Block::Image`, append caption paragraph; remove `math_blocks`, `inject_math_into_docx`, `apply_math_replacements`, `replace_math_placeholder`, `find_enclosing_para_start`, `build_math_para_xml`, and `xml_escape` if they become unused. |
| Modify | `third_party/docgen_core/src/lib.rs` (`#[cfg(test)] mod tests`) | Integration tests: DOCX zip contains `word/media/` PNG, `document.xml` references image relationship, caption substring present; raster smoke for `\frac{1}{2}`. |

## Data Models & Schemas

None. `Block::Math { display: bool, latex: String }` in `third_party/docgen_core/src/lib.rs` is unchanged. No Room / database schema changes.

## API Contracts & Interfaces

```rust
// third_party/docgen_core/src/math_png.rs (visibility pub(crate) unless tests require pub)

/// Renders a single math expression to a PNG (single page, tight layout).
/// - `latex`: LaTeX **after** JSON deserialization (i.e. what `serde` already put in `Block::Math.latex`).
/// - `display`: mirrors `Block::Math.display` — selects Typst display vs inline math wrapper in the generated `.typ` snippet.
/// Returns raw PNG bytes suitable for `image::load_from_memory` / `Pic::new_with_dimensions` path in `docx_impl.rs`.
pub(crate) fn render_math_latex_to_png(
    latex: &str,
    display: bool,
) -> Result<Vec<u8>, crate::DocGenError>;

pub(crate) const INVALID_MATH_LATEX_FOR_DOCX: &str = "\\frac{1}{";
```

**Raster implementation contract:** Use `typst_render::render(&document.pages[0], PIXELS_PER_POINT)` where `PIXELS_PER_POINT` is a named private constant set to `2.0` unless tests prove another value is required. Encode the returned `tiny-skia` pixmap with `encode_png()`; if that method is unavailable under resolved features, convert the pixmap RGBA bytes into an `image::RgbaImage` and write PNG with the existing `image` dependency. Return `DocGenError::GenerationError` for Typst diagnostics, empty `document.pages`, render encoding failures, or a panicked render thread.

**Typst source contract:** Generate a single-purpose source string, not a full document export. Use `#set page(width: auto, height: auto, margin: 4pt, fill: none)` plus a body text size that yields readable equations in Word. Render only the converted math expression, wrapped as `$ {typst_math} $` for display and `${typst_math}$` for inline. Do not include user LaTeX in Typst text outside math mode.

**Caption contract (DOCX, not Rust API):** Immediately following the image paragraph, emit one paragraph whose full visible text is exactly:

`LaTeX source: ` + `<contents of Block::Math.latex as received in Rust>`

(no translation of backslashes beyond what Rust already holds in `String`; use `docx_rs::Run::add_text` / `paragraph_from_markdown` only if escaping is handled—prefer `Run::add_text` with the exact concatenation so Word shows the same bytes the model intended after JSON parse).

**Alignment:** `display: true` → image paragraph uses `AlignmentType::Center` (match existing `Block::Image` centering pattern). `display: false` → image paragraph left-aligned (default) with picture height scaled to approximate 24pt body line height (exact px formula stated in implementation comments, not in this spec).

## Config / Permissions Delta

- **Cargo:** add `typst-render = "0.14.2"` while the existing Typst stack remains on `0.14.2`; if those versions have changed by implementation time, match the new exact Typst version instead of hard-coding `0.14.2`. **`typst-render` is Apache-2.0**; extend third-party attribution if the repo has a license inventory, otherwise document the new dependency in the PR description.
- **Binary size:** APK grows with `typst-render`; acceptable trade-off per product decision — document in PR description. No Android manifest permission changes.

## Constitution Audit

This design stays inside `third_party/docgen_core` (Rust library), does not touch `:domain` / Compose / Room, and does not modify `AGENTS.md` or `/contracts/`. Kotlin modules are unchanged unless a **separate** follow-up ticket later audits double JSON encoding at the artifact boundary.

## Execution Coordination

Downstream executors act as **coordinators first, implementers second**. If the execution environment explicitly authorizes subagents, use a child subagent scoped to one task at a time; otherwise execute the same tasks sequentially in the main thread. For each **Implementation Task** below:

1. **Assign or execute** one task at a time.
2. **Review** the task output against this document as the contract — accept nothing that contradicts the File Structure, API Contracts & Interfaces, the task **Files** lists, or the task-to-scenario bindings — before advancing to the next task.
3. **Repeat** task by task in order.

**Review checklist:**
- Paths touched match the task **Files** entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID for that task shows the intended test state for the current phase (red where failure is expected; green where pass is expected), per `test_spec.md`.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec — no substitute that embeds runnable tests or implementation bodies here.

---

## Implementation Tasks

### Task 1: Add failing DOCX + `math_png` tests (TDD Red)

**Files:**
- Modify: `third_party/docgen_core/src/lib.rs` (`#[cfg(test)] mod tests`)
- Create: `third_party/docgen_core/src/math_png.rs`

**Change:** Add `mod math_png;` to `lib.rs` now, not in Task 3, so the new module tests are compiled during red. In `math_png.rs`, define the production-signature stub `pub(crate) fn render_math_latex_to_png(...) -> Result<Vec<u8>, crate::DocGenError>` outside `#[cfg(test)]`; it must return `Err(DocGenError::GenerationError("math PNG renderer not implemented".to_string()))`, not `todo!()` / `unimplemented!()`, so the red phase fails through assertions rather than panics or compile errors. Define `INVALID_MATH_LATEX_FOR_DOCX` with the exact literal in the API contract above. Add a `#[cfg(test)]` unit test proving a valid expression such as `\\frac{1}{2}` should produce decodable PNG bytes.

In `lib.rs`, replace or rewrite `test_generate_docx_math_block_embeds_word_native_omath_text`; it currently asserts the legacy OMML behavior this feature removes. Add integration-style tests per `test_spec.md` scenarios **S1, S2, S3, S4, S5, E1, E2, M1, M2** using `generate_docx` JSON fixtures and ZIP inspection as specified. Initially happy-path PNG tests must fail because the stub returns `Err`; error-path tests may already pass after Task 4 only.

**Verifies scenarios:** S1, S2, S3, S4, S5, E1, E2, M1, M2

**Commit message:**

```
test(FEAT-docx-math-png-caption): red — DOCX math PNG + caption scenarios
```

### Task 2: Add `typst-render` dependency

**Files:**
- Modify: `third_party/docgen_core/Cargo.toml`
- Modify: `third_party/docgen_core/Cargo.lock`

**Change:** Add `typst-render` with the exact version matching the crate’s existing `typst` / `typst-pdf` / `typst-assets` line (currently `0.14.2`; no version drift). Use default features unless compilation proves a narrower feature set is required. Refresh only the docgen lockfile.

**Verifies scenarios:** *(dependency only — unblocks Task 3)*

**Commit message:**

```
build(FEAT-docx-math-png-caption): add typst-render for math PNG rasterization
```

### Task 3: Implement `math_png::render_math_latex_to_png`

**Files:**
- Modify: `third_party/docgen_core/src/math_png.rs` (implement production `render_math_latex_to_png`; keep tests)
- Modify: `third_party/docgen_core/src/lib.rs` only if Task 1 was not completed exactly as written

**Change:** Implement `render_math_latex_to_png` per **API Contracts**. Keep `INVALID_MATH_LATEX_FOR_DOCX` ASCII-only and free of `&`, `<`, and `>` so raw DOCX XML assertions remain stable. Return `Err` before Typst compilation when `latex.trim().is_empty()`. Build a **single-page** Typst source string as described in the Typst source contract. Compile with `typst::compile::<typst::layout::PagedDocument>` using `PocketCrewWorld::new(source, HashMap::new())` (no extra binary files). Rasterize **page 1** with `typst-render` into PNG bytes. Run compilation and rasterization on a **thread with enlarged stack** (reuse the same `8 * 1024 * 1024` pattern as `typst_gen::compile_to_pdf`) to avoid stack overflow on Android-class stacks.

**Verifies scenarios:** M1, M2, S4 (unit-level), partial S1/S2

**Commit message:**

```
feat(FEAT-docx-math-png-caption): rasterize math to PNG via Typst
```

### Task 4: DOCX — embed PNG + caption for `Block::Math`

**Files:**
- Modify: `third_party/docgen_core/src/docx_impl.rs`

**Change:** In `build_docx_bytes`, replace the `Block::Math` arm: call `math_png::render_math_latex_to_png(latex, *display)` unless `latex.trim().is_empty()`. On `Ok(png_bytes)`, decode dimensions via `image::load_from_memory`, construct `Pic::new_with_dimensions` exactly like the successful branch of `Block::Image`, and apply centering when `display == true`. For inline math (`display == false`), override `pic.size` to approximately 24pt line height while preserving aspect ratio: compute target height in pixels as `24.0 * 96.0 / 72.0`, derive width from decoded aspect ratio, then convert both through `docx_rs::from_px`; document this formula in an implementation comment.

Append a second paragraph: body font, size **20** half-points, Consolas, gray color **`808080`** if `docx_rs` supports `.color()` on `Run`, else omit color; text **exactly** `LaTeX source: ` concatenated with `latex` as stored in `Block::Math`. Implement a small private helper such as `math_caption_paragraph(latex: &str, include_failure_marker: bool) -> Paragraph` to keep success and failure paths byte-for-byte consistent. On `Err(_)`, undecodable PNG bytes, or empty trimmed input, emit the caption paragraph with `[Equation render failed]` in the same paragraph or the immediately following paragraph — **no panic, `build_docx_bytes` remains `Ok`**.

**Verifies scenarios:** S1, S2, S4, S5, E1, E2

**Commit message:**

```
feat(FEAT-docx-math-png-caption): embed math PNG and LaTeX caption in DOCX
```

### Task 5: Remove math ZIP post-processing

**Files:**
- Modify: `third_party/docgen_core/src/docx_impl.rs`

**Change:** Delete `math_blocks` / `math_count` state, the `inject_math_into_docx` call after `pack`, and the functions `inject_math_into_docx`, `apply_math_replacements`, `replace_math_placeholder`, `find_enclosing_para_start`, `build_math_para_xml`, and `xml_escape` if unused. `build_docx_bytes` returns the `docx.build().pack` bytes directly without math-only ZIP rewriting.

**Verifies scenarios:** S3

**Commit message:**

```
refactor(FEAT-docx-math-png-caption): remove OMML placeholder ZIP pass for math
```

### Task 6: Document JSON / LaTeX string contract

**Files:**
- Modify: `third_party/docgen_core/src/math_png.rs` (module-level `//!` comment)

**Change:** Document that `render_math_latex_to_png` **does not** attempt to fix double-JSON-encoded backslashes; callers must pass correct Rust `String` content. Reference `test_spec.md` scenario **S4** for intentional `\\` preservation.

**Verifies scenarios:** S4 (documentation only)

**Commit message:**

```
docs(FEAT-docx-math-png-caption): document LaTeX string contract for math PNG
```

---

## Optional follow-up (out of scope for this ticket)

- **Kotlin / `append_blocks` audit:** If telemetry or fixtures show `latex` still contains doubled `\\` after `Json.decode`, fix at serialization boundary in `:core:data` or tool executor — **not** in `math_png` heuristics.

---

## Executable Validation

Run these before considering implementation complete:

1. `cargo test --manifest-path third_party/docgen_core/Cargo.toml`
2. `third_party/docgen_core/build-android.sh`
3. `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:docgen:compileDebugKotlin --no-daemon --stacktrace`

For a docs-only update to this plan, no executable validation is required beyond Markdown/diff review.

---

### PLAN COMPLETE

* spec.md:      `plans/FEAT-docx-math-png-caption/spec.md`      (6 tasks)
* test_spec.md: `plans/FEAT-docx-math-png-caption/test_spec.md` (9 scenarios + mutation defense)
* Self-review:  UPDATED after codebase review; known legacy OMML test and lockfile gaps addressed

**HUMAN GATE:** Approve `spec.md` and `test_spec.md` before TDD Red or implementation.
