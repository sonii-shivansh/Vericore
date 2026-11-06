# Vericore 2.0.0 Migration Guide

This guide assists users, automation pipelines, and CI/CD integrations migrating from Vericore **1.0.x** (including 1.0.2) to Vericore **2.0.0**.

---

## 1. Summary of Breaking Changes

Vericore 2.0.0 introduces two breaking changes:
1. **Minimum Runtime Upgrade to Java 25 LTS**: Support for Java 21, 22, 23, and 24 has been discontinued. Launchers and distribution binaries fail closed at startup if launched on runtimes older than Java 25.
2. **Universal CLI Command Envelope & `init --json` Structure**: Machine-readable JSON output has been standardized across all 25 CLI commands. In `vericore init --json`, the top-level `repository` property changed from a primitive string path to a structured object, and domain data was relocated under `"data"`.

---

## 2. Upgrading Your Java Runtime (Java 25 Required)

Vericore requires **Java 25 LTS or newer** (tested on Eclipse Temurin 25).

### Ubuntu / Debian
```bash
sudo apt-get update
sudo apt-get install -y openjdk-25-jdk
# Or install Eclipse Temurin 25 via Adoptium PPA
```

### macOS (Homebrew)
```bash
brew install openjdk@25
```

### Windows (winget / PowerShell)
```powershell
winget install EclipseAdoptium.Temurin.25.JDK
```

### Verification
Ensure `java -version` reports 25 or newer:
```bash
java -version
# openjdk version "25.0.2" ...
```
If an outdated Java runtime is detected, Vericore 2.0.0 terminates immediately:
```text
VERICORE JAVA VERSION REQUIREMENT
Installed Java version: 21
Required Java version: 25 or newer
Vericore cannot start because your Java runtime is outdated.
```

---

## 3. Universal Command Envelope Migration

All CLI commands emitting JSON now strictly conform to `CommandEnvelope<T>` (`schemaVersion: "1.0"`).

### Standard Envelope Structure
```json
{
  "schemaVersion": "1.0",
  "command": "init",
  "status": "PASS",
  "summary": "Vericore successfully initialized for repository example",
  "repository": {
    "name": "example",
    "path": "/workspace/example"
  },
  "data": { ... },
  "findings": [],
  "recommendations": [],
  "artifacts": [],
  "nextStep": "vericore scan --path /workspace/example",
  "execution": {
    "exitCode": 0
  }
}
```

---

## 4. `init --json` Migration Details

### Pre-2.0.0 Output (v1.0.2)
```json
{
  "schemaVersion": "1.0",
  "command": "init",
  "status": "PASS",
  "repository": "/workspace/example",
  "findings": [],
  "artifacts": [
    { "kind": "config", "path": "/workspace/example/vericore.json" }
  ],
  "nextStep": "vericore scan --path /workspace/example"
}
```

### Post-2.0.0 Output (v2.0.0)
```json
{
  "schemaVersion": "1.0",
  "command": "init",
  "status": "PASS",
  "summary": "Vericore successfully initialized for repository example",
  "repository": {
    "name": "example",
    "path": "/workspace/example"
  },
  "data": {
    "schemaVersion": "1.0",
    "command": "init",
    "status": "PASS",
    "repository": "/workspace/example",
    "artifacts": [
      { "kind": "config", "path": "/workspace/example/vericore.json" }
    ],
    "nextStep": "vericore scan --path /workspace/example"
  },
  "findings": [],
  "artifacts": [
    { "kind": "config", "path": "/workspace/example/vericore.json" }
  ],
  "nextStep": "vericore scan --path /workspace/example",
  "execution": {
    "exitCode": 0
  }
}
```

### Migration Code Snippets

#### Python Client
```python
# Before (v1.0.2):
repo_path = envelope["repository"]  # Expected string path

# After (v2.0.0 compatible with both):
repo = envelope.get("repository")
if isinstance(repo, dict):
    repo_path = repo.get("path")
    repo_name = repo.get("name")
else:
    repo_path = repo

# Access execution status
exit_code = envelope.get("execution", {}).get("exitCode", 0)
```

#### Bash with `jq`
```bash
# Before:
REPO_PATH=$(vericore init --json | jq -r '.repository')

# After:
REPO_PATH=$(vericore init --json | jq -r 'if .repository | type == "object" then .repository.path else .repository end')
EXIT_CODE=$(vericore init --json | jq -r '.execution.exitCode // 0')
```

#### TypeScript / Node.js
```typescript
interface VericoreEnvelope<T> {
  schemaVersion: string;
  command: string;
  status: string;
  summary: string;
  repository?: { name: string; path: string } | string;
  data?: T;
  findings: Array<{ level: string; message: string }>;
  execution?: { exitCode: number };
}

function getRepoPath(env: VericoreEnvelope<unknown>): string {
  if (typeof env.repository === "object" && env.repository !== null) {
    return env.repository.path;
  }
  return String(env.repository ?? "");
}
```

---

## 5. Standardized Exit Code Semantics

Vericore 2.0.0 enforces centralized error mapping via `ErrorHandler.kt`:

| Exit Code | Classification | Trigger Scenarios |
| :--- | :--- | :--- |
| **0** | `SUCCESS` | Normal successful command execution. |
| **1** | `VALIDATION_FAILURE` | Contract verification failed, change invariant violated, or Clikt option parsing error. |
| **2** | `INVALID_ARGUMENT / CONFIG` | Unsupported `--format`, conflicting options, port out of range, or invalid configuration. |
| **3** | `INTERNAL_ERROR` | Unexpected unhandled exceptions or JVM runtime errors. |

---

## 6. Schema Version vs. Application Version

- **Application Release Version**: `2.0.0` (Semantic Versioning for the Vericore binary and distribution).
- **JSON Envelope Contract Version**: `"1.0"` (The version of the `CommandEnvelope<T>` envelope specification defined in `docs/CLI_OUTPUT_CONTRACTS.md`).
- **Domain Schema Versions**: Individual domain artifacts retain their respective schema specification versions (e.g., `grounded-evidence` is `1.1`, `agent-change-contract` is `3.1`, `analysis-snapshot` is `1.0`).
