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

## Roadmap

### Phase 0 — Public foundation
- concise discovery-first README
- clear product explanation
- task-oriented Getting Started
- coherent documentation hub
- implementation status separated from future work
- technical documents kept as authoritative references

### Phase 1 — Understandability
- finish the unified public command experience
- consistent machine-readable product output
- short reproducible 60-second product journey
- strong verification receipt/output
- incremental verification where it improves speed without weakening determinism

Phase 1 verification contract:
- Release Audit calls the product-journey workflow, so release readiness cannot pass without exercising the public journey.
- `init`, `inspect`, and `review` expose the same schema-versioned product result envelope when `--json` is requested. `inspect` and `review` preserve their detailed JSON artifacts and include those payloads under `details`; `init` returns its config/workspace artifact references.
- Verification reuses the content-addressed parser cache, but still recomputes repository changes, graph/impact/architecture signals, contract binding, and declared verification commands. A cache hit never reuses an earlier PASS decision.

### Phase 2 — Agent-native workflow
- native integrations for major coding-agent environments
- agent-facing skills and commands
- polished MCP onboarding
- reliable prepare → agent change → verify workflows

### Phase 3 — Verification moat
- stronger change contracts
- protected paths and expected operations
- explainable deterministic verification results
- verification receipts suitable for CI/PR artifacts
- architecture and dependency expectations tied to verification

### Phase 4 — Evidence-grounded intelligence
- project memory built from verified repository evidence
- feature-gap analysis
- broader cross-repository intelligence where evidence boundaries remain explicit
- repeatable benchmarks and public evaluation examples

### Later

GitHub App, dashboards, team/org governance, hosted services, and commercial packaging are deliberately later. The immediate goal is an excellent open-source verification layer for AI-assisted development.

## Product principles

- Evidence before AI.
- Deterministic analysis is authoritative.
- AI interprets; it does not invent repository facts.
- Verification is repository-bound.
- Fail closed when the verification boundary cannot be trusted.
- Preserve CLI compatibility while the public journey improves.
- Keep MCP machine-readable and safe for local agent use.
- Do not expand into hosted control-plane features before the developer/agent workflow is genuinely useful.
