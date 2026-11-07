# VERICORE — Comprehensive Project Knowledge & Onboarding Document
*Document ID: VERICORE_PROJECT_KNOWLEDGE.md*  
*Target Release Line: v2.0.0 (Latest published baseline: v1.0.1 on 2026-10-20)*  
*System Baseline: Java 25 LTS | Kotlin 2.4.21 / JVM 25 | Gradle 9.7.1*  
*Authoritative Rule: Deterministic repository evidence first; optional AI reasoning second.*

---

## 1. Product Overview

### What is Vericore?
**Vericore** is an open-source, local-first engineering intelligence and verification platform for AI-assisted software development. Its primary mission is summarized by its core thesis:

> **"AI writes the code. Vericore verifies the change."**

In modern software development, AI coding agents (Claude Code, Cursor, Codex, Gemini/Antigravity, GitHub Copilot) can produce code changes rapidly. However, traditional code review and static analysis run post-facto—after the repository has already been mutated—and agents often hallucinate completion, wander outside intended file boundaries, break architectural boundaries, or operate on stale repository state.

Vericore introduces a **pre-change to post-change verification loop**:
1. **Before the edit:** Developers or agents record human intent and planned scope (`vericore prepare`), capturing an immutable, repository-bound **Agent Change Contract** and a cryptographic baseline of candidate repository inputs.
2. **During the edit:** The AI coding agent or developer mutates the codebase.
3. **After the edit:** Vericore verifies the mutated repository strictly against the **original persisted contract** (`vericore verify`), evaluating boundary compliance, dependency impact, blast radius, architecture drift, candidate state stability, declared verification commands, and test case evidence.

Vericore operates **fail-closed**: if the contract is missing, tampered with, stale, or if files outside the planned boundary were touched, verification fails.

---

## 2. Problem Statement & Target Users

### The Problem
When AI coding agents edit code, they frequently:
- **Expand scope silently:** Edit configuration, utilities, or unrelated modules outside the task boundary.
- **Hallucinate verification:** Assert that tests passed or requirements were met without executing verification or validating coverage.
- **Introduce architectural drift:** Add cyclic dependencies, violate layering rules, or bypass domain boundaries.
- **Operate on stale context:** Generate code based on outdated repository state or commit revisions.
- **Fail post-facto review:** Reviewers receive a sprawling diff with no verifiable link between the original intent and the resulting changes.

### Target Users
1. **Software Engineers & Tech Leads:** Seeking local-first, deterministic safeguards before accepting AI-generated pull requests or diffs.
2. **AI Coding Agents & Harnesses:** Agents (via MCP) needing bounded repository context, pre-flight change preparation, and automated verification feedback.
3. **Engineering Organizations & Platform Teams:** Teams requiring automated PR intelligence, architecture contracts, and verifiable change receipts in CI/CD (GitHub Actions).

---

## 3. Technology Stack & Runtime Requirements

### Core Runtime & Build Toolchain
- **Language:** Kotlin 2.4.21 (JVM 25 bytecode target, `kotlinx.serialization`).
- **Runtime Environment:** Java 25 LTS or newer (`--enable-native-access=ALL-UNNAMED`). Pre-JVM start scripts and runtime startup checks fail closed if Java < 25.
- **Build System:** Gradle 9.7.1 (`build.gradle.kts` with custom Windows/Unix launcher scripts enforcing Java 25).
- **CLI Framework:** Ajalt Clikt 5.1.0 (`com.github.ajalt.clikt`).
- **AST & Source Analysis:**
  - Java: JavaParser (`javaparser-symbol-solver-core:3.28.2`) with automatic Java language-level detection.
  - Kotlin: `KotlinRegexParser` (fast regex-based extraction of packages, imports, aliases, and KDoc blocks).
