#!/usr/bin/env python3
"""Reproducible Phase 0 schema, golden-fixture, and verification-contract audit."""

from __future__ import annotations

import argparse
import copy
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from jsonschema import Draft202012Validator, ValidationError


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "docs" / "schemas"
FIXTURE_SOURCE = ROOT / "testdata" / "phase0" / "golden-java"
MANIFEST_PATH = FIXTURE_SOURCE / "manifest.json"


class AuditFailure(RuntimeError):
    pass


class Audit:
    def __init__(self, cli: Path, output: Path):
        self.cli = cli.resolve()
        self.output = output.resolve()
        self.cases: list[dict[str, object]] = []
        self.failures: list[str] = []
        self.fixture_id = "phase0.golden.java.v1"

    def record(self, case_id: str, expected: str, observed: str, started: float, passed: bool, details: str = "") -> None:
        self.cases.append({
            "id": case_id,
            "expected": expected,
            "observed": observed,
            "passed": passed,
            "elapsedMs": round((time.perf_counter() - started) * 1000, 2),
            "details": details,
        })
        if not passed:
            self.failures.append(case_id)
            raise AuditFailure(f"{case_id}: expected {expected}; observed {observed}. {details}")

    def command(self, case_id: str, args: list[str], cwd: Path, env: dict[str, str], expect: str = "success") -> subprocess.CompletedProcess[str]:
        started = time.perf_counter()
        try:
            result = subprocess.run(
                [str(self.cli), *args],
                cwd=cwd,
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=240,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            self.record(case_id, expect, "timeout", started, False, str(exc))
            raise AuditFailure(case_id)
        observed = f"exit={result.returncode}"
        passed = result.returncode == 0 if expect == "success" else result.returncode != 0
        detail = (result.stderr or result.stdout)[-2000:]
        self.record(case_id, expect, observed, started, passed, detail)
        return result

    def load_json(self, path: Path) -> object:
        return json.loads(path.read_text(encoding="utf-8"))

    def validate_schema_documents(self) -> None:
        started = time.perf_counter()
        schemas = sorted(SCHEMA_DIR.glob("*.schema.json"))
        schemas.append(ROOT / "docs" / "release-readiness.schema.json")
        checked = []
        for path in schemas:
            schema = self.load_json(path)
            Draft202012Validator.check_schema(schema)
            checked.append(path.relative_to(ROOT).as_posix())
        self.record(
            "schema-documents-valid",
            "all published schemas parse and validate as Draft 2020-12",
            f"validated {len(checked)} schema documents",
            started,
            bool(checked),
            ", ".join(checked),
        )

    def validate_artifact(self, case_id: str, artifact: Path, schema_file: str) -> object:
        started = time.perf_counter()
        try:
            data = self.load_json(artifact)
            schema = self.load_json(SCHEMA_DIR / schema_file)
            Draft202012Validator(schema).validate(data)
            detail = f"{artifact.relative_to(artifact.parents[1]) if len(artifact.parents) > 1 else artifact.name}"
            self.record(case_id, schema_file, "schema-valid", started, True, detail)
            return data
        except (OSError, json.JSONDecodeError, ValidationError, ValueError) as exc:
            self.record(case_id, schema_file, f"invalid: {exc}", started, False, str(exc))
            raise AuditFailure(case_id)

    def git(self, fixture: Path, *args: str) -> str:
        result = subprocess.run(
            ["git", *args], cwd=fixture, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, timeout=30, check=False
        )
        if result.returncode != 0:
            raise AuditFailure(f"git {' '.join(args)} failed: {result.stderr[-2000:]}")
        return result.stdout.strip()

    def run(self) -> None:
        self.validate_schema_documents()
        manifest = self.load_json(MANIFEST_PATH)
        with tempfile.TemporaryDirectory(prefix="vericore-phase0-") as temp_dir:
            temp = Path(temp_dir)
            fixture = temp / "golden-java"
            shutil.copytree(FIXTURE_SOURCE, fixture)
            output_dir = fixture / "output"
            output_dir.mkdir(exist_ok=True)
            command_log = temp / "verification-commands.log"
            env = os.environ.copy()
            env["VERICORE_FIXTURE_COMMAND_LOG"] = str(command_log)
            env["VERICORE_ALLOWED_PATHS"] = str(fixture)
            (fixture / "mvnw").chmod(0o755)
            self.git(fixture, "init", "-q")
            self.git(fixture, "config", "user.name", "Vericore Phase 0")
            self.git(fixture, "config", "user.email", "phase0@example.invalid")
            self.git(fixture, "add", "--", ".")
            self.git(fixture, "commit", "-qm", "golden fixture baseline")

            snapshot_path = output_dir / "analysis-snapshot.json"
            self.command("golden-analyze-first", ["analyze", str(fixture), "--clear-cache"], fixture, env)
            snapshot_first = self.validate_artifact("golden-snapshot-schema-first", snapshot_path, "analysis-snapshot.schema.json")
            self.assert_golden_snapshot(snapshot_first, manifest, fixture)
            self.validate_artifact("engineering-risks-schema", output_dir / "engineering-risks.json", "engineering-risks.schema.json")

            self.command("inspect", ["inspect", "--path", str(fixture), "--json"], fixture, env)
            self.validate_artifact("inspection-schema", output_dir / "inspection.json", "inspection.schema.json")
            self.command("architecture", ["architecture", str(fixture), "--json"], fixture, env)
            architecture = self.validate_artifact("architecture-schema", output_dir / "architecture.json", "architecture-analysis.schema.json")
            self.assert_golden_architecture(architecture, manifest, fixture)
            self.command("impact", ["impact", str(fixture), "src/main/java/example/Service.java", "--json"], fixture, env)
            self.validate_artifact("change-impact-schema", output_dir / "change-impact.json", "change-impact.schema.json")
            self.command("pr-intelligence", ["pr-intelligence", str(fixture), "--json"], fixture, env)
            self.validate_artifact("pr-intelligence-schema", output_dir / "pr-intelligence.json", "pr-intelligence.schema.json")

            self.command("golden-analyze-repeat", ["analyze", str(fixture), "--clear-cache"], fixture, env)
            snapshot_second = self.validate_artifact("golden-snapshot-schema-repeat", snapshot_path, "analysis-snapshot.schema.json")
            normalized_first = copy.deepcopy(snapshot_first)
            normalized_second = copy.deepcopy(snapshot_second)
            normalized_first.get("repository", {}).pop("analyzedAtEpochMillis", None)
            normalized_second.get("repository", {}).pop("analyzedAtEpochMillis", None)
            started = time.perf_counter()
            self.record(
                "analysis-determinism",
                "same normalized analysis snapshot for same fixture and commit",
                "identical" if normalized_first == normalized_second else "different",
                started,
                normalized_first == normalized_second,
                "Only repository observation time is normalized.",
            )

            self.command("context-snapshot-before", ["context-snapshot", str(fixture), "--json"], fixture, env)
            context_path = output_dir / "engineering-context.json"
            self.validate_artifact("context-snapshot-schema-before", context_path, "engineering-context-snapshot.schema.json")
            before_path = output_dir / "context-before.json"
            shutil.copyfile(context_path, before_path)

            controller_path = fixture / "src/main/java/example/Controller.java"
            controller_original = controller_path.read_text(encoding="utf-8")
            controller_path.write_text(controller_original + "\n// temporary context diff marker\n", encoding="utf-8")
            self.command("context-snapshot-after-change", ["context-snapshot", str(fixture), "--json"], fixture, env)
            context_after = self.validate_artifact("context-snapshot-schema-after", context_path, "engineering-context-snapshot.schema.json")
            self.command("context-diff", ["context-diff", str(before_path), str(context_path), "--json"], fixture, env)
            self.validate_artifact("context-diff-schema", output_dir / "engineering-context-diff.json", "engineering-context-diff.schema.json")
            started = time.perf_counter()
            self.record(
                "context-diff-detects-change",
                "at least one modified file",
                f"modified={context_after['totalFiles'] and context_after['dirty']}",
                started,
                context_after["dirty"] is True and "src/main/java/example/Controller.java" in context_after["changedPaths"],
                "Context snapshot dirty state and changed paths must reflect the controlled edit.",
            )
            controller_path.write_text(controller_original, encoding="utf-8")

            self.command("reanalyze-restored-golden", ["analyze", str(fixture), "--clear-cache"], fixture, env)
            snapshot = self.validate_artifact("restored-analysis-snapshot-schema", snapshot_path, "analysis-snapshot.schema.json")
            evidence_path = output_dir / "grounded-evidence.json"
            self.command(
                "repo-qa-evidence",
                ["repo-qa", "Which files depend on Service?", "--path", str(fixture), "--evidence-output", str(evidence_path)],
                fixture,
                env,
            )
            evidence = self.validate_artifact("grounded-evidence-schema", evidence_path, "grounded-evidence.schema.json")

            # A legacy 1.0 evidence file can be decoded, but must never be reused as
            # current evidence because it has no repository/source-state binding.
            evidence_path.write_text(json.dumps({"schemaVersion": "1.0", "citations": []}) + "\n", encoding="utf-8")
            self.command(
                "prepare-rejects-legacy-evidence",
                ["prepare", "Update Service safely", "--path", str(fixture),
                 "--planned-path", "src/main/java/example/Service.java"],
                fixture,
                env,
            )
            legacy_recovery = self.validate_artifact(
                "legacy-evidence-regeneration-schema",
                output_dir / "engineering-preparation.json",
                "engineering-preparation.schema.json",
            )
            regenerated = legacy_recovery["evidence"]
            started = time.perf_counter()
            legacy_rejected = (
                regenerated["schemaVersion"] == "1.1"
                and bool(regenerated["citations"])
                and regenerated["repositoryStateDigest"] == snapshot["repository"].get("repositoryStateDigest")
            )
            self.record(
                "legacy-evidence-not-reused",
                "legacy 1.0 evidence is decoded for compatibility but regenerated before reuse",
                "regenerated" if legacy_rejected else "legacy-evidence-reused",
                started,
                legacy_rejected,
            )
            self.command(
                "repo-qa-refreshes-grounded-evidence",
                ["repo-qa", "Which files depend on Service?", "--path", str(fixture), "--evidence-output", str(evidence_path)],
                fixture,
                env,
            )
            evidence = self.validate_artifact("grounded-evidence-schema-refreshed", evidence_path, "grounded-evidence.schema.json")
            started = time.perf_counter()
            evidence_bound = (
                evidence["schemaVersion"] == "1.1"
                and evidence["repositoryStateDigest"] == snapshot["repository"].get("repositoryStateDigest")
                and evidence["repositoryCommit"] == snapshot["repository"].get("repositoryCommit")
                and evidence["analysisSchemaVersion"] == snapshot["schemaVersion"]
            )
            self.record(
                "grounded-evidence-bound",
                "evidence commit/digest/schema match current analysis snapshot",
                "bound" if evidence_bound else "not-bound",
                started,
                evidence_bound,
            )

            self.command("evidence-graph", ["evidence-graph", str(fixture), "--json"], fixture, env)
            self.validate_artifact("semantic-evidence-graph-schema", output_dir / "semantic-evidence-graph.json", "semantic-evidence-graph.schema.json")

            self.command(
                "plan",
                ["plan", "Update the greeting safely", "--repository", str(fixture),
                 "--changed", "src/main/java/example/Service.java", "--evidence", str(evidence_path),
                 "--output", str(output_dir / "engineering-plan.json")],
                fixture,
                env,
            )
            self.validate_artifact("engineering-plan-schema", output_dir / "engineering-plan.json", "engineering-plan.schema.json")

            # This changes the source without changing HEAD. Preparation must reject cached
            # evidence and bind a newly generated result to the changed source digest.
            service_path = fixture / "src/main/java/example/Service.java"
            service_original = service_path.read_text(encoding="utf-8")
            service_path.write_text(service_original.replace("Hello ", "Hello from Vericore "), encoding="utf-8")
            self.command("context-snapshot-source-edit", ["context-snapshot", str(fixture), "--json"], fixture, env)
            fresh_context = self.validate_artifact("fresh-context-schema", context_path, "engineering-context-snapshot.schema.json")
            self.command(
                "prepare-stale-cache-regression",
                ["prepare", "Update Service greeting safely", "--path", str(fixture),
                 "--planned-path", "src/main/java/example/Service.java"],
                fixture,
                env,
            )
            prep_path = output_dir / "engineering-preparation.json"
            preparation = self.validate_artifact("engineering-preparation-schema-freshness", prep_path, "engineering-preparation.schema.json")
            self.validate_artifact("engineering-plan-schema-from-prepare", output_dir / "engineering-plan.json", "engineering-plan.schema.json")
            self.validate_artifact("agent-change-contract-schema-from-prepare", output_dir / "agent-change-contract.json", "agent-change-contract.schema.json")
            # The file owned by context-snapshot must remain a context snapshot after prepare.
            context_again = self.validate_artifact("context-path-not-overwritten", context_path, "engineering-context-snapshot.schema.json")
            evidence_now = preparation["evidence"]
            started = time.perf_counter()
            fresh_binding = (
                evidence_now["schemaVersion"] == "1.1"
                and evidence_now["repositoryCommit"] == fresh_context["repositoryCommit"]
                and evidence_now["repositoryStateDigest"] == fresh_context["snapshotDigest"]
                and evidence_now["analysisSchemaVersion"] == "1.1"
                and context_again["schemaVersion"] == "1.0"
            )
            self.record(
                "source-edit-invalidates-cache",
                "preparation evidence matches current digest while context artifact stays a context snapshot",
                "freshly-bound" if fresh_binding else "stale-or-overwritten",
                started,
                fresh_binding,
            )
            service_path.write_text(service_original, encoding="utf-8")

            # Regenerate current analysis/evidence for a clean positive prepare/verify case.
            self.command("reanalyze-before-verification", ["analyze", str(fixture), "--clear-cache"], fixture, env)
            self.command(
                "repo-qa-before-verification",
                ["repo-qa", "Which files depend on Service?", "--path", str(fixture), "--evidence-output", str(evidence_path)],
                fixture,
                env,
            )
            self.command(
                "prepare-positive",
                ["prepare", "Update Service greeting safely", "--path", str(fixture),
                 "--planned-path", "src/main/java/example/Service.java"],
                fixture,
                env,
            )
            self.validate_artifact("positive-preparation-schema", prep_path, "engineering-preparation.schema.json")
            self.validate_artifact("positive-contract-schema", output_dir / "agent-change-contract.json", "agent-change-contract.schema.json")
            service_path.write_text(service_original.replace("Hello ", "Hello safely "), encoding="utf-8")
            verify_path = output_dir / "verification.json"
            self.command(
                "verify-planned-mutation",
                ["verify", "--path", str(fixture), "--plan", str(output_dir / "engineering-plan.json"),
                 "--contract", str(output_dir / "agent-change-contract.json"), "--output", str(verify_path)],
                fixture,
                env,
            )
            verification = self.validate_artifact("verification-result-schema", verify_path, "engineering-verification.schema.json")
            self.validate_artifact("verification-receipt-schema", output_dir / "verification-receipt.json", "verification-receipt.schema.json")
            started = time.perf_counter()
            positive_passed = (
                verification["status"] in {"PASS", "REVIEW_REQUIRED"}
                and verification["verification"]["executionComplete"] is True
                and verification["verification"]["allCommandsPassed"] is True
            )
            self.record(
                "planned-mutation",
                "PASS or REVIEW_REQUIRED with all declared commands executed and passed",
                f"status={verification['status']}, commandsPassed={verification['verification']['allCommandsPassed']}",
                started,
                positive_passed,
            )

            # No mutation must fail closed.
            service_path.write_text(service_original, encoding="utf-8")
            self.command(
                "prepare-no-mutation",
                ["prepare", "Update Service greeting safely", "--path", str(fixture),
                 "--planned-path", "src/main/java/example/Service.java"],
                fixture,
                env,
            )
            no_change_path = output_dir / "verification-no-mutation.json"
            self.command(
                "verify-without-mutation",
                ["verify", "--path", str(fixture), "--plan", str(output_dir / "engineering-plan.json"),
                 "--contract", str(output_dir / "agent-change-contract.json"), "--output", str(no_change_path)],
                fixture,
                env,
                expect="failure",
            )
            no_change_result = self.validate_artifact("no-mutation-verification-schema", no_change_path, "engineering-verification.schema.json")
            started = time.perf_counter()
            self.record("no-mutation-fails-closed", "FAIL", no_change_result["status"], started, no_change_result["status"] == "FAIL")

            # An out-of-scope source change must fail.
            self.command(
                "prepare-unexpected-path",
                ["prepare", "Update Service greeting safely", "--path", str(fixture),
                 "--planned-path", "src/main/java/example/Service.java"],
                fixture,
                env,
            )
            controller_path.write_text(controller_original + "\n// unexpected change\n", encoding="utf-8")
            unexpected_path = output_dir / "verification-unexpected-path.json"
            self.command(
                "verify-unexpected-path",
                ["verify", "--path", str(fixture), "--plan", str(output_dir / "engineering-plan.json"),
                 "--contract", str(output_dir / "agent-change-contract.json"), "--output", str(unexpected_path)],
                fixture,
                env,
                expect="failure",
            )
            unexpected = self.validate_artifact("unexpected-path-verification-schema", unexpected_path, "engineering-verification.schema.json")
            started = time.perf_counter()
            self.record(
                "unexpected-path-fails-closed",
                "FAIL",
                unexpected["status"],
                started,
                unexpected["status"] == "FAIL" and "src/main/java/example/Controller.java" in unexpected["safety"]["unexpectedPaths"],
            )
            controller_path.write_text(controller_original, encoding="utf-8")

            # A commit change after prepare invalidates the contract even with no source diff.
            self.command(
                "prepare-stale-head",
                ["prepare", "Update Service greeting safely", "--path", str(fixture),
                 "--planned-path", "src/main/java/example/Service.java"],
                fixture,
                env,
            )
            note = fixture / "phase0-stale-head-marker.txt"
            note.write_text("commit changes HEAD after preparation\n", encoding="utf-8")
            self.git(fixture, "add", "--", note.name)
            self.git(fixture, "commit", "-qm", "stale contract regression")
            stale_path = output_dir / "verification-stale-head.json"
            self.command(
                "verify-stale-head",
                ["verify", "--path", str(fixture), "--plan", str(output_dir / "engineering-plan.json"),
                 "--contract", str(output_dir / "agent-change-contract.json"), "--output", str(stale_path)],
                fixture,
                env,
                expect="failure",
            )
            stale = self.validate_artifact("stale-head-verification-schema", stale_path, "engineering-verification.schema.json")
            started = time.perf_counter()
            self.record("stale-head-fails-closed", "FAIL", stale["status"], started, stale["status"] == "FAIL")

        self.run_kotlin_golden(env)

    def run_kotlin_golden(self, env: dict[str, str]) -> None:
        manifest_path = ROOT / "testdata" / "phase0" / "golden-kotlin" / "manifest.json"
        manifest = self.load_json(manifest_path)
        with tempfile.TemporaryDirectory(prefix="vericore-phase0-kotlin-") as temp_dir:
            fixture = Path(temp_dir) / "golden-kotlin"
            shutil.copytree(ROOT / "testdata" / "phase0" / "golden-kotlin", fixture)
            output_dir = fixture / "output"
            output_dir.mkdir(exist_ok=True)
            (fixture / "gradlew").chmod(0o755)
            self.git(fixture, "init", "-q")
            self.git(fixture, "config", "user.name", "Vericore Phase 0")
            self.git(fixture, "config", "user.email", "phase0@example.invalid")
            self.git(fixture, "add", "--", ".")
            self.git(fixture, "commit", "-qm", "golden Kotlin fixture")

            snapshot_path = output_dir / "analysis-snapshot.json"
            self.command("kotlin-golden-analyze-first", ["analyze", str(fixture), "--clear-cache"], fixture, env)
            first = self.validate_artifact("kotlin-golden-snapshot-schema-first", snapshot_path, "analysis-snapshot.schema.json")
            self.assert_golden_snapshot(first, manifest, fixture, "kotlin-golden")

            self.command("kotlin-golden-architecture", ["architecture", str(fixture), "--json"], fixture, env)
            architecture = self.validate_artifact("kotlin-golden-architecture-schema", output_dir / "architecture.json", "architecture-analysis.schema.json")
            self.assert_golden_architecture(architecture, manifest, fixture, "kotlin-golden")

            self.command("kotlin-golden-analyze-repeat", ["analyze", str(fixture), "--clear-cache"], fixture, env)
            second = self.validate_artifact("kotlin-golden-snapshot-schema-repeat", snapshot_path, "analysis-snapshot.schema.json")
            normalized_first = copy.deepcopy(first)
            normalized_second = copy.deepcopy(second)
            normalized_first.get("repository", {}).pop("analyzedAtEpochMillis", None)
            normalized_second.get("repository", {}).pop("analyzedAtEpochMillis", None)
            started = time.perf_counter()
            self.record(
                "kotlin-golden-analysis-determinism",
                "same normalized Kotlin analysis snapshot for the same fixture and commit",
                "identical" if normalized_first == normalized_second else "different",
                started,
                normalized_first == normalized_second,
                "Only repository observation time is normalized.",
            )

    def assert_golden_architecture(self, architecture: object, manifest: object, fixture: Path, prefix: str = "golden") -> None:
        expected_edges = sorted(
            (item["source"], item["target"]) for item in manifest["expectedAnalysis"]["dependencyEdges"]
        )
        actual_edges = sorted(
            (edge["source"].replace("\\\\", "/"), edge["target"].replace("\\\\", "/"))
            for edge in architecture["dependencyEdges"]
        )
        started = time.perf_counter()
        self.record(
            f"{prefix}-architecture-edges",
            json.dumps(expected_edges),
            json.dumps(actual_edges),
            started,
            actual_edges == expected_edges,
            "Architecture edges must match the explicit fixture manifest.",
        )

    def assert_golden_snapshot(self, snapshot: object, manifest: object, fixture: Path, prefix: str = "golden") -> None:
        expected = manifest["expectedAnalysis"]
        metrics = snapshot["metrics"]
        architecture = snapshot["architecture"]
        relative_paths = sorted(str(Path(item["path"]).resolve().relative_to(fixture.resolve())).replace(os.sep, "/") for item in snapshot["files"])
        actual_paths = sorted(path.replace("\\", "/") for path in relative_paths)
        expected_paths = sorted(manifest["sourceFiles"])
        valid = (
            metrics["totalFiles"] == expected["totalFiles"]
            and metrics["totalNodes"] == expected["totalNodes"]
            and metrics["totalEdges"] == expected["totalEdges"]
            and metrics.get("parseFailures", 0) == expected["parseFailures"]
            and metrics["cycleDetected"] == expected["cycleDetected"]
            and architecture["packageCount"] == expected["packageCount"]
            and sorted(snapshot["repository"]["languages"]) == expected["languages"]
            and actual_paths == expected_paths
        )
        started = time.perf_counter()
        self.record(
            f"{prefix}-analysis-expectations",
            json.dumps(expected, sort_keys=True),
            json.dumps({
                "totalFiles": metrics["totalFiles"], "totalNodes": metrics["totalNodes"],
                "totalEdges": metrics["totalEdges"], "parseFailures": metrics.get("parseFailures", 0),
                "cycleDetected": metrics["cycleDetected"], "packageCount": architecture["packageCount"],
                "languages": snapshot["repository"]["languages"], "paths": actual_paths
            }, sort_keys=True),
            started,
            valid,
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    audit = Audit(args.cli, args.output)
    started = time.perf_counter()
    top_error = ""
    try:
        if not audit.cli.is_file():
            raise AuditFailure(f"CLI executable not found: {audit.cli}")
        audit.run()
    except Exception as exc:  # Always write a machine-readable failure report.
        top_error = f"{type(exc).__name__}: {exc}"
        if not audit.failures:
            audit.cases.append({
                "id": "phase0-audit-execution",
                "expected": "all foundation checks finish",
                "observed": top_error,
                "passed": False,
                "elapsedMs": round((time.perf_counter() - started) * 1000, 2),
                "details": top_error,
            })
            audit.failures.append("phase0-audit-execution")

    result = {
        "schemaVersion": "1.0",
        "benchmarkId": "vericore-agent-change",
        "benchmarkVersion": "0.1.0",
        "fixtureId": audit.fixture_id,
        "candidateSha": os.environ.get("GITHUB_SHA") or subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=False
        ).stdout.strip(),
        "vericoreVersion": subprocess.run(
            [str(audit.cli), "--version"], text=True, capture_output=True, timeout=30, check=False
        ).stdout.strip(),
        "platform": {"system": platform.system(), "release": platform.release(), "python": platform.python_version()},
        "generatedAtUtc": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "cases": audit.cases,
        "summary": {
            "executed": len(audit.cases),
            "passed": sum(1 for case in audit.cases if case["passed"]),
            "failed": sum(1 for case in audit.cases if not case["passed"]),
            "overallPass": not audit.failures,
            "failureIds": audit.failures,
            "elapsedMs": round((time.perf_counter() - started) * 1000, 2),
        },
        "error": top_error or None,
        "limitations": [
            "The golden fixture is small and Java-only.",
            "This is a correctness/contract smoke benchmark, not a comparative agent leaderboard.",
            "Benchmark outcomes apply only to cases actually executed on this candidate."
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result["summary"]["overallPass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
