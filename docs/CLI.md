# CLI Reference

> Complete reference for the Vericore command-line interface. Examples below describe the current 0.8.2 command surface and the post-0.8.2 changes currently present on `main`.

## Global syntax

```text
vericore [OPTIONS] COMMAND [ARGS...]
```

Global options:

```bash
vericore --help
vericore --version
```

The executable is `build/install/vericore/bin/vericore` when built from source. Released platform archives provide the platform launcher under `bin/`.

### Repository path conventions

Unless stated otherwise, `path` means a local repository directory. Vericore analyzes Java and Kotlin source repositories; remote repository URLs are not accepted by the local analysis/server boundaries.

### JSON output

Commands that provide `--json` write their machine-readable artifact under the analyzed repository's `output/` directory. Commands that do not provide `--json` print their human-readable result to stdout.

---

## 1. `analyze`

Analyze a Java/Kotlin repository and generate deterministic engineering intelligence plus the HTML report.

### Syntax

```bash
vericore analyze <path> [--no-cache] [--clear-cache] [--no-snapshot] [--verbose|-v]
```

### Examples

```bash
vericore analyze .
vericore analyze /workspace/my-repository
vericore analyze . --no-cache
vericore analyze . --clear-cache
vericore analyze . --no-snapshot
vericore analyze . --verbose
```

### Options

| Option | Purpose |
|---|---|
| `<path>` | Repository to analyze. Required. |
| `--no-cache` | Disable the analysis cache for this run. |
| `--clear-cache` | Clear the cache before analysis. |
| `--no-snapshot` | Do not persist the analysis snapshot/risk artifacts. |
| `--verbose`, `-v` | Include verbose diagnostics when failures occur. |

### Outputs

Default:

```text
output/index.html
output/analysis-snapshot.json
output/engineering-risks.json
```

`--no-snapshot` suppresses the snapshot and risk artifacts. If AI is enabled and configured, an additional `output/ai-insights.md` may be generated.

### Important behavior

Run `analyze` before `evidence-graph` or `reality`. Those commands consume the persisted `output/analysis-snapshot.json`.

---

## 2. `impact`

Analyze the dependency impact of one or more changed files.

### Syntax

```bash
vericore impact <path> <changed>... [--json]
```

### Examples

```bash
vericore impact . src/main/kotlin/com/example/PaymentService.kt
vericore impact . src/main/kotlin/com/example/PaymentService.kt src/main/kotlin/com/example/PaymentRepository.kt --json
```

### Options

| Option | Purpose |
|---|---|
| `<path>` | Repository path. Required. |
| `<changed>...` | One or more changed file paths relative to the repository. Required. |
| `--json` | Write `output/change-impact.json`. |

The command reports changed files, impacted files/packages, cross-package impact, test candidates, dependency depth, and the highest-ranked impact nodes.

---

## 3. `architecture`

Analyze repository architecture and report deterministic structural findings.

### Syntax

```bash
vericore architecture <path> [--json]
```

### Examples

```bash
vericore architecture .
vericore architecture . --json
```

### Outputs

With `--json`:

```text
output/architecture.json
```

The result includes analyzed-file count, dependency edges, findings, cycles, cross-layer dependencies, and high-coupling files.

---

## 4. `architecture-drift`

Compare current Architecture Intelligence with a previously generated architecture baseline.

### Syntax

```bash
vericore architecture-drift <path> --baseline <baseline-json> [--json]
```

### Examples

```bash
vericore architecture . --json
vericore architecture-drift . --baseline output/architecture.json --json
```

### Options

| Option | Purpose |
|---|---|
| `<path>` | Repository to analyze. |
| `--baseline <file>` | Existing `ArchitectureIntelligenceResult` JSON. Required. |
| `--json` | Write `output/architecture-drift.json`. |

The report identifies added/removed findings, new/removed cycles, changed layers, and the underlying deterministic changes.

---

## 5. `architecture-contract`

Evaluate architecture against a deterministic governance contract.

### Syntax

