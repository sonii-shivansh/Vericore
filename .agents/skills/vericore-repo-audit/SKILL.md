---
name: vericore-repo-audit
description: Conduct a read-only, evidence-linked Vericore code, documentation, release, capability or governance audit before proposing changes.
---
# Vericore Repository Audit (Read Only)

1. Report the current branch, HEAD SHA, status of the working tree and repository scope.
2. Enumerate relevant files and mark inspected versus not inspected. Do not silently imply a complete audit.
3. Compare claims with primary sources: checked-out implementation, existing tests, schema files, tag/release records and (where available) actual GitHub settings. Avoid reliance on speculative onboarding files.
4. Produce a discrepancy ledger: ID, claimed behavior, observed behavior, file and line numbers, SHA/tag, severity, confidence, reproduction/verification step and narrowly scoped proposed fix.
5. For Sprint 0, pay attention to release-version claims, Gradle/Kotlin versions, API/CLI/MCP examples, docs-parity coverage, REST/MCP trust boundaries and effective GitHub main-branch rules.
6. Separate MATCH, STALE, AMBIGUOUS, PLANNED, HISTORICAL and NOT_REVIEWED.
7. Present findings and a proposed docs-only PR plan **in chat only** until the human approves editing files. Do not commit, push, install dependencies, edit code or configure GitHub.
