#!/usr/bin/env python3
"""Render a safe, concise GitHub Actions summary from a Vericore verify JSON report."""

from __future__ import annotations

import json
import os
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

STATUSES = ("PASS", "FAIL", "INCONCLUSIVE", "NOT_EVALUATED")
OVERALL_STATUSES = {"PASS", "FAIL", "REVIEW_REQUIRED"}


def _status(value: Any) -> str:
    text = str(value or "UNKNOWN").upper()
    return text if text in OVERALL_STATUSES or text in STATUSES else "UNKNOWN"


def _criterion_id(value: Any) -> str:
    # IDs are metadata, not prose. Keep arbitrary persisted text from becoming Markdown.
    return re.sub(r"[^A-Za-z0-9_.-]", "?", str(value or "unknown"))[:100] or "unknown"


def render_summary(report: dict[str, Any]) -> str:
    overall = _status(report.get("status"))
    safety = report.get("safety") if isinstance(report.get("safety"), dict) else {}
    changed = safety.get("changedPaths", report.get("changedPaths", []))
    unexpected = safety.get("unexpectedPaths", report.get("unexpectedPaths", []))
    changed_count = len(changed) if isinstance(changed, list) else 0
    unexpected_count = len(unexpected) if isinstance(unexpected, list) else 0

    lines = [
        "## Vericore verification",
        "",
        f"- **Outcome:** `{overall}`",
        f"- **Changed paths:** {changed_count}",
        f"- **Unexpected paths:** {unexpected_count}",
    ]

    evaluation = report.get("specificationEvaluation")
    if not isinstance(evaluation, dict):
        lines.extend([
            "",
            "### Requirement evidence",
            "",
            "No structured change specification was declared; semantic requirement coverage was not evaluated.",
        ])
    else:
        criteria = evaluation.get("criteria", [])
        if not isinstance(criteria, list):
            criteria = []
        counts = Counter(
            _status(item.get("status")) for item in criteria if isinstance(item, dict)
        )
        lines.extend([
            "",
            "### Requirement evidence",
            "",
            f"- **Evaluation status:** `{_status(evaluation.get('status'))}`",
            f"- **Total criteria:** {len(criteria)}",
        ])
        for status in STATUSES:
            lines.append(f"- **{status}:** {counts.get(status, 0)}")
        if criteria:
            lines.extend(["", "#### Criterion statuses", ""])
            for item in criteria:
                if not isinstance(item, dict):
                    continue
                lines.append(f"- `{_criterion_id(item.get('id'))}` — `{_status(item.get('status'))}`")

    lines.extend([
        "",
        "> A successful command establishes command execution outcome only; it does not prove arbitrary semantic correctness.",
        "",
    ])
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("Usage: action_verification_summary.py REPORT_PATH REPOSITORY_PATH", file=sys.stderr)
        return 2

    report_path = Path(argv[1])
    if not report_path.is_absolute():
        report_path = Path(argv[2]) / report_path

    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        if not isinstance(report, dict):
            raise ValueError("report root must be a JSON object")
        markdown = render_summary(report)
    except (OSError, json.JSONDecodeError, ValueError):
        markdown = (
            "## Vericore verification\n\n"
            "The verification JSON report was not available to render. "
            "Check the verify step logs; this summary did not change the verification exit status.\n"
        )

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as output:
            output.write(markdown)
            output.write("\n")
    else:
        print(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
