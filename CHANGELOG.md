# Changelog

This file records user-visible changes by release. It follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses [Semantic Versioning](https://semver.org/).

> Releases `0.1.0` through `0.6.0` were published under the project's former name. `0.7.0` is the first release line with Vericore as the canonical product identity.

## [Unreleased]

### Fixed

- Ignore the generated root `.vericore.json` settings file during change-scope verification so first-run initialization does not count as an unexpected source edit.
- Reject filesystem roots as REST API allowed roots, even when the server is launched from `/` or a Windows drive root.
- Report controlled verification and MCP configuration failures as actionable validation errors instead of generic stack traces.

- MCP change-safety and verification tools return an invalid-parameters error when the required plan argument is missing, rather than a generic internal error.
- Verification results include explicit reasons when no verification command runs or a verification command fails.

## [0.9.0] — 2026-10-10

This release was published on 2026-10-10 after the exact-candidate release audit passed.

### Added

- One-command installers for Linux x64, macOS x64, macOS arm64, and Windows x64, with published-archive SHA-256 verification and bundled-runtime installation.
- First-run `doctor` behavior that works after installation, supports `--path`, distinguishes warnings from actionable failures, and is covered by clean-environment onboarding E2E validation.
- MCP prepare/contract-retrieval/verify integration that persists the preparation, plan, and Agent Change Contract artifacts used by the CLI.
- A dedicated agent-verification end-to-end gate covering MCP preparation, repository modification, contract retrieval, and verification.
- Live/black-box validation workflows for the VCORE-001 through VCORE-004 remediation tracks.
- CI-enforced CLI documentation parity so runtime command changes cannot silently leave `docs/CLI.md` stale.

### Changed

- Preparation accepts explicit planned paths before source edits, enabling a more bounded prepare → change → verify workflow.
- Verification-command validation accepts only platform-native Maven/Gradle wrapper or system build executables; generated commands use native Windows `cmd` syntax on Windows.
- Repository analysis and dependency-graph handling were strengthened, including same-package dependency coverage and parser robustness.
- Engineering-plan scope handling, working-tree mutation checks, onboarding documentation, and release-audit contracts were tightened.
- Analysis and grounded-evidence artifacts are bound to the analysis schema, repository commit, and source-state digest; stale evidence is rejected before planning, while legacy evidence is not treated as fresh.

### Fixed

- Unsafe shell separators and embedded line breaks in verification commands are rejected before execution.
- Verification executes the validated build-command body rather than a persisted directory-prefix string.
- Regression coverage checks Unix ampersand/newline separators and Windows CMD separators/line breaks.
- Windows Maven/Gradle wrapper detection recognizes `mvnw.cmd` and `gradlew.bat`, with prepare-to-verify E2E coverage in the Windows CI path.

### Validation and documentation

- Clean-environment onboarding, MCP, live-repository, output-quality, cross-platform, and regression checks cover the relevant boundaries.
- CLI, Getting Started, and release-audit documentation track the implemented installation and onboarding contracts.

## [0.8.2] — 2026-10-04

### Fixed

- Closed the post-`0.8.1` audit findings around diagnostic exit semantics, controlled validation failures, repository-QA dependency evidence, architecture dependency-edge persistence and drift detection, and organization-analysis response serialization.
- Stabilized organization-analysis hotspots as JSON objects with explicit `file` and `score` fields instead of Kotlin `Pair` serialization.
- Persisted deterministic architecture dependency edges so architecture drift can report `EDGE_ADDED` and `EDGE_REMOVED` changes from explicit edge sets.
- Added regression and release-hardening coverage for the historical `0.8.1` findings.

### Changed

- Architecture Intelligence artifacts use schema `1.1` and include persisted `dependencyEdges`; schema `1.0` remains historical compatibility material where applicable.
- Release preparation and current-release documentation are aligned to `0.8.2` while preserving the published `0.8.1` release record.

### Release engineering

- Application and Gradle build version aligned to `0.8.2`.
- Release certification continues to require deterministic audit, complete tests, CLI/MCP/REST validation, live repositories, onboarding/regression gates, four-platform packaging, bundled-runtime checks, and SHA-256 checksums.

## [0.8.1] — 2026-10-04

### Fixed

- Closed the post-0.8.0 end-to-end audit findings across REST serialization, diagnostic exit semantics, repository-QA evidence grounding, engineering-plan mutation scope, validation failure UX, and architecture-drift reporting.
- Hardened release and cross-platform validation for the corrected engineering workflow.

### Release engineering

- Application and Gradle build version aligned to `0.8.1`.
- `v0.8.1` was published on 2026-10-04 with Linux x64, Windows x64, macOS x64, and macOS ARM64 artifacts plus SHA-256 checksums.
- The later post-release remediation is intentionally excluded from this historical release record and is prepared as `0.8.2`.

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
- Documentation now has one complete CLI reference covering every user-facing command, syntax, options, defaults, outputs, and important failure semantics.
- Redundant release-planning and duplicate semantic-evidence notes were removed from the public documentation tree.

### Fixed

- Repository-scoped CLI failure semantics and truthful parse-failure propagation.
- Evolution history-window sampling so selected commits remain inside the requested cutoff-to-HEAD interval.
- Windows launcher failures caused by expanded dependency classpaths.
- Evidence integrity edge cases including duplicate, dangling, self-referential, stale-commit, and cross-repository relationships.
- Documentation drift between the implemented CLI surface, release workflow, architecture/evidence contracts, and public guides.

### Release engineering

- Application and Gradle build version aligned to `0.8.0`.
- Release packaging covers Linux x64, Windows x64, macOS x64, and macOS ARM64 with bundled Java runtimes and SHA-256 checksums.
- Release validation covers deterministic analysis, grounded intelligence, semantic evidence graph materialization, Engineering Reality, prepare/verify contracts, MCP, REST, and live-repository gates.
- Pre-release certification was performed through GitHub Actions before publication; the release workflow was blocked until the candidate passed the release audit.
- `v0.8.0` was published successfully on 2026-10-04 with platform archives and SHA-256 checksums.

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
