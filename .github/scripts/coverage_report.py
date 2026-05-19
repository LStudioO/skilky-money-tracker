#!/usr/bin/env python3
"""Render coverage outputs from per-module Kover XML reports.

Inputs (per-module `report.xml` produced by Kover, either by a local
`./gradlew :module:koverXmlReport` or by downloading the CI artifacts
into the same paths):

    core/build/reports/kover/report.xml
    server/build/reports/kover/report.xml
    app/shared/build/reports/kover/report.xml

Outputs:

    build/reports/kover/comment.md   markdown table for the PR comment
    build/reports/kover/badge.json   shields.io endpoint JSON (total)
    build/reports/kover/index.html   landing page linking each module's
                                     Kover HTML report (Pages root)

Total coverage is summed from the per-module XMLs rather than read from
a separate aggregated XML — that way nothing depends on the root
`koverXmlReport` task having run.
"""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

BAR_WIDTH = 10
BAR_FULL = "█"
BAR_EMPTY = "░"

# (gradle path, XML path, slug used for Pages subdirectory)
MODULES: list[tuple[str, str, str]] = [
    (":core", "core/build/reports/kover/report.xml", "core"),
    (":server", "server/build/reports/kover/report.xml", "server"),
    (":app:shared", "app/shared/build/reports/kover/report.xml", "app-shared"),
]


@dataclass
class Coverage:
    covered: int
    total: int

    @property
    def pct(self) -> float:
        return (self.covered * 100 / self.total) if self.total else 0.0

    def __add__(self, other: "Coverage") -> "Coverage":
        return Coverage(self.covered + other.covered, self.total + other.total)


def parse(path: str) -> Coverage | None:
    p = Path(path)
    if not p.exists():
        return None
    root = ET.parse(p).getroot()
    counters = {c.attrib["type"]: c.attrib for c in root.findall("./counter")}
    line = counters.get("LINE", {"missed": "0", "covered": "0"})
    covered = int(line["covered"])
    missed = int(line["missed"])
    return Coverage(covered=covered, total=covered + missed)


def bar(pct: float) -> str:
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


def write_badge(cov: Coverage, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "schemaVersion": 1,
        "label": "coverage",
        "message": f"{cov.pct:.1f}%",
        "color": color(cov.pct),
    }))


def write_comment(rows: list[tuple[str, Coverage]], total: Coverage, out: Path) -> None:
    # Baseline / Delta columns are placeholders. They light up once a
    # ratchet baseline file lands in the repo; until then the values
    # are just em-dashes so the table layout stays stable.
    lines = [
        "## Coverage",
        "",
        "| Module | Coverage | Baseline | Delta |",
        "|---|---|---|---|",
    ]
    for name, cov in rows:
        lines.append(f"| `{name}` | `{bar(cov.pct)}` {cov.pct:.1f}% | — | — |")
    lines.append(f"| **Total** | `{bar(total.pct)}` **{total.pct:.1f}%** | — | — |")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n")


def write_index(rows: list[tuple[str, str, Coverage]], total: Coverage, out: Path) -> None:
    items = "\n".join(
        f'    <li><a href="{slug}/">{name}</a> — {cov.pct:.1f}% ({cov.covered} / {cov.total} lines)</li>'
        for name, slug, cov in rows
    )
    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Skilky coverage</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
           max-width: 720px; margin: 3rem auto; padding: 0 1rem; color: #24292f; }}
    h1 {{ margin-bottom: 0.5rem; }}
    .total {{ color: #57606a; margin-top: 0; }}
    ul {{ padding-left: 1.25rem; }}
    li {{ margin: 0.4rem 0; }}
    a {{ color: #0969da; text-decoration: none; }}
    a:hover {{ text-decoration: underline; }}
  </style>
</head>
<body>
  <h1>Skilky coverage</h1>
  <p class="total"><strong>Total:</strong> {total.pct:.1f}% ({total.covered} / {total.total} lines)</p>
  <ul>
{items}
  </ul>
</body>
</html>
"""
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html)


def main() -> int:
    rows_for_comment: list[tuple[str, Coverage]] = []
    rows_for_index: list[tuple[str, str, Coverage]] = []
    for name, path, slug in MODULES:
        cov = parse(path)
        if cov is None:
            print(f"warning: {path} not found, skipping {name}", file=sys.stderr)
            continue
        rows_for_comment.append((name, cov))
        rows_for_index.append((name, slug, cov))

    if not rows_for_comment:
        print("error: no module coverage reports found", file=sys.stderr)
        return 1

    total = rows_for_comment[0][1]
    for _, cov in rows_for_comment[1:]:
        total = total + cov

    out_dir = Path("build/reports/kover")
    write_comment(rows_for_comment, total, out_dir / "comment.md")
    write_badge(total, out_dir / "badge.json")
    write_index(rows_for_index, total, out_dir / "index.html")

    print(f"total={total.pct:.1f}% ({total.covered}/{total.total} lines)")
    for name, cov in rows_for_comment:
        print(f"  {name}: {cov.pct:.1f}% ({cov.covered}/{cov.total} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
