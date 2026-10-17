# Vericore — Master Product & Engineering Execution Plan

**Version:** 1.0  
**Prepared:** 2026-10-17  
**Repository:** https://github.com/sonii-shivansh/Vericore  
**Observed release:** v0.9.0 (2026-10-10)  
**Purpose:** Prompt-ready operating context for every future Vericore task.

> **Mission:** Independently evaluate whether an AI-authored software change satisfies its declared obligations, stays within its agreed scope, and has fresh, auditable evidence for the exact change being merged.

**Positioning:** *Vericore — Know whether your AI coding agent made the right change, and see the evidence before you merge.*  
Short promise: *AI writes the code. Vericore verifies the change.*

## 0. Rules for every coding agent

1. Read this document, then inspect the actual branch, HEAD, source, tests, schemas, docs, workflows and releases. This plan is not proof that work has shipped.
2. Never hallucinate. Separate observed facts, hypotheses, planned work and validated outcomes. A workflow file does not prove it passed; a task catalog does not prove cases ran.
3. Prefer a small, reviewable branch and make code, tests, schemas, benchmark and docs changes together when applicable.
4. Preserve fail-closed semantics. Missing, stale, malformed, unsupported or mismatched evidence must never silently become PASS.
5. A passing test/build/command proves only its defined property. It does not generally prove semantic correctness, test relevance, production safety, or absence of bugs.
6. Do not claim a fingerprint is a signature, or that restricted build-command parsing is a sandbox. Keep AI reasoning separate from deterministic repository facts.
7. Don't add commands/features for their own sake. Prefer the existing CLI, MCP, REST, GitHub Action and artifact pipeline.
8. State exactly which tests/audits ran, for which candidate SHA, and what failed or wasn't run. Never invent results.
9. Preserve privacy and trust boundaries. Don't add unauthenticated remote access or upload source code without a deliberate, documented need.
10. Use a focused branch/PR, never rewrite public history, and do not release unless the exact candidate passes required gates.
11. Every handoff must report files changed, behavior, exact validation/results, commit/PR, limitations, and next task.
12. Legal ownership remains with the repository owner unless separately transferred through appropriate legal/platform mechanisms; an AI assistant is not the legal owner.

## 1. Product strategy

### Differentiation
Do not compete mainly on more bug findings, more commands, generic AI-security claims, or an opaque correctness score. Build a trustworthy link between:
- the task and requirements that existed before coding;
- a persisted, repository-bound change contract;
- actual changes and engineering signals;
- per-criterion evidence and explicit uncertainty;
- an independent, portable receipt for the exact candidate commit.

The near-term product wedge is **requirement-to-evidence verification**: every declared obligation receives an explicit status; unsupported or missing evidence cannot silently become PASS. Vericore must not promise that arbitrary software can be proved correct.

### Primary user
Developers and teams using coding agents in Git-based repositories. Start with local-first CLI + MCP + GitHub Actions, not a hosted enterprise control plane.

### Market hypothesis
Adjacent categories include AI code review (Cursor Bugbot, GitHub Copilot code review, Greptile, Graphite), security/static analysis (Semgrep), pre-action agent policies/guardrails, general agent evaluations/observability, and build provenance (SLSA, GitHub attestations). They overlap but make different claims. Never assert that “nobody solves this” without dated evidence. Recheck official docs/release notes and independent evidence before competitor comparisons. Treat the differentiation thesis as a hypothesis until real users validate it.

## 2. Repository baseline observed on 2026-10-17

