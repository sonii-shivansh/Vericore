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
- schema 3.0 contracts embed a validated, deterministic per-path candidate baseline whose digest is fingerprint-bound; prepare fails on input drift during analysis, and full plus contract-only verification reject unplanned prepare-to-verify drift (including ignored/untracked inputs)
- legacy schema 2.0 contracts without a baseline fail closed and require re-preparation; verification receipt schema 1.5 records baseline digest validity, unplanned drift paths, JUnit report provenance, and optional agent/session/event context
- contract tamper, plan mismatch, repository mismatch, stale-HEAD, and unexpected-change detection
- candidate repository-input state digests captured before analysis, before declared verification commands, and after those commands; relevant candidate drift fails closed
- structured specification evaluation records stable per-criterion IDs; numeric and Boolean metric criteria are evaluated against current-candidate grounded evidence; malformed expressions, unbound/dangling citations, missing metrics, and type mismatches fail closed; unsupported free-form criteria remain `NOT_EVALUATED` or `INCONCLUSIVE`; any failed criterion makes the overall verification `FAIL`, while unevaluated/inconclusive criteria require review; contract-only verification leaves metrics `NOT_EVALUATED` because it does not rebuild candidate evidence
- explicit JUnit testcase bindings use `junit-test-case:<class>#<name>` selectors; only fresh Gradle/Maven report bytes with stable candidate SHA/state are accepted, report digests and execution IDs are preserved, and passing testcase evidence does not prove natural-language requirement coverage
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

The latest confirmed published release is `v1.0.1`, published on 2026-10-20. Changes on an open pull request remain unreleased until the release tag and assets exist. Consult the [GitHub Releases page](https://github.com/sonii-shivansh/Vericore/releases) for subsequent publication records. Source version strings alone are not proof that an artifact has been published. A new release requires a successful machine-readable readiness certificate and official release workflow for the exact candidate SHA.

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

**Phase 0 — Foundation Freeze & Truth is complete for its documented scope.** Its exit decision, acceptance evidence, and limitations are recorded in [Phase 0 Foundation Checklist](PHASE0_FOUNDATION_CHECKLIST.md) and [Phase 0 Benchmark Baseline](PHASE0_BENCHMARK_BASELINE.md). That closeout does not automatically certify a release; each published artifact requires its own exact-candidate readiness evidence.

**Current operational priority: keep source, documentation, and published artifacts aligned.** Treat every candidate as unreleased until its tag and assets exist. Run the required gates on the exact candidate SHA before publication, and re-certify after any subsequent change.

**Current product focus: structured change specification.** The prepare/plan CLI and MCP paths record explicit required behaviors, forbidden behaviors, protected invariants, acceptance criteria, and required evidence, with the specification bound to the plan and contract fingerprint. The deterministic evaluator supports numeric and Boolean metric comparisons against current-candidate evidence, plus the narrow `ALL_BOUND_COMMANDS_SUCCEEDED` rule for required-evidence criteria linked to declared verification commands, and `ALL_BOUND_JUNIT_CASES_PASSED` for explicitly selected test cases in fresh Gradle/Maven reports. Command success proves exit status only; a passing named test proves that case passed but not that it covers the requested behavior or establishes semantic correctness. Natural-language obligations without a suitable evaluator remain `NOT_EVALUATED` or `INCONCLUSIVE`; citation presence alone is not proof, and contract-only verification does not rebuild candidate evidence.

### Ongoing regression expectations

Phase 0 closure does not mean these controls should be removed. Keep them covered as normal maintenance work:

- validate schemas and versioned golden fixtures;
- preserve deterministic ordering and stable result identities;
- test evidence freshness after source changes, including working-tree changes that do not move Git `HEAD`;
- keep parser diagnostics and failure semantics consistent across commands;
- retain mutation, tampering, stale-state, and repository-binding regression coverage;
- document supported boundaries and keep benchmark results tied to their exact inputs and candidate SHA.

The operational sequence is therefore: validate post-release fixes, certify any intended next release on its exact candidate SHA, publish without rewriting prior tags, then continue product-journey work. Later phases follow the ordering and acceptance discipline in [Product Direction](PRODUCT_DIRECTION.md).
