<p align="center">
  <img src="docs/images/vericore-icon.svg" alt="Vericore" width="96" height="96">
</p>

# Vericore

**Understand. Change. Verify.**

**Evidence-grounded engineering intelligence for your codebase.**

Vericore is a Kotlin/JVM CLI and local REST application that analyzes Java and Kotlin source code, dependency structure, Git history, and engineering signals to produce reproducible engineering intelligence. It also provides grounded repository Q&A, evidence-backed engineering planning, a local MCP interface for AI agents, and a deterministic prepare → change → verify safety loop.

> **Core principle:** deterministic evidence first, optional AI reasoning second.

## Current status

Vericore `0.8.0` is the release candidate currently being prepared from the post-`0.7.0` main line. The product identity, CLI, package namespace, configuration namespace, distribution name, reports, REST server, MCP tools, and documentation are canonical Vericore surfaces.

## Capabilities

- Java and Kotlin source analysis
- dependency graph construction
- PageRank knowledge hotspots
- cycle and architecture analysis
- Git authorship, churn, and evolution analysis
- learning-path generation
- deterministic change-impact analysis
- deterministic PR Intelligence
- Architecture Intelligence, architecture drift, and deterministic architecture contracts
- versioned analysis/evidence artifacts
- deterministic engineering-context snapshots and diffs
- deterministic semantic evidence graph with repository/commit-bound identity
- deterministic Engineering Reality identity across analysis, repository state, and evidence graph
- grounded repository Q&A retrieval
- deterministic, evidence-backed engineering planning
- evidence-first `prepare` workflow
- repository-bound **Agent Change Contract** artifacts with SHA-256 fingerprints
- deterministic `verify` workflow against the persisted contract
- optional AI assistance over bounded repository-derived context
- local Ktor REST API
- local MCP stdio server for AI-agent integration
- path validation and rate limiting
- clean-environment end-to-end verification
- cross-platform distribution smoke verification for Linux x64, Windows x64, macOS x64, and macOS ARM64

## Quick start

**New to Vericore? Start with [Getting Started](docs/GETTING_STARTED.md).**

### Use a released archive

Released platform archives bundle the Java runtime, so a separate Java installation is not required for normal end-user use.

```text
Windows:      bin\\vericore.bat --version
Linux/macOS:  ./bin/vericore --version
```

From the repository you want to analyze:

```text
vericore analyze .
vericore reality . --json
vericore evidence-graph . --json
```

The default report is written to `output/index.html`. Machine-readable artifacts are written under the analyzed repository's `output/` directory.

### Build from source

```bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test
./gradlew --no-daemon installDist
./build/install/vericore/bin/vericore --version
```

## Common workflows

### Analyze a repository

```bash
vericore analyze /path/to/repository
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
```

The evidence graph is a bounded, deterministic relationship layer over already-produced repository evidence. It is bound to the observed repository commit and exposes a stable SHA-256 digest for downstream identity and provenance.

### Ask a grounded repository question

```bash
vericore repo-qa "why is PaymentService risky?" --path /path/to/repository
```

AI is optional. Deterministic evidence remains the source of repository facts.

### Prepare and verify a change

```bash
vericore prepare "add payment validation" --path /path/to/repository
```

Then implement the change and verify the original persisted contract:

```bash
vericore verify \
  --path /path/to/repository \
  --plan output/engineering-plan.json \
  --contract output/agent-change-contract.json
```

`prepare` writes:

```text
output/engineering-context.json
output/engineering-plan.json
output/agent-change-contract.json
```

The persisted Agent Change Contract is the verification boundary. Do not replace it with a newly generated contract after preparation.

See [Change Safety](docs/CHANGE_SAFETY.md).

### Run the local REST server

```bash
vericore server --host 127.0.0.1 --port 8080
```

```bash
curl --fail http://127.0.0.1:8080/health
```

Keep the server on loopback for local development. The application does not provide deployment-grade authentication, authorization, tenant isolation, or TLS.

See [API](docs/API.md).

### Integrate an AI agent with MCP

```bash
vericore mcp
```

See [MCP](docs/MCP.md) for the current tool contract and safety boundary.

## CLI reference