This was a public source/document inspection, not clean-room runtime certification. Recheck current HEAD before relying on the status.
- Kotlin/JVM + Gradle project with CLI, tests, schemas, benchmark fixtures, scripts and GitHub Actions workflows.
- JavaParser-backed Java parsing and a regex-based Kotlin parser (documented limitation).
- Dependency/architecture/change-impact/PR intelligence, grounded evidence/Q&A, deterministic plans, engineering context and semantic evidence graph.
- prepare/verify contract with repository identity, Git HEAD binding, planned paths, fingerprint and schema 3.0 prepare-time candidate-state manifest.
- Scope/state verification, allowed repository build/test commands, versioned JSON output and VerificationReceipt.
- MCP over stdio, local REST, GitHub Action, platform installers and published v0.9.0.
- Optional AI; documented design keeps deterministic facts authoritative.
- Current criterion evaluator only understands a limited deterministic subset, including numeric/Boolean metrics and bounded command-result evidence. Free-form criteria can remain INCONCLUSIVE/NOT_EVALUATED. Command exit zero does not prove test relevance.
- Fingerprints are integrity checks, not signatures or authorization. The tool does not sandbox every agent/build operation. Local REST isn't a production multi-tenant control plane with built-in authentication/authorization/TLS.
- A universal common agent adapter and complete GitLab/IDE/hosted products are not established complete products.

Revalidate these primary docs when changing the related areas:
- README.md
- docs/PRODUCT_DIRECTION.md
- docs/IMPLEMENTATION_STATUS.md
- docs/SUPPORT_MATRIX.md
- docs/ARCHITECTURE.md
- docs/CHANGE_SAFETY.md
- docs/DETERMINISTIC_EVIDENCE_EVALUATORS.md
- docs/VERIFICATION_BENCHMARK.md
- docs/CLI_OUTPUT_CONTRACTS.md
- docs/DATA_PRIVACY.md

## 3. 48-hour execution charter

**Reality check:** phases 0–9 cannot honestly be claimed production-complete, fully tested, integrated and secure in 48 hours. The sprint must deliver a validated high-value increment and an evidence-backed backlog, not fictitious completion.

### T+0–4h — Reconfirm baseline
- Capture default branch/HEAD, releases, open issues, relevant CI/release status.
- Run clean Gradle test/build and applicable audits if the environment allows.
- Trace current requirement → contract → evidence → evaluator → receipt → CLI/action.
- Record blockers and choose one specific high-impact gap based on code/tests.

Deliverable: candidate-SHA-specific baseline and chosen task.

### T+4–12h — Fix one core assurance gap
- Add reproducible positive/negative regression tests before or with implementation.
- Improve criterion-level status/evidence behavior without overclaiming semantic correctness.
- Preserve fail-closed handling for missing/stale/unsupported evidence.

Deliverable: one focused code + test + documentation/schema change.

### T+12–24h — Benchmark and onboarding
- Extend the benchmark with meaningful positive and negative cases: intended change, no-op/omission, unexpected paths, stale state, weak/missing evidence, failing/timeout commands, and legitimate non-blocked changes.
- Report actual executed outcomes, not case definitions.
- Improve shortest prepare → agent change → verify journey and result clarity.

Deliverable: runnable regressions and a concise user journey.

### T+24–36h — Reliability and integration
- Validate affected CLI/MCP/GitHub Action paths.
- Verify exact contract/candidate state and artifact compatibility.
- Run targeted tests, full Gradle test suite and relevant audits; examine false positives/false negatives.

Deliverable: tested candidate branch.

### T+36–48h — Review and handoff
- Review diff and security/privacy boundaries; update docs/schemas/changelog.
- Check required workflow results for exact candidate SHA.
- Create PR with test evidence, limitations and rollback plan.
- Release only if all release gates pass; otherwise report blockers.

Deliverable: PR or release-ready candidate with honest evidence, and next prioritized work. A failed gate is not a reason to hide a failure.

## 4. Roadmap through 2030

Unless code plus validation evidence exists, all roadmap items are PLANNED. Update actual status with evidence, not by inference.

### Phase 0 — Foundation freeze & truth
Stabilize build/test/release gates, schema contracts, CLI-output compatibility, deterministic artifacts, stale-evidence checks and reproducible benchmark fixtures. Record support boundaries and exact candidate SHA.  
**Gate:** clean `./gradlew --no-daemon clean test installDist`, applicable audit, schema/output parity, candidate-specific benchmark artifact. Never infer green CI from a workflow file.

### Phase 1 — 60-second Vericore
Make `init → scan/inspect → review/plan → prepare → agent change → verify → report` a coherent, understandable journey.  
**Gate:** measure first useful result (initial experiment target <5 min), show status/reasons/changed paths/unevaluated criteria/next step, preserve JSON output contracts, validate clean install.

