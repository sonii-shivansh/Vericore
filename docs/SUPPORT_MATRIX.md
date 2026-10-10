# Vericore Support Matrix

This matrix separates implemented interfaces from platforms and behaviors exercised by automated checks. It is not a promise that every third-party repository, agent, or build configuration is supported.

## Languages and analysis

| Area | Current status | Evidence / limitation |
|---|---|---|
| Java source | Supported for repository scan, parsing, dependency analysis, and verification context | JavaRealParser and parser/graph tests |
| Kotlin source | Supported for repository scan and analysis | KotlinRegexParser is regex-based and has known limitations for complex syntax; it is not a Kotlin compiler front end |
| Other source languages | Not supported by the current scanner as first-class parsed source | RepositoryScanner selects .java and .kt source files |
| Build/test execution | Maven and Gradle wrapper/system commands, subject to the current strict verification-command grammar | BuildSystemDetector and VerificationCommandExecutor; arbitrary shell commands are intentionally not accepted |
| Repository scale | Bounded by effective maxFilesAnalyze configuration (default 50,000) | VericoreConfig; live-repository workflows use timeouts and contract-only verification for selected large repositories |

## Interfaces

| Interface | Status | Trust boundary |
|---|---|---|
| CLI | Implemented | Runs with the invoking user's filesystem permissions |
| MCP over stdio | Implemented and exercised by MCP audits and prepare/verify E2E | Assumes the local MCP client is trusted; does not itself sandbox agent or build processes |
| Local REST server | Implemented; health/readiness and API audit workflows exist | Local/internal use; no built-in authentication, authorization, tenant isolation, or deployment TLS |
| GitHub Action | Implemented; composite action and clean-runner audit exist | Release downloads are checksum-verified; unsupported action commands/runners fail closed |
| Installers | Linux x64, macOS x64, macOS arm64, Windows x64 | Installer/platform workflows cover these targets; final candidate artifacts still require release-candidate verification |
| Native adapters for every named coding agent | Not yet complete as a common adapter layer | Phase 2 roadmap; MCP is currently the common integration boundary |
| GitLab app, IDE marketplace integrations, hosted multi-tenant service | Not implemented as complete supported products | Later roadmap work; do not infer support from the existence of CLI/MCP/REST |

## Security and verification boundaries

- Verification checks the persisted contract, repository identity, prepared Git HEAD, plan binding, and current mutation scope.
- Persisted SHA-256 contract fingerprints detect inconsistent/tampered contents; they are not signatures, user authentication, or authorization.
- Verification-command validation restricts the allowed command grammar. It is not a sandbox for the build tool: a permitted Maven/Gradle build may execute repository build logic with the invoking user's permissions.
- Local REST rejects remote repository URLs; remote checkout/analysis through the local endpoint is not supported.
- A PASS result should be read with its receipt and executed-command evidence. It is not a guarantee of semantic correctness or a replacement for human review.
- Autonomous source-code modification is not implemented; Vericore prepares and verifies changes made by an external agent or developer.

## CI evidence map

| Evidence | Workflow or test |
|---|---|
| Unit/compile suite | ./gradlew clean test installDist |
| Cross-platform/JVM/installer checks | ci.yml, platform.yml, installer-regression.yml, scripts/test-installers.sh |
| CLI and public journey | scripts/audit/cli-audit.sh, phase1-product-journey.yml |
| MCP/contract journey | scripts/audit/mcp-audit.sh, scripts/audit/agent-verification-e2e.sh, v3-agent-verification-e2e.yml |
| REST behavior | scripts/audit/rest-audit.sh, scripts/audit/production-e2e.sh |
| Live repositories/output quality | end-to-end-live-validation.yml, live-repository-gate.yml, live-output-quality.yml |
| Full release candidate | release-audit.yml and its exact-candidate readiness certificate |

A workflow file existing is not evidence of a particular run passing. Release readiness is evaluated against the exact candidate SHA.