```bash
vericore analyze /path/to/repository
vericore impact /path/to/repository src/main/Service.kt --json
vericore pr-intelligence /path/to/repository --json
vericore pr-intelligence /path/to/repository --base main --head feature/my-change --json
vericore architecture /path/to/repository --json
vericore architecture-drift /path/to/repository --baseline /path/to/architecture-baseline.json --json
vericore architecture-contract /path/to/repository --contract /path/to/.vericore-architecture-contract.json --json
vericore context-snapshot /path/to/repository --json
vericore context-diff /path/to/before.json /path/to/after.json --json
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
vericore repo-qa "why is PaymentService risky?" --path /path/to/repository
vericore prepare "add payment validation" --path /path/to/repository
vericore verify --path /path/to/repository --plan output/engineering-plan.json --contract output/agent-change-contract.json
vericore ask "What are the main architectural hotspots in this repository?"
vericore evolution /path/to/repository
vericore server --host 127.0.0.1 --port 8080
vericore mcp
```

For the complete developer workflow and implementation guidance, see [Development](docs/DEVELOPMENT.md).

## Architecture

```mermaid
flowchart TD
    R[Repository + Git] --> A[Deterministic Analysis]
    A --> C[Engineering Context]
    A --> G[Semantic Evidence Graph]
    C --> E[Engineering Reality]
    G --> E
    E --> I[Deterministic Intelligence]
    I --> Q[Grounded Evidence]
    G --> Q
    Q --> P[Q&A / Planner]
    Q --> V[Prepare / Verify]
    V --> K[Agent Change Contract]
    Q --> X[CLI / REST / MCP / CI]
    K --> X
    Q --> AI[Optional AI]
    AI --> X
```

See [Architecture](docs/ARCHITECTURE.md) for the detailed system model and package boundaries.

## Configuration and privacy

The recommended setup path is:

```bash
vericore setup
vericore doctor
```

`VERICORE_GEMINI_API_KEY` and `VERICORE_GOOGLE_API_KEY` are supported for CI and non-interactive environments. Never commit API keys.

A repository-local `.vericore.json` is the canonical configuration file. `VERICORE_ALLOWED_PATHS` controls server workspace boundaries; keep allowed roots as narrow as practical.

For users migrating from older releases, legacy configuration/environment names are accepted only as explicitly deprecated migration paths and are not part of the canonical Vericore contract.

Vericore is local-first. With AI disabled, deterministic repository analysis does not send repository content to a Vericore telemetry or storage service. AI is opt-in and sends bounded repository-derived context directly to the configured provider when invoked.

See [Data & Privacy](docs/DATA_PRIVACY.md).

## Development and CI

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon build installDist
```

GitHub Actions is the authoritative clean-environment verification path. It validates compilation, tests, CLI flows, generated artifacts, intelligence flows, architecture governance, prepare/verify contracts, server/API boundaries, cross-platform distribution smoke tests, and live-repository E2E behavior.

For contributor workflow, see [Contributing](CONTRIBUTING.md) and [Development](docs/DEVELOPMENT.md).

## Documentation

| Topic | Document |
|---|---|
| Start here | [Getting Started](docs/GETTING_STARTED.md) |
| Documentation hub | [docs/INDEX.md](docs/INDEX.md) |
| Architecture | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Engineering Reality | [docs/ENGINEERING_REALITY.md](docs/ENGINEERING_REALITY.md) |
| Change Safety | [docs/CHANGE_SAFETY.md](docs/CHANGE_SAFETY.md) |
| REST API | [docs/API.md](docs/API.md) |
| MCP / AI agents | [docs/MCP.md](docs/MCP.md) |
| PR Intelligence | [docs/PR_INTELLIGENCE.md](docs/PR_INTELLIGENCE.md) |
| Data & Privacy | [docs/DATA_PRIVACY.md](docs/DATA_PRIVACY.md) |
| Development | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Implementation Status | [docs/ENTERPRISE_ROADMAP.md](docs/ENTERPRISE_ROADMAP.md) |
| Contributing | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Security | [SECURITY.md](SECURITY.md) |

## License

Vericore is released under the MIT License. See [LICENSE](LICENSE).