### Phase 2 — Common agent adapter
Create one versioned contract for repository/session/task identity, observable metadata, changed files, verification request and result. Use MCP as a key boundary; initially support only agents with validated E2E tests.  
**Gate:** each claimed-supported agent has E2E evidence; agent claims cannot override verifier; missing identity fails explicitly.

### Phase 3 — Change Contract 2.0
Typed and versioned requirements, allowed/protected paths, operations, invariants, evaluators, required tests/evidence, architecture/security/dependency constraints, exact repo/base/candidate identity, risk/approval policy and fingerprint.  
**Gate:** malformed, dangling, ambiguous, unsupported or excessive inputs are rejected or explicitly unevaluated; migration is safe; the original persisted contract remains authoritative.

### Phase 4 — Independent verification engine
Evaluate supported obligations using deterministic rules, tests and external checkers. Detect omissions, unexpected changes, stale evidence, configuration/dependency changes and regressions.  
**Gate:** category-specific false-pass/false-block measurements; unsupported natural language never auto-passes; command success is described only as command success; candidate state is checked after verification.

### Phase 5 — Portable receipt & provenance
Versioned receipt with repo/base/candidate, contract fingerprint, evaluator versions, criterion statuses, evidence hashes, commands/results, time/environment/freshness. Reuse in-toto/SLSA/Sigstore/GitHub attestations where suitable instead of inventing a cryptographic format.  
**Gate:** independent schema validation; tampered/stale/mismatched receipts rejected; authenticity distinguished from truth of claims.

### Phase 6 — Multi-agent/cross-repository verification
Model sessions/contracts/commits/shared components; detect overlapping writes, interface/dependency collisions, stale plans and missing coordinated changes. Respect repository permissions.  
**Gate:** reproducible positive/negative conflict fixtures; evidence identifies each repository state; low enough false-block rate to be useful.

### Phase 7 — Explainability & verified project memory
Link decisions, invariants, exceptions, owners, risks and trade-offs to source documents/commits/receipts. Store source, version, timestamp and freshness.  
**Gate:** each memory has provenance; stale/conflicting memory prompts review rather than silently passing/failing; users can inspect and manage retention.

### Phase 8 — Distribution, ecosystem & public benchmark
Publish reproducible benchmark cases, raw artifacts, methodology, support matrix and honest baseline. Expand languages and integrations based on user demand; improve docs/demos/contributor experience.  
**Gate:** report executed cases, candidate, environment, outcomes, limitations and repeatability. Comparative claims require comparable conditions. Track onboarding/retention/time-to-result/false passes/false blocks.

### Phase 9 — Enterprise change assurance
Only after validated demand: policy-as-code, RBAC/SSO, approvals/exceptions, audit/retention, compliance exports, private deployments, registry and organization-wide controls.  
**Gate:** customer requirements, threat model, tenant-isolation/authz tests, documented retention and production operations. Do not build an enterprise control plane for prestige.

## 5. Future resilience assumptions (not predictions)

Design for model/agent churn, polyglot repositories, agent fleets, supply-chain attack pressure, hosted test/simulation/telemetry evidence, organizationally varying policies, privacy/cost/latency constraints and long-lived receipts. Keep contracts vendor-neutral, foundations language-neutral, evidence provenance explicit, evaluators pluggable and LLM use optional. Do not build all future possibilities before validating demand.

## 6. Benchmark plan

Required case families:
- correct in-scope change and adequate evidence;
- no-op/omitted requirement despite agent claiming completion;
- unexpected path/protected invariant violation;
- stale HEAD/working tree and state changed during verification;
- contract/receipt tampering, missing/dangling/stale/cross-repo evidence;
- failed/timed-out/not-executed command;
- passing command with semantic criterion still not evaluated;
- a test missing a deliberate representative defect (when mutation testing can establish that fact);
- architecture/dependency/security regressions;
- benign change that must not be blocked.

Metrics:
- false-pass and false-block rates by category (with denominators/caveats);
- declared-obligation statuses evaluated vs NOT_EVALUATED/INCONCLUSIVE;
- first useful result time, repeat workflow completion and CI burden;
- latency/cost only under comparable runners;
- schema/adapter compatibility failures.