- **Git Engine:** Eclipse JGit 7.8.0 (`org.eclipse.jgit`).
- **Graph Algorithms:** JGraphT Core 1.5.3 (`DefaultDirectedGraph`, `PageRank`, `CycleDetector`, `KosarajuStrongConnectivityInspector`).
- **HTTP / REST Server:** Ktor 3.6.1 Server Netty (`ktor-server-core`, `ktor-server-netty`, `ktor-server-cors`, `ktor-serialization-kotlinx-json`).
- **Logging:** `io.github.oshai:kotlin-logging-jvm:8.0.4` with `ch.qos.logback:logback-classic:1.6.6`.
- **HTML Reporting:** `org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0` (self-contained HTML reports with embedded SVG/graph visualization, zero third-party CDN calls).
- **Testing:** Kotest 6.2.6 (JUnit5 runner, assertions, property testing) and MockK 1.14.11.

---

## 4. Complete Application Architecture & Data Flow

### Architecture Layers

```
┌────────────────────────────────────────────────────────────────────────┐
│                   ADAPTERS / INTERFACES LAYER                          │
│   CLI (Clikt)    │   MCP Server (stdio)   │   REST Server (Ktor)       │
│   27 Subcommands │   16 RPC Tools         │   Health/Analyze/Impact    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                  WORKFLOW & SAFETY CONTRACT LAYER                      │
│   EngineeringPreparation  │  EngineeringVerification  │ ChangeSafety   │
│   AgentChangeContract     │  CandidateStateSnapshotter │ Receipt Gen   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                   DETERMINISTIC INTELLIGENCE LAYER                     │
│   RobustDependencyGraph (JGraphT) │ ChangeImpactEngine (Blast Radius)  │
│   ArchitectureIntelligenceEngine  │ PRIntelligenceEngine               │
│   EngineeringRealityEngine        │ SemanticEvidenceGraph              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                   SCANNING, PARSING & STORAGE LAYER                    │
│   RepositoryScanner               │ JGit GitAnalyzer                   │
│   JavaRealParser (JavaParser)     │ KotlinRegexParser                  │
│   Content Cache (CacheManager)    │ File-based Artifact Store          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Optional & Strictly Bounded)
┌───────────────────────────────────▼────────────────────────────────────┐
│                         AI BOUNDARY LAYER                              │
│   GroundedAIService (Prompt grounding & Citation checking)             │
│   AICodeAnalyzer (Gemini 3.8 Flash / Claude 3.7 Sonnet)                │
└────────────────────────────────────────────────────────────────────────┘
```

### Core Data Flow
1. **Scanning & Ingestion:** `RepositoryScanner` reads `.java` and `.kt` source files respecting exclusions (default up to 50,000 files).
2. **Parsing & Caching:** `CodeParallelParser` dispatches files across coroutines. `CacheManager` checks `.vericore/cache/<sha256>.json` to reuse parse trees for unmodified files.
3. **Graph Construction:** `RobustDependencyGraph` builds an in-memory directed graph of file dependencies, resolving explicit and wildcard (`.*`) imports and package references. Computes PageRank centrality and detects circular dependency cycles.
4. **Git Mining:** `OptimizedGitAnalyzer` and `TemporalAnalyzer` query commit history using JGit to compute code churn, change frequency, author count, and historical timelines without modifying the working tree.
5. **Intelligence Snapshot:** `AnalysisSnapshotBuilder` compiles deterministic facts into `output/analysis-snapshot.json`.
6. **State Binding (Engineering Reality):** `EngineeringRealityEngine` computes a state identity (`realityDigest`) combining analysis digest, context digest, and evidence graph digest—excluding wall-clock time so equivalent states share the same identity.
7. **Preparation:** `EngineeringPreparation` records the pre-change baseline (`CandidateStateManifest`), generates an evidence-backed `EngineeringPlan`, and mints an immutable `AgentChangeContract`.
8. **Verification:** `EngineeringVerification` captures pre-analysis, pre-command, and post-command candidate state digests. It compares the working tree against planned paths, executes declared verification commands, parses fresh JUnit XML reports, evaluates structured specifications, and emits `output/verification-receipt.json`.

