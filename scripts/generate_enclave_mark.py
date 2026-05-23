#!/usr/bin/env python3
"""Regenerate Android vector drawables from the canonical Inkscape export.

Source: enclave_e_source.svg (derived from ~/Pictures/Enclave/E.svg).
Edit the source SVG, then run: python3 scripts/generate_enclave_mark.py
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "enclave_e_source.svg"

CREAM = "#F2F1EC"
ACCENT = "#FF8A00"
VIEW_W = 183.0
VIEW_H = 238.0

# Launcher: scale E to fit 108×108 safe zone and center in viewport.
LAUNCHER_VIEWPORT = 108.0
LAUNCHER_SAFE = 92.0


def _parse_paths(svg_path: Path) -> list[tuple[str, str]]:
    tree = ET.parse(svg_path)
    root = tree.getroot()
    ns = {"svg": "http://www.w3.org/2000/svg"}
    paths: list[tuple[str, str]] = []
    for elem in root.findall(".//svg:path", ns):
        fill = elem.get("fill", CREAM).upper()
        d = elem.get("d")
        if not d:
            continue
        # Skip background rects if re-exported from Inkscape with a backdrop.
        if fill in {"#0D0D0D", "#000000", "#0F0F10", "#18181B"}:
            continue
        color = ACCENT if fill not in {CREAM.upper(), "#E7E6E1"} else CREAM
        # Android pathData: comma after M/x/y groups is optional; keep decimals.
        d_android = re.sub(r"(\d)\s+(-?\d)", r"\1,\2", d.strip())
        paths.append((color, d_android))
    if not paths:
        raise SystemExit(f"No paths found in {svg_path}")
    return paths


def _vector_xml(
    paths: list[tuple[str, str]],
    *,
    width_dp: str,
    height_dp: str,
    group: bool = False,
) -> str:
    path_lines = []
    for color, d in paths:
        path_lines.append(
            f'    <path\n        android:fillColor="{color}"\n'
            f'        android:pathData="{d}"/>'
        )
    body = "\n".join(path_lines)
    if group:
        scale = LAUNCHER_SAFE / max(VIEW_W, VIEW_H)
        scaled_w = VIEW_W * scale
        scaled_h = VIEW_H * scale
        tx = (LAUNCHER_VIEWPORT - scaled_w) / 2.0
        ty = (LAUNCHER_VIEWPORT - scaled_h) / 2.0
        body = (
            f'  <group\n      android:translateX="{tx:.2f}"\n'
            f'      android:translateY="{ty:.2f}"\n'
            f'      android:scaleX="{scale:.4f}"\n'
            f'      android:scaleY="{scale:.4f}">\n'
            f"{body}\n  </group>"
        )
    return f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width_dp}"
    android:height="{height_dp}"
    android:viewportWidth="{VIEW_W:.0f}"
    android:viewportHeight="{VIEW_H:.0f}">
{body}
</vector>
"""


def main() -> None:
    paths = _parse_paths(SOURCE)
    aspect_w = round(108 * VIEW_W / VIEW_H)

    chat_icon = _vector_xml(paths, width_dp=f"{aspect_w}dp", height_dp="108dp")
    launcher = _vector_xml(paths, width_dp="108dp", height_dp="108dp", group=True)
    mono = launcher.replace(CREAM, "#FFFFFF").replace(ACCENT, "#FFFFFF")

    (ROOT / "feature/chat/src/main/res/drawable/enclave_icon.xml").write_text(
        chat_icon, encoding="utf-8"
    )
    (ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").write_text(
        launcher, encoding="utf-8"
    )
    (ROOT / "app/src/main/res/drawable/ic_launcher_monochrome.xml").write_text(
        mono, encoding="utf-8"
    )
    (ROOT / "enclave_mark_e.svg").write_text(SOURCE.read_text(encoding="utf-8"), encoding="utf-8")
    (ROOT / "enclave_icon_no_background.svg").write_text(
        SOURCE.read_text(encoding="utf-8"), encoding="utf-8"
    )
    print(f"Wrote drawables from {SOURCE.name} ({len(paths)} paths)")


if __name__ == "__main__":
    main()
