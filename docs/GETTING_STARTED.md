# Getting Started

> From installation to your first repository analysis and verified AI-assisted change.

For the product story, start with [Why Vericore?](WHY_VERICORE.md). This page is the practical path.

## 1. Install

### Release archive

Download the published archive from [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases).

Platform archives bundle a Java runtime:

```text
Windows:      bin\vericore.bat --version
Linux/macOS:  ./bin/vericore --version
```

### One-command installer

**Linux / macOS:**

```bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
```

**Windows PowerShell:**

```powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
```

The installer downloads the latest published platform archive and verifies its SHA-256 checksum before installation. Supported targets are Linux x64, macOS x64, macOS arm64, and Windows x64. Open a new shell after a PATH update.

## 2. Check the installation

Run:

```bash
vericore doctor
```

You do not need to be inside a Git repository for this check.

For a specific repository:

```bash
vericore doctor --path /path/to/repository
```

Doctor checks the local runtime and configuration, with repository and AI-provider details reported when applicable. Missing AI credentials are warnings because deterministic analysis does not require AI.

## 3. Understand a repository

For a quick repository analysis:

```bash
vericore analyze /path/to/repository
```

The default HTML report is written to:

```text
/path/to/repository/output/index.html
```

For deeper machine-readable evidence:

```bash
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
```

For exact command syntax, options, defaults, outputs, and failure behavior, see the **[CLI Reference](CLI.md)**.

## 4. Safely make an AI-assisted change

This is Vericore's primary verification workflow:

```text
prepare → AI agent changes code → verify
```

### Before the edit

Create the repository-bound change contract:

```bash
vericore prepare "add payment validation" \
  --path /path/to/repository
```

For tighter scope, specify planned paths:

```bash
vericore prepare "add payment validation" \
  --path /path/to/repository \
  --planned-path src/main/kotlin/com/example/PaymentService.kt
```

### Let the agent change the repository

Use your normal AI coding workflow. Vericore does not autonomously modify source code.

### After the edit

Run:

```bash
vericore verify --path /path/to/repository
```

Verification evaluates the **original persisted Agent Change Contract** rather than silently creating a new boundary from the mutated repository.

Run the project's normal tests as part of your engineering workflow. A Vericore verification result does not replace tests or human review.

## 5. Connect an AI agent

Generate a client-ready MCP configuration:

```bash
vericore mcp-config
```

Or start the local server directly:

```bash
vericore mcp
```

See [MCP](MCP.md) for the canonical tool surface and trust boundary.

## 6. Build Vericore from source

For Vericore development, use **JDK 21+** and Git:

```bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test
./gradlew --no-daemon installDist
```

The installed CLI is:

```text
build/install/vericore/bin/vericore
```

Verify it:

```bash
./build/install/vericore/bin/vericore --version
./build/install/vericore/bin/vericore --help
```

## Go deeper

| Goal | Read |
|---|---|
| Exact command contract | [CLI Reference](CLI.md) |
| Understand safe changes | [Change Safety](CHANGE_SAFETY.md) |
| Understand the system | [Architecture](ARCHITECTURE.md) |
| Understand repository state | [Engineering Reality](ENGINEERING_REALITY.md) |
| Integrate an AI agent | [MCP](MCP.md) |
| Integrate REST | [API](API.md) |
| Review data handling | [Data & Privacy](DATA_PRIVACY.md) |
| Contribute code | [Development](DEVELOPMENT.md) |

## Troubleshooting

Start with the exact command and sanitized output. For CI-only failures, inspect the corresponding GitHub Actions job and artifact rather than inferring from a local result.