---

## 5. Storage Design & Persistence Model

### No SQL/RDBMS Database
Vericore intentionally does not use an RDBMS (PostgreSQL, MySQL, SQLite). It is **local-first, file-based, and content-addressable**:

1. **Working Directories:**
   - `.vericore/`: Tool workspace containing local configuration, sessions, and cached parsing trees (`.vericore/cache/`).
   - `output/`: Project-local artifact directory for generated JSON snapshots, contracts, plans, receipts, and HTML reports.
2. **Content-Addressable Parse Cache:**
   - Key: `SHA-256("2:<canonicalPath>:<fileContentSHA256>")`.
   - File: `.vericore/cache/<key>.json`.
   - Concurrency: `ConcurrentHashMap` with atomic file replacements (`StandardCopyOption.ATOMIC_MOVE`).
3. **Deterministic Schemas & Artifacts (`docs/schemas/`):**
   - `output/analysis-snapshot.json` (schema 1.0): AST facts, metrics, dependencies, hotspots.
   - `output/engineering-plan.json` (schema 1.0): Plan steps, concerns, risk levels, build commands, specification.
   - `output/agent-change-contract.json` (schema 3.0 / 3.1): Fingerprint-bound change contract with embedded candidate baseline manifest and optional agent context.
   - `output/engineering-preparation.json` (schema 1.2): Aggregated prepare payload.
   - `output/verification-receipt.json` (schema 1.5): Cryptographic record of verification decisions, baseline validity, unplanned drift paths, and JUnit test case provenance.
   - `output/index.html`: Fully self-contained visual audit report.

---

## 6. Key Modules & Source File Responsibilities

