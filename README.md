<p align="center">
  <img src="docs/images/vericore-icon.svg" alt="Vericore" width="96" height="96">
</p>

<h1 align="center">Vericore</h1>

<p align="center">
  <strong>AI writes the code. Vericore verifies the change.</strong>
</p>

<p align="center">
  The verification layer for AI coding agents.
</p>

<p align="center">
  <a href="https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml"><img src="https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://github.com/sonii-shivansh/Vericore/actions/workflows/release-audit.yml"><img src="https://github.com/sonii-shivansh/Vericore/actions/workflows/release-audit.yml/badge.svg?branch=main" alt="Release Audit"></a>
  <a href="https://github.com/sonii-shivansh/Vericore/actions/workflows/docs-parity.yml"><img src="https://github.com/sonii-shivansh/Vericore/actions/workflows/docs-parity.yml/badge.svg?branch=main" alt="Documentation Parity"></a>
  <a href="https://github.com/sonii-shivansh/Vericore/releases"><img src="https://img.shields.io/github/v/release/sonii-shivansh/Vericore" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="MIT License">
</p>

AI coding agents can change a repository in seconds. Vericore creates a repository-bound verification boundary **before** the edit, then checks the actual repository state **after** the edit.

It is local-first, deterministic by default, and designed to work around AI coding agents rather than replace them.

## The idea

```text
       INTENDED CHANGE
              │
              ▼
       ┌──────────────┐
       │    PREPARE   │
       │ evidence +   │
       │ plan +       │
       │ contract     │
       └──────┬───────┘
              │
              ▼
          AI agent
        changes code
              │
              ▼
       ┌──────────────┐
       │    VERIFY    │
       │ original     │
       │ boundary     │
       └──────┬───────┘
              │
        ┌─────┼─────┐
        ▼     ▼     ▼
      PASS  REVIEW  FAIL
```

The important question is not only:

> "Did the agent produce code?"

It is:

> **"Did the repository change stay inside the boundary established before the agent edited it?"**

Vericore does not autonomously modify source code. It provides the evidence, planning, and verification layer around the change.

**Core principle:** deterministic repository evidence first; optional AI reasoning second.

## Try it in minutes

After installing Vericore, from a Git repository:

```bash
vericore init
vericore scan --path .
vericore inspect --path .
```

Before an AI agent edits the repository:

```bash
vericore prepare "add payment validation" \
  --path . \
  --planned-path src/main/kotlin/com/example/PaymentService.kt
```

Let your normal coding workflow or AI agent make the change. Then:

```bash
vericore verify --path .
```

Vericore verifies the persisted change contract, repository identity, prepared Git state, planned-path scope, and declared verification expectations.

For the complete product journey, see [Getting Started](docs/GETTING_STARTED.md).

## Install

Published platform archives bundle a Java runtime for normal end-user use.

### Linux / macOS

```bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
```

### Windows PowerShell

```powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
```

Then check the installation:

```bash
vericore doctor
```

The installers select the latest published release and verify its SHA-256 checksum before installation. Supported installer targets are Linux x64, macOS x64, macOS arm64, and Windows x64.

For source builds and detailed installation guidance, see [Getting Started](docs/GETTING_STARTED.md).

## For AI agents

Vericore exposes a local MCP server for agent workflows.

```bash
vericore mcp-config
```

The intended boundary is:

```text
AI agent
   │
   ├── understand / plan
   ▼
Vericore
   │
   ├── repository-bound change contract
   ▼
agent changes repository
   │
   ▼
Vericore
   │
   └── verify
```

The MCP surface includes repository analysis, evidence, architecture signals, planning, preparation, change safety, and verification.

See [MCP / AI-Agent Integration](docs/MCP.md) and [Change Safety](docs/CHANGE_SAFETY.md).

## GitHub Actions

Vericore can run in CI through its reusable GitHub Action:

```yaml
- uses: actions/checkout@v7
- uses: sonii-shivansh/Vericore@main
  with:
    command: analyze
```

The action downloads a published Vericore archive, verifies its SHA-256 checksum, and runs the selected command.

For consumer workflows, pin the action to a release tag or immutable commit.

See [GitHub Action](docs/GITHUB_ACTION.md).

## What Vericore does today

- Java and Kotlin repository scanning and parsing
- deterministic dependency and architecture analysis
- Git history, change-impact, and PR intelligence
- repository-bound engineering context and evidence
- grounded repository Q&A and evidence-backed planning
- Agent Change Contracts and post-change verification
- local MCP and REST integration
- optional provider-backed AI reasoning
- cross-platform installers and GitHub Actions verification

The latest published release is **v0.8.2**. The `main` branch may contain unreleased development work after that release.

## What Vericore deliberately does not do

- It does not replace the coding agent.
- It does not autonomously modify source code.
- It does not make AI the source of repository truth.
- It does not claim that a `PASS` result replaces tests or human review.
- It does not currently provide production-grade authentication or tenant isolation for the local REST/MCP boundaries.

Vericore currently focuses on **Java and Kotlin** repositories. Kotlin parsing has known limitations for complex syntax.

## Documentation

| I want to... | Start here |
|---|---|
| Understand Vericore | [Why Vericore?](docs/WHY_VERICORE.md) |
| Get started | [Getting Started](docs/GETTING_STARTED.md) |
| Learn every command | [CLI Reference](docs/CLI.md) |
| Connect an AI agent | [MCP](docs/MCP.md) |
| Run it in CI | [GitHub Action](docs/GITHUB_ACTION.md) |
| Understand verification safety | [Change Safety](docs/CHANGE_SAFETY.md) |
| Understand the architecture | [Architecture](docs/ARCHITECTURE.md) |
| Review data handling | [Data & Privacy](docs/DATA_PRIVACY.md) |
| Contribute | [Contributing](CONTRIBUTING.md) |
| Browse all documentation | [Documentation Hub](docs/INDEX.md) |

## Development

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon build installDist
```

GitHub Actions is the authoritative clean-environment verification path for the repository.

See [Development](docs/DEVELOPMENT.md) and [Implementation Status](docs/IMPLEMENTATION_STATUS.md).

## License

Vericore is released under the [MIT License](LICENSE).
