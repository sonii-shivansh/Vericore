# Phase 0 — Foundation Freeze & Truth Checklist

> Working checklist aligned to the Vericore master roadmap. This is an audit snapshot, not a release certificate.
>
> Audit baseline: main at 35a46ac1a1b1cdca683b3a7fae35fb40184eaf15 (2026-10-10). Phase 0 hardening pull requests are separate, unmerged work and do not change that baseline.

## Exit objective

Make the existing Vericore foundation trustworthy before major feature work. A row is complete only when its acceptance evidence is recorded; a workflow existing in the repository is not proof that every relevant behavior passes.

## Checklist

| Foundation area | Current assessment from inspected code/docs | Required evidence to close |
|---|---|---|
| CI stability | **In progress.** Recent Release Audit on the inspected main commit passed, and PR workflows exercise several focused regression/E2E paths. Phase 0 patch checks must be evaluated on their exact final commits. | Full required workflow matrix green on the final candidate; failures explained and resolved; no pending required checks. |
| Release certification | **Baseline documented.** The published v0.8.2 record is immutable; RELEASE_READINESS_1.0.0.md states 1.0 is not yet certified. | Keep release artifact/tag identity exact; do not publish a new version until its candidate satisfies the certification gate. |
| Documentation parity | **Partial.** CLI-help parity exists in CI, but PRODUCT_DIRECTION.md did not match the 0–9 phases in the supplied master roadmap. | Keep the master phase names/sequencing aligned across roadmap and status docs; verify product claims against code and tests. |
| Stable JSON schemas | **Partial.** Several artifacts carry schema versions, but a complete central inventory and compatibility policy were not found in the inspected tree. | Inventory each persisted/public JSON type, version, required/optional fields, consumers, compatibility rules, and migration tests. |
| Stable artifact schemas | **Partial.** Snapshot, grounded evidence, contracts, receipts, and reports have different versioning maturity. | Define artifact catalog and validation/compatibility tests; keep old fixtures and published-release records immutable where applicable. |
| Deterministic output | **Partial.** Snapshot builder sorts files/hotspots, while repository scanning returns filesystem traversal order and graph hotspot ranking lacks an explicit tie-breaker. | Stable path ordering, explicit tie-breakers, shuffled-input fixtures, and repeatability tests on supported platforms. |
| Golden test repositories | **Not yet established as a named catalog.** CI has live-repository and product-journey validation, but a dedicated, versioned golden-fixture catalog was not found in the inspected tree. | Define a small, licensed/reproducible fixture set with expected snapshots, findings, evidence, plans, and verification outcomes. |
| Benchmark fixtures | **Not yet established as a first-class protocol.** | Define benchmark task definitions, controlled repository states, expected outcomes, scoring, rerun instructions, and versioning before publishing claims. |
| Supported/unsupported boundaries | **Partially documented** across implementation status, architecture, API/MCP, and release docs. | Publish a coherent support matrix with platforms, languages, interfaces, security/trust assumptions, limitations, and tests supporting each claim. |
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
