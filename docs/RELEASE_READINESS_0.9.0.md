# Vericore v0.9.0 Release Readiness

> **Purpose: Historical release certification record (snapshot: 2026-10-10).**
>
> `v0.9.0` was published from tag `v0.9.0`, pointing to commit `19309e96c6d14298cd439cf01ee936715eead19a`. The Release Audit run [38065287251](https://github.com/sonii-shivansh/Vericore/actions/runs/38065287251) completed successfully and its machine-readable readiness certificate reported `overallReady: true` for that exact candidate. This document records the pre-publication gate contract; it is not a claim that later commits are certified.

## Release intent

- Target version: `0.9.0`
- Previous public release: `v0.8.2`
- Release type: substantial pre-1.0 feature, integration, hardening, and installation release
- Candidate branch: `main` (the version-alignment preparation has been merged)
- Candidate SHA: freeze and record the final SHA after all documentation and code changes are merged, immediately before exact-candidate certification
- Public release: published on 2026-10-10 as `v0.9.0`

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

## Publication record

The publication sequence was completed for the `v0.9.0` tag on 2026-10-10. The exact-candidate Release Audit passed, the release workflow completed successfully, and the four-platform artifact/checksum contract was validated by the release workflow. Any follow-up code change must be certified separately on its own commit; the historical certificate does not certify follow-up commits.

## Known product boundaries to state accurately

- Primary source analysis is Java and Kotlin; Kotlin parsing has documented limitations.
- Local REST/MCP are intended for trusted local/internal use and do not provide a full authentication/authorization or sandbox boundary.
- The planner is read-only; autonomous source-code modification is not implemented.
- A PASS is evidence about the declared verification boundary, not a guarantee of semantic correctness or a replacement for review.
- `v0.9.0` is pre-1.0; do not promise 1.0 compatibility guarantees.

## Certificate gate contract

The Release Audit certificate requires these gates on one candidate SHA: deterministic audit, Windows CI, product verification, four-platform smoke, onboarding E2E, the live-E2E matrix, both live-repository gates, both output-quality gates, documentation parity, the clean-runner GitHub Action audit, and installer path-safety regression. The certificate generator rejects missing, duplicated, or unexpected gate names; a skipped, cancelled, or failed required gate cannot yield `overallReady: true`.

DCO and pull-request required checks are separate contribution controls. They must pass independently before merge; they are not represented as a release-audit job in the certificate.

## Certification rule

For the original publication, every required gate had to pass on the exact candidate SHA, and the tag-triggered Release workflow had to validate the tag/version contract and artifact set. That requirement was satisfied for commit `19309e96c6d14298cd439cf01ee936715eead19a`, as recorded above. This historical certificate does not certify any subsequent code changes; follow-up changes require their own CI and release decision.
