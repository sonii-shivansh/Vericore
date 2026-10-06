# Implementation Status & Roadmap

> This document distinguishes what Vericore implements today from explicit future work.

- **V2-003 first-run onboarding**: the existing `setup` flow is exposed in the installed CLI, paired with `doctor`, and covered by clean-environment onboarding E2E validation.

- **V2-004 cross-platform verification hardening**: Windows Maven/Gradle wrapper detection, native `cmd` verification command generation, safe executor validation, and Windows prepare→verify CI coverage are implemented.

- **V3-001 Agent Verification flagship flow**: MCP `prepare` now persists the same repository-scoped preparation, plan, and Agent Change Contract artifacts used by the CLI; agents can declare `plannedPaths` before editing, with a dedicated end-to-end gate covering MCP prepare → repository modification → contract retrieval → MCP verify.

- **V2-005 documentation parity**: CI now builds the CLI and verifies that the runtime command surface is represented in `docs/CLI.md`.

## How to read this document

- **Implemented** — present in the repository and covered by tests, CI, or release-gate validation.
- **Current limitation** — an intentional or known boundary of the shipped implementation.
- **Future direction** — not shipped; do not depend on it as an existing capability.

## Implemented capabilities

### Repository intelligence

- Java and Kotlin source discovery and parsing
- configurable exclusions and file limits
- parallel parsing and content-based caching
- dependency graph construction
- wildcard-import handling
- cycle detection
- PageRank knowledge hotspots
- Git authorship, churn, modification-time, and recent-change analysis
- learning-path generation
- self-contained HTML reporting

### Change and PR intelligence

- dependency-aware change-impact analysis
- changed-file analysis
- blast-radius signals
- deterministic PR Intelligence
- change-size and risk signals
- architecture findings
- test recommendations/signals
- machine-readable intelligence artifacts

### Architecture intelligence

- architecture-oriented dependency findings
- cycle and boundary signals
- deterministic architecture artifacts
- architecture drift comparison against an explicit baseline artifact
- deterministic drift reporting for findings, cycles, layer counts, and dependency-edge additions/removals
- deterministic architecture contracts for CI/governance enforcement
- `architecture-contract` CLI command
- contract templates with explicit finding/cycle/severity limits
- deterministic contract decision records with repository commit, contract/result digests, and stable decision IDs
- idempotent contract decision history persistence via `--record`
- architecture artifact schema `1.1` with persisted dependency edges

### Installation and first-run onboarding

- one-command installers for Linux x64, macOS x64, macOS arm64, and Windows x64;
- SHA-256 verification against published release checksums before installation;
- bundled Java runtime for released platform archives, avoiding a separate JDK for normal end-user execution;
- first-run `doctor` diagnostics that work outside a Git repository;
- `doctor --path <directory>` for explicit repository diagnostics;
- warning-versus-failure semantics for missing optional AI credentials and Git repository context;
- clean-environment onboarding E2E coverage and release-audit regression coverage for the installation/Doctor contract.

## Engineering context and Reality

- versioned deterministic engineering-context snapshots
- repository-relative file fingerprints
- repository `HEAD` capture when Git metadata is available
- working-tree dirty/change-path state
- stable snapshot digests
- deterministic snapshot-to-snapshot diffs
- versioned `EngineeringRealitySnapshot`
- deterministic composition of analysis + repository-context identities
- repository commit binding
- stable analysis/context digests
- explicit `realityDigest`
- semantic evidence graph digest and cardinality bound into Engineering Reality
- protection against analysis wall-clock timestamps changing identity
- `context-snapshot`, `context-diff`, `evidence-graph`, and `reality` CLI commands

### Semantic evidence graph

- bounded deterministic relationship graph over existing evidence
- repository and observed-commit binding for every evidence node
- deterministic node and edge ordering
- duplicate, dangling-edge, self-edge, stale-commit, and cross-repository validation
- deterministic SHA-256 graph identity
- cross-feature resolution by repository source reference
- serializable `semantic-evidence-graph.json` artifact
- stable graph schema version and cardinality metadata
- release-gate validation of the materialized graph on a real Git repository

The graph does not invent repository facts and is not a replacement for the dependency graph.

### Grounded evidence and Q&A

- versioned evidence contracts
- bounded evidence generation
- repository-relative citation paths
- deterministic citation ordering
- evidence size limits
- repository question intent classification
- dependency, risk, impact, architecture, PR, and test-oriented retrieval intents
- deterministic evidence ranking
- bounded result sets
- explicit insufficient-evidence handling
- `repo-qa` CLI command

### Engineering Planner

- versioned engineering-plan model
- deterministic plan synthesis from grounded evidence
- affected components and risk
- implementation steps
- evidence IDs
- verification criteria
- uncertainty handling
- contract fingerprint binding
- bounded input/output
- `plan` CLI command

