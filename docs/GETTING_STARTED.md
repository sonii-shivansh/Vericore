# Getting Started

Get from install to your first verified AI-assisted change in a few minutes.

## 1. Install Vericore

**Linux / macOS**
```bash
curl -fsSL https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.sh | bash
```

**Windows PowerShell**
```powershell
irm https://raw.githubusercontent.com/sonii-shivansh/Vericore/main/scripts/install.ps1 | iex
```

The installer selects the latest published release and checks its SHA-256 checksum. Release archives include a Java runtime.

Check that Vericore is ready:
```bash
vericore doctor
vericore --version
```

## 2. Prepare before your coding agent edits

Open a terminal at the root of the Git repository you want to change. Initialize Vericore once, then record what you want changed and which files the agent is expected to touch:

```bash
vericore init --path .
vericore prepare "add payment validation" \
  --path . \
  --planned-path src/main/kotlin/com/example/PaymentService.kt
```

Replace the sample summary and path with your task and an expected file in your project.

Preparation creates a plan and an Agent Change Contract from the repository state **before** the edit. Keep the original contract; do not recreate it after the agent has changed the code.

## 3. Let your agent work, then verify

Use your normal coding agent to make the change. Afterwards, run:

```bash
vericore verify --path .
```

Vericore checks the actual repository against the prepared boundary and writes a verification receipt to `output/verification-receipt.json`. Read the status and reasons, inspect the diff, and run your project's normal tests.

**Important:** A successful build/test command proves only that the command succeeded. The current evaluator supports defined numeric/Boolean metric checks and narrowly bound command-success evidence; arbitrary natural-language requirements may remain `NOT_EVALUATED` or `INCONCLUSIVE`. A `PASS` is not proof that a feature is semantically correct or that the test suite is adequate.

## Connect an AI agent

Generate MCP client configuration:

```bash
vericore mcp-config
```

Follow [MCP / AI-agent integration](MCP.md) to connect your chosen client. MCP is a local integration; it does not sandbox the agent or build commands.

## Need help?

- [Exact commands and options](CLI.md)
- [How change verification works](CHANGE_SAFETY.md)
- [Supported languages and known limits](SUPPORT_MATRIX.md)
- [GitHub Actions integration](GITHUB_ACTION.md)
- [Data and privacy](DATA_PRIVACY.md)
- [Development and contribution guide](DEVELOPMENT.md)

Vericore currently focuses on Java and Kotlin repositories. Kotlin parsing is regex-based and has known limitations. Local REST/MCP interfaces are intended for trusted local/internal use, not unauthenticated public hosting.
