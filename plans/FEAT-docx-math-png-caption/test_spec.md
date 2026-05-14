# Test Spec: DOCX math as Typst-rendered PNG + LaTeX source caption

## Test Framework & Run Command

**Framework:** Rust `#[test]` in `third_party/docgen_core` (project convention for the `docgen_core` crate; JVM `UNIT_TESTING.md` / JUnit applies to Android modules, not this crate).

**Run:** `cargo test --manifest-path third_party/docgen_core/Cargo.toml`

## Scenario Index

- **S1** — Display math produces PNG media + centered image + exact caption prefix and body
- **S2** — Inline math produces PNG + caption
- **S3** — Generated DOCX has no `MATHBLOCK_` placeholder substrings and no self-closing `<m:oMath/>` from old math inject
- **E1** — Intentionally invalid Typst/LaTeX still yields a successful `generate_docx` with caption and `[Equation render failed]` marker
- **E2** — Empty `latex` yields `Ok` DOCX with `[Equation render failed]` and `LaTeX source: ` substring
- **S4** — Caption preserves two consecutive backslashes between `a` and `b` when Rust `latex` is `a\\b`
- **S5** — DOCX relationship file contains a valid image relationship referenced from `document.xml`
- **M1** — Raster width proves real render (mutation defense)
- **M2** — Same-input renders have stable decoded dimensions

## Happy Path Scenarios

### S1: Display math — PNG, media file, caption

- **Given:** JSON document `version` `"1.0"`, `metadata.title` `"T"`, `metadata.author` `null`, `blocks` a single element `{ "type": "math", "display": true, "latex": "E = mc^2" }`
- **When:** `generate_docx` is called with the JSON string (pretty or minified, valid UTF-8)
- **Then:** Returned bytes are a ZIP whose entry `word/document.xml` contains neither the substring `MATHBLOCK_0` nor `<m:oMath`, AND the ZIP contains at least one entry under `word/media/` whose name matches the regex `image[0-9]+\\.png`, AND `word/document.xml` contains the exact substring `LaTeX source: E = mc^2`, AND `word/document.xml` contains the substring `w:jc w:val="center"` at least once

### S2: Inline math — PNG + caption

- **Given:** Same as S1 except `"display": false` and `"latex": "x^2"`
- **When:** `generate_docx` is invoked
- **Then:** The ZIP contains at least one `word/media/image*.png` entry AND `word/document.xml` contains the exact substring `LaTeX source: x^2`

### S3: No legacy math placeholder or OMML inject artifacts

- **Given:** Same document as S1
- **When:** `generate_docx` is invoked
- **Then:** `word/document.xml` does not contain the substring `MATHBLOCK_` at all AND does not contain the substring `<m:oMath`

### S4: Caption preserves two consecutive backslashes

- **Given:** JSON `blocks` single math with `"display": true` and `latex` such that the deserialized Rust `String` contains, in order: `a`, U+005C, U+005C, `b` (achieved in JSON source with `"latex": "a\\\\b"` — four backslashes in the JSON file produce two backslashes in Rust)
- **When:** `generate_docx` is invoked
- **Then:** Parsing `word/document.xml` as XML and reading the **concatenated text** of all `w:t` elements yields a string that contains as a substring exactly `LaTeX source: ` immediately followed by `a`, then U+005C, then U+005C, then `b` with no characters between those five parts

### S5: DOCX image relationship integrity

- **Given:** Same document as S1
- **When:** `generate_docx` is invoked
- **Then:** `word/document.xml` contains an image drawing with an `r:embed="rId..."` relationship id, AND `word/_rels/document.xml.rels` contains a relationship with that same id whose `Type` contains `/image` and whose `Target` points to `media/image*.png`, AND the target entry exists in the ZIP

## Error & Edge Case Scenarios

### E1: Render failure — document still succeeds

- **Given:** JSON with one math block with `"display": true` and `latex` set to the **same** string constant used in `math_png` unit test `invalid_latex_fixture` (defined in `third_party/docgen_core/src/math_png.rs` as `pub(crate) const INVALID_MATH_LATEX_FOR_DOCX: &str = "..."` — the literal value must be chosen so `render_math_latex_to_png(INVALID_MATH_LATEX_FOR_DOCX, true)` returns `Err` in CI)
- **When:** `generate_docx` is invoked
- **Then:** Return is `Ok(Vec<u8>)` AND `word/document.xml` contains the exact substring `[Equation render failed]` AND contains the exact substring `LaTeX source: ` immediately followed by the verbatim characters of `INVALID_MATH_LATEX_FOR_DOCX` (no XML entity substitution in the test assertion beyond what `docx-rs` emits — the constant must be chosen from ASCII set listed in Task 3 so raw substring search on `document.xml` succeeds)

### E2: Empty latex

- **Given:** JSON with one math block `"display": true`, `latex` `""`
- **When:** `generate_docx` is invoked
- **Then:** Return is `Ok(Vec<u8>)` AND `word/document.xml` contains the exact substring `[Equation render failed]` AND contains the exact substring `LaTeX source: `

## Mutation Defense

### Lazy Implementation Risk

An implementation that **always** writes a fixed 1×1 transparent PNG (or a constant prebuilt PNG) for every equation without running Typst would pass S1–S3 string checks if those checks only looked for “any PNG in media”.

### M1: Defense Scenario

- **Given:** JSON with one math block `"display": true`, `latex` `"\\frac{1}{2}"` (JSON string with doubled backslashes so Rust `String` is `\frac{1}{2}` — i.e. JSON literal `"\\\\frac{1}{2}"`)
- **When:** `generate_docx` is invoked and the first `word/media/image*.png` entry is extracted and decoded with the Rust `image` crate to dimensions `(w, h)`
- **Then:** `w >= 40` AND `h >= 20` (literal integers; a 1×1 pixel stub fails)

### M2: Same-input determinism

- **Given:** JSON with one display math block whose Rust `latex` is `\frac{1}{2}`
- **When:** `generate_docx` is invoked twice in the same process
- **Then:** Each DOCX contains a PNG entry whose decoded dimensions are identical across both runs. Do not require byte-for-byte DOCX equality because ZIP metadata and relationship numbering may differ.

## Existing Test Replacement

- Replace `test_generate_docx_math_block_embeds_word_native_omath_text` in `third_party/docgen_core/src/lib.rs`; it asserts the removed OMML behavior and must not survive this feature unchanged.
- Keep paragraph inline dollar tests unchanged. This feature changes `Block::Math` DOCX rendering only; markdown inline `$...$` inside paragraph text should still render as visible code-span text in DOCX.

---

**HUMAN GATE:** User approves this file and `spec.md` before TDD Red.
