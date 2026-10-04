# Vericore 0.8.0 Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn the `release/0.8.0-preparation-v2` branch into a machine-verifiable, pre-release-certified 0.8.0 candidate without relying on post-release documentation changes.

**Architecture:** Keep permanent CI/regression workflows separate from release certification and publishing. `release-audit.yml` remains the release-candidate orchestrator and produces a machine-readable readiness certificate; `release.yml` invokes that certification before packaging/publishing. Reusable live repository workflows remain the authoritative real-repository E2E layers.

**Tech Stack:** GitHub Actions, Bash, Python 3, Gradle, Kotlin/JVM, JSON.

**Spec:** `docs/RELEASE_READINESS_0.8.0.md` and the 0.8.0 preparation requirements captured in the project work log.

## Global Constraints

- Work only on `release/0.8.0-preparation-v2` until 0.8.0 readiness is proven.
- GitHub Actions is the authoritative execution environment because local end-to-end execution is unavailable.
- Do not merge PR #130 or publish 0.8.0 during preparation.
- Do not defer documentation synchronization until after release.
- Do not delete unique regression or platform coverage merely to reduce workflow count.
- A readiness certificate must identify the exact candidate commit and declared application version.
- A failed required release check must never produce a publishable-ready result.

## Review Focus

- Exact candidate identity: certificate SHA/version must match the source being certified.
- False-green release states: a certificate must fail closed when any required check fails or is missing.
- Workflow overlap: permanent CI and release certification must have explicit responsibilities rather than accidental duplication.
- Repository safety: live gates must continue to prove real-repository immutability and adversarial contract behavior.
- Documentation drift: the readiness contract and workflow matrix must remain synchronized before release.

---

### Task 1: Workflow responsibility baseline

- [x] Inventory all workflows on `release/0.8.0-preparation-v2`.
- [x] Classify each workflow as permanent CI/regression, release certification, reusable live gate, or publishing.
- [x] Record unique coverage and intentional overlap.
- [x] Commit the baseline matrix.

### Task 2: Machine-readable readiness certificate

- [x] Define a versioned certificate schema containing candidate SHA, ref, version, timestamp, required checks, and overall readiness.
- [x] Generate the certificate only after the deterministic/live release-audit jobs complete.
- [x] Fail closed when any required audit job is not successful.
- [x] Upload the certificate as a release-audit artifact.
- [x] Verify the certificate in GitHub Actions, including exact-candidate binding.

### Task 3: Release publication gate

- [x] Make release audit callable as a reusable workflow.
- [x] Make the release workflow invoke the release-candidate audit before packaging/publishing.
- [x] Require successful certification before downstream release jobs can execute.
- [x] Verify the application version contract against the release version.

### Task 4: Full release verification matrix

- [x] Determine which permanent checks must be part of final 0.8.0 certification.
- [x] Reuse existing workflows where possible instead of duplicating test logic.
- [x] Add platform/onboarding/regression/live-repository evidence to the certification path.
- [x] Run the complete matrix on GitHub Actions and fix every failure.
- [x] Confirm DCO and all required release-audit dependencies are green on the certified candidate.

### Task 5: Documentation freeze

- [x] Audit root and `docs/` Markdown for stale CodeContext, old version, command, workflow, and branch references.
- [x] Update documentation to the verified 0.8.0 candidate behavior.
- [x] Synchronize the readiness documents with the exact certified candidate and workflow architecture.
- [ ] Add additional documentation consistency checks to CI if practical.
- [ ] Run the final full GitHub Actions matrix after the documentation-only commits below.

### Task 6: Final 0.8.0 certification

- [ ] Generate the final readiness certificate for the exact post-documentation candidate SHA.
- [ ] Confirm all required checks are green on that exact SHA.
- [ ] Confirm documentation is frozen before release.
- [ ] Produce the final release-readiness report.
- [ ] Only then allow release publication.