| Package / File | Core Responsibility |
|---|---|
| `com.vericore.Main.kt` | Application entry point; enforces Java 25 runtime checks; registers 27 CLI subcommands. |
| `cli/MainCommand.kt` & commands | Clikt CLI command implementations (`InitCommand`, `ScanCommand`, `ImprovedAnalyzeCommand`, `PrepareCommand`, `VerifyCommand`, etc.). |
| `cli/CommandEnvelope.kt` | Standardized universal JSON envelope contract (`CommandEnvelope<T>`) for all machine-readable CLI outputs. |
| `cli/ErrorHandler.kt` | Maps domain and system exceptions to deterministic exit codes (0 = Success, 1 = Verification failure / drift, 2 = Usage / config error, 3 = Fatal system error). |
| `core/scanner/RepositoryScanner.kt` | Discovers Java/Kotlin files, applying pattern exclusions and maximum file thresholds. |
| `core/scanner/OptimizedGitAnalyzer.kt` | Extracts Git churn, author metadata, and commit frequencies using JGit. |
| `core/parser/JavaRealParser.kt` | AST parsing for Java using JavaParser; extracts package, imports, types, javadocs, and syntax warnings. |
| `core/parser/KotlinRegexParser.kt` | Fast regex parser for Kotlin files extracting package declarations, imports, aliases, and KDoc blocks. |
| `core/parser/ParserFactory.kt` | Routes `.java` to `JavaRealParser` and `.kt` to `KotlinRegexParser`. |
| `core/graph/RobustDependencyGraph.kt` | Constructs JGraphT directed dependency graph; runs PageRank centrality and Kosaraju cycle detection. |
| `core/cache/CacheManager.kt` | Thread-safe, content-hashed disk cache for parsed file representations. |
| `core/intelligence/ChangeImpactEngine.kt` | Calculates blast radius and reverse-dependency impact depths (direct, transitive, test candidates). |
| `core/intelligence/PRIntelligence.kt` | Deterministic pull request analysis; surfaces `CHANGE_UNRESOLVED`, `IMPACT_BROAD`, `HOTSPOT_MUTATION`, `TEST_MISSING`. |
| `core/intelligence/ArchitectureIntelligence.kt`| Enforces layer rules (e.g. api → application → domain) and detects cross-layer violations and forbidden boundaries. |
| `core/intelligence/ArchitectureContract.kt` | Evaluates architecture findings against contract constraints (`maxFindings`, `maxCycles`, `allowedSeverities`). |
| `core/intelligence/ArchitectureDrift.kt` | Compares baseline vs. current architecture snapshots to detect added/removed findings, cycles, and dependency edges. |
| `core/planner/EngineeringPlanner.kt` | Generates bounded, evidence-backed implementation plans and detects build tools (`BuildSystemDetector`). |
| `core/planner/ChangeSpecification.kt` | Caller-authored specifications: required/forbidden behaviors, invariants, acceptance criteria, and evidence bindings. |
| `core/workflow/AgentChangeContract.kt` | Represents the immutable change contract; computes SHA-256 fingerprint over all canonical fields and baseline manifest digest. |
| `core/workflow/CandidateStateSnapshotter.kt` | Recursively hashes all tracked and untracked repository inputs (excluding build/output dirs) to detect drift. |
| `core/workflow/ChangeSafety.kt` | Validates actual Git working tree changes against planned paths; checks for unexpected edits and file deletions. |
| `core/workflow/EngineeringPreparation.kt` | Orchestrates the `prepare` pipeline: snapshotting, planning, baseline capture, and contract persistence. |
| `core/workflow/EngineeringVerification.kt` | Orchestrates the `verify` pipeline: state stability check, command execution, JUnit report collection, and criterion evaluation. |
| `core/workflow/ChangeSpecificationEvaluator.kt` | Deterministically evaluates metric criteria, `ALL_BOUND_COMMANDS_SUCCEEDED`, and `ALL_BOUND_JUNIT_CASES_PASSED`. |
| `core/workflow/VerificationReceipt.kt` | Mints durable verification receipt schema 1.5. |
| `core/qa/RepositoryQA.kt` | Deterministic keyword and regex-based repository Q&A; ranks GroundedEvidence citations without requiring an LLM. |
| `core/reality/EngineeringReality.kt` | Computes state identity (`realityDigest`) uniting analysis and working tree context without timestamps. |
| `core/evidence/SemanticEvidenceGraph.kt` | Bounded graph of evidence nodes and edges linking repository facts, commits, and citations. |
| `core/ai/AICodeAnalyzer.kt` | HTTP client integration for Google Gemini (`gemini-3.8-flash`) and Anthropic Claude (`claude-3-7-sonnet`). |
| `core/ai/GroundedAIService.kt` | Constructs evidence-grounded prompts and validates that model answers cite valid evidence IDs (`GroundingAssessor`). |
| `mcp/McpProtocol.kt` | JSON-RPC 2.0 stdio server implementing the Model Context Protocol for AI coding assistants. |
| `mcp/EngineeringContextGateway.kt` | Bridges MCP tool calls to core deterministic workflows (prepare, verify, change safety, reality, drift). |
| `server/VericoreServer.kt` | Ktor Netty REST API providing HTTP endpoints for local health, analysis, impact, architecture, and reports. |
| `server/RateLimiter.kt` | In-memory token bucket rate limiter for REST routes. |
| `output/ReportGenerator.kt` | Generates self-contained HTML dashboards with embedded SVG graph visualizations. |

---

## 7. Interfaces & API Contracts

### 1. Command-Line Interface (CLI)
Global syntax: `vericore [OPTIONS] COMMAND [ARGS...]`.  
All commands support `--json` and emit the standardized `CommandEnvelope<T>` envelope on stdout:
```json
{
  "schemaVersion": "1.0",
  "command": "verify",
  "status": "PASS",
  "summary": "Working tree verification against engineering plan and change contract",
  "repository": { "name": "Vericore", "path": "C:/workspace/Vericore" },
  "data": { ... },
  "findings": [],
  "recommendations": [],
  "artifacts": [{ "kind": "receipt", "path": "output/verification-receipt.json" }],
  "nextStep": "Review receipt and merge",
  "execution": { "exitCode": 0 }
}
```

