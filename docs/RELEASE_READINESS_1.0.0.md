# Vericore v1.0.0 Release Readiness

> **Status: Stabilization baseline — not yet certified for release.**
>
> This document defines the exit gate for the first stable Vericore 1.0.0 release. A checked item means the capability or validation exists; it does **not** by itself certify the final release candidate. Final certification must be performed on the exact release candidate SHA.

## Release strategy

Vericore will remain on the current development line until the 1.0 exit gate is satisfied. Do not tag or publish `v1.0.0` merely because the product has reached feature completeness.

The final release must have:

- no known release-blocking defects;
- all required CI/release gates green on the exact candidate SHA;
- documentation matching the candidate implementation;
- reproducible release artifacts with SHA-256 verification;
- post-merge validation green before tagging;
- the GitHub Action and Marketplace metadata validated against the release candidate.

## 1. Core Agent Verification

- [x] Prepare → agent changes → verify is the canonical workflow.
- [x] Agent Change Contract persists the pre-change verification boundary.
- [x] Planned-path mutation scope is enforced.
- [x] Verification uses the persisted contract rather than silently rebuilding it.
- [x] MCP prepare → contract retrieval → verify E2E exists.
- [x] Authoritative live end-to-end application validation workflow exists.
- [ ] Final live end-to-end application validation passes on the exact release candidate SHA.

## 2. CLI and public contracts

- [x] CLI documentation parity is enforced in CI.
- [x] Core `analyze`, `prepare`, `verify`, `pr-intelligence`, and `architecture-contract` contracts are documented.
- [x] `mcp-config` is documented and tested.
- [ ] Final 1.0 candidate CLI audit passes on the exact release SHA.

## 3. MCP / AI-agent integration

- [x] Local stdio MCP server is implemented.
- [x] MCP client discovery configuration is implemented.
- [x] MCP prepare/verify Agent Verification boundary is covered by E2E validation.
- [ ] Final MCP protocol/gateway audit passes on the exact release SHA.
- [ ] No known release-blocking MCP regressions remain.

## 4. GitHub Action

- [x] Reusable composite GitHub Action exists.
- [x] Linux x64, macOS x64, and macOS arm64 targets are defined.
- [x] Published release archives are SHA-256 verified before execution.
- [x] Latest-release resolution is authenticated with the workflow token.
- [x] Action execution is covered by a real clean-runner audit.
- [x] Marketplace-safe action metadata name is implemented.
- [ ] Final release-candidate Action execution passes using the release candidate tag.
- [ ] Marketplace publication is validated against the final release candidate.

## 5. Installation and cross-platform behavior

- [x] Linux x64 installer exists.
- [x] macOS x64 installer exists.
- [x] macOS arm64 installer exists.
- [x] Windows x64 installer exists.
- [x] Windows prepare → verify path has real CI coverage.
- [ ] Final packaged artifacts pass the complete cross-platform release audit on the 1.0 candidate.

## 6. Security and safety boundaries

- [x] Mutation scope is explicit and fail-closed.
- [x] Verification command execution is allowlisted and platform-aware.
- [x] Release downloads verify published SHA-256 checksums.
- [x] Unsupported Action commands/runners fail closed.
- [x] REST/MCP trust boundaries are documented.
- [ ] Final security/safety audit passes on the exact release candidate SHA.
- [ ] No known P0/P1 release-blocking security or integrity issue remains.

## 7. Documentation and governance

- [x] CLI documentation parity is automated.
- [x] Implementation status distinguishes shipped capability from future work.
- [x] Current release documentation identifies published `v0.9.0` and distinguishes post-release `main` changes from its immutable artifacts.
- [ ] All public documentation is reviewed against the 1.0 candidate.
- [ ] Changelog contains the final 1.0 release scope.
- [ ] Release notes describe only behavior actually present in the candidate artifacts.

## 8. Final release certification

The final `v1.0.0` candidate is **not ready** until all applicable items below are green on the exact candidate SHA:

- [ ] DCO
- [ ] complete test suite
- [ ] deterministic release audit
- [ ] CLI audit
- [ ] documentation parity
- [ ] MCP audit
- [ ] REST/API audit
- [ ] safety/adversarial audit
- [ ] onboarding E2E
- [ ] authoritative live end-to-end application validation
- [ ] Agent Verification E2E
- [ ] live repository gates
- [ ] live output-quality audit
- [ ] cross-platform packaging and smoke tests
- [ ] GitHub Action clean-runner execution
- [ ] release artifact SHA-256 verification
- [ ] exact-candidate version/ref/readiness checks
- [ ] post-merge `main` validation
- [ ] Marketplace metadata validation

## Release rule

Only after every required gate is green should the project:

1. update the application version to `1.0.0`;
2. update release documentation/changelog;
3. create the immutable `v1.0.0` tag;
4. build and publish the four supported release artifacts;
5. publish the GitHub Marketplace Action against the stable release;
6. run the final post-publication verification.

As of 2026-10-11, `v0.9.0` is the latest published release. Commits after its tag are unreleased development work until a new exact-SHA release certification and publication completes.