The planner is read-only.

### Agent Change Contract and verification

- versioned repository-bound `AgentChangeContract`
- canonical repository identity
- prepared Git `HEAD` binding
- planned-path and expected-component scope
- verification-command and evidence binding
- architecture expectation binding
- deterministic SHA-256 fingerprint
- persisted `output/agent-change-contract.json` artifact from `prepare`
- verification against the persisted contract
- contract tamper detection
- plan mismatch detection
- repository mismatch detection
- stale-HEAD detection
- live mutation tests for tampering and unexpected source changes

**Authoritative rule:** the persisted contract is the verification boundary. Verification does not silently reconstruct a replacement contract from a mutable plan, and `vericore_get_change_contract` retrieves the persisted artifact rather than generating a replacement.

### Provenance and temporal intelligence

- versioned `DecisionProvenance`
- stable SHA-256 provenance IDs
- repository `HEAD` capture through read-only Git metadata
- explicit unknown-Git state
- provenance attached to prepare and verify results
- idempotent architecture decision persistence
- Git-history evolution analysis
- deterministic time-based commit sampling
- source line counts read directly from Git objects
- cumulative source-file change-frequency hotspots
- deterministic fallback hotspots
- safe dirty-working-tree analysis without destructive checkout

### AI integration

- optional provider-aware AI analyzer
- Gemini and Anthropic/Claude provider paths in `AICodeAnalyzer`
- Gemini-first interactive setup flow and CLI assistant path
- canonical Gemini 3.8 request configuration without legacy sampling controls
- bounded repository-derived context for model calls
- a separate `GroundedAIService` path that can enforce evidence-ID citations
- explicit separation between deterministic repository facts and model reasoning
- semantic evidence graph identity available to grounded Engineering Reality

AI is disabled unless configured.

### MCP

- local stdio JSON-RPC MCP server
- repository analysis, impact, architecture, and PR Intelligence
- Engineering Reality and context snapshot/diff tools
- architecture drift and contract tools
- grounded evidence, prepare, change-safety, and verify tools
- local path-safety boundary
- rejection of remote repository URLs
- persisted change-contract retrieval for the verification boundary

The MCP server is a trusted local integration without authentication or tenant isolation. Legacy `codecontext_*` names remain as compatibility aliases and emit deprecation warnings.

### Local REST API

- service banner
- health/readiness/liveness
- local repository analysis
- generated report serving
- repository question/AI flow
- organization analysis with bounded concurrency
- change-impact and PR Intelligence
- path validation
- rate limiting
- sanitized public errors
- organization-analysis hotspots serialized as explicit `{file, score}` objects

The server is intended for trusted local/internal use and does not provide authentication, authorization, tenant isolation, or deployment-level TLS.

### CI and release verification

The clean-environment workflows validate:

- JVM compilation and tests
- lint/code quality
- CLI behavior
- generated artifacts
- self-analysis
- PR Intelligence
- Architecture Intelligence
- architecture drift/contracts
- engineering-context snapshots/diffs
- semantic evidence graph materialization and invariants
- prepare/verify contracts
- REST API behavior
- Linux x64
- Windows x64
- macOS x64
- macOS ARM64
- live repository mutation and immutability checks
- deterministic release audit

GitHub Actions is the authoritative automated execution environment for release verification.

## Current release line

`v0.8.2` is the **latest published Vericore release**, published on 2026-10-04. The current `main` branch is 15 commits ahead of the `v0.8.2` tag and contains post-release hardening and VCORE validation workflows. Those changes are development work and must not be described as part of the published `v0.8.2` artifact set. Earlier public releases were published under the former project name.

## Current limitations

These are known boundaries of the current implementation:

- analysis focuses on Java and Kotlin;
- Kotlin parsing has known complex-syntax limitations;
- remote repository URLs are not accepted by the local server endpoints;
- the REST server does not provide authentication or multi-tenant authorization;
- the planner is read-only;
- autonomous source-code modification is not implemented;
- production telemetry integrations are not implemented;
- organization-wide governance and cross-repository intelligence are not implemented;
- temporal archaeology provides deterministic source-history metrics but does not reconstruct full semantic dependency graphs for arbitrary historical commits;
- architecture contract history records deterministic evaluation decisions, but explicit human approval/exception workflows are not implemented;
- the Agent Change Contract is a deterministic verification boundary, not an authorization system or autonomous coding mechanism.

## Future direction

Future work must be documented here as future work until code, tests, and release validation establish the capability. Do not use roadmap entries as API contracts.

When a future item becomes implemented, move it into the appropriate **Implemented** section in the same pull request that changes the behavior.
