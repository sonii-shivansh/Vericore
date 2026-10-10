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
4. ask — ask grounded repository questions
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
init → scan → inspect → ask → explain → review → plan
     → prepare → agent works → verify → report
~~~

Keep specialized commands. The unified journey is the product surface, not a reason to delete useful lower-level commands.

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