Zero false passes on a defined critical regression suite may be a gate; it is not a claim of zero false passes in all real-world code.

## 7. Definition of done

- [ ] User problem and expected outcome stated.
- [ ] Supported/unsupported behavior specified.
- [ ] Focused source change with positive, negative and boundary tests.
- [ ] Schemas/artifacts versioned compatibly.
- [ ] Affected CLI/MCP/REST/action paths checked.
- [ ] Docs, support matrix, changelog and CLI output contracts updated as appropriate.
- [ ] Security/privacy boundary reviewed.
- [ ] Benchmark cases actually executed and outcomes recorded.
- [ ] Validation tied to exact candidate SHA; limitations stated.
- [ ] No unsupported market, correctness, security, latency or accuracy claims.

## 8. Execution log

| ID | Work | Status | Evidence | Next action |
|---|---|---|---|---|
| B0 | Source/docs/tree reconnaissance + master plan | IMPLEMENTED | Master plan merged with PR #311; repository source/docs inspected 2026-10-17 | Full Gradle clean test/installDist passed in GitHub Actions for PR candidate `e7f4115468994beffa9f080d16cb786febae9d8c`; no local Gradle run |
| B1 | Select requirement-to-evidence gap | IMPLEMENTED | Traced `VerifyCommand`: `REVIEW_REQUIRED` reasons and criterion detail were not emitted in the normal console summary; `NOT_EVALUATED` could be less visible than intended | Verification verdict and exit semantics preserved |
| B2 | Add regression cases for summary | IMPLEMENTED | `VerificationConsoleSummaryTest.kt` covers missing specifications and mixed per-criterion statuses | Phase 0 Foundation and Phase 1 Regression workflows passed on PR candidate `e7f4115468994beffa9f080d16cb786febae9d8c` |
| B3 | Implement criterion-level console summary | MERGED | `VerificationConsoleSummary.kt`; full and contract-only verify print criterion summaries and non-pass reasons; merged to main at `d0f7900378f2169936796f40a542ec584e35d92b` | All 14 reported PR workflows passed for the reviewed head before merge |
| B4 | Verify merge and baseline CI | IMPLEMENTED | PR #311 merged to `main` as `d0f7900378f2169936796f40a542ec584e35d92b` | PR head had 14/14 GitHub Actions runs successful; post-merge release readiness not claimed |
| B5 | Emit evaluator benchmark execution report | IN_PROGRESS | Branch `feature/evaluator-benchmark-report`; test emits per-vector outcomes and candidate/fixture metadata; schema + audit/workflow artifact wiring added | Await exact-head Phase 0 and release-audit validation; no local Gradle run |

Update each row only with verified facts. Keep this file current.

## 9. Prompt template

Attach this file, then prompt:

> Read `VERICORE_MASTER_EXECUTION_PLAN.md` first. Inspect the actual branch and HEAD; do not assume the status table is current. Work only on: **[specific task]**. Explain the observed gap and evidence before changing code. Preserve the persisted contract and fail-closed semantics. Add tests and benchmark cases, update schemas/docs/changelog as needed, and run all relevant validation available. Never invent results or claim unsupported capabilities. Prefer a focused branch/PR. Report changed files, exact validation/results, candidate SHA/PR, limitations and next task. If full completion is not safe in this turn, deliver the highest-value safe increment.

## 10. Immediate priority

1. Confirm actual HEAD and clean build/test/CI status.
2. Trace one acceptance criterion through plan → contract → current-candidate evidence → evaluator → receipt → user-facing output.
3. Add regressions for false acceptance/rejection when evidence is missing, stale, mismatched or insufficient.
4. Implement the smallest high-value fix.
5. Execute benchmark cases and retain candidate-specific evidence.
6. Simplify the first GitHub Action/CLI experience.
7. Validate the common adapter with selected agents.
8. Only then expand into signing/provenance, more languages, cross-repo checks, project memory and enterprise features.

**Guiding rule:** Make Vericore more trustworthy before making it broader. Popularity must follow a reproducible developer benefit, not a longer feature list or promises about 2030.
