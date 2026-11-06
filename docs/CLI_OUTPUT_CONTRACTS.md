# CLI Output Contract Inventory & Standardization

**Audited against:** `main` at `f046c6d06105ebb4c529a17f2b5bf5116c748bf0` with CLI standardization & targeted fixes
**Purpose:** Record standardized human and machine-readable output contracts across all 27 registered Vericore CLI command areas.

## Core Contract Guiding Principles

1. **Human by Default, JSON on Request:** Every command produces human-readable output by default. When `--format json` or `--json` is specified, pure parseable JSON is emitted to standard output.
2. **Universal JSON Envelope:** All machine-readable JSON outputs conform to the standardized `CommandEnvelope<T>` schema (version `"1.0"`), including `schemaVersion`, `command`, `status`, `summary`, `repository`, `data`, `findings`, `recommendations`, `artifacts`, `nextStep`, and `execution: { exitCode }`.
3. **Status and Process Exit Code Alignment:**
   - Analytical severity (e.g. CRITICAL/HIGH findings in `review` or `pr-intelligence`) is mapped to `CommandStatus.REVIEW_REQUIRED` with exit code `0`, distinguishing successful analysis with required attention from command failure.
   - `verify`: `PASS` (0), `NO_CHANGES` (0), `REVIEW_REQUIRED` (0), and `FAIL` (1) strictly agree between `envelope.execution.exitCode` and process exit code.
   - `setup`: AI validation failure emits a `FAIL` envelope with `execution.exitCode = 1` and exits with code 1 while keeping stdout pure JSON.
   - `doctor`: Failed Gemini credential validation counts as exactly 1 failure.
4. **Output Format Validation (`OutputFormat.resolve`):**
   - Valid formats: `human`, `json` (case-insensitive).
   - Invalid formats (e.g. `--format xml`, `--format invalid`): rejected immediately with `IllegalArgumentException` and exit code `2`.
   - Conflicting options (`--format human --json`): rejected with `IllegalArgumentException` and exit code `2`.
5. **Standardized `--output` Behavior:**
   - When `--format json --output <file>` is specified, the exact same `CommandEnvelope<T>` JSON is written to `<file>` as emitted to `stdout`.
   - Domain artifacts (e.g., `output/analysis-snapshot.json`, `output/engineering-plan.json`) remain saved in their canonical directory locations.
6. **Impact Analysis Test Candidate Representation:**
   - Both dependency-graph-reachable test files and naming-convention test candidates are included in `ChangeImpactResult.nodes` (`relationship = TEST_CANDIDATE`), `ChangeImpactResult.summary.testCandidates`, and `ChangeImpactResult.testCandidates`.
   - Impact summary displays all identified test candidates with the reason each was selected.
7. **Repository QA Evidence Relevance:**
   - General intent questions do not receive an unconditional baseline score.
   - Relevance threshold `MIN_RELEVANCE_THRESHOLD = 20` ensures unrelated questions yield `insufficientEvidence = true` with empty evidence.
   - Path segments (such as `/main/` or `/test/`) do not match entities `Main` or `Test`.
8. **Structured JSON Errors:**
   - When JSON output is requested and a post-parsing execution or validation failure occurs before output generation, `ErrorHandler` emits a structured error envelope to `stdout` and diagnostic text to `stderr`.
9. **MCP and Server Isolation:**
   - `mcp` stdout is strictly reserved for JSON-RPC stdio protocol traffic. Logs and diagnostics go exclusively to stderr or file logs.

## Command-by-Command Inventory

