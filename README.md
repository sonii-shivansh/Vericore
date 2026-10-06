<p align="center">
  <img src="docs/images/vericore-icon.svg" alt="Vericore" width="96" height="96">
</p>

# Vericore

**The verification layer for AI coding agents.**

**AI can change your repository in seconds. Vericore verifies that it changed what you actually intended.**

[![Push on main](https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml)
[![Release Audit](https://github.com/sonii-shivansh/Vericore/actions/workflows/release-audit.yml/badge.svg?branch=main)](https://github.com/sonii-shivansh/Vericore/actions/workflows/release-audit.yml)
[![Documentation Parity](https://github.com/sonii-shivansh/Vericore/actions/workflows/docs-parity.yml/badge.svg?branch=main)](https://github.com/sonii-shivansh/Vericore/actions/workflows/docs-parity.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/sonii-shivansh/Vericore)](https://github.com/sonii-shivansh/Vericore/releases)

Vericore is a local-first engineering verification tool for Java and Kotlin repositories. It builds deterministic repository evidence, creates a repository-bound **Agent Change Contract** before a code change, and verifies the actual repository state against that original contract afterward.

~~~text
                  ┌─────────────────────┐
                  │   Developer / Agent │
                  └──────────┬──────────┘
                             │
                             │ "Add payment validation"
                             ▼
                    ┌────────────────────┐
                    │      PREPARE       │
                    │ evidence + plan +  │
                    │ change contract    │
                    └─────────┬──────────┘
                              │
                              ▼
                       AI agent edits code
                              │
                              ▼
                    ┌────────────────────┐
                    │       VERIFY       │
                    │ compare repository  │
                    │ state with original │
                    │ change boundary     │
                    └─────────┬──────────┘
                              │
                       PASS / REVIEW / FAIL
~~~

### The core idea

AI coding agents are good at making changes. Vericore focuses on a different question:

> **Did the repository change stay inside the boundary that was prepared before the edit?**

The workflow is:

~~~text
prepare → agent changes → verify
~~~

The `prepare` step establishes the evidence and verification boundary. The persisted Agent Change Contract binds the intended change to repository identity and the prepared Git state, including planned paths and verification expectations. The `verify` step checks that original contract rather than silently replacing it with a new one derived from the mutated repository.

### Why Vericore

| Without a verification boundary | With Vericore |
|---|---|
| Agent edits code | Agent edits code |
| Review the final diff and infer intent | Verify against a pre-change contract |
| Repository facts may be spread across tools | Deterministic repository evidence is captured first |
| AI context can drift from repository state | Evidence remains bound to the observed repository state |
| Scope expansion can be easy to miss | Unexpected mutations become explicit verification findings |

Vericore is **not** the coding agent and does **not** autonomously modify source code. It is the evidence, planning, and verification layer around the change.

## 30-second example

Prepare the change before editing:

~~~bash
vericore prepare "add payment validation" \
  --path /path/to/repository \
  --planned-path src/main/kotlin/com/example/PaymentService.kt
~~~

Let your normal coding workflow or AI agent make the change. Then verify the original boundary:

~~~bash
vericore verify --path /path/to/repository
~~~

Vericore checks the persisted contract, repository identity, prepared Git state, planned-path scope, dependency/architecture signals, and declared verification commands.

**Think of it as a pre-change contract for AI-assisted software changes.**

> **Core principle:** deterministic repository evidence first; optional AI reasoning second.

## Quick start

### Use a released archive

The published platform archives bundle a Java runtime, so normal end-user use does not require a separate JDK.

Download a release from [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases), then verify the installed archive:

~~~text
Windows:      bin\vericore.bat --version
Linux/macOS:  ./bin/vericore --version
~~~

### Install with one command

For end users, V2 adds installers that select the published platform archive and verify its SHA-256 checksum before installation.

**Linux x64 / macOS Intel / macOS Apple Silicon:**

~~~bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
~~~

**Windows PowerShell (x64):**

~~~powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
~~~

The installers use the latest published GitHub Release, verify SHA256SUMS, and install without requiring a separate JDK. The Unix installer uses a user-local directory by default; the Windows installer adds its user-local bin directory to the user PATH. Open a new shell after installation so PATH changes are picked up.

Supported V2 installer targets are Linux x64, macOS x64, macOS arm64, and Windows x64.

### First-run Doctor

After installation, verify the local runtime immediately. You do **not** need to be inside a Git repository:

~~~bash
vericore doctor
~~~

To diagnose a specific repository, pass its path explicitly:

~~~bash
vericore doctor --path /path/to/repository
~~~

Doctor treats missing Git repository context and optional AI credentials as warnings; invalid diagnostic paths and runtime/configuration failures remain actionable failures.

### Build from source

~~~bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test
./gradlew --no-daemon installDist
./build/install/vericore/bin/vericore --version
~~~

### Analyze a repository

~~~bash
vericore analyze /path/to/repository
~~~

The default HTML report is written to:

~~~text
/path/to/repository/output/index.html
~~~

Create machine-readable repository-state artifacts when needed:

~~~bash
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
~~~

### Ask a grounded repository question

~~~bash
vericore repo-qa "Why is PaymentService risky?" --path /path/to/repository
~~~

repo-qa is deterministic retrieval over repository evidence. Optional provider-backed AI reasoning is a separate path.

### Prepare → change → verify

Create the engineering context, plan, and persisted change contract:

~~~bash
vericore prepare "add payment validation" --path /path/to/repository
~~~

Make the code change, run the project's normal tests, then verify the original persisted contract:

~~~bash
vericore verify --path /path/to/repository
~~~

The verification step checks the persisted contract rather than silently generating a replacement from a mutable plan.

For a faster boundary check, use:

~~~bash
vericore verify --path /path/to/repository --contract-only
~~~

## GitHub Action

Run Vericore in GitHub Actions without installing Java or wiring the CLI manually:

~~~yaml
- uses: actions/checkout@v7
- uses: sonii-shivansh/Vericore@main
  with:
    command: analyze
~~~

The reusable action downloads a published Vericore archive, verifies its SHA-256 checksum, and runs the selected command. Supported commands are `analyze`, `verify`, `pr-intelligence`, and `architecture-contract`.

For stable consumer workflows, pin the action to a release tag or immutable commit.

See [GitHub Action](docs/GITHUB_ACTION.md) for inputs, supported runners, and verification semantics.

## AI-agent integration

Vericore exposes a local MCP stdio server for AI-agent workflows:

~~~bash
vericore mcp-config
~~~

This prints a client-ready `mcpServers.vericore` configuration using the installed `vericore` executable. Start the server directly with `vericore mcp` when needed.

The intended pattern is:

~~~text
AI agent
   │
   │ understand / plan
   ▼
Vericore
   │
   │ repository-bound contract
   ▼
code change
   │
   │ verify
   ▼
Vericore
~~~

The MCP surface includes repository analysis, impact analysis, architecture intelligence, Engineering Reality, context snapshots/diffs, evidence, preparation, change safety, and verification.

See [MCP](docs/MCP.md) and [Change Safety](docs/CHANGE_SAFETY.md) for the exact protocol and safety boundaries.

## What is implemented today

Vericore currently provides:

- Java and Kotlin repository scanning and parsing;
- configurable exclusions, parallel parsing, and content-based caching;
- dependency graphs, wildcard-import handling, cycle detection, and PageRank hotspots;
- Git authorship, churn, modification-time, and evolution signals;
- deterministic architecture intelligence, architecture drift, and architecture contracts;
- versioned engineering-context snapshots and deterministic Engineering Reality;
- a repository/commit-bound semantic evidence graph;
- deterministic grounded repository Q&A and evidence ranking;
- evidence-backed engineering planning;
- repository-bound Agent Change Contracts and post-change verification;
- optional Gemini/Anthropic provider-backed AI paths;
- local REST and MCP boundaries;
- clean-environment, cross-platform, and live-repository GitHub Actions verification.

The published release line is currently **Vericore 0.8.2**. The main branch may contain unreleased hardening after that release.

## Important boundaries

Vericore is intentionally local-first.

- Analysis focuses on **Java and Kotlin** repositories.
- Kotlin parsing has known limitations for complex syntax.
- AI is optional; deterministic repository analysis does not require an external model.
- The local REST and MCP boundaries are intended for trusted local/internal use and do not provide deployment-grade authentication, authorization, tenant isolation, or TLS.
- The planner is read-only.
- Vericore does **not** autonomously modify source code.

These are current product boundaries, not promises about future releases.

## CLI reference

The complete command contract is maintained separately:

**[→ Complete CLI Reference](docs/CLI.md)**

It documents the current commands, syntax, options, defaults, outputs, and important failure semantics.

Discover the surface directly:

~~~bash
vericore --help
vericore --version
~~~

## Architecture

~~~mermaid
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
~~~

See [Architecture](docs/ARCHITECTURE.md) for package boundaries, deterministic rules, and trust boundaries.

## Configuration and privacy

Recommended setup:

~~~bash
vericore setup
vericore doctor
~~~

The canonical project configuration file is .vericore.json. VERICORE_ALLOWED_PATHS controls server workspace boundaries. API keys must never be committed.

Vericore is local-first. Deterministic analysis does not require an external service. Provider-backed AI features receive only the bounded repository-derived context required by the invoked operation.

See [Data & Privacy](docs/DATA_PRIVACY.md).

## Development and verification

For source development:

~~~bash
./gradlew --no-daemon clean test
./gradlew --no-daemon build installDist
~~~

GitHub Actions is the authoritative clean-environment verification path for the repository. The release audit currently exercises builds/tests, CLI/MCP/REST surfaces, generated artifacts, prepare/verify safety boundaries, live repositories, onboarding, and cross-platform packaging.

See [Contributing](CONTRIBUTING.md), [Development](docs/DEVELOPMENT.md), and [Implementation Status](docs/IMPLEMENTATION_STATUS.md).

## Documentation

| Topic | Document |
|---|---|
| **Start here** | [Getting Started](docs/GETTING_STARTED.md) |
| **Complete CLI reference** | [CLI](docs/CLI.md) |
| Architecture | [Architecture](docs/ARCHITECTURE.md) |
| Change safety | [Change Safety](docs/CHANGE_SAFETY.md) |
| Engineering Reality | [Engineering Reality](docs/ENGINEERING_REALITY.md) |
| MCP / AI agents | [MCP](docs/MCP.md) |
| REST API | [API](docs/API.md) |
| PR Intelligence | [PR Intelligence](docs/PR_INTELLIGENCE.md) |
| Data & Privacy | [Data & Privacy](docs/DATA_PRIVACY.md) |
| Implementation status | [Implementation Status](docs/IMPLEMENTATION_STATUS.md) |
| Documentation hub | [docs/INDEX.md](docs/INDEX.md) |
| Contributing | [Contributing](CONTRIBUTING.md) |
| Security | [Security](SECURITY.md) |

## License

Vericore is released under the MIT License. See [LICENSE](LICENSE).
