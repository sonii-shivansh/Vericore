# Phase 1 — The 60-Second Vericore: Acceptance Checklist

This checklist defines the exit gate for Phase 1. Keep every item evidence-backed; do not mark the phase complete based only on merged feature PRs or a green unit-test suite.

## Product journey

- [ ] **First-run setup:** a clean Git fixture can run `vericore init` and `vericore scan` successfully.
- [ ] **Understand the repository:** `inspect` consumes the persisted analysis snapshot, emits a useful human summary, and writes valid `output/inspection.json` when `--json` is supplied.
- [ ] **Grounded questions:** `repo-qa` returns parseable JSON, cites repository evidence, handles insufficient evidence explicitly, and can persist reusable grounded evidence.
- [ ] **Explain findings:** grounded explanation is available through `repo-qa` and review's evidence-backed explanation path; no undocumented standalone `explain` command is implied.
- [ ] **Review changes:** `review` runs on a fixture and its optional JSON artifact parses without changing deterministic findings.
- [ ] **Plan and prepare:** a grounded plan can be produced, and `prepare` persists the engineering plan and original Agent Change Contract.
- [ ] **Agent change and verification:** after a planned fixture edit, `verify` checks the persisted contract and produces a machine-readable result plus verification receipt.
- [ ] **Report:** `report` creates a non-empty HTML engineering report from the analysis artifacts.
- [ ] **Discoverability:** global help and command help expose the intended public commands, and Getting Started documents a runnable path without implying Vericore edits source autonomously.

## Compatibility and safety

- [ ] JSON schemas/fields and serialization remain compatible.
- [ ] Existing artifact paths and stdout-vs-file behavior remain unchanged unless a separately reviewed contract change explicitly says otherwise.
- [ ] Failure/exit behavior remains non-success on invalid or unsafe verification states.
- [ ] MCP stdio remains protocol-only; human output does not leak into its stdout.
- [ ] Deterministic analysis remains authoritative; AI remains optional and cannot turn missing evidence into a verification pass.
- [ ] The published CLI output contract inventory names the exact source commit audited and does not imply an audit that was not performed.

## Required evidence to close the phase

- [ ] Full test/build/install task passes on the exact candidate commit.
- [ ] The Phase 1 product-journey workflow passes on the exact candidate commit and its evidence artifacts are inspected.
- [ ] `scripts/audit/cli-audit.sh` passes.
- [ ] Relevant MCP/contract audit passes, or a documented reason explains why the Phase 1 change did not affect that surface.
- [ ] Documentation parity checks pass.
- [ ] All checklist items above are either checked with linked evidence or explicitly deferred with a rationale accepted by the phase owner.

**Evidence and closeout rule:** The `Phase 1 Product Journey E2E` workflow must run on the exact candidate commit, including after a push to `main`. Its `phase1-product-journey-evidence` artifact records the candidate SHA and workflow-run URL. The artifact is created before the CLI audit step, so it is not by itself proof that the entire job passed; the workflow run's final conclusion is authoritative.

The merged candidate is accepted only when all of the following are true on the same `main` SHA:

1. `Phase 1 Product Journey E2E` succeeds and its evidence artifact is inspected.
2. `Release Audit` succeeds, including build/tests, CLI audit, MCP audit, contract regressions, documentation parity, and platform checks.
3. The checklist items above are supported by those artifacts or have an explicit, accepted deferral.

## Candidate closeout

- Candidate commit and workflow-run URL: recorded in `phase1-closeout.md` inside the product-journey evidence artifact.
- Product journey and CLI audit: evidenced by the corresponding successful `Phase 1 Product Journey E2E` run.
- MCP/contract audit and documentation parity: evidenced by the successful `Release Audit` run on the same SHA.
- Decision: **OPEN until the merged-main product-journey run and release audit both pass on the same SHA.**
