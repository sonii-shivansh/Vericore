# CLI Output Contract Inventory

**Audited against:** `main` at `f046c6d06105ebb4c529a17f2b5bf5116c748bf0` (PR #256 merge)  
**Purpose:** Record observed stdout/file-output behavior before migrating more commands to the shared human-readable renderer.

This is an implementation inventory, not a promise that every output shape is already ideal. The source code is authoritative if this document and implementation disagree. Update this inventory alongside future CLI output-contract changes.

## Compatibility rules

1. **Do not infer stdout behavior from a `--json` flag.** Some commands write a JSON artifact and still print a human summary; others print JSON to stdout.
2. Preserve existing JSON fields/schema, artifact paths, and whether JSON is emitted to stdout or a file unless a separately reviewed change explicitly changes that contract.
3. Preserve success/failure behavior and error handling. Human-readable formatting must not decide verification, risk, or validation outcomes.
4. Keep standard output usable for its documented purpose. In particular, commands whose default output is JSON must not have progress text prepended to that JSON.
5. Error text, progress output, and generated artifacts are separate concerns. Do not claim a command is JSON-only unless the implementation actually makes stdout JSON-only.
6. Treat this table as a source audit, not proof that every documented case has a regression test. Add tests for the contract before changing it.

## Command-by-command inventory

“Human console” means status/progress/summary text is printed. “JSON file” means a machine-readable artifact is written, not that stdout is JSON.

| Command | Default stdout | Machine-readable mode / artifact | Contract notes |
|---|---|---|---|
| `init` | Human initialization summary | `--json`: `ProductCommandResult` JSON to stdout | JSON mode returns before printing the human summary. Already-initialized status is `ALREADY_INITIALIZED`; successful initialization is `PASS`. |
| `scan` | Delegates to `analyze`; progress and analysis summary | No `--json` option of its own | Passes `--no-cache`, `--clear-cache`, `--no-snapshot`, and `--verbose` through to analysis. |
| `analyze` | Progress plus human-readable codebase report | Persists analysis/session artifacts as implemented; no `--json` flag in this command | Verbose mode may print stack traces. Keep progress output and errors out of any future machine-only stream. |
| `inspect` | Human inspection summary rendered through `HumanCommandOutput` | `--json`: `output/inspection.json` | Writes the JSON file and still prints the human summary. Requires the persisted analysis snapshot. Summary formatting does not change inspection data, JSON schema, artifact path, or exit behavior. |
| `doctor` | Diagnostic lines and final readiness/attention summary | No JSON option observed | Errors produce a non-success exit through `ConfigurationException`; optional AI configuration can be reported as warnings. |
| `impact` | Human impact summary rendered through `HumanCommandOutput` | `--json`: `output/change-impact.json` | Writes JSON and still prints the human summary; node-detail lines remain unchanged. Summary rendering does not change the JSON schema, artifact path, or impact calculations. |
| `architecture` | Human architecture summary rendered through `HumanCommandOutput` | `--json`: `output/architecture.json` | Writes JSON and still prints the human summary. Summary rendering preserves the architecture result, JSON schema, artifact path, and analysis behavior. |
| `architecture-drift` | Human drift summary rendered through `HumanCommandOutput` | `--json`: `output/architecture-drift.json` | Requires a baseline argument; JSON mode still prints a human summary. Summary rendering preserves drift calculations, JSON schema, artifact path, and error behavior. |
| `architecture-contract` | Human contract decision summary rendered through `HumanCommandOutput` | `--json`: `output/architecture-contract.json` | Also may print legacy-contract warnings and decision-history path. A failed contract still raises `ValidationException`; JSON mode does not mean summary-free stdout. Rendering preserves decision, fingerprint, artifact path, and the 30-violation display limit. |
| `context-snapshot` | Human context summary rendered through `HumanCommandOutput` | `--json`: `output/engineering-context.json` | JSON mode writes the file and still prints the summary. Rendering preserves snapshot values, JSON encoding, and artifact path. |
| `context-diff` | Human context-diff summary rendered through `HumanCommandOutput`, with up to 30 changes | `--json`: `output/engineering-context-diff.json` | JSON mode writes the file and still prints the summary. Rendering preserves diff calculation, JSON encoding, relative artifact path, and 30-change display limit. |
| `evidence-graph` | Human graph summary rendered through `HumanCommandOutput` | `--json`: `output/semantic-evidence-graph.json` | JSON mode writes the artifact and still prints the human summary. Rendering preserves the graph values/digest, JSON schema and encoding, artifact path, stdout mode, and missing-analysis failure behavior. |
| `reality` | Human engineering-reality summary rendered through `HumanCommandOutput` | `--json`: `output/engineering-reality.json` | JSON mode writes the file and still prints the human summary. Rendering preserves the reality snapshot/digest, JSON encoding, artifact path, stdout behavior, and validation failures. |
| `pr-intelligence` | Human PR-intelligence summary rendered through `HumanCommandOutput`, including finding details when present | `--json`: `output/pr-intelligence.json` | JSON mode writes the file and still prints the human summary. Rendering preserves result values, finding order/content, JSON serialization, artifact path, and the paired `--base`/`--head` requirement. |
| `repo-qa` | JSON result to stdout | Optional `--evidence-output <file>` writes reusable grounded-evidence JSON | Default stdout is machine-readable JSON; do not prepend human progress output. |
| `report` | Human report-generated summary rendered through `HumanCommandOutput` | `--output <file>` selects the HTML report path | Generates HTML; this is not a JSON command. The summary layout is presentation-only; session selection and artifact paths are unchanged. |
| `review` | Human review summary rendered through `HumanCommandOutput`, including deterministic findings and optional grounded AI review | `--json`: `output/review.json` | JSON mode writes the artifact and still prints the human summary. Rendering preserves deterministic findings, optional AI details, JSON schema, artifact path, paired `--base`/`--head` requirement, and next-step guidance. |
| `recommendations` | Human prioritized recommendations rendered through `HumanCommandOutput` | `--json`: `output/recommendations.json` | Writes JSON and still prints human recommendations. Requires an analysis snapshot. Summary formatting does not change recommendation generation, JSON fields, artifact path, or exit behavior. |
| `plan` | JSON to stdout by default | `--output <file>` writes plan JSON to a repository-local file | With `--output`, stdout becomes a human summary; without it, stdout is JSON. |
| `prepare` | Human summary and artifact paths | Writes preparation, engineering-plan, and agent-change-contract JSON files; paths can be overridden with output options | New contracts are schema 3.0 and embed the validated prepare-time candidate baseline; paths can be overridden with output options. Keep the preparation, plan, and contract aligned. |
| `verify` | Status/path summary on success; failure reasons go to stderr | Verification result JSON goes to stdout by default or to the path passed to `--output`; full verification also writes `output/verification-receipt.json` (schema 1.3) with baseline digest validity and unplanned-drift paths | Both full and `--contract-only` verification validate the prepare-time baseline and detect unplanned tracked, untracked, or ignored input drift. Legacy contracts without a baseline fail and must be prepared again. `--contract-only` returns before writing the full verification receipt. Non-empty specifications with `NOT_EVALUATED` criteria return `REVIEW_REQUIRED`; malformed specifications fail closed. Failure raises `ValidationException` after reporting reasons. |
| `ask` | Progress messages followed by the final answer rendered through `HumanCommandOutput`, with suggested files and confidence when available | No JSON option observed | Rendering affects only the final answer; progress messages, grounded context construction, provider selection, sanitized errors, and command failure behavior remain unchanged. |
| `evolution` | Progress messages followed by a report rendered through `HumanCommandOutput`, with timestamped snapshots and net file growth | No JSON option observed | Preserves `--months` and `--interval` defaults, local-time timestamp formatting, 7-character commit IDs, growth calculation, and the no-history error path. |
| `server` | Startup message, then server operation | HTTP API, not CLI JSON mode | Keep startup logs separate from HTTP response bodies. |
| `mcp` | MCP protocol on stdout | MCP stdio protocol | **Protocol-sensitive:** do not print human summaries or ordinary logs to stdout while serving MCP; use the established logging/error channel. |
| `mcp-config` | JSON configuration to stdout by default | `--write` writes `.mcp.json`; `--force` permits replacement | Default stdout is JSON. `--write` prints a confirmation message instead. |
| `setup` | Interactive/setup status messages | Saves user-level AI configuration | No JSON option observed. Do not echo credential contents. |

## Output-contract risks to address before broad migration

- **Flag naming is inconsistent:** `--json` means “write an artifact and keep printing human text” for several analysis commands, but means “JSON on stdout” for `init`. `plan`, `verify`, `repo-qa`, and `mcp-config` have their own stdout/file rules.
- **There is no universal JSON-output contract yet.** Do not implement a global rule that changes current behavior. First add explicit command-level tests and choose a compatibility-preserving renderer migration.
- **Exit behavior is part of the contract.** `doctor`, `verify`, and `architecture-contract` have failure paths that must not be converted to success merely to improve presentation.
- **Artifact paths are observable behavior.** Consumers may rely on paths such as `output/review.json`, `output/inspection.json`, and `output/verification-receipt.json`.
- **MCP stdout is protocol data.** Human-oriented rendering must never leak into the MCP protocol stream.

## Suggested test checklist for each output migration

- Default invocation: expected human output or JSON stdout, as documented.
- JSON/artifact option: artifact exists, parses, and retains the same schema/fields.
- Stdout contract: verify whether stdout is human text, JSON, or protocol data; do not mix formats accidentally.
- Error path: nonzero exit remains nonzero and diagnostic stream remains appropriate.
- Paths and side effects: default and overridden artifact locations remain unchanged.
- Empty/no-finding case: output remains understandable without changing the underlying decision.

## Source files reviewed

The inventory was assembled from the command registration in `src/main/kotlin/com/vericore/Main.kt` and command implementations under `src/main/kotlin/com/vericore/cli/`, including `InitCommand`, `ScanCommand`, `ImprovedAnalyzeCommand`, `InspectCommand`, `DoctorCommand`, `ImpactCommand`, architecture commands, context commands, `EvidenceGraphCommand`, `RealityCommand`, `PRIntelligenceCommand`, `RepositoryQACommand`, `ReportCommand`, `ReviewCommand`, `RecommendationsCommand`, `EngineeringPlanCommand`, `PrepareCommand`, `VerifyCommand`, `AIAssistantCommand`, `EvolutionCommand`, `ServerCommand`, `McpCommand`, `McpConfigCommand`, and `SetupCommand`.
