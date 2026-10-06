<p align="center">
  <img src="docs/images/vericore-icon.svg" alt="Vericore" width="96" height="96">
</p>

# Vericore

**Understand the codebase. Plan the change. Verify the result.**

**Evidence-grounded engineering intelligence for Java and Kotlin repositories.**

Vericore is a local-first Kotlin/JVM tool that turns repository source, dependency structure, Git state, architecture signals, and grounded evidence into deterministic engineering context. It then uses that context to support repository Q&A, impact analysis, engineering plans, and a repository-bound **prepare → change → verify** workflow.

> **Core principle:** deterministic repository evidence first; optional AI reasoning second.

## Why Vericore

Most code-change workflows answer two different questions:

1. **What is true about this repository?**
2. **Did the change stay inside the boundary we intended to make?**

Vericore keeps those questions connected.

| Stage | What Vericore provides |
|---|---|
| **Understand** | Source parsing, dependency graphs, cycles, PageRank hotspots, Git signals, architecture findings, Engineering Reality, and grounded evidence |
| **Plan** | Repository questions, change-impact signals, deterministic PR Intelligence, evidence-backed engineering plans, and a persisted Agent Change Contract |
| **Verify** | Repository identity, prepared Git HEAD, planned-path scope, change impact, architecture signals, and declared verification commands |

The planner is read-only. The persisted Agent Change Contract becomes the verification boundary used after the change.

## Quick start

### Use a released archive

The published platform archives bundle a Java runtime, so normal end-user use does not require a separate JDK.

Download a release from [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases), then verify the installed archive:

~~~text
Windows:      bin\vericore.bat --version
Linux/macOS:  ./bin/vericore --version
~~~

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

## AI-agent integration

Vericore exposes a local MCP stdio server for AI-agent workflows:

~~~bash
vericore mcp
~~~

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