```bash
vericore architecture-contract <path> [--contract <file>] [--json] [--record <file>]
```

### Examples

```bash
vericore architecture-contract .
vericore architecture-contract . --json
vericore architecture-contract . --contract .vericore-architecture-contract.json --json
vericore architecture-contract . --record output/architecture-contract-history.jsonl
```

### Options

| Option | Purpose |
|---|---|
| `<path>` | Repository to evaluate. |
| `--contract <file>` | Explicit architecture contract. If omitted, Vericore resolves the canonical `.vericore-architecture-contract.json` contract and otherwise evaluates the default contract. |
| `--json` | Write `output/architecture-contract.json`. |
| `--record <file>` | Persist deterministic contract decision history. |

A failed contract exits unsuccessfully. The command reports the contract decision fingerprint and violations.

---

## 6. `context-snapshot`

Create a deterministic snapshot of repository engineering context.

### Syntax

```bash
vericore context-snapshot <path> [--json]
```

### Example

```bash
vericore context-snapshot . --json
```

### Output

With `--json`:

```text
output/engineering-context.json
```

The snapshot records repository commit (when available), file count, byte count, dirty state, and a stable snapshot digest.

---

## 7. `context-diff`

Compare two previously generated engineering-context snapshots.

### Syntax

```bash
vericore context-diff <before> <after> [--json]
```

### Example

```bash
vericore context-diff output/before.json output/after.json --json
```

### Options

| Option | Purpose |
|---|---|
| `<before>` | Baseline engineering-context JSON. Required. |
| `<after>` | Current engineering-context JSON. Required. |
| `--json` | Write `output/engineering-context-diff.json`. |

The diff reports added, removed, modified, and unchanged paths.

---

## 8. `evidence-graph`

Build the deterministic semantic evidence graph from the current analysis snapshot.

### Syntax

```bash
vericore evidence-graph [<path>] [--json]
```

`<path>` defaults to `.`.

### Example

```bash
vericore analyze .
vericore evidence-graph . --json
```

### Output

With `--json`:

```text
output/semantic-evidence-graph.json
```

The graph reports repository identity, observed commit, node count, edge count, and a stable graph digest. It requires `output/analysis-snapshot.json`, so run `analyze` first.

---

## 9. `reality`

Build one deterministic Engineering Reality identity across the analysis snapshot and current repository state.

### Syntax

```bash
vericore reality [<path>] [--json]
```

`<path>` defaults to `.`.

### Example

```bash
vericore analyze .
vericore reality . --json
```

### Output

With `--json`:

```text
output/engineering-reality.json
```

Reality binds analysis state to current repository context and rejects incompatible/stale state instead of silently combining artifacts.

---

## 10. `pr-intelligence`

Analyze a working tree or an explicit Git base/head revision pair for deterministic change risk and impact signals.

### Syntax

```bash
vericore pr-intelligence <path> [--base <revision> --head <revision>] [--json]
```

### Examples

Working tree:

```bash
vericore pr-intelligence . --json
```

Revision pair:

```bash
vericore pr-intelligence . --base main --head feature/payment-retry --json
```

### Rules

`--base` and `--head` must be supplied together. If neither is supplied, the current working tree is analyzed.

### Output

With `--json`:

```text
output/pr-intelligence.json
```

The result combines change size, dependency impact, package boundaries, risk, architecture signals, and test candidates. These are review signals, not proof of correctness or test coverage.

---

## 11. `repo-qa`

Retrieve bounded, deterministic repository evidence for a natural-language engineering question.

### Syntax

```bash
vericore repo-qa <question> [--path <repository>] [--max-results <1..32>] [--evidence-output <file>]
```

### Examples

```bash
vericore repo-qa "Why is PaymentService risky?" --path .
vericore repo-qa "Where is authentication configured?" --path . --max-results 12
vericore repo-qa "What depends on PaymentService?" --path . --evidence-output output/grounded-evidence.json
```

### Options

