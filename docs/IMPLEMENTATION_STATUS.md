# Implementation Status

> What Vericore implements today, where it is intentionally limited, and what is not yet shipped.

## How to read this document

- **Implemented** — present in the repository and covered by tests, CI, or release-gate validation.
- **Current limitation** — a known or intentional boundary of the current implementation.
- **Future direction** — not shipped; do not depend on it as an existing capability.

## Implemented

### Repository intelligence
- Java and Kotlin source discovery and parsing
- configurable exclusions and file limits
- parallel parsing and content-based caching
- dependency graphs, wildcard-import handling, cycle detection, and PageRank hotspots
- Git authorship, churn, modification-time, and recent-change analysis
- learning-path generation and self-contained HTML reporting

### Change, PR, and architecture intelligence
- dependency-aware change-impact analysis
- changed-file and blast-radius analysis
- deterministic PR Intelligence
- change-size, risk, architecture, and test signals
- machine-readable intelligence artifacts
- architecture drift comparison and deterministic architecture contracts
- architecture contract decision records and persisted dependency edges

### Engineering context and evidence
- versioned deterministic engineering-context snapshots
- repository-relative file fingerprints and Git HEAD capture
- working-tree state and deterministic snapshot diffs
- repository/commit-bound semantic evidence graph
- bounded evidence generation, deterministic citation ordering, and evidence ranking
- grounded repository Q&A with explicit insufficient-evidence handling
- deterministic engineering plans with affected components, risk, evidence, verification criteria, and uncertainty

### Agent Change Contract and verification
- repository-bound AgentChangeContract
- prepared Git HEAD, planned-path, expected-component, verification-command, evidence, and architecture binding
- deterministic SHA-256 contract fingerprint
- persisted output/agent-change-contract.json
- contract tamper, plan mismatch, repository mismatch, stale-HEAD, and unexpected-change detection
- verification against the persisted contract rather than a replacement derived from the mutated repository

**Authoritative rule:** the persisted contract is the verification boundary.

### AI
- optional Gemini and Anthropic/Claude provider paths
- bounded repository-derived context
- separate grounded-AI path with evidence-ID citations
- explicit separation between deterministic repository facts and model reasoning

AI is disabled unless configured.

### MCP and REST
- local stdio MCP server with repository analysis, impact, architecture, PR Intelligence, evidence, preparation, change safety, and verification
- local path-safety boundary and remote-URL rejection
- persisted change-contract retrieval
- local REST health/readiness/liveness, analysis, reports, Q&A/AI, organization analysis, impact, and PR Intelligence
- input validation, bounded concurrency/rate limiting, and sanitized public errors

The local MCP/REST boundaries are intended for trusted local/internal use.

### Installation and verification
- one-command installers for Linux x64, macOS x64, macOS arm64, and Windows x64
- SHA-256 verification against published release checksums
- bundled Java runtime in released platform archives
- first-run doctor diagnostics
- clean-environment onboarding and release-audit validation
- CI coverage across JVM tests, CLI behavior, generated artifacts, intelligence, architecture, evidence, prepare/verify, REST, Linux, Windows, macOS x64, macOS ARM64, live-repository checks, and release audit

GitHub Actions is the authoritative automated execution environment for release verification.

## Current release line

**v0.8.2** is the latest published Vericore release, published on 2026-10-04. Main contains post-release development and must not be treated as the contents of the published v0.8.2 artifact set.

## Current limitations

- Analysis focuses on Java and Kotlin.
- Kotlin parsing has known limitations for complex syntax.
- Remote repository URLs are rejected by local server endpoints.
- Local REST does not provide authentication, authorization, tenant isolation, or deployment-level TLS.
- Local MCP assumes a trusted caller.
- The planner is read-only.
- Autonomous source-code modification is not implemented.
- Production telemetry integrations are not implemented.
- Organization-wide governance and cross-repository intelligence are not implemented.
- Historical analysis provides deterministic source-history metrics but does not reconstruct full semantic dependency graphs for arbitrary historical commits.
- Architecture contract history records deterministic evaluation decisions, but explicit human approval/exception workflows are not implemented.
- The Agent Change Contract is a verification boundary, not an authorization system or autonomous coding mechanism.

## Future direction

Future work is not an API contract. A capability moves into **Implemented** only when the behavior exists in code and is supported by tests, CI, or release validation.

Current priorities are:
1. a clear unified developer/agent workflow;
2. native agent integrations and agent-facing skills;
3. stronger verification artifacts and incremental verification;
4. deterministic explain capabilities;
5. evidence-grounded project memory and feature-gap analysis;
6. broader distribution and repeatable benchmarks.

Team dashboards, hosted control planes, and commercial packaging are deliberately later concerns.
