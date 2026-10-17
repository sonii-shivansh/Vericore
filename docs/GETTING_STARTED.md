# Getting Started

> Install Vericore, inspect a repository, and verify an AI-assisted change.

New to Vericore? Read [Why Vericore?](WHY_VERICORE.md) for the product story. This page is the shortest practical path.

## 1. Install

Download a platform archive from [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases). Published archives bundle a Java runtime.

```text
Windows:      bin\vericore.bat --version
Linux/macOS:  ./bin/vericore --version
```

Or use the installer:

**Linux / macOS**
```bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
```

**Windows PowerShell**
```powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
```

The installer verifies the downloaded archive's SHA-256 checksum. Supported targets are Linux x64, macOS x64, macOS arm64, and Windows x64. Open a new shell after a PATH update.

## 2. Inspect a repository

Check the local installation, then analyze the repository you want to understand:

```bash
vericore doctor
vericore analyze /path/to/repository
```

The default HTML report is written to `/path/to/repository/output/index.html`. Missing AI credentials are warnings: deterministic analysis does not require an AI provider.

For machine-readable repository evidence:

```bash
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
```

## 3. Verify an AI-assisted change

Vericore's main workflow establishes the verification boundary before the edit:

```text
prepare → AI agent changes code → verify
```

Before the agent edits the repository, persist the intended change:

```bash
vericore prepare "add payment validation" --path /path/to/repository --planned-path src/main/kotlin/com/example/PaymentService.kt
```

Make the edit with your normal coding agent, then verify the original contract:

```bash
vericore verify --path /path/to/repository
```

Verification evaluates the persisted Agent Change Contract; do not replace it after the edit. Vericore does not autonomously modify source code. Run the project's normal tests and review the diff too—a Vericore result does not replace either.

## 4. Connect an AI agent

Generate an MCP client configuration:

```bash
vericore mcp-config
```

To start the local MCP server directly:

```bash
vericore mcp
```

Read [MCP / AI-agent integration](MCP.md) for available tools and the trust boundary.

## Find the right reference

| If you want to… | Read |
|---|---|
| Find exact command syntax, options, and outputs | [CLI Reference](CLI.md) |
| Understand why Vericore exists | [Why Vericore?](WHY_VERICORE.md) |
| Learn the change-safety model | [Change Safety](CHANGE_SAFETY.md) |
| Understand the architecture | [Architecture](ARCHITECTURE.md) |
| Inspect repository state and evidence | [Engineering Reality](ENGINEERING_REALITY.md) |
| Integrate through REST | [API](API.md) |
| Review data handling | [Data & Privacy](DATA_PRIVACY.md) |
| Build from source and contribute | [Development](DEVELOPMENT.md) |

For troubleshooting, include the exact command and sanitized output. For CI-only failures, inspect the corresponding GitHub Actions job and artifact instead of inferring success from a local run.