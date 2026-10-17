<p align="center">
  <img src="docs/images/vericore-icon.svg" alt="Vericore" width="84" height="84">
</p>

<h1 align="center">Vericore</h1>

<p align="center">
  <strong>AI writes the code. Vericore verifies the change.</strong>
</p>

<p align="center">
  A local-first verification layer for AI-assisted software changes.
</p>

<p align="center">
  <a href="https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml"><img src="https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://github.com/sonii-shivansh/Vericore/actions/workflows/release-audit.yml"><img src="https://github.com/sonii-shivansh/Vericore/actions/workflows/release-audit.yml/badge.svg?branch=main" alt="Release Audit"></a>
  <a href="https://img.shields.io/github/v/release/sonii-shivansh/Vericore"><img src="https://img.shields.io/github/v/release/sonii-shivansh/Vericore" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="MIT License"></a>
</p>

AI coding agents can change many files quickly. The hard part is checking whether a change stayed within its intended boundary—and whether the evidence used to assess it still describes the same repository state.

**Vericore records the intended change boundary before editing, then checks the repository against that original contract afterward.** It adds deterministic repository evidence around your existing AI coding workflow. It complements tests, code review, and your coding agent; it does not replace them.

## See the workflow

```text
Prepare → AI agent changes code → Verify
   │                                │
   └── save the original boundary ──┘
                                    ↓
                       inspect the actual repository state
```

Before the edit, `prepare` records the change contract. After the edit, `verify` evaluates that persisted contract instead of silently inventing a new boundary from the changed working tree.

## Get started

Install the latest published release:

**Linux / macOS**
```bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
```

**Windows PowerShell**
```powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
```

Then check the installation and analyze a repository:

```bash
vericore doctor
vericore analyze /path/to/your/repository
```

To verify an AI-assisted change, run these commands from the repository you want to change:

```bash
vericore prepare "add payment validation" \
  --path . \
  --planned-path src/main/kotlin/com/example/PaymentService.kt
```

Now make the change with your usual coding agent, then run:

```bash
vericore verify --path .
```

Run your project's tests and review the diff too. A Vericore PASS means the defined verification boundary passed; it is **not** proof that the feature is semantically correct.

**New to Vericore?** The [Getting Started guide](docs/GETTING_STARTED.md) walks through installation, repository analysis, and the full workflow.

## What it helps with

- **Understand a repository:** source analysis, dependency relationships, Git signals, and architecture indicators.
- **Assess a change:** deterministic evidence about changed files, affected components, and risk.
- **Ground engineering decisions:** repository-bound snapshots, evidence, and plans.
- **Verify agent changes:** a persisted Agent Change Contract checked after the edit.
- **Integrate into existing workflows:** local MCP integration and a reusable GitHub Action.
- **Automate with structured output:** versioned JSON artifacts as well as CLI reports.

Vericore currently focuses on **Java and Kotlin repositories**. Its Kotlin parsing is regex-based and has known limitations with complex syntax; it is not a Kotlin compiler front end.

## Why not just use tests or code review?

Tests tell you whether tested behavior meets expectations. Code review helps people judge design and correctness. Vericore addresses a different, narrower question: **did the repository change remain within the boundary established before the edit, and does the evidence still refer to the intended repository state?**

Use all three together. Vericore is not an AI coding agent, does not autonomously modify source code, and does not replace tests or human judgment.

## Integrate it

**AI coding agents (MCP)**

```bash
vericore mcp-config
```

Start with the [MCP integration guide](docs/MCP.md) and [Change Safety](docs/CHANGE_SAFETY.md).

**GitHub Actions**

```yaml
- uses: actions/checkout@v7
- uses: sonii-shivansh/Vericore@v0.9.0
  with:
    command: analyze
```

The action downloads a published archive and verifies its SHA-256 checksum before execution. For stricter supply-chain controls, pin third-party actions to a reviewed immutable commit SHA. See the [GitHub Action guide](docs/GITHUB_ACTION.md).

## Important boundaries

- A verification PASS is not a correctness proof; run project tests and review the diff.
- The planner is read-only. An Agent Change Contract defines a verification boundary, not an authorization system.
- Local REST and MCP are intended for trusted local/internal use. The REST server refuses non-loopback binding unless `--allow-remote-host` is explicitly supplied; that flag does **not** add authentication, authorization, tenant isolation, or deployment-level TLS.
- Optional provider-backed AI may receive bounded repository-derived context when configured and invoked. Review [Data & Privacy](docs/DATA_PRIVACY.md) before enabling it.

See the [Support Matrix](docs/SUPPORT_MATRIX.md) for current support and known limitations. The latest published release is listed on [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases); unreleased `main` changes are not included in published archives.

## Documentation

| I want to… | Go to |
|---|---|
| Understand the idea and design | [Why Vericore?](docs/WHY_VERICORE.md) |
| Install and run the full workflow | [Getting Started](docs/GETTING_STARTED.md) |
| Look up commands and options | [CLI Reference](docs/CLI.md) |
| Understand architecture and trust boundaries | [Architecture](docs/ARCHITECTURE.md) |
| Connect an AI agent or REST client | [MCP](docs/MCP.md) · [API](docs/API.md) |
| Review privacy and data handling | [Data & Privacy](docs/DATA_PRIVACY.md) |
| Build, test, or contribute | [Development](docs/DEVELOPMENT.md) · [Contributing](CONTRIBUTING.md) |
| Browse advanced references and project records | [Documentation Hub](docs/INDEX.md) |

## Build from source

Requirements: JDK 21+ and Git.

```bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test
./gradlew --no-daemon build installDist
```

The CLI is created under `build/install/vericore/bin/`. CI is the authoritative clean-environment validation path for release certification.

## Contribute

Start with [Contributing](CONTRIBUTING.md). Include a small reproduction, exact command output, and relevant repository details with secrets removed. Report vulnerabilities privately using [Security Policy](SECURITY.md).

If Vericore is useful to you, a GitHub star helps other developers discover it. Focused contributions and reproducible bug reports help us make it better.

## License

Vericore is released under the [MIT License](LICENSE).
