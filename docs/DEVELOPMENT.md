# Development Guide

> Build, test, and extend Vericore without crossing its deterministic, local-first boundaries.

## Before you start

Read [Getting Started](GETTING_STARTED.md) if you have not built Vericore before.

### Prerequisites

- JDK 21+
- Git
- Kotlin-capable editor
- Bash, PowerShell, or another shell supported by the Gradle wrapper

## The development loop

Use a small, evidence-driven loop:

```text
understand
   ↓
change
   ↓
test
   ↓
inspect diff
   ↓
update docs/contracts
   ↓
CI
   ↓
review
```

For significant behavior changes, document the intended behavior before implementation. Keep one pull request focused on one coherent change.

## Build and test

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon build
./gradlew --no-daemon installDist
```

The installed CLI is:

```bash
./build/install/vericore/bin/vericore --version
```

GitHub Actions is the authoritative clean-environment validation path. When local hardware is unavailable, use CI to validate the application and inspect the complete workflow logs and artifacts.

## CLI smoke test

```bash
./build/install/vericore/bin/vericore --help
./build/install/vericore/bin/vericore --version
./build/install/vericore/bin/vericore analyze .
./build/install/vericore/bin/vericore reality . --json
./build/install/vericore/bin/vericore repo-qa "why is this component risky?" --path . --evidence-output output/grounded-evidence.json
./build/install/vericore/bin/vericore plan "change the component" --evidence output/grounded-evidence.json --output output/engineering-plan.json
./build/install/vericore/bin/vericore prepare "change the component"
./build/install/vericore/bin/vericore verify --plan output/engineering-plan.json --contract output/agent-change-contract.json
```

A successful `prepare` produces repository-scoped context, plan, and Agent Change Contract artifacts. Do not replace the persisted contract before `verify`.

## Local server

```bash
./build/install/vericore/bin/vericore server --host 127.0.0.1 --port 8080
curl --fail http://127.0.0.1:8080/health
```

Keep the server bound to loopback for local development. Do not expose it publicly without an appropriate deployment boundary providing authentication, authorization, TLS, trusted-origin controls, quotas, and report-retention controls.

See [API](API.md).

## Configuration

Create a local configuration file from the template when needed:

```bash
cp .vericore.json.template .vericore.json
```

`VericoreConfig` controls exclusions, file limits, Git history, caching, parsing, reporting, AI, and rate limiting. Never commit credentials.

For server path validation, configure the narrowest practical value for `VERICORE_ALLOWED_PATHS`.

## Project structure

```text
src/main/kotlin/com/vericore/
├── Main.kt
├── cli/                       # user-facing commands and adapters
├── core/
│   ├── ai/                   # optional provider integrations
│   ├── cache/                # analysis cache
│   ├── config/               # configuration and credentials
│   ├── exceptions/           # domain/application errors
│   ├── generator/            # report and learning helpers
│   ├── graph/                # dependency graph algorithms
│   ├── intelligence/         # deterministic engineering intelligence
│   ├── parser/               # Java/Kotlin parser contracts
│   ├── planner/              # evidence-backed planning
│   ├── qa/                   # grounded repository Q&A
│   ├── reality/              # repository-state identity
│   ├── scanner/              # repository and Git scanning
│   ├── temporal/             # evolution analysis
│   └── workflow/             # prepare/verify contracts
├── enterprise/               # organization-oriented capabilities
├── mcp/                      # local MCP protocol adapter
├── output/                   # report generation
└── server/                   # Ktor API boundary

src/test/kotlin/              # unit, property, security, CLI, server, and E2E tests
docs/                         # architecture, contracts, integration, and contributor docs
.github/workflows/            # authoritative CI and release verification
```

## Engineering boundaries

- deterministic analysis is the source of repository facts;
- evidence is bounded and repository-relative;
- AI is optional and must not overwrite deterministic facts;
- planner output is read-only;
- Agent Change Contracts are verification artifacts, not authorization tokens;
- external input is validated at system boundaries;
- source is not executed by analysis;
- public APIs must not expose stack traces, provider bodies, credentials, or unnecessary absolute paths.

For the architectural model, see [Architecture](ARCHITECTURE.md).

## Adding deterministic intelligence

1. Define a stable result contract.
2. Implement the signal under `core/`.
3. Make ordering and tie-breaking deterministic.
4. Add unit/property tests, including edge cases.
5. Convert important facts to grounded evidence where applicable.
6. Add CLI/REST/MCP adapters only after the core contract is stable.
7. Update the relevant API, architecture, MCP, and implementation-status documentation.

## Adding a parser

1. Implement `LanguageParser`.
2. Register it in `ParserFactory`.
3. Add representative tests, including malformed and empty files.
4. Update supported-language documentation.

## Adding an API route

1. Define serializable request and response models.
2. Validate size, path, revision, and content constraints before analysis.
3. Return a stable public error shape.
4. Avoid exposing local filesystem paths or internal exception messages.
5. Add route and security tests.
6. Update [API](API.md).

## Adding AI behavior

Keep the evidence-first boundary:

```text
Deterministic analysis
       ↓
Grounded evidence
       ↓
Bounded context
       ↓
AI reasoning
       ↓
Validated response
```

Do not introduce model calls directly into parsers, graph algorithms, or security boundaries. Provider-specific behavior belongs behind an explicit abstraction.

## Reports and generated assets

Reports must remain self-contained. Treat source text, commit messages, author names, and descriptions as untrusted content. Changes to HTML or JavaScript serialization require escaping/regression tests.

## Pull requests

Before opening or updating a pull request:

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon build installDist
```

Then inspect the complete diff and let the full GitHub Actions matrix run. Do not treat one green local command as release evidence.

A pull request should explain:

- what behavior changed;
- why it changed;
- compatibility/configuration implications;
- security/data-handling implications;
- tests and CI evidence;
- documentation or schema changes.

For AI-assisted features, also document evidence sources, provider boundaries, data exposure, uncertainty behavior, and CI verification requirements.

## Release checklist

1. Confirm the application version and `build.gradle.kts` agree.
2. Update `CHANGELOG.md` and implementation-status documentation.
3. Freeze root and `docs/` Markdown before publication; do not defer documentation updates until after the release.
4. Run the complete `0.8.0 Release Audit`, including Windows, Linux, cross-platform, onboarding, regression, and real-repository E2E/output-quality checks.
5. Confirm the machine-readable readiness certificate identifies the exact candidate SHA, ref, version, and successful required checks.
6. Confirm the release workflow invokes the release audit before version resolution, packaging, or publication.
7. Review generated artifacts, checksums, and dependency changes.
8. Review security and data-handling implications.
9. Confirm Agent Change Contract mutation tests pass.
10. Tag and publish only from the exact candidate certified by the final release audit.

## Documentation ownership

When behavior or a public contract changes, update the relevant documentation in the same pull request. Prefer linking to one authoritative contract over copying the same rules into several files.
