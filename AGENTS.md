# Vericore — Repository Agent Instructions

This file applies to agents operating on the Vericore repository. It is process guidance, not evidence that a capability has shipped.

## Mission and sources of truth
- Vericore is a local-first JVM/Java-Kotlin change-assurance project. The current product uses repository analysis and an original persisted Agent Change Contract to assess a candidate change. Verification is bounded by the checks actually performed.
- Establish current facts from **the checked-out code and executable tests**. Cross-check tagged releases for shipped behavior and living docs for user-facing claims. Historical changelogs describe past releases; plans and onboarding snapshots are proposals/reference, not executable truth.
- Reference the long-term strategy in `docs/strategy/VERICORE_MASTER_PLAN.md` only when relevant; it is NOT an instruction to implement everything.
- Reference the operating agreement in `docs/process/VERICORE_OPERATING_MODEL_AND_SPRINT0.md`.

## Permission and Git controls
- The human maintainer authorizes every scoped implementation task and approves merges and releases. Never treat a master plan, issue description, or past chat as blanket approval.
- Never push directly to `main`, create or publish a release, rewrite history, modify GitHub settings, expose secrets, or install tools that grant broad unattended permissions without explicit authorization.
- Before a write task, inspect `git status`, current branch, the issue, acceptance criteria, impacted code, tests and documentation. Avoid overwriting unrelated local changes.
- Use a small branch and reviewable PR for each approved change. No parallel writing agents on the same checkout.
- In setup and Sprint 0: **do not edit application source, Gradle build logic, release workflows, or existing documentation**. Creation of the specifically approved setup-only guidance files is the sole write scope.

## Technical and quality guardrails
- Preserve currently supported CLI, MCP, REST and JSON artifact contracts unless a separately approved migration provides compatibility tests.
- Treat SHA-256 fingerprints as integrity checks, not identity, authorization, cryptographic signature or an isolation boundary. A passing build or JUnit case is not proof that arbitrary user requirements are satisfied.
- Separate IMPLEMENTED, DOCUMENTED, PROPOSED, UNKNOWN and VERIFIED-BY-TEST. Do not claim a security property or feature works without appropriate evidence and assumptions.
- Do not automatically trust repository code, tests, dependency scripts or external artifacts as safe to execute. Explain and seek approval for untrusted execution or tools requiring elevated access.
- For code changes: introduce focused tests, include negative cases and valid alternatives when relevant, and provide commands, exit codes, evidence and open risks in the final handoff.
- For docs: retain historical records; correct current claims by comparing current source, active CLI behavior, schemas and actual release metadata. Do not rewrite evidence to make checks pass.
- Never invoke Spec Kit's implementation skill unless the human expressly approves that exact feature and its acceptance criteria.
