# Vericore v0.9.0 Release Readiness

> **Status: Preparation in progress — not certified and not published.**
>
> This checklist is the release work tracker. The earlier `release/0.9.0-preparation` branch was merged through PR #234; this file now tracks the unreleased candidate on main. A green Phase 0 audit or a green check on a different SHA does not certify v0.9.0. Freeze and record the final candidate SHA, run the full release audit on that exact commit, then verify the immutable tag/release workflow before publication.

## Release intent

- Target version: `0.9.0`
- Previous public release: `v0.8.2`
- Release type: substantial pre-1.0 feature, integration, hardening, and installation release
- Candidate branch: `main` (the version-alignment preparation has been merged)
- Candidate SHA: freeze and record the final SHA after all documentation and code changes are merged, immediately before exact-candidate certification
- Public release: not yet created

## Scope to review and document

- [ ] Agent Verification prepare → change → verify workflow and persisted Agent Change Contract
- [ ] MCP prepare/contract retrieval/verify integration and end-to-end coverage
- [ ] CLI behavior and documentation parity
- [ ] Linux x64, Windows x64, macOS x64, and macOS arm64 installers and bundled Java runtimes
- [ ] GitHub Action command/version/install behavior and clean-runner audit
- [ ] Verification-command grammar, mutation-scope checks, evidence freshness, and contract binding
- [ ] REST/MCP trust boundaries and documented limitations
- [ ] Artifact schema changes and compatibility notes
- [ ] Release notes derived from actual changes since `v0.8.2`; exclude roadmap-only and unimplemented claims

## Version and artifact contract

- [ ] Gradle version is `0.9.0`
- [ ] Application `Version.current` is `0.9.0`
- [ ] Unix release-preparation script expects `0.9.0`
- [ ] Windows release-preparation script expects `0.9.0`
- [ ] CLI reports `0.9.0` from a clean distribution
- [ ] All active version assertions, release scripts, docs, and packaging references are reconciled
- [ ] Historical `v0.8.2` tag, release assets, checksums, and historical readiness record remain unchanged

## Required certification on the exact candidate SHA

- [ ] DCO and required PR checks
- [ ] Clean build, complete JVM test suite, and `installDist`
- [ ] Phase 0 schema/golden repository/correctness benchmark audit
- [ ] CLI audit and documentation parity
- [ ] MCP audit and Agent Verification E2E
- [ ] REST audit
- [ ] Adversarial contract, tamper, stale-state, and unexpected-mutation checks
- [ ] Windows CI and installer regression
- [ ] Linux x64 and macOS x64/arm64 platform smoke
- [ ] Clean-environment onboarding E2E
- [ ] Live end-to-end validation matrix (Spring Petclinic, RuneLite, Quarkus, Kotlin)
- [ ] Live repository gates and output-quality checks
- [ ] GitHub Action clean-runner audit
- [ ] Machine-readable release-readiness certificate reports `overallReady: true` for the exact candidate SHA
- [ ] Release workflow resolves version and candidate correctly from the exact `v0.9.0` tag
- [ ] Four platform archives and `SHA256SUMS` are produced and verified

## Publication sequence

1. Complete this preparation PR and merge only after its checks pass.
2. Run and inspect the full Release Audit on the resulting `main` candidate SHA.
3. Resolve every failure; do not waive or silently skip a required gate.
4. Finalize the changelog and public release notes from verified behavior.
5. Confirm the release workflow and exact candidate version contract on the intended tag.
6. Create/push `v0.9.0` only when all required checks are green. The repository's tag-triggered Release workflow publishes the four platform archives and SHA-256 checksums.
7. Verify the published release page, assets, checksums, tag SHA, and post-publication workflow results.

## Known product boundaries to state accurately

- Primary source analysis is Java and Kotlin; Kotlin parsing has documented limitations.
- Local REST/MCP are intended for trusted local/internal use and do not provide a full authentication/authorization or sandbox boundary.
- The planner is read-only; autonomous source-code modification is not implemented.
- A PASS is evidence about the declared verification boundary, not a guarantee of semantic correctness or a replacement for review.
- `v0.9.0` is pre-1.0; do not promise 1.0 compatibility guarantees.

## Certification rule

Until every required gate passes on the exact candidate SHA, `v0.8.2` remains the latest public release and `main` remains unreleased development work. Do not create the `v0.9.0` tag or publish artifacts during preparation.
