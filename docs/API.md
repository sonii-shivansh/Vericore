# API Reference

Vericore exposes a CLI and a local REST API. The REST API is implemented by `com.vericore.server.VericoreServer` and is intended for trusted local or internal use.

## Build and start

```bash
./gradlew installDist
./build/install/vericore/bin/vericore server --host 127.0.0.1 --port 8080
```

Keep the default bind address on loopback for local use. Deployments beyond loopback must provide authentication, trusted-origin controls, TLS, quotas, report authorization, and tenant isolation at the deployment boundary.

## CLI surface

The current application registers these command families:

```text
analyze
impact
architecture
architecture-drift
architecture-contract
context-snapshot
context-diff
evidence-graph
reality
pr-intelligence
repo-qa
plan
prepare
verify
ask
evolution
server
mcp
setup
doctor
```

Run `vericore <command> --help` for exact installed options.

### Engineering Reality and context

```bash
vericore context-snapshot /path/to/repository --json
vericore context-diff output/before.json output/after.json --json
vericore reality /path/to/repository --json
```

Reality binds deterministic analysis and repository-context state. A stale analysis is rejected instead of being silently combined with a newer repository state.

### Evidence graph

```bash
vericore evidence-graph /path/to/repository --json
```

The semantic evidence graph is a bounded, deterministic relationship layer over already-produced repository evidence. It is bound to repository/commit identity, rejects invalid provenance relationships, and exposes a stable SHA-256 digest for downstream identity and provenance.

### Architecture drift and governance

```bash
vericore architecture /path/to/repository --json
vericore architecture-drift /path/to/repository --baseline output/architecture-baseline.json --json
vericore architecture-contract /path/to/repository --json
```

`architecture-drift` compares a previously generated `ArchitectureIntelligenceResult` with the current deterministic architecture analysis. `architecture-contract` evaluates current architecture findings against the configured deterministic governance contract.

### Evidence, planning, and safe changes

```bash
vericore repo-qa "why is PaymentService risky?" \
  --path /workspace/example \
  --evidence-output output/grounded-evidence.json

vericore plan "add payment validation" \
  --evidence output/grounded-evidence.json \
  --output output/engineering-plan.json

vericore prepare "add payment validation" --path /workspace/example

vericore verify \
  --path /workspace/example \
  --plan output/engineering-plan.json \
  --contract output/agent-change-contract.json \
  --output output/verification.json
```

`prepare` persists three repository-scoped artifacts by default:

- `output/engineering-context.json`
- `output/engineering-plan.json`
- `output/agent-change-contract.json`

The Agent Change Contract is bound to the canonical repository identity and prepared Git `HEAD` when available. Its fingerprint covers the change summary, repository identity, prepared `HEAD`, planned paths, expected components, verification commands, evidence IDs, and architecture expectations.

`verify` loads the persisted contract and rejects missing, tampered, mismatched, cross-repository, or stale contracts rather than silently generating a replacement.

## REST endpoints

### `GET /`

Returns a service banner.

### `GET /health`

Returns service health and version information. Liveness and readiness endpoints are also available.

### `POST /analyze`

Analyzes an existing local repository and generates an HTML report.

```json
{
  "repoPath": "/workspace/example"
}
```

Remote URLs are rejected. The request path must resolve to a readable directory under a configured allowed root.

### `GET /reports/{id}.html`

Serves a generated report. Retention and authorization remain deployment responsibilities.

### `POST /ask`

Answers a repository question using the configured AI provider and repository-derived context.

```json
{
  "repoPath": "/workspace/example",
  "question": "Where is authentication configured?"
}
```

AI must be explicitly enabled. Provider data-handling requirements must be considered before sending source-derived context outside the local environment.

### `POST /analyze-org`

Analyzes multiple local repositories with bounded concurrency. At most 20 repositories may be submitted per request, and each repository is subject to `maxFilesAnalyze`.

### Change and PR intelligence

Local change-impact and PR Intelligence flows are exposed through the application and CLI. See [PR_INTELLIGENCE.md](PR_INTELLIGENCE.md) for the deterministic result model and rules.

## Path security

The server resolves paths with `Path.toRealPath()` and accepts only readable directories equal to or descendants of a configured allowed root.

Allowed roots are configured with `VERICORE_ALLOWED_PATHS`, separated by the platform path separator. The legacy `CODECONTEXT_ALLOWED_PATHS` variable remains supported as a compatibility path. Configure the smallest practical set of roots.

## Rate limiting

Rate limiting is enabled by default. Configuration includes:

```json
{
  "rateLimit": {
    "enabled": true,
    "requestsPerMinute": 60,
    "requestsPerHour": 1000
  }
}
```

## AI boundary

AI is an optional reasoning layer. It must not be treated as the source of repository facts. Grounded evidence is deterministic and bounded before it is included in an AI request.

Do not place secrets, credentials, or unapproved confidential source code in AI prompts. Provider and internal failures are sanitized before public API clients receive them.

## Error shape

```json
{
  "error": "Invalid or unsafe repository path"
}
```

Provider and internal failures are sanitized before being returned to clients.
