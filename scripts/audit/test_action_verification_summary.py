#!/usr/bin/env python3
"""Unit tests for the safe GitHub Actions verification summary."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from action_verification_summary import render_summary  # noqa: E402


class ActionVerificationSummaryTest(unittest.TestCase):
    def test_full_verification_reports_status_counts_without_raw_reasons(self) -> None:
        summary = render_summary({
            "status": "REVIEW_REQUIRED",
            "safety": {
                "changedPaths": ["src/A.kt", "src/B.kt"],
                "unexpectedPaths": ["tmp/strange.md"],
            },
            "specificationEvaluation": {
                "status": "NOT_EVALUATED",
                "criteria": [
                    {"id": "acceptance-criterion-001", "status": "PASS", "statement": "safe", "reason": "secret-value"},
                    {"id": "protected-invariant-001", "status": "NOT_EVALUATED", "statement": "do not leak", "reason": "private-reason"},
                    {"id": "required-evidence-001", "status": "INCONCLUSIVE"},
                ],
            },
        })

        self.assertIn("Outcome:** `REVIEW_REQUIRED`", summary)
        self.assertIn("Changed paths:** 2", summary)
        self.assertIn("Unexpected paths:** 1", summary)
        self.assertIn("**PASS:** 1", summary)
        self.assertIn("**INCONCLUSIVE:** 1", summary)
        self.assertIn("**NOT_EVALUATED:** 1", summary)
        self.assertIn("`protected-invariant-001` — `NOT_EVALUATED`", summary)
        self.assertNotIn("secret-value", summary)
        self.assertNotIn("private-reason", summary)
        self.assertNotIn("do not leak", summary)

    def test_contract_only_result_uses_top_level_changed_paths(self) -> None:
        summary = render_summary({
            "status": "REVIEW_REQUIRED",
            "changedPaths": ["src/A.kt"],
            "specificationEvaluation": {
                "status": "NOT_EVALUATED",
                "criteria": [{"id": "acceptance-criterion-001", "status": "NOT_EVALUATED"}],
            },
        })
        self.assertIn("Changed paths:** 1", summary)
        self.assertIn("Unexpected paths:** 0", summary)

    def test_missing_specification_does_not_claim_semantic_coverage(self) -> None:
        summary = render_summary({"status": "PASS"})
        self.assertIn("No structured change specification was declared", summary)
        self.assertIn("semantic requirement coverage was not evaluated", summary)

    def test_malformed_criterion_entries_are_excluded_from_total(self) -> None:
        summary = render_summary({
            "status": "REVIEW_REQUIRED",
            "specificationEvaluation": {
                "status": "NOT_EVALUATED",
                "criteria": [{"id": "criterion-1", "status": "PASS"}, "malformed"],
            },
        })
        self.assertIn("**Total criteria:** 1", summary)
        self.assertIn("**PASS:** 1", summary)

    def test_cli_writes_summary_to_github_step_summary(self) -> None:
        import json
        import os
        import tempfile
        from unittest.mock import patch

        from action_verification_summary import main

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.json"
            output = root / "step-summary.md"
            report.write_text(json.dumps({"status": "PASS"}), encoding="utf-8")
            with patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(output)}):
                self.assertEqual(main(["action_verification_summary.py", str(report), str(root)]), 0)
            self.assertIn("Outcome:** `PASS`", output.read_text(encoding="utf-8"))

    def test_cli_missing_report_warns_and_writes_fallback_summary(self) -> None:
        import os
        import tempfile
        from unittest.mock import patch, redirect_stderr
        from io import StringIO

        from action_verification_summary import main

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "step-summary.md"
            errors = StringIO()
            with patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(output)}), redirect_stderr(errors):
                self.assertEqual(main(["action_verification_summary.py", str(root / "missing.json"), str(root)]), 0)
            self.assertIn("::warning::", errors.getvalue())
            self.assertIn("was not available to render", output.read_text(encoding="utf-8"))

    def test_malicious_criterion_id_cannot_inject_markdown(self) -> None:
        summary = render_summary({
            "status": "REVIEW_REQUIRED",
            "specificationEvaluation": {
                "status": "NOT_EVALUATED",
                "criteria": [{"id": "criterion`\n## injected", "status": "NOT_EVALUATED"}],
            },
        })
        self.assertNotIn("## injected", summary)
        self.assertRegex(summary, r"`criterion\?+injected`")


if __name__ == "__main__":
    unittest.main()
