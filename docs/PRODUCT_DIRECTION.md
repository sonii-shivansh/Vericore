# Vericore Product Direction

> **AI writes the code. Vericore verifies the change.**

Vericore is being evolved into an engineering intelligence and verification layer for AI-assisted software development.

## Product model

repository facts → evidence → deterministic analysis → plan/contract → AI agent change → verification → explainable result

The repository remains the source of truth. AI is an optional interpretation layer, not the authority for repository facts or verification decisions.

## Public journey

The public experience should stay small and understandable:

1. init — initialize the project
2. scan — understand the repository
3. inspect — summarize what matters
4. repo-qa — ask grounded repository questions; ask — optional AI interpretation
5. review — review a change
6. plan — produce a deterministic engineering plan
7. prepare — establish the change boundary
8. AI agent changes the repository
9. verify — verify the original boundary
10. report — produce the engineering record
11. doctor — diagnose installation/configuration

Specialized commands remain available underneath this product journey.

## Output direction

Human-readable output is the default. Machine-readable JSON remains available for automation.

Product-level commands should make the same core information easy to find:
- repository/state
- status
- key findings
- evidence/artifacts
- optional AI interpretation
- next step

## Master roadmap

The strategic roadmap follows **Phase 0 through Phase 9**. Phase names and sequencing below are the project roadmap; present-day implementation status and known limitations remain documented in [Implementation Status](IMPLEMENTATION_STATUS.md).

### Phase 0 — Foundation Freeze & Truth

Make the existing foundation trustworthy before major feature work.

- Stabilize current CI and release certification.
- Align documentation with actual behavior.
- Establish stable JSON and artifact schema contracts.
- Make generated output deterministic.
- Maintain golden test repositories and benchmark fixtures.
- Define supported and unsupported boundaries.
- Establish the baseline for a public verification benchmark.

No large new feature should displace these foundation tasks before their acceptance criteria are met.

### Phase 1 — The 60-Second Vericore

Make Vericore understandable and usable through one coherent public workflow:

~~~text
init → scan → inspect → ask/repo-qa → review → plan
     → prepare → agent works → verify → report
~~~

The journey's explanation step is delivered through grounded `repo-qa` answers and the evidence-backed explanations available in `review`; Phase 1 does not define a separate `explain` CLI command. Do not document or test a standalone `explain` command unless a separate product decision introduces one. Keep specialized commands. The unified journey is the product surface, not a reason to delete useful lower-level commands.

Phase 1 completion is gated by the measurable checks in [Phase 1 Acceptance Checklist](PHASE1_ACCEPTANCE_CHECKLIST.md). A passing individual PR or unit-test suite is not, by itself, phase-completion evidence.

### Phase 2 — Universal Agent Layer

Create one common **Vericore Agent Adapter** for Claude Code, Cursor, Codex, Gemini/Antigravity, GitHub Copilot, and future agents. Standardize agent/session identity, repository, contract, event metadata, changed files, verification requests, and results. Keep MCP as a key integration boundary rather than building independent implementations for every agent.

### Phase 3 — Change Contract 2.0

Evolve the current Agent Change Contract to express the complete change boundary: intent, repository and base commit, agent identity, allowed and protected paths, expected components and operations, dependency/architecture/security constraints, required tests and evidence, verification commands, risk budget, approvals, and contract fingerprint.

### Phase 4 — Independent Verification Engine

Verify requested work, unexpected or omitted changes, dependency and architecture drift, protected paths, tests and their relevance, configuration/dependency changes, and the exact commit under verification. Preserve explicit outcomes such as PASS, PASS_WITH_WARNINGS, REVIEW_REQUIRED, FAIL, INCONCLUSIVE, STALE, and NOT_EVALUATED. Missing or stale evidence must never be turned into a pass.

### Phase 5 — Verification Receipt & Provenance

Produce a stable machine-readable receipt for every verified change. Include repository/base/candidate identities, contract fingerprint, agent/session, changed and unexpected paths, impact, architecture/security/dependency results, test and command results, evidence IDs, risk, verdict, timestamp, environment, and freshness. Prefer compatibility with established provenance/attestation formats where appropriate. Capture observable evidence, not private model reasoning.

### Phase 6 — Multi-Agent & Cross-Repository Verification

Model relationships among agents, contracts, changes, commits, and affected components. Detect overlapping changes, conflicts, dependency collisions, contract invalidation, stale plans, and missing coordinated changes across repositories.

### Phase 7 — Explain + Verified Project Memory

Provide evidence-first explanations for findings and their impact, architecture context, relevant historical decisions, and recommendations. Build structured engineering memory for rules, decisions, exceptions, risks, architecture, verification history, dependencies, ownership, and accepted trade-offs, with evidence and freshness information.

### Phase 8 — Distribution, Ecosystem & Benchmark

