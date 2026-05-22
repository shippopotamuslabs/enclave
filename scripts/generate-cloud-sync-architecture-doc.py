#!/usr/bin/env python3
"""Generate docs/cloud_sync_architecture.docx (stdlib only, no python-docx)."""

from __future__ import annotations

import html
import zipfile
from datetime import date
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
OUTPUT = PROJECT_ROOT / "docs" / "cloud_sync_architecture.docx"

# Minimal WordprocessingML body fragments (escaped plain text in w:t).
def para(text: str, style: str | None = None) -> str:
    style_xml = ""
    if style:
        style_xml = f'<w:pPr><w:pStyle w:val="{style}"/></w:pPr>'
    escaped = html.escape(text)
    return f"<w:p>{style_xml}<w:r><w:t xml:space=\"preserve\">{escaped}</w:t></w:r></w:p>"


def heading(text: str, level: int) -> str:
    return para(text, f"Heading{level}")


def bullet(text: str) -> str:
    return (
        "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/>"
        "<w:numId w:val=\"1\"/></w:numPr></w:pPr>"
        f"<w:r><w:t xml:space=\"preserve\">{html.escape(text)}</w:t></w:r></w:p>"
    )


SECTIONS: list[str] = []

def add(*parts: str) -> None:
    SECTIONS.extend(parts)


