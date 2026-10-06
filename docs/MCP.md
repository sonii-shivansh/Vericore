# MCP / AI-Agent Integration

> Connect a trusted local AI agent to Vericore's deterministic repository intelligence and verification boundary.

## Purpose

Vericore exposes a local stdio MCP server for agents that need repository evidence, architecture signals, planning, and change verification.

**Vericore is the evidence and verification layer, not the coding agent.** It does not autonomously modify repository source files.

## Quick start

Build/install Vericore, then launch:

```bash
vericore mcp
```

The process uses newline-delimited JSON-RPC over stdin/stdout. stdout is reserved for protocol messages; do not pipe human-readable CLI output into the MCP process.

Client configuration uses the stable executable boundary:

```text
vericore mcp-config
```

This prints:

```json
{
  "mcpServers": {
    "vericore": {
      "command": "vericore",
      "args": ["mcp"]
    }
  }
}
```

The generated configuration intentionally uses the `vericore` executable from `PATH`, so it remains portable across machines. If your MCP client requires an absolute executable path, replace `command` with the installed Vericore path while keeping `args` as `["mcp"]`.
```

For the complete CLI command contract, see [CLI Reference](CLI.md).

## Tool catalog

| Tool | What it does | Mutates source? |
|---|---|---:|
| `vericore_analyze_repository` | Repository structure, dependency graph, hotspots, and deterministic analysis | No |
| `vericore_impact_analysis` | Dependency-aware impact for changed paths | No |
| `vericore_architecture_analysis` | Architecture Intelligence | No |
| `vericore_pr_intelligence` | Working-tree or revision-pair change intelligence | No |
| `vericore_get_engineering_reality` | Repository-state-bound Engineering Reality | No |
| `vericore_get_context_snapshot` | Versioned engineering-context snapshot | No |
| `vericore_get_context_diff` | Deterministic snapshot diff | No |
| `vericore_get_architecture_drift` | Baseline/current architecture drift | No |
| `vericore_get_architecture_contract` | Architecture governance evaluation | No |
| `vericore_prepare_change` | Evidence + engineering plan + persisted change contract | Writes artifacts under `output/` |
| `vericore_get_change_contract` | Retrieves the persisted `output/agent-change-contract.json` | No |
| `vericore_get_evidence` | Bounded grounded repository evidence | No |
| `vericore_change_safety` | Current working-tree scope signal | No |
| `vericore_verify_change` | Verifies the persisted Agent Change Contract and plan | No |

All repository arguments use `repoPath`. Remote repository URLs are rejected.

## Compatibility

The canonical MCP tool namespace is `vericore_*`. Retained `codecontext_*` aliases are legacy compatibility paths only; they emit a deprecation warning and should be migrated to the corresponding `vericore_*` tool.

## Request examples

### Repository analysis

```json
{"repoPath":"/absolute/path/to/repository"}
```

### Impact analysis

```json
{
  "repoPath":"/absolute/path/to/repository",
  "changedPaths":["src/main/kotlin/com/example/PaymentService.kt"]
}
```

The server accepts at most 100 changed paths per call.

### PR Intelligence

Working tree:

```json
{"repoPath":"/absolute/path/to/repository"}
```

Revision pair:

```json
{
  "repoPath":"/absolute/path/to/repository",
  "baseRevision":"main",
  "headRevision":"feature/payment-retry"
}
```

Both revisions must be supplied together.

### Prepare

```json
{
  "repoPath":"/absolute/path/to/repository",
  "changeSummary":"Add payment retry validation",\n  "plannedPaths":["src/main/kotlin/com/example/PaymentService.kt"]
}
```

Preparation persists repository-scoped artifacts, including:

```text
output/engineering-context.json
output/engineering-plan.json
output/agent-change-contract.json
```

## Change-safety workflow

```text
Engineering Reality
        ↓
Context / architecture / impact
        ↓
Grounded evidence
        ↓
Prepare change
        ↓
Persist Agent Change Contract
        ↓
Agent modifies repository
        ↓
Verify ORIGINAL persisted contract
        ↓
Tests / human review
```

The contract binds repository identity and prepared Git `HEAD` to planned paths. Agents may supply explicit `plannedPaths` during MCP/CLI preparation so the verification scope can be declared before source edits exist., expected components, verification commands, evidence IDs, architecture expectations, and a SHA-256 fingerprint.

### Agent workflow guarantee

`vericore_prepare_change` is the agent-facing preparation boundary. It returns the plan and contract and persists the same contract to `output/agent-change-contract.json` (plus the preparation and plan artifacts). An agent can therefore call MCP `prepare`, modify the repository, then call MCP `get_change_contract` and `verify` without falling back to the CLI to establish the verification boundary.

### Contract retrieval vs. contract preparation

These operations have intentionally different responsibilities:

- `vericore_prepare_change` **creates/persists** the change contract.
- `vericore_get_change_contract` **retrieves** the persisted `output/agent-change-contract.json`.
- `vericore_verify_change` verifies the persisted contract and does not silently generate a replacement.

This distinction prevents an agent from replacing the verification boundary after it has prepared a change.

## Security boundary

The MCP server is intended for trusted local use.

- Repository paths must resolve to readable directories permitted by the configured path-safety boundary.
- Remote repository URLs are rejected.
- The MCP transport does not implement authentication or tenant isolation.
- Tools do not provide arbitrary filesystem reads outside Vericore's repository boundaries.
- `vericore_prepare_change` writes repository-local artifacts; it does not modify source code.

For a shared or remote deployment, put an authenticated service boundary in front of Vericore rather than exposing the stdio process directly.

## Compatibility and lifecycle

The current implementation targets the **2025-11-25 MCP lifecycle** and implements `initialize`, `notifications/initialized`, `ping`, `tools/list`, and `tools/call` for the current Vericore tool surface. Legacy `codecontext_*` aliases are accepted only for migration and emit deprecation warnings on stderr.

Modern MCP lifecycle behavior is intentionally deferred until the server can implement the required request/discovery model correctly rather than advertising unsupported behavior.

## Related documents

- [CLI Reference](CLI.md) — complete command syntax
- [Architecture](ARCHITECTURE.md) — system boundaries
- [Engineering Reality](ENGINEERING_REALITY.md) — repository-state identity
- [Change Safety](CHANGE_SAFETY.md) — persisted contract semantics
- [API](API.md) — local REST integration
- [Data & Privacy](DATA_PRIVACY.md) — data handling and AI boundaries