| Option | Default | Purpose |
|---|---:|---|
| `<question>` | — | Repository question. Required. |
| `--path <repository>` | `.` | Repository to inspect. |
| `--max-results <n>` | `8` | Maximum evidence items; must be 1–32. |
| `--evidence-output <file>` | none | Persist reusable `GroundedEvidence` JSON for `plan`. |

The command prints the grounded retrieval result as JSON. It is deterministic and does not require an external AI provider.

---

## 12. `plan`

Generate a deterministic, evidence-backed engineering plan for a proposed change.

### Syntax

```bash
vericore plan <change-summary> [--repository <path>] [--changed <path>...] [--evidence <file>] [--output <file>]
```

### Examples

```bash
vericore repo-qa "Where should payment validation be added?" --evidence-output output/grounded-evidence.json
vericore plan "add payment validation" --evidence output/grounded-evidence.json --output output/engineering-plan.json
```

### Options

| Option | Default | Purpose |
|---|---|---|
| `<change-summary>` | — | Proposed change description. Required. |
| `--repository <path>` | `.` | Repository containing the evidence and planned change. |
| `--changed <path>` | none | One or more repository-relative planned paths. |
| `--evidence <file>` | `output/grounded-evidence.json` | Grounded evidence artifact. |
| `--output <file>` | stdout | Write the plan JSON to a repository-local path. |

The plan is read-only and cannot modify source code.

---

## 13. `prepare`

Create the evidence-backed preparation bundle used by the safe change workflow.

### Syntax

```bash
vericore prepare <change-summary> [--path <repository>] [--output <file>] [--plan-output <file>] [--contract-output <file>]
```

### Examples

```bash
vericore prepare "add payment validation"
vericore prepare "add payment validation" --path /workspace/repository
vericore prepare "add payment validation" --plan-output output/payment-plan.json --contract-output output/payment-contract.json
```

### Default outputs

```text
output/engineering-context.json
output/engineering-plan.json
output/agent-change-contract.json
```

The Agent Change Contract is persisted at preparation time. Do not replace it before `verify`.

---

## 14. `verify`

Verify the current working tree against the original persisted engineering plan and Agent Change Contract.

### Syntax

```bash
vericore verify [--path <repository>] [--plan <file>] [--contract <file>] [--output <file>] [--contract-only]
```

### Example

```bash
vericore verify
vericore verify --path . --plan output/engineering-plan.json --contract output/agent-change-contract.json --output output/verification.json
```

### Options

| Option | Default | Purpose |
|---|---|---|
| `--path <repository>` | `.` | Repository to verify. |
| `--plan <file>` | `output/engineering-plan.json` | Persisted engineering plan. |
| `--contract <file>` | `output/agent-change-contract.json` | Original immutable Agent Change Contract. Required to exist. |
| `--output <file>` | stdout | Optional verification artifact. |
| `--contract-only` | off | Validate contract identity, prepared `HEAD`, and mutation scope without rebuilding the full analysis graph. |

Verification executes the persisted build/test commands using the native shell and wrapper conventions for the current platform. On Windows, Maven/Gradle wrapper commands use `mvnw.cmd`/`gradlew.bat` and `cmd` directory syntax; on Unix-like systems they use the executable wrapper form. Verification fails when the contract is missing, tampered, stale, cross-repository, inconsistent with the plan, or when changes fall outside the prepared boundary. `PASS` and `REVIEW_REQUIRED` are not substitutes for tests or human review.

---

## 15. `ask`

Ask an AI provider a question about the current repository using bounded repository context.

### Syntax

```bash
vericore ask <question>
```

### Example

```bash
vericore ask "What are the main architectural hotspots in this repository?"
```

AI must be configured before use. The normal setup path is:

```bash
vericore setup
vericore doctor
vericore ask "Explain the main architectural hotspots."
```

The current interactive setup path provisions Gemini. The underlying `AICodeAnalyzer` also contains an Anthropic/Claude provider path for explicitly configured integrations. If the provider is unavailable or rate-limited, the command reports a sanitized provider failure.

---

## 16. `evolution`

Analyze repository evolution from Git history using deterministic time-based snapshots.