Expand distribution and integration through GitHub/GitLab, IDEs, agent marketplaces, MCP, and CI/CD. Publish the **Vericore Agent Change Benchmark** with a transparent methodology and measurable results for scope drift, omitted requirements, unexpected files, architecture/dependency/security regressions, false completion, stale verification, agent conflicts, and cross-repository omissions.

### Phase 9 — Enterprise Change Assurance

Only after the core verification engine is strong and customer demand is validated, consider organization policies, RBAC, SSO, approvals, exception management, audit history, organization-wide risk, cross-repository governance, policy-as-code, retention, compliance exports, private deployment, and a central verification registry.

Commercialization is a hypothesis to validate—not a reason to build a hosted control plane ahead of the core product.

## Execution and phase gates

The phase order above is the strategic roadmap. The following are execution rules recommended for delivery; they are not claims that the phases have already passed:

- Break work into small, reviewable changes with regression tests for the behavior being changed.
- Define measurable acceptance checks before declaring a phase complete.
- Use CI and reproducible fixtures as evidence; distinguish passed checks from pending or unrun checks.
- Keep unsupported behavior, schema compatibility, and known limitations explicit.
- Do not publish a release based only on feature completion. Certify the exact candidate commit and artifact set.
- Do not begin large work in a later phase while a release-blocking foundation defect remains unresolved.

## Product principles

- Evidence before AI.
- Deterministic analysis is authoritative.
- AI interprets; it does not invent repository facts.
- Verification is repository-bound.
- Fail closed when the verification boundary cannot be trusted.
- Preserve CLI compatibility while the public journey improves.
- Keep MCP machine-readable and safe for local agent use.
- Do not expand into hosted control-plane features before the developer/agent workflow is genuinely useful.


## Strategic product decision — 2026-10-11

This section records the product thesis and execution constraints agreed for Vericore. Keep it as durable project context when future work, reviews, or contributors revisit priorities.

### Product thesis

Vericore should become an **agent-neutral, local-first trust layer for AI-made software changes**, not another general-purpose coding agent and not merely another AI code reviewer.

The proposed long-term product concept is the **Vericore Change Passport**: a portable, machine-verifiable record connecting the requested intent, the original change contract, the exact repository candidate evaluated, the checks that actually ran, the evidence for each claim, and a verdict that states what remains unverified.

The product promise is evidence integrity and inspectable verification—not a guarantee that arbitrary software is correct.

### Non-negotiable engineering principles

- Deterministic repository facts remain authoritative; optional AI may interpret evidence but must not invent facts or turn missing evidence into a pass.
- The persisted pre-change contract remains the verification boundary. Verification must not silently replace it with a contract derived from the mutated tree.
- Verification claims must be bound to the exact repository-input state they evaluated. Relevant mutations during analysis or test execution must invalidate a PASS.
- Distinguish PASS, review-required, failure, stale evidence, inconclusive results, and checks that were not evaluated as the result model evolves.
- Keep verification receipts machine-readable, content-addressable where appropriate, and compatible with established provenance/attestation formats instead of inventing a disconnected ecosystem.
- Treat hashes and signatures as integrity/provenance mechanisms, not proof of semantic correctness.
- Publish benchmark methodology and measured results, including false acceptance, false rejection, stale-evidence detection, cost and limitations. Do not claim superiority before controlled comparisons.
- Prioritize developer adoption and recurring use over command count, feature count, vanity metrics or a premature hosted enterprise control plane.

### Execution order

1. Strengthen exact-state binding and fail-closed behavior in the existing prepare → change → verify workflow.
2. Evolve the Agent Change Contract into a structured, testable change specification with explicit evidence obligations.
3. Improve receipt provenance and provide simple human-readable explanations alongside stable machine-readable artifacts.
4. Expand the public benchmark to realistic repositories, deliberate failure cases and more than one agent workflow.
5. Pursue broader languages, agent adapters, multi-agent/cross-repository coordination, signed attestations and enterprise controls only as correctness and real user demand justify them.

### 2030 design horizon

Design the core so it can later support multi-agent change conflicts, cross-repository change coordination, evidence freshness over time, and portable engineering assurance. These are strategic hypotheses, not assertions that every future problem is known or that any feature is already implemented.

### Scope of the first implementation step

The first implementation step introduces a deterministic candidate repository-input digest and checks it before analysis, immediately before verification commands, and after those commands. The digest excludes version-control metadata and conventional generated/build-output directories so Vericore's own outputs and build caches do not invalidate the result. In Git repositories, untracked diagnostic logs/temp files outside typical source/test/fixture/input trees are also excluded, while tracked files and untracked files under those input trees remain included. It does not attest to the complete execution environment, external services, or external symlink targets; those require separate provenance or isolation controls.