Key CLI Commands:
- `vericore init`: Initializes `.vericore.json` and `.vericore/` workspace.
- `vericore scan`: Fast repository discovery and language breakdown.
- `vericore analyze`: Full AST parsing, PageRank, dependency graph, and HTML report generation.
- `vericore inspect`: Compact repository health and risk summary.
- `vericore repo-qa "<question>"`: Grounded, deterministic repository Q&A without AI.
- `vericore ask "<question>"`: Grounded AI Q&A via Gemini/Claude citing evidence IDs.
- `vericore impact <path>`: Reverse-dependency blast radius analysis for changed files.
- `vericore architecture`: Evaluates package layering and cycle detection.
- `vericore architecture-drift`: Compares current architecture against a saved baseline.
- `vericore architecture-contract`: Verifies architecture against contractual thresholds.
- `vericore plan "<summary>"`: Generates an evidence-backed engineering plan.
- `vericore prepare "<summary>" --planned-path <path>`: Persists the pre-change baseline and Agent Change Contract.
- `vericore verify [--contract-only]`: Verifies current working tree against the persisted contract.
- `vericore mcp`: Starts the stdio JSON-RPC MCP server.
- `vericore server`: Starts the local Ktor REST server.
- `vericore doctor`: Validates Java 25 runtime, Git repository health, and configuration.

### 2. Model Context Protocol (MCP) Server (`vericore mcp`)
Protocol version: `2025-11-25`.  
Available Tools (16):
1. `vericore_analyze_repository`: AST and dependency graph analysis.
2. `vericore_impact_analysis`: Reverse dependency and blast radius calculation.
3. `vericore_architecture_analysis`: Layering, cycles, and coupling analysis.
4. `vericore_pr_intelligence`: PR change risk, test candidate mapping.
5. `vericore_review`: Deterministic review with optional grounded AI review.
6. `vericore_recommendations`: Actionable codebase recommendations.
7. `vericore_get_engineering_reality`: State snapshot and reality digest.
8. `vericore_get_context_snapshot`: Repository file fingerprints and Git HEAD.
9. `vericore_get_context_diff`: Semantic diff between two snapshots.
10. `vericore_get_architecture_drift`: Drift detection against an architecture baseline.
11. `vericore_get_architecture_contract`: Contractual validation.
12. `vericore_prepare_change`: Pre-change preparation; mints contract and baseline.
13. `vericore_get_change_contract`: Retrieves the persisted contract (read-only, never generates a new one).
14. `vericore_get_evidence`: Grounded evidence citations for a proposed change.
15. `vericore_change_safety`: Working tree check against planned paths.
16. `vericore_verify_change`: Full post-change verification.

### 3. REST Server (`vericore server`)
Endpoints:
- `GET /`: Status check.
- `GET /health`, `/health/live`, `/health/ready`: Service readiness and version probes.
- `GET /reports/{filename}`: Serves generated HTML reports (strictly resolves UUID filenames under `output/`).
- `POST /analyze`: Triggers repository analysis.
- `POST /impact`: Calculates reverse dependencies for supplied paths.
- `POST /architecture`: Architecture analysis for a repository.
- `POST /pr-intelligence`: Evaluates Git revisions or working tree.
- `POST /ask`: Grounded AI query.
- `POST /analyze-org`: Concurrent multi-repository scanning.

---

## 8. Current Implementation Status vs. Roadmap

