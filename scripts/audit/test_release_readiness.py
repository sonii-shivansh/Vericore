#!/usr/bin/env python3
"""Regression tests for the release-readiness certificate's fail-closed contract."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("release-readiness.py")
REQUIRED_CHECKS = (
    "deterministic-audit",
    "windows-ci",
    "verification",
    "platform",
    "onboarding",
    "live-e2e-matrix",
    "live-petclinic",
    "live-runelite",
    "output-petclinic",
    "output-runelite",
    "docs-parity",
    "github-action-audit",
    "installer-regression",
)


class ReleaseReadinessTests(unittest.TestCase):
    def run_certificate(self, checks: list[tuple[str, str]], sha: str = "a" * 40):
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        output = Path(temp_dir.name) / "certificate.json"
        command = [
            sys.executable,
            str(SCRIPT),
            "--output",
            str(output),
            "--version",
            "0.9.0",
            "--sha",
            sha,
            "--ref",
            "refs/heads/main",
        ]
        for name, result in checks:
            command.extend(["--check", f"{name}={result}"])
        completed = subprocess.run(command, check=False, capture_output=True, text=True)
        return completed, output

    def test_all_required_checks_are_required_and_ready_when_successful(self):
        completed, output = self.run_certificate([(name, "success") for name in REQUIRED_CHECKS])
        self.assertEqual(completed.returncode, 0, completed.stderr)
        certificate = json.loads(output.read_text(encoding="utf-8"))
        self.assertTrue(certificate["overallReady"])
        self.assertEqual(set(certificate["checks"]), set(REQUIRED_CHECKS))
        self.assertEqual(certificate["candidate"]["sha"], "a" * 40)

    def test_missing_gate_fails_instead_of_certifying_partial_checks(self):
        completed, _ = self.run_certificate([("deterministic-audit", "success")])
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("missing required check(s)", completed.stderr)

    def test_duplicate_gate_name_is_rejected(self):
        checks = [(name, "success") for name in REQUIRED_CHECKS]
        checks.append(("installer-regression", "success"))
        completed, _ = self.run_certificate(checks)
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("duplicate check name", completed.stderr)

    def test_failed_required_gate_makes_certificate_not_ready(self):
        checks = [
            (name, "failure" if name == "installer-regression" else "success")
            for name in REQUIRED_CHECKS
        ]
        completed, output = self.run_certificate(checks)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        certificate = json.loads(output.read_text(encoding="utf-8"))
        self.assertFalse(certificate["overallReady"])
        self.assertFalse(certificate["checks"]["installer-regression"]["passed"])

    def test_non_full_commit_sha_is_rejected(self):
        completed, _ = self.run_certificate([(name, "success") for name in REQUIRED_CHECKS], sha="main")
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("40-character Git commit SHA", completed.stderr)


if __name__ == "__main__":
    unittest.main()
