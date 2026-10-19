<p align="center">
  <img src="docs/images/vericore-icon.svg" alt="Vericore" width="72" height="72">
</p>

<h1 align="center">Vericore</h1>

<p align="center"><strong>AI writes the code. Vericore verifies the change.</strong></p>

<p align="center">An open-source, local-first way to check whether an AI coding agent stayed within the change you asked for—and see what the evidence does and does not prove.</p>

<p align="center">
  <a href="https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml"><img src="https://github.com/sonii-shivansh/Vericore/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://github.com/sonii-shivansh/Vericore/releases"><img src="https://img.shields.io/github/v/release/sonii-shivansh/Vericore" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="MIT License"></a>
</p>

## Get started

Install the latest published release. The installer verifies the downloaded archive's SHA-256 checksum; release archives include a Java runtime.

**Linux / macOS**
```bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
```

**Windows PowerShell**
```powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
```

Check the installation:
```bash
vericore doctor
vericore --version
```

Need manual installation? See [Getting Started](docs/GETTING_STARTED.md).

## Verify an AI-assisted change

Run these steps from your Git repository. Replace the example path with a file your agent is expected to change.

**1. Before asking your coding agent to edit, record the intent and scope:**
```bash
vericore prepare "add payment validation" \
  --path . \
  --planned-path src/main/kotlin/com/example/PaymentService.kt
```

**2. Ask your coding agent to make the change.** Do not replace the saved Vericore contract after the edit.

**3. After the edit, check the original contract:**
```bash
vericore verify --path .
```

Vericore reports whether the observed change matched the prepared boundary and what its checks established. Run your project's tests and review the diff as well.

## Why Vericore?

- **Check the change, not just the agent's claim.** Vericore compares the repository with a change contract recorded before editing.
- **Use evidence you can inspect.** Results identify checks, reasons and criteria that still need review.
- **Keep repository analysis local by default.** AI-provider features are optional; deterministic analysis does not require an AI API key.
- **Keep your existing workflow.** Use the CLI or connect an agent through MCP.

## Important limits

Vericore currently focuses on Java and Kotlin repositories. Kotlin parsing is regex-based and has known limitations. A successful build or test command does **not** prove that the requested behavior is correct or that the tests cover it. A `PASS` applies only to the defined checks—it is not a guarantee that the software is bug-free.

The local MCP and REST interfaces are for trusted local/internal use; they are not a sandbox for coding agents or build scripts, and the REST server does not provide production-grade authentication or tenant isolation.

## Use it in your workflow

- [Connect an AI coding agent with MCP](docs/MCP.md)
- [Use Vericore in GitHub Actions](docs/GITHUB_ACTION.md)
- [Read the short product explanation](docs/WHY_VERICORE.md)
- [See exact commands and options](docs/CLI.md)
- [Check supported features and limits](docs/SUPPORT_MATRIX.md)
- [Review privacy and data handling](docs/DATA_PRIVACY.md)
- [Build, test or contribute](docs/DEVELOPMENT.md)

## Build from source

Requires JDK 21+ and Git.

```bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test installDist
```

For bug reports, include a small reproduction and sanitized command output. See [Contributing](CONTRIBUTING.md), or report security issues privately via [Security Policy](SECURITY.md).

If Vericore helps your workflow, a GitHub star helps other developers discover it.

## License

[MIT](LICENSE)