| Capability Area | Current Status | Notes & Limitations |
|---|---|---|
| **Java Parsing** | Implemented (Production) | Full AST via JavaParser; symbol solving; detects Java language level. |
| **Kotlin Parsing** | Implemented (Regex-based) | Extracts packages, imports, and KDocs. Not a full AST compiler front-end. |
| **Dependency Graph** | Implemented (Production) | JGraphT directed graph; resolves imports, wildcards, and same-package types; PageRank; cycles. |
| **Git & Temporal Analysis**| Implemented (Production) | JGit log mining; non-destructive commit tree sampling; author churn. |
| **Change Safety Loop** | Implemented (Production) | `prepare` → contract → edit → `verify`. Embeds pre-change baseline; detects drift. |
| **Candidate State Hashing**| Implemented (Production) | Captures input hashes before analysis, before verification commands, and post-commands. |
| **Specification Evaluator** | Implemented (Narrow) | Evaluates `metric:`, `ALL_BOUND_COMMANDS_SUCCEEDED`, and `ALL_BOUND_JUNIT_CASES_PASSED`. |
| **MCP Integration** | Implemented (Production) | 16 tools over stdio; compatible with Cursor, Claude Code, Antigravity. |
| **Local REST Server** | Implemented (Internal) | Designed for local loopback; no auth/authz or multi-tenant isolation. |
| **GitHub Action** | Implemented (Production) | Composite action (`action.yml`) supporting analyze, prepare, verify, and PR intelligence. |
| **AI Providers** | Implemented (Optional) | Gemini (`gemini-3.8-flash`) and Anthropic (`claude-3-7-sonnet`). Opt-in only. |
| **Other Languages** | Planned (Phase 8+) | TypeScript, Python, Go, C# not yet supported in scanner. |
| **Autonomous Code Editing**| Intentionally Excluded | Vericore verifies changes; it does not autonomously mutate user source code. |
| **Multi-Agent Coordination**| Roadmap (Phase 6) | Conflict detection across multiple concurrent agents working on shared branches. |
| **Cross-Repo Intelligence**| Roadmap (Phase 6 & 9) | Organization Analyzer exists as an MVP; cross-repo dependency coordination is planned. |

---

## 9. Strategic Technical & Architectural Analysis

### Technical Strengths
1. **Uncompromising Determinism:** Unlike AI-based review tools that give probabilistic, non-reproducible answers, Vericore's fact layer is 100% deterministic, backed by graph theory and cryptographic digests.
2. **Fail-Closed Verification Boundary:** The Agent Change Contract cannot be silently replaced after an edit. If an agent wanders or touches unexpected files, verification fails.
3. **Candidate Input Integrity:** Hashes all repository inputs before analysis and before/after verification commands. Timed-out commands have their entire process tree terminated to prevent post-timeout repository mutation.
4. **Local-First & Zero Leakage:** Completely functional without network access or cloud accounts; code is never transmitted externally unless the user explicitly enables an AI provider.
5. **Unified Agent Access via MCP:** Exposes the exact same verification boundary to agents via MCP as developers use via the CLI.

### Architectural Weaknesses & Technical Debt
1. **Kotlin Parsing Limitations:** Using regex for Kotlin parsing (`KotlinRegexParser`) means complex Kotlin constructs (companion objects, internal DSLs, function references without explicit types, delegated properties) cannot participate in type-level dependency graphs.
2. **Language Monoculture:** The scanner is restricted to Java and Kotlin (`.java`, `.kt`). In modern polyglot stacks (e.g., Kotlin backend with TypeScript frontend), changes crossing language boundaries cannot be fully verified.
3. **Local REST Server Security:** The Ktor server lacks authentication, authorization, TLS, or tenant isolation. It is strictly limited to localhost trusted environments.
4. **Narrow Specification Grammar:** The deterministic change specification evaluator only supports numeric/Boolean metrics and command/JUnit success. Rich natural-language requirements without metric bindings remain `INCONCLUSIVE` or `NOT_EVALUATED`.
5. **Single-Repository Scope:** Contracts and baselines are strictly repository-bound; multi-repo distributed architectures cannot coordinate atomic cross-repo contracts yet.

---

## 10. Product Strategy, Competitive Landscape & Differentiation

### Competitive Positioning

