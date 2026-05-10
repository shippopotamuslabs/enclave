# Rust Document Generation & Extraction Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `docgen_core` Rust library for document generation (DOCX, PDF) and extraction (PDF, DOCX, XLSX) with UniFFI support and JSON schema validation.

**Architecture:** A core library defining a `Document` model with various `Block` types. It uses `serde` for serialization, `schemars` for schema generation, and specialized crates like `docx-rs`, `typst`/`printpdf`, `lopdf`, and `calamine` for document handling. Functions are exported via UniFFI for cross-language compatibility.

**Tech Stack:** Rust, Serde, Schemars, JsonSchema, Pulldown-CMark, Docx-rs, Typst/Printpdf, UniFFI, Lopdf, Calamine.

---

### Task 1: Update Cargo.toml and Project Configuration

**Files:**
- Modify: `docgen_core/Cargo.toml`
- Create: `docgen_core/build.rs`

- [ ] **Step 1: Update Cargo.toml with dependencies and library types**

Update `docgen_core/Cargo.toml`:
```toml
[package]
name = "docgen_core"
version = "0.1.0"
edition = "2024"

[lib]
crate-type = ["cdylib", "staticlib", "rlib"]

[dependencies]
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
schemars = "0.8"
jsonschema = "0.29.0"
pulldown-cmark = "0.13.0"
docx-rs = "0.4.15"
printpdf = "0.7.0"
uniffi = { version = "0.29.0", features = ["cli"] }
lopdf = "0.35.0"
calamine = "0.26.1"
anyhow = "1.0"
thiserror = "2.0"

[build-dependencies]
uniffi = { version = "0.29.0", features = ["build"] }
```

- [ ] **Step 2: Create build.rs for UniFFI**

Create `docgen_core/build.rs`:
```rust
fn main() {
    uniffi::generate_scaffolding("src/docgen.udl").expect("Generating scaffolding failed");
}
```

- [ ] **Step 3: Create docgen.udl for UniFFI (Initial version)**

Create `docgen_core/src/docgen.udl`:
```udl
namespace docgen {
    string get_schema();
    void validate_document(string json);
    sequence<u8> generate_docx(string json);
    sequence<u8> generate_pdf(string json);
    string extract_text(sequence<u8> bytes, string mime_type);
};
```

- [ ] **Step 4: Verify project compiles with new dependencies**

Run: `cargo build` in `docgen_core`
Expected: Successful compilation of dependencies.

### Task 2: Define Data Models and Schema Generation

**Files:**
- Modify: `docgen_core/src/lib.rs`

- [ ] **Step 1: Define Document and Block models**

Implement models in `docgen_core/src/lib.rs` with `Serialize`, `Deserialize`, and `JsonSchema` derives.

- [ ] **Step 2: Implement get_schema and validate_document**

- [ ] **Step 3: Write tests for schema and validation**

- [ ] **Step 4: Verify tests pass**

Run: `cargo test`

### Task 3: Implement Document Generation (DOCX)

**Files:**
- Modify: `docgen_core/src/lib.rs`

- [ ] **Step 1: Implement generate_docx using docx-rs**

Map `Block` variants to `docx-rs` elements. Use `pulldown-cmark` for markdown parsing.

- [ ] **Step 2: Write tests for DOCX generation**

- [ ] **Step 3: Verify tests pass**

Run: `cargo test`

### Task 4: Implement Document Generation (PDF)

**Files:**
- Modify: `docgen_core/src/lib.rs`

- [ ] **Step 1: Implement generate_pdf using printpdf or typst**

- [ ] **Step 2: Write tests for PDF generation**

- [ ] **Step 3: Verify tests pass**

Run: `cargo test`

### Task 5: Implement Text Extraction

**Files:**
- Modify: `docgen_core/src/lib.rs`

- [ ] **Step 1: Implement extract_text for PDF, DOCX, XLSX**

Use `lopdf`, `docx-rs` (reading support), and `calamine`.

- [ ] **Step 2: Write tests for text extraction**

- [ ] **Step 3: Verify tests pass**

Run: `cargo test`

### Task 6: Final UniFFI Integration and Cleanup

**Files:**
- Modify: `docgen_core/src/lib.rs`
- Modify: `docgen_core/src/docgen.udl`

- [ ] **Step 1: Finalize UniFFI exports**

Use `uniffi::setup_scaffolding!()` and ensure all functions match the UDL.

- [ ] **Step 2: Run final validation and tests**

Run: `cargo build && cargo test`