add(
    heading("Pocket Crew Cloud Sync Architecture", 1),
    para(
        "How Firebase Cloud Functions, Firestore, and the Android data layer keep "
        "Room in sync through a server-sequenced event feed."
    ),
    para(f"Generated: {date.today().isoformat()}"),
    heading("Table of Contents", 2),
    para("1. Architecture Overview"),
    para("2. Firestore Data Model"),
    para("3. Cloud Functions (Event Feed Writer)"),
    para("4. Android Replay and Live Sync"),
    para("5. Conflict Policy and Pending Upload"),
    para("6. Security Rules"),
    para("7. Entry Points and Lifecycle"),
    para("8. Module Map"),
    para("9. Summary"),
    heading("1. Architecture Overview", 1),
    para(
        "Pocket Crew sync replaced per-collection Firestore snapshot listeners with a "
        "single ordered feed per user. Entity documents under users/{uid}/ remain the "
        "canonical encrypted payloads; sync_events rows are pointers and ordering metadata only."
    ),
    para("The key architectural principle: Room is the local source of truth; the cloud feed is authoritative for ordering."),
    bullet("Server: Firestore triggers append monotonic sync_events after each allowlisted write."),
    bullet("Client: Replay missed events, then attach one live listener on sync_events."),
    bullet("Apply path: Fetch source documents, resolve parent dependencies, upsert/delete in Room inside a transaction, then advance lastAppliedSequence."),
    heading("2. Firestore Data Model", 1),
    heading("Server-owned collections (per user)", 2),
    bullet("users/{uid}/sync_meta/state — nextSequence, schemaVersion, updatedAtMillis"),
    bullet("users/{uid}/sync_events/{paddedSequence} — operation, collectionPath, entityId, entityPath, deviceId, triggerEventId, …"),
    bullet("users/{uid}/sync_processed_events/{eventIdHash} — idempotency ledger for Cloud Functions trigger IDs"),
    heading("Entity collections (allowlisted)", 2),
    para(
        "chats, messages, chat_summaries, memories, vision_analysis, message_files, "
        "web_search_sources, message_transcript_segments, documents, api_credentials, "
        "api_keys, api_model_configs, local_models, local_model_configs, default_models, "
        "tts_providers, media_providers, studio_albums, studio_gallery"
    ),
    heading("Client metadata on outbound writes", 2),
    bullet("_sync_device_id — stable device ID from DataStore"),
    bullet("_sync_client_mutation_id — per-upload idempotency hint"),
    bullet("_sync_written_at — upload timestamp"),
    heading("3. Cloud Functions (Event Feed Writer)", 1),
    heading("handleSyncEntityWriteTrigger", 2),
    para(
        "Firestore onWrite on users/{uid}/{collectionPath}/{entityId}. Non-allowlisted "
        "paths are ignored. Each invocation runs inside firestore.runTransaction():"
    ),
    bullet("Read sync_processed_events/{hash(triggerEventId)}; exit if already processed."),
    bullet("Read sync_meta/state for nextSequence."),
    bullet("On delete triggers, read the live entity document (stale-delete guard)."),
    bullet("Emit upsert or delete event; write sync_events, processed marker, and meta atomically."),
    heading("Stale delete protection", 2),
    para(
        "If a delete trigger arrives but the source document still exists (recreate race), "
        "the handler emits an upsert for the current document instead of a destructive delete."
    ),
    heading("Exported functions (functions/src/index.ts)", 2),
    bullet("handleSyncEntityWriteTrigger — sync event feed"),
    bullet("uploadSharedChat, share — shared chat hosting (unchanged)"),
    bullet("cleanupExpiredShares — scheduled maintenance"),
    heading("4. Android Replay and Live Sync", 1),
    heading("StartCloudSyncListeningUseCase", 2),
    para("Post-unlock ordering enforced in core/domain:"),
    bullet("Optional syncAllLocalDataToCloud() when uploadLocalFirst is true."),
    bullet("Loop syncRemoteChangesFromCloud() until hasMore is false (paged replay, default page size 200)."),
    bullet("startListening() only after upload and replay succeed."),
    heading("FirestoreSyncEventApplier", 2),
    bullet("Ignore events with sequence <= lastAppliedSequence."),
    bullet("Resolve parents via FirestoreSyncDependencyGraph before child upsert."),
    bullet("Missing required parent → IllegalStateException; sequence not advanced."),
    bullet("Room transaction applies parents then child; then setLastAppliedSequence."),
    heading("FirestoreListenerCoordinator", 2),
    para(
        "Exactly one snapshot listener on users/{uid}/sync_events with "
        "whereGreaterThan(sequence, lastApplied). No listeners on chats, messages, or other entity collections."
    ),
    heading("5. Conflict Policy and Pending Upload", 1),
    para("FirestoreSyncRemoteApplyPolicy (core/data):"),
    bullet("Dirty local row (pendingSync != 0) + remote from another device → preserve local, skip upsert."),
    bullet("Dirty local + remote with null deviceId (legacy) → preserve local."),
    bullet("Dirty local + same _sync_device_id → allow echo; adapter merge may clear pendingSync."),
    bullet("Clean local → apply remote in sequence order."),
    heading("6. Security Rules (firestore.rules)", 1),
    bullet("Owner may read own sync_events; client writes denied."),
    bullet("sync_meta and sync_processed_events: client read/write denied."),
    bullet("Allowlisted entity collections: owner read/write under users/{uid}/."),
    heading("7. Entry Points and Lifecycle", 1),
    bullet("MainViewModel — vault reconciliation → StartCloudSyncListeningUseCase(uploadLocalFirst=true)"),
    bullet("SettingsGeneralDelegate — reauth/adopt paths with appropriate uploadLocalFirst flag"),
    bullet("CloudSyncPort implemented by FirestoreDataSource; domain never sees Firebase types"),
    heading("8. Module Map", 1),
    bullet("functions/src/syncEventFeed.ts — allowlist, pure handler, production trigger"),
    bullet("core/domain — CloudSyncPort, CloudSyncEventReplayResult, StartCloudSyncListeningUseCase"),
    bullet("core/data/remote/firestore — codec, feed client, applier, dependency graph, state store, production wiring"),
    bullet("core/data/remote/firestore/handlers — EntitySyncAdapter implementations (no placeholder inserts on feed path)"),
    heading("9. Summary", 1),
    para(
        "Sync is event-sourced at the cloud boundary: every allowlisted write becomes one "
        "sequenced feed entry; Android consumes that feed deterministically into Room. "
        "Triggers are transactional and idempotent; the client replays before listening and "
        "never hydrates from per-collection listeners during normal operation."
    ),
)

BODY = "".join(SECTIONS)

DOCUMENT_XML = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
    {BODY}
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>
"""

CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>
"""

RELS_ROOT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>
"""

RELS_DOC = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"""

STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:rPr><w:b/><w:sz w:val="32"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
  </w:style>
</w:styles>
"""

CORE_PROPS = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
  xmlns:dc="http://purl.org/dc/elements/1.1/">
  <dc:title>Pocket Crew Cloud Sync Architecture</dc:title>
  <dc:creator>Pocket Crew</dc:creator>
</cp:coreProperties>
"""


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", CONTENT_TYPES)
        zf.writestr("_rels/.rels", RELS_ROOT)
        zf.writestr("word/document.xml", DOCUMENT_XML)
        zf.writestr("word/_rels/document.xml.rels", RELS_DOC)
        zf.writestr("word/styles.xml", STYLES)
        zf.writestr("docProps/core.xml", CORE_PROPS)
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