| Feature / Dimension | Vericore | SonarQube / Snyk | CodeRabbit / GitHub Copilot Review | Cursor / Claude Code Built-in Checks |
|---|---|---|---|---|
| **Timing of Verification**| **Pre-Change Boundary + Post-Change Proof** | Post-Change Analysis only | Post-Change AI Review | In-flight context only |
| **Authority of Truth** | **Deterministic Graph & Code Facts** | AST / Linter rules | Probabilistic LLM generation | Model self-reflection |
| **Scope Enclosure** | **Cryptographic Contract (`prepare` → `verify`)** | None (analyzes whatever changed) | None (reviews git diff) | Prompt instructions only |
| **Agent Neutrality** | **Universal MCP & CLI layer** | Toolchain integration | GitHub PR integration | Vendor-locked to IDE/Agent |
| **Network & Privacy** | **100% Local-First by default** | Self-hosted or Cloud | Mandatory Cloud / LLM processing | Cloud LLM provider |

### The Defining Differentiator: The "Vericore Change Passport"
While existing tools either run static analysis after code is committed or ask an LLM to review a pull request, Vericore is pioneering the **Vericore Change Passport**:
- A content-addressable, machine-verifiable receipt that connects:
  1. The original human intent and planned scope.
  2. The pre-change candidate baseline.
  3. The exact commit and input state evaluated.
  4. The deterministic graph impact and architecture constraints.
  5. The verification commands and fresh JUnit test executions that actually ran.
  6. A verdict that explicitly enumerates what was proven and what remains unverified.

---

## 11. Strategic Roadmap Summary (Phases 0 – 9)

- **Phase 0 (Foundation Freeze & Truth):** Completed. CI stabilization, schema versioning, deterministic golden tests, and Java 25 migration.
- **Phase 1 (The 60-Second Vericore):** Unified public CLI journey (`init → scan → inspect → repo-qa → review → plan → prepare → [agent edits] → verify → report`).
- **Phase 2 (Universal Agent Layer):** Standardized Vericore Agent Adapters across Claude Code, Cursor, Codex, Gemini/Antigravity, and Copilot via MCP.
- **Phase 3 (Change Contract 2.0):** Richer specification grammar and dependency/architecture constraint policies.
- **Phase 4 (Independent Verification Engine):** Advanced drift detection, test relevance mapping, and fail-closed evaluation.
- **Phase 5 (Verification Receipt & Provenance):** Attestation-compatible Change Passports (in-toto / SLSA alignment).
- **Phase 6 (Multi-Agent & Cross-Repository Verification):** Detecting conflicting agent edits and coordinated cross-repo contracts.
- **Phase 7 (Explain + Verified Project Memory):** Structured engineering memory of past decisions, exceptions, and architecture history.
- **Phase 8 (Distribution, Ecosystem & Benchmark):** Public Agent Change Benchmark and IDE/CI distribution.
- **Phase 9 (Enterprise Change Assurance):** Organization-wide governance, compliance exports, and policy-as-code registries.

---

## 12. Terminology & Key Concepts

- **Agent Change Contract:** An immutable, JSON-persisted contract (`output/agent-change-contract.json`) created before an edit, binding planned paths, components, verification commands, evidence IDs, and a candidate baseline digest to a SHA-256 fingerprint.
- **Candidate State Manifest:** A deterministic, sorted inventory of all candidate repository inputs, file sizes, SHA-256 content hashes, and executable bits, used to detect any unauthorized drift between prepare and verify.
- **Engineering Reality:** A state-identity model uniting analysis snapshots, working tree state, and evidence graphs into a single `realityDigest` that intentionally ignores wall-clock timestamps.
- **Grounded Evidence:** Deterministic citations extracted from repository facts (metrics, hotspots, dependencies) that can be cited in plans or fed to AI models to prevent hallucination.
- **Fail-Closed:** The security and verification principle where any missing, tampered, stale, inconclusive, or unexpected state results in a `FAIL` or `REVIEW_REQUIRED` verdict, never an assumed `PASS`.
- **Change Passport:** A portable, machine-verifiable verification receipt documenting intent, baseline, executed checks, evidence, and remaining unverified obligations.
