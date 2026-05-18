#!/usr/bin/env python3
"""Render coverage outputs from Kover XML reports.

Outputs:
  build/reports/kover/html/badge.json   shields.io endpoint JSON for the overall %
  build/reports/kover/comment.md        markdown table with per-module Unicode bars

Inputs:
  build/reports/kover/report.xml        aggregated report (root)
  <module>/build/reports/kover/report.xml  per-module reports
"""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

BAR_WIDTH = 10
BAR_FULL = "█"   # full block
BAR_EMPTY = "░"  # light shade

MODULES: list[tuple[str, str]] = [
    (":core", "core/build/reports/kover/report.xml"),
    (":server", "server/build/reports/kover/report.xml"),
    (":app:shared", "app/shared/build/reports/kover/report.xml"),
]
AGGREGATED = "build/reports/kover/report.xml"


@dataclass
class Coverage:
    covered: int
    total: int

    @property
    def pct(self) -> float:
        return (self.covered * 100 / self.total) if self.total else 0.0


def parse(path: str) -> Coverage | None:
    p = Path(path)
    if not p.exists():
        return None
    root = ET.parse(p).getroot()
    # Counters that are direct children of <report> hold totals.
    counters = {c.attrib["type"]: c.attrib for c in root.findall("./counter")}
    line = counters.get("LINE", {"missed": "0", "covered": "0"})
    covered = int(line["covered"])
    missed = int(line["missed"])
    return Coverage(covered=covered, total=covered + missed)


def bar(pct: float) -> str:
    # 10 segments, one per 10%. Coverage of 99.9% still shows 9 filled
    # segments — only a perfect 100% lights all 10.
    filled = int(pct // 10)
    if filled > BAR_WIDTH:
        filled = BAR_WIDTH
    return BAR_FULL * filled + BAR_EMPTY * (BAR_WIDTH - filled)


def color(pct: float) -> str:
    if pct >= 90:
        return "brightgreen"
    if pct >= 80:
        return "green"
    if pct >= 70:
        return "yellow"
    if pct >= 50:
        return "orange"
    return "red"


def write_badge(total: Coverage, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "schemaVersion": 1,
        "label": "coverage",
        "message": f"{total.pct:.1f}%",
        "color": color(total.pct),
    }))


def write_comment(per_module: list[tuple[str, Coverage]], total: Coverage | None, out: Path) -> None:
    lines = [
        "## Coverage",
        "",
        "| Module | Coverage | Lines |",
        "|---|---|---|",
    ]
    for name, cov in per_module:
        lines.append(
            f"| `{name}` | `{bar(cov.pct)}` {cov.pct:.1f}% | {cov.covered} / {cov.total} |"
        )
    if total is not None:
        lines.append(
            f"| **Total** | `{bar(total.pct)}` **{total.pct:.1f}%** | "
            f"**{total.covered} / {total.total}** |"
        )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n")


def main() -> int:
    per_module: list[tuple[str, Coverage]] = []
    for name, path in MODULES:
        cov = parse(path)
        if cov is None:
            print(f"warning: {path} not found, skipping {name}", file=sys.stderr)
            continue
        per_module.append((name, cov))

    total = parse(AGGREGATED)
    if total is None:
        print(f"error: aggregated report {AGGREGATED} not found", file=sys.stderr)
        return 1

    write_badge(total, Path("build/reports/kover/html/badge.json"))
    write_comment(per_module, total, Path("build/reports/kover/comment.md"))

    print(f"coverage={total.pct:.1f}% ({total.covered}/{total.total} lines)")
    for name, cov in per_module:
        print(f"  {name}: {cov.pct:.1f}% ({cov.covered}/{cov.total} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
