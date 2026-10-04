# Getting Started

> From a fresh checkout to a useful Vericore analysis in a few steps.

## Prerequisites

For a source build:

- JDK 21+
- Git
- Bash, PowerShell, or another shell supported by the Gradle wrapper

Released platform archives bundle a Java runtime, so a separate JDK is not required for normal end-user use.

## 1. Build from source

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

For every command, option, default, and output artifact, use the **[CLI Reference](CLI.md)**.

## 2. Check the local installation

From a Git repository:

```bash
vericore doctor
```

`doctor` checks the Java runtime, Git repository context, effective AI configuration, credentials when configured, and Gemini reachability when applicable. Missing AI credentials are a warning rather than a failure because deterministic analysis does not require AI.

## 3. Analyze a repository

```bash
vericore analyze /path/to/repository
```

The default HTML report is written to:

```text
/path/to/repository/output/index.html
```

Create machine-readable repository-state artifacts when needed:

```bash
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
```

`evidence-graph` and `reality` consume the analysis snapshot produced by `analyze`, so keep the artifacts from the same repository state.

## 4. Review impact and architecture

```bash
vericore impact /path/to/repository src/main/kotlin/com/example/PaymentService.kt --json
vericore pr-intelligence /path/to/repository --json
vericore architecture /path/to/repository --json
```

For an explicit Git revision pair:

```bash
vericore pr-intelligence /path/to/repository --base main --head feature/payment-retry --json
```

## 5. Ask repository questions

For deterministic, grounded evidence:

```bash
vericore repo-qa "Why is PaymentService risky?" --path /path/to/repository
```

For optional AI reasoning:

```bash
vericore setup
vericore doctor
vericore ask "What are the main architectural hotspots in this repository?"
```

Read [Data & Privacy](DATA_PRIVACY.md) before enabling provider-backed features.

## 6. Prepare and verify a change safely

Create a repository-bound engineering plan and persisted change contract:

```bash
vericore prepare "add payment validation" --path /path/to/repository
```

The default artifacts are:

```text
output/engineering-context.json
output/engineering-plan.json
output/agent-change-contract.json
```

Make the code change, run normal tests, then verify the **original** persisted contract:

```bash
vericore verify --path /path/to/repository
```

Do not regenerate or replace the contract between `prepare` and `verify`.

See [Change Safety](CHANGE_SAFETY.md) for the contract semantics and failure states.

## 7. Run the local REST API

```bash
vericore server --host 127.0.0.1 --port 8080
```

In another terminal:

```bash
curl --fail http://127.0.0.1:8080/health
```

Keep the server on loopback for local use. The application does not provide deployment-grade authentication, authorization, tenant isolation, or TLS.

See [API](API.md).

## 8. Connect an AI agent with MCP

```bash
vericore mcp
```

The server uses stdin/stdout for MCP protocol traffic. See [MCP](MCP.md) for the tool catalog and safety boundary.

## Common next steps

| Goal | Read |
|---|---|
| Find exact command syntax | [CLI Reference](CLI.md) |
| Understand system architecture | [Architecture](ARCHITECTURE.md) |
| Understand Engineering Reality | [Engineering Reality](ENGINEERING_REALITY.md) |
| Understand safe changes | [Change Safety](CHANGE_SAFETY.md) |
| Integrate REST | [API](API.md) |
| Integrate an AI agent | [MCP](MCP.md) |
| Review data handling | [Data & Privacy](DATA_PRIVACY.md) |
| Contribute code | [Development](DEVELOPMENT.md) |

## Troubleshooting principle

Start with the exact command and sanitized output. For CI-only failures, inspect the corresponding GitHub Actions job and artifact rather than inferring from a local result.
