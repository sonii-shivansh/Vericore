# Changelog

All notable changes to Vericore are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and the project uses [Semantic Versioning](https://semver.org/).

> Releases `0.1.0` through `0.6.0` were published under the project's former name. `0.7.0` is the first release line with Vericore as the canonical product identity.

## [Unreleased]

Future changes after the published `0.8.0` release are documented here as they are implemented.

## [0.8.0] — 2026-10-04

### Added

- Semantic evidence graph with repository/commit-bound evidence identity, deterministic relationships, provenance validation, and stable SHA-256 graph identity.
- Cross-feature evidence resolution across architecture, hotspot, temporal, and repository-Q&A evidence.
- Materialized `semantic-evidence-graph.json` artifacts and the `evidence-graph` CLI command.
- Evidence-graph identity and cardinality binding into Engineering Reality.
- Deterministic evidence-backed AI grounding with citation validation and grounding-confidence controls.
- Repository-scoped engineering planning and Agent Change Contracts with persisted verification boundaries.
- Expanded deterministic repository correctness, evolution-window, and release-gate regression coverage.

### Changed

- Engineering Reality now incorporates semantic evidence graph state into its deterministic identity.
- Repository-QA retrieval preserves relevant architecture and hotspot evidence across feature-specific queries.
- Engineering plans and contracts are explicitly bound to their target repository and prepared Git state.
- Windows distributions use a compact `lib\\*` launcher classpath to avoid command-line length failures.
- Release and cross-platform verification now exercise the complete evidence-grounded engineering workflow on clean environments and real repositories.

### Fixed

- Repository-scoped CLI failure semantics and truthful parse-failure propagation.
- Evolution history-window sampling so selected commits remain inside the requested cutoff-to-HEAD interval.
- Windows launcher failures caused by expanded dependency classpaths.
- Evidence integrity edge cases including duplicate, dangling, self-referential, stale-commit, and cross-repository relationships.

### Release engineering

- Application and Gradle build version aligned to `0.8.0`.
- Release packaging continues to cover Linux x64, Windows x64, macOS x64, and macOS ARM64 with bundled Java runtimes and SHA-256 checksums.
- Release validation covers deterministic analysis, grounded intelligence, semantic evidence graph materialization, Engineering Reality, prepare/verify contracts, MCP, REST, and live-repository gates.

## [0.7.0] — 2026-10-03

### Added

- Engineering Reality and deterministic engineering-context snapshot/diff capabilities.
- Architecture drift and deterministic architecture-contract governance.
- Repository-bound Agent Change Contracts for the prepare → change → verify workflow.
- Persisted contract validation for repository identity, prepared Git `HEAD`, plan binding, tamper detection, and unexpected working-tree scope.
- Expanded MCP engineering-context, architecture-governance, evidence, preparation, safety, and verification tools.
- Live-repository release-gate coverage for contract tampering, unexpected source changes, repository restoration, MCP, REST, and optional Gemini integration.

### Documentation

- Reworked the public documentation around the canonical Vericore product identity and current command surface.
- Removed completed migration working documents from the active documentation tree.

### Release engineering

- Application and build version aligned to `0.7.0`.
- Release verification uses the declared application version instead of a hard-coded previous release version.
- Release packaging continues to cover Linux x64, Windows x64, macOS x64, and macOS ARM64 with bundled Java runtimes and SHA-256 checksums.

## [0.6.0]

### Added

- Launch-hardening verification for CLI, repository Q&A, engineering planning, REST health, intelligence endpoints, and security input boundaries.
- A single application version contract used by the build and REST health endpoint.
- Self-contained HTML report visualization with no runtime CDN dependency.
- Cross-platform distribution smoke verification for Linux x64, Windows x64, macOS x64, and macOS ARM64.

### Changed

- Project version is aligned to the v0.6.0 release line.
- CI quality gates now fail on ktlint errors instead of ignoring them.
- Clean-environment verification validates the installed CLI version and exercises the deterministic intelligence flows.
- Documentation reflects the current local-first implementation and offline report behavior.
- Release verification covers CLI behavior and distribution artifacts across the supported platform matrix.

### Security

- Generated report scripts escape data before embedding it in HTML.
- Verification covers unsafe repository URLs, traversal-like paths, and incomplete Git revision pairs.
- Security support policy is aligned with the current release line.

## [0.5.0]

### Added

- Deterministic Change Impact Intelligence and PR Intelligence.
- Architecture Intelligence and architecture evidence.
- Versioned `AnalysisSnapshot` and grounded `EvidenceCitation` contracts.
- Grounded repository Q&A retrieval through the `repo-qa` CLI command.
- Evidence-backed engineering planning through the `plan` CLI command.
- Linux and Windows CI verification for the current intelligence flows.

### Changed

- Documentation describes the implemented evidence-first architecture and current command/API surface.
- AI is an optional reasoning layer rather than a repository source of truth.

### Security

- Grounded evidence exposes repository-relative paths instead of absolute filesystem paths.
- AI context remains bounded and opt-in.
- Server path validation and rate limiting remain enabled for the local API.

## [0.2.0]

### Added

- CLI release packaging and verification workflow.
- Build, test, packaging, report-generation, and server smoke validation.
- Ktor REST server health endpoints.
- Java and Kotlin repository analysis with dependency visualization.
- PageRank hotspot detection, learning paths, Git metadata, caching, and optional AI assistance.

### Changed

- Improved parser concurrency and rate-limit rollback behavior.
- Added server-side path validation and configurable resource limits.
- Improved local distribution and release validation.

## [0.1.0]

### Added

- Initial public release.
- Java and Kotlin parsing.
- Interactive dependency graph reports.
- PageRank-based hotspots.
- Learning-path generation.
- Git history integration.
- CLI and Ktor server modes.
- Optional AI code insights.

### Known limitations

- The server does not provide authentication, tenant isolation, or report retention.
- AI analysis requires external provider access and explicit configuration.

[Unreleased]: https://github.com/sonii-shivansh/Vericore/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/sonii-shivansh/Vericore/releases/tag/v0.8.0
[0.7.0]: https://github.com/sonii-shivansh/Vericore/releases/tag/v0.7.0
[0.6.0]: https://github.com/sonii-shivansh/Vericore/releases/tag/v0.6.0
[0.5.0]: https://github.com/sonii-shivansh/Vericore/releases/tag/v0.5.0
[0.2.0]: https://github.com/sonii-shivansh/Vericore/releases/tag/v0.2.0
[0.1.0]: https://github.com/sonii-shivansh/Vericore/releases/tag/v0.1.0
