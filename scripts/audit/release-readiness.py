#!/usr/bin/env python3
"""Generate the machine-readable Vericore release-readiness certificate."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = "1.0"


def parse_check(value: str) -> tuple[str, str]:
    name, separator, result = value.partition("=")
    if not separator or not name or not result:
        raise argparse.ArgumentTypeError("checks must use NAME=RESULT")
    return name, result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--check", action="append", type=parse_check, default=[])
    args = parser.parse_args()

    if not args.version.strip():
        parser.error("version must not be empty")
    if not args.sha.strip():
        parser.error("sha must not be empty")

    checks: dict[str, dict[str, object]] = {}
    invalid_results: list[str] = []
    for name, result in args.check:
        passed = result == "success"
        if result not in {"success", "failure", "cancelled", "skipped", "neutral"}:
            invalid_results.append(f"{name}={result}")
        checks[name] = {"required": True, "result": result, "passed": passed}

    if invalid_results:
        parser.error("unsupported check result(s): " + ", ".join(invalid_results))

    ready = bool(checks) and all(check["passed"] for check in checks.values())
    certificate = {
        "schemaVersion": SCHEMA_VERSION,
        "project": "Vericore",
        "releaseVersion": args.version,
        "candidate": {"sha": args.sha, "ref": args.ref},
        "checks": checks,
        "overallReady": ready,
        "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(certificate, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(certificate, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