| Command | Default stdout | Machine-readable mode (`--format json` / `--json`) | Standardized Contract Notes |
|---|---|---|---|
| `init` | Human summary | `CommandEnvelope<ProductCommandResult>` | Emits universal envelope containing ProductCommandResult lifecycle data and execution block (0). Returns `PASS` or `ALREADY_INITIALIZED`. |
| `scan` | Human analysis summary | `CommandEnvelope<AnalysisSnapshot>` | Emits universal envelope with command identity `"scan"`, sharing analysis engine. |
| `analyze` | Progress + human report | `CommandEnvelope<AnalysisSnapshot>` | Emits universal envelope with command identity `"analyze"`. Canonical artifacts saved in `output/`. |
| `inspect` | Human summary via `HumanCommandOutput` | `CommandEnvelope<InspectionResult>` | Writes `output/inspection.json`. |
| `doctor` | Human diagnostic checklist | `CommandEnvelope<List<DoctorCheckResult>>` | Exit code 0 on pass/warning; exit code 2 on failures. Gemini failure counts as 1. |
| `impact` | Human summary with test candidates & reasons | `CommandEnvelope<ChangeImpactResult>` | `result.testCandidates` and `nodes` include all candidates. |
| `architecture` | Human architecture summary | `CommandEnvelope<ArchitectureIntelligenceResult>` | Analyzes dependencies, cycles, and cross-layer violations. |
| `architecture-drift` | Human drift summary | `CommandEnvelope<ArchitectureDriftResult>` | Compares current against baseline. Invalid baseline throws exception. |
| `architecture-contract`| Human contract decision | `CommandEnvelope<ArchitectureContractResult>` | Enforces contract; failures exit 1 with structured violations. |
| `context-snapshot` | Human context summary | `CommandEnvelope<EngineeringContextSnapshot>` | Deterministic file hashes and digest. |
| `context-diff` | Human diff summary | `CommandEnvelope<EngineeringContextDiff>` | Compares two snapshots; indicates added/removed/modified. |
| `evidence-graph` | Human graph summary | `CommandEnvelope<SemanticEvidenceGraphArtifact>`| Semantic evidence graph with nodes and edges. |
| `reality` | Human reality summary | `CommandEnvelope<EngineeringRealityArtifact>` | Unified reality verification. |
| `pr-intelligence` | Human PR intelligence summary | `CommandEnvelope<PRIntelligenceResult>` | High/Critical severity produces `REVIEW_REQUIRED` (exit code 0). |
| `repo-qa` | Human answer and citations | `CommandEnvelope<RepositoryQAResult>` | Relevance threshold 20; unrelated queries produce `insufficientEvidence = true`. |
| `report` | Human confirmation + HTML | `CommandEnvelope<Map<String, String>>` | Generates HTML report in `output/index.html`. |
| `review` | Human code review summary | `CommandEnvelope<ReviewOutput>` | Critical severity produces `REVIEW_REQUIRED` (exit code 0). |
| `recommendations` | Human recommendations | `CommandEnvelope<List<EngineeringRecommendation>>` | Baseline healthy or actionable recommendations. |
| `plan` | Human engineering plan | `CommandEnvelope<EngineeringPlan>` | Binds specification to planned mutation scope. |
| `prepare` | Human preparation summary | `CommandEnvelope<EngineeringPreparationResult>` | Persists preparation, plan, and contract (schema 3.0). |
| `verify` | Human verification summary | `CommandEnvelope<VerificationResult>` | `PASS`, `NO_CHANGES`, `REVIEW_REQUIRED` exit 0; `FAIL` exits 1. |
| `ask` | Human answer with files & confidence | `CommandEnvelope<GroundedAIResponse>` | AI grounded query response. |
| `evolution` | Human repo evolution summary | `CommandEnvelope<List<CodebaseSnapshot>>` | Git commit evolution over time with ISO-8601 UTC timestamps. |
| `server` | Server startup log | HTTP endpoints | REST API daemon. |
| `mcp` | MCP stdio protocol | Pure JSON-RPC | Never mix human text into stdout. |
| `mcp-config` | Human summary / stdout JSON | `CommandEnvelope<McpConfig>` | Generates `.mcp.json` client configuration. |
| `setup` | Interactive / diagnostic output | `CommandEnvelope<Map<String, String>>` | Secure key validation. Failure exits 1 with clean JSON. |
