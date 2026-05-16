#!/usr/bin/env python3
"""AGENT-050 — schema_hint.required_fields must exist on Kotlin ToolParameters classes."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "third_party/agent_core/resources/tool_catalog.json"
TOOL_DEF = (
    ROOT
    / "core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ToolDefinition.kt"
)
INFERENCE_MODEL = (
    ROOT
    / "core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference"
)

# Map tool name -> (kotlin file stem or full filename, data class name)
STATIC_MAP: dict[str, tuple[str, str]] = {
    "start_artifact": ("ArtifactGenerationLoopParams.kt", "StartArtifactParams"),
    "append_blocks": ("ArtifactGenerationLoopParams.kt", "AppendBlocksParams"),
    "manage_memories": ("ToolParameterModels.kt", "ManageMemoriesParams"),
}


def parse_tool_parameters_classes(src: str) -> dict[str, str]:
    """tool_name -> ParametersClassName from ToolDefinition blocks."""
    out: dict[str, str] = {}
    for m in re.finditer(
        r'name = "([^"]+)"[^;]{0,400}?parametersClass = ([A-Za-z0-9_]+)::class',
        src,
        re.DOTALL,
    ):
        out[m.group(1)] = m.group(2)
    return out


def kotlin_properties(text: str, class_name: str) -> set[str]:
    m = re.search(
        rf"data class {re.escape(class_name)}\s*\((.*?)\)\s*(?::|\))",
        text,
        re.DOTALL,
    )
    if not m:
        raise ValueError(f"Could not find data class {class_name}")
    body = m.group(1)
    props: set[str] = set()
    for line in body.splitlines():
        line = line.strip()
        if not line or line.startswith("//") or line.startswith("@") or line.startswith("*"):
            continue
        # val foo: or val foo =
        vm = re.match(r"val\s+([a-zA-Z0-9_]+)\s*:", line)
        if vm:
            props.add(vm.group(1))
    return props


def find_class_file(class_name: str) -> Path | None:
    for p in INFERENCE_MODEL.rglob("*.kt"):
        if p.name.endswith(".kt") and f"data class {class_name}" in p.read_text(encoding="utf-8"):
            return p
    return None


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    tool_src = TOOL_DEF.read_text(encoding="utf-8")
    name_to_params = parse_tool_parameters_classes(tool_src)

    errors: list[str] = []
    for tool in catalog.get("tools", []):
        tname = tool.get("name")
        hint = tool.get("schema_hint") or {}
        req = hint.get("required_fields") or []
        if not req:
            continue
        if tname in STATIC_MAP:
            file_name, class_name = STATIC_MAP[tname]
            path = INFERENCE_MODEL / file_name
        else:
            class_name = name_to_params.get(tname or "")
            if not class_name:
                errors.append(f"{tname}: no parametersClass mapping in ToolDefinition.kt")
                continue
            path = find_class_file(class_name)
            if path is None:
                errors.append(f"{tname}: Kotlin file for {class_name} not found")
                continue
        text = path.read_text(encoding="utf-8")
        try:
            props = kotlin_properties(text, class_name)
        except ValueError as e:
            errors.append(f"{tname}: {e}")
            continue
        for field in req:
            fname = field.get("name")
            if fname and fname not in props:
                errors.append(
                    f"{tname}: schema_hint requires field '{fname}' "
                    f"but {class_name} ({path.name}) has {sorted(props)}",
                )

    if errors:
        print("Catalog / Kotlin schema_hint parity FAILED:\n", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print("OK: catalog schema_hint fields match Kotlin ToolParameters classes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