### Syntax

```bash
vericore evolution [<path>] [--months <n>] [--interval <days>]
```

`<path>` defaults to `.`.

### Examples

```bash
vericore evolution .
vericore evolution . --months 12 --interval 14
```

### Options

| Option | Default | Purpose |
|---|---:|---|
| `<path>` | `.` | Git repository path. |
| `--months <n>` | `6` | Number of months to examine. |
| `--interval <days>` | `30` | Days between historical snapshots. |

The command requires useful Git commit history and reports timestamp, commit, file count, line count, and net file growth.

---

## 17. `server`

Start the local Vericore REST API server.

### Syntax

```bash
vericore server [--host <host>] [--port <port>]
```

### Example

```bash
vericore server --host 127.0.0.1 --port 8080
```

### Options

| Option | Default | Purpose |
|---|---|---|
| `--host`, `-h` | `127.0.0.1` | Bind address. |
| `--port`, `-p` | `8080` | Listening port, 1–65535. |

For local use, keep the server on loopback. The application does not provide deployment-grade authentication, authorization, tenant isolation, or TLS.

See [API](API.md) for endpoints.

---

## 18. `mcp`

Start the local Model Context Protocol server over stdio.

### Syntax

```bash
vericore mcp
```

The command takes no CLI options. It uses stdin/stdout for the MCP protocol. See [MCP](MCP.md) for the tool catalog and integration contract.

---

## 19. `setup`

Configure AI credentials for the current user.

### Syntax

```bash
vericore setup [--provider <provider>] [--model <model>] [--force]
```

### Examples

```bash
vericore setup
vericore setup --provider gemini --model <gemini-model>
vericore setup --force
```

### Options

| Option | Default | Purpose |
|---|---|---|
| `--provider` | `gemini` | AI provider. The current setup command accepts `gemini`. |
| `--model` | configured default Gemini model | Gemini model to use. |
| `--force` | off | Replace an existing saved credential. |

`setup` accepts supported environment variables for non-interactive use, otherwise it prompts for the API key. It validates the credential before saving it.

Never commit a real API key to `.vericore.json` or source control.

---

## 20. `doctor`

Check the local Vericore installation, repository context, AI configuration, and provider reachability when credentials are configured.

### Syntax

```bash
vericore doctor [--path <directory>]
```

`--path` defaults to the current directory. The command is safe to run immediately after installation, even outside a Git repository.

### What it checks

- Vericore version;
- Java runtime (21+ required);
- diagnostic path exists;
- Git repository context when the diagnostic path is a repository;
- effective AI provider configuration;
- AI credentials when configured;
- Gemini API reachability when Gemini credentials are available;
- deprecated legacy AI configuration warnings.

Exit behavior is suitable for automation: configuration/runtime failures are reported as errors; missing optional AI configuration is a warning because deterministic analysis does not require AI.

---

## Recommended command workflows

### First analysis

```bash
vericore doctor
vericore analyze .
vericore evidence-graph . --json
vericore reality . --json
```

### Impact and PR review

```bash
vericore impact . src/main/kotlin/com/example/PaymentService.kt --json
vericore pr-intelligence . --base main --head feature/payment-retry --json
vericore architecture . --json
```

### Safe engineering change

```bash
vericore repo-qa "Where should payment validation be added?" --path . --evidence-output output/grounded-evidence.json
vericore plan "add payment validation" --evidence output/grounded-evidence.json --output output/engineering-plan.json
vericore prepare "add payment validation"
# make the change
vericore verify
```

### Architecture governance

```bash
vericore architecture . --json
vericore architecture-drift . --baseline output/architecture.json --json
vericore architecture-contract . --json
```

## Command contract rule

This document describes the CLI implementation, not future roadmap ideas. When a command, option, output artifact, or safety boundary changes, update this reference in the same change.

For the system-level model, see [Architecture](ARCHITECTURE.md). For agent integrations, see [MCP](MCP.md). For safe changes, see [Change Safety](CHANGE_SAFETY.md).
