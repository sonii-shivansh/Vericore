# Getting Started

> From a fresh install to repository understanding and a verified code change.

## Choose your path

### I want to understand a repository

Start with:

~~~bash
vericore analyze /path/to/repository
~~~

Then open:

~~~text
/path/to/repository/output/index.html
~~~

For machine-readable deterministic state:

~~~bash
vericore evidence-graph /path/to/repository --json
vericore reality /path/to/repository --json
~~~

### I want to understand a risky change

Use deterministic repository evidence:

~~~bash
vericore repo-qa "Why is PaymentService risky?" --path /path/to/repository
vericore impact /path/to/repository src/main/kotlin/com/example/PaymentService.kt --json
vericore architecture /path/to/repository --json
~~~

### I want to let an AI agent use Vericore

Start the local MCP server:

~~~bash
vericore mcp
~~~

Then read [MCP](MCP.md) for the canonical tool surface and trust boundary.

### I want to make a code change safely

Prepare the repository before editing:

~~~bash
vericore prepare "add payment validation" --path /path/to/repository
~~~

Make the code change, run the project's normal tests, then verify:

~~~bash
vericore verify --path /path/to/repository
~~~

The verification step is bound to the persisted Agent Change Contract created by prepare.

## Prerequisites

For a source build, use JDK 21 or newer:

- JDK 21+
- Git
- Bash, PowerShell, or another shell supported by the Gradle wrapper

Released platform archives bundle a Java runtime, so a separate JDK is not required for normal end-user use.

## 1. Install from a release archive

Download the published archive from [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases).

Platform archives bundle a Java runtime for normal end-user execution:

~~~text
Windows:      bin\vericore.bat --version
Linux/macOS:  ./bin/vericore --version
~~~

## 2. Install with one command

### Linux x64 / macOS

~~~bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
~~~

### Windows PowerShell (x64)

~~~powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
~~~

The installer downloads the latest published platform archive, verifies its SHA-256 checksum from SHA256SUMS, installs the bundled Java runtime, and avoids requiring a separate JDK.

Supported targets: Linux x64, macOS x64, macOS arm64, and Windows x64. After installation, open a new shell if PATH was updated.

## 3. Build from source

If you are developing Vericore itself:

~~~bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test
./gradlew --no-daemon installDist
~~~

The installed CLI is:

~~~text
build/install/vericore/bin/vericore
~~~

Verify it:

~~~bash
./build/install/vericore/bin/vericore --version
./build/install/vericore/bin/vericore --help
~~~

For every command, option, default, output artifact, and failure behavior, use the **[CLI Reference](CLI.md)**.

## 4. Check the local installation

From a Git repository:

~~~bash
vericore doctor
~~~

doctor checks the Java runtime, Git repository context, effective AI configuration, credentials when configured, and Gemini reachability when applicable. Missing AI credentials are a warning rather than a failure because deterministic repository analysis does not require AI.

## 5. Go deeper

| Goal | Read |
|---|---|
| Exact command syntax | [CLI Reference](CLI.md) |
| Understand deterministic repository identity | [Engineering Reality](ENGINEERING_REALITY.md) |
| Understand safe changes | [Change Safety](CHANGE_SAFETY.md) |
| Understand the system | [Architecture](ARCHITECTURE.md) |
| Integrate an AI agent | [MCP](MCP.md) |
| Integrate REST | [API](API.md) |
| Review data handling | [Data & Privacy](DATA_PRIVACY.md) |
| Contribute code | [Development](DEVELOPMENT.md) |

## Troubleshooting principle

Start with the exact command and sanitized output. For CI-only failures, inspect the corresponding GitHub Actions job and artifact rather than inferring from a local result.
