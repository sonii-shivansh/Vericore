# Phase 0 — Foundation Freeze & Truth Checklist

> Working checklist aligned to the Vericore master roadmap. This is an audit snapshot, not a release certificate.
>
> Audit baseline: main at b62e1177c899822901f6c177d291e319082830bd (2026-10-10). The branch phase0/complete-foundation is a completion candidate and is not merged yet. Its new schema/golden audit requires its own CI result and post-merge Release Audit.

## Exit objective

Make the existing Vericore foundation trustworthy before major feature work. A row is complete only when its acceptance evidence is recorded; a workflow existing in the repository is not proof that every relevant behavior passes.

## Checklist

| Foundation area | Current assessment from inspected code/docs | Required evidence to close |
|---|---|---|
| CI stability | **Main baseline passed; candidate not yet validated.** Release Audit and Push on main passed for b62e1177c899822901f6c177d291e319082830bd. The Phase 0 foundation workflow is new and has not yet run on its final candidate. | Phase 0 workflow and full Release Audit green on the exact candidate; failures resolved; no pending required checks. |
| PR certification wiring | **Mismatch to resolve.** REPOSITORY_GOVERNANCE.md describes Release Audit as the authoritative PR certification, but release-audit.yml declares workflow_dispatch, workflow_call, and push to main; it has no pull_request trigger. | Decide whether full Release Audit should run on PRs despite the long live-repository matrix, or whether targeted PR gates plus post-merge/exact-candidate Release Audit are the intended policy. Align workflow triggers, repository rulesets, and governance wording before closing Phase 0. |
| Release certification | **Baseline documented.** The published v0.8.2 record is immutable; RELEASE_READINESS_1.0.0.md states 1.0 is not yet certified. | Keep release artifact/tag identity exact; do not publish a new version until its candidate satisfies the certification gate. |
| Documentation parity | **Roadmap alignment merged.** Product Direction and Implementation Status now use the Phase 0–9 master sequence, and the docs parity check passed on main. | Keep public behavior claims accurate and pass documentation parity on the completion candidate. |
| Stable JSON schemas | **Core schemas drafted; candidate validation pending.** JSON Schemas now cover analysis snapshots, grounded evidence, context snapshots/diffs, plans, contracts, preparation, verification/results/receipts, evidence graph, architecture, impact, PR intelligence, engineering risks, inspection, and release readiness. | Run the schema validator against schema syntax and representative serialized artifacts; add migrations/compatibility tests and document remaining command-specific outputs. |
| Stable artifact schemas | **Partial.** Snapshot, grounded evidence, contracts, receipts, and reports have different versioning maturity. | Define artifact catalog and validation/compatibility tests; keep old fixtures and published-release records immutable where applicable. |
| Deterministic output | **Stable scanner/hotspot ordering added; candidate test pending.** Scanner output sorts by repository-relative path; hotspot ranking breaks equal-score ties by path; a repeated-snapshot golden check is included in the new audit. | Candidate CI must confirm repeatable normalized snapshot/graph output; add additional cross-platform/shuffled-input checks as needed. |
| Golden test repositories | **Starter Java fixture added; execution pending.** testdata/phase0/golden-java has a manifest and expected source/graph counts, with a repeatability check in the audit runner. | Candidate CI must validate expected graph structure and repeatability; expand to Kotlin and additional failure modes after this fixture is stable. |
| Benchmark fixtures | **v0.1 correctness benchmark defined; baseline pending.** benchmark/agent-change-v0.1/cases.json defines contract-level cases and the audit emits candidate-SHA-bound per-case measurements. | Complete the first green run on the candidate and retain its JSON artifact; make no comparative performance claims. |
| Supported/unsupported boundaries | **Consolidated in docs/SUPPORT_MATRIX.md.** It explicitly documents Java/Kotlin scope, trusted local MCP, unauthenticated local REST, build-command limits, and unimplemented agent/enterprise integrations. | Verify every support statement has matching tests or clearly marked limitations; keep matrix in parity/release review. |
| Public verification benchmark | **Not established in the inspected tree.** The master roadmap calls for a baseline in Phase 0 and the expanded Agent Change Benchmark in Phase 8. | Publish benchmark design, initial fixtures, baseline results, methodology, failure categories, and explicit limitations; defer broad marketing claims until measured. |

## Confirmed defects and high-priority regression candidates

These findings come from the inspected source at the baseline above. Fixes remain unmerged until their review and CI gates complete.

### Verification command execution

The initial validator checked several shell metacharacters but missed the single ampersand and embedded line breaks. A source-equivalent reproduction showed that a second shell command could execute after the allowed build command. The hardening change must reject unsafe separators, preserve valid nested build directories inside the repository, and prove that rejected commands execute nothing. It must also retain cross-platform regression coverage.

### Preparation/evidence freshness

AnalysisSnapshot carries a repository source-state digest, but preparation's initial cache-reuse path compared Git HEAD alone. Uncommitted edits can change source content without changing HEAD. Cached grounded evidence also lacked explicit commit/digest binding. Reuse should be allowed only when repository identity, commit, current source digest, and evidence-to-snapshot binding all match; legacy evidence can be decoded for compatibility but should not be trusted as fresh.

### Parser diagnostics

CodeParallelParser counts parseWarning values, including cases where a parser returns a ParsedFile with warnings. prepare and repo-qa computed parse failures as input file count minus returned parsed file count, which can undercount warning-bearing results. Use one documented definition and test identical warning semantics across analyze, prepare, repo-qa, and verify.

### Parse-cache compatibility

The parse-cache key is based on canonical path and source contents, not a parser/cache schema version. Parser behavior can therefore change while an old cached ParsedFile remains addressable for unchanged input. Add an explicit cache schema version to the key and a regression test proving prior-format entries are ignored.

## Recommended closure sequence

1. Finish and review the command-execution hardening and source-freshness pull requests.
2. Fix parser-diagnostic counting and cache invalidation, each with focused regression coverage.
3. Add stable sorting/tie-breakers and deterministic-output fixtures.
4. Finish the schema/artifact inventory and compatibility tests.
5. Align all roadmap/status documents and publish the supported/unsupported matrix.
6. Establish versioned golden repositories and the first public benchmark baseline.
7. Run release certification on the exact candidate commit and keep the result linked to that commit.

## Definition of done

Phase 0 is not complete merely because the checklist document exists or because one CI run passes. Each area above needs reproducible evidence, open blockers must be explicit, and the final release-readiness decision must be made against the exact candidate SHA.
