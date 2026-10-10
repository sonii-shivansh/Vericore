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

## Roadmap and current priorities

The master strategic roadmap is documented in [Product Direction](PRODUCT_DIRECTION.md) and follows Phase 0 through Phase 9. It is a plan, not a statement that every listed capability is already shipped.

**Immediate priority: Phase 0 — Foundation Freeze & Truth.** Before adding major features, close the foundation gaps identified by code, regression tests, and release gates:

1. Complete the current CI/release baseline and preserve the distinction between published v0.8.2 and unreleased main.
2. Reconcile roadmap/product documentation with the master Phase 0–9 sequence.
3. Inventory JSON and persisted artifact schemas, versions, and compatibility rules.
4. Establish deterministic ordering and regression fixtures for equal-score/tied results.
5. Verify cache and evidence freshness against actual source state.
6. Make parser diagnostics and other machine-readable quality metrics consistent across commands.
7. Catalogue golden repositories, benchmark fixtures, and supported/unsupported boundaries.
8. Define a reproducible baseline for the public verification benchmark.

Only after those items have explicit acceptance criteria and supporting evidence should work advance to Phase 1's unified product journey, followed by the agent adapter, stronger contract, independent verification, receipts/provenance, multi-agent and cross-repository work, evidence-first explanation/memory, ecosystem distribution, and enterprise assurance in the sequence defined by the roadmap.

