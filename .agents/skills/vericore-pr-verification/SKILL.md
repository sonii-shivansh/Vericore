---
name: vericore-pr-verification
description: Independently review an approved Vericore change or pull request against the issue, existing contracts, tests, documentation and stated acceptance criteria.
---
# Vericore PR Verification

1. Read the approved issue, feature spec and acceptance criteria; capture the base/head SHA and exact changed files.
2. Check compatibility: public CLI, JSON schemas, MCP tools, REST endpoints, installer/release and supported runtime.
3. Inspect diffs, negative tests, valid alternative behavior, safety/error paths and whether docs match implementation.
4. Execute only pre-approved safe test commands; capture exit codes and exact output excerpts. If unavailable, mark NOT RUN.
5. Review CI checks for the exact candidate SHA. A green check does not establish semantic correctness.
6. Report PASS/FAIL/NEEDS REVIEW per criterion with evidence and explicit limits; list risks and unresolved questions.
7. Do not change unrelated files, approve your own work as an independent reviewer, merge, push or publish releases.
