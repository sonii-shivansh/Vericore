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
