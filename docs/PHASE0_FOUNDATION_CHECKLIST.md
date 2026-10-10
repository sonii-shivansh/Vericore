# Phase 0 — Foundation Freeze & Truth Checklist

> Working checklist aligned to the Vericore master roadmap. This is an audit snapshot, not a release certificate.
>
> Audit baseline: main at abd55294d94f7cdc47e755d88138885e9f33d010 (2026-10-10). The Phase 0 foundation audit and full post-merge Release Audit passed on this exact commit. This checklist records the evidence-backed closeout state; it is not a release certificate.

## Exit objective

Make the existing Vericore foundation trustworthy before major feature work. A row is complete only when its acceptance evidence is recorded; a workflow existing in the repository is not proof that every relevant behavior passes.

## Checklist

| Foundation area | Current assessment from inspected code/docs | Required evidence to close |
|---|---|---|
| CI stability | **Complete for Phase 0 closeout.** Phase 0 Foundation passed on the pre-closeout merge, and Release Audit, Push on main, Documentation Parity, and Pages deployment all passed on closeout commit `abd55294d94f7cdc47e755d88138885e9f33d010`. | [Release Audit run](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894); [Push on main](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925884).
| PR certification wiring | **Policy decision recorded: targeted PR gates, full exact-candidate audit after merge.** Release Audit intentionally runs on workflow dispatch, reusable workflow call, and pushes to `main`; it is not represented as a required PR status check. Targeted CI/regression checks and DCO are the PR merge gates; full Release Audit is the post-merge certification gate. This avoids duplicating the expensive external-repository matrix on every PR. | Governance wording and this checklist now agree with `.github/workflows/release-audit.yml`. External GitHub ruleset enforcement is not asserted by repository files and must be verified in Settings separately.
| Release certification | **Baseline documented.** The published v0.8.2 record is immutable; RELEASE_READINESS_1.0.0.md states 1.0 is not yet certified. | Keep release artifact/tag identity exact; do not publish a new version until its candidate satisfies the certification gate. |
| Documentation parity | **Roadmap alignment merged.** Product Direction and Implementation Status now use the Phase 0–9 master sequence, and the docs parity check passed on main. | Keep public behavior claims accurate and pass documentation parity on the completion candidate. |
| Stable JSON schemas | **Core schema syntax and representative artifact validation passed.** The Phase 0 audit validates schema documents and representative generated artifacts. The verification workflow now asserts grounded evidence schema version `1.1`; the legacy `1.0` compatibility path is tested and stale legacy evidence is regenerated rather than reused. | [Phase 0 Release Audit](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894); `scripts/audit/phase0-foundation-audit.py`.
| Stable artifact schemas | **Catalog and compatibility policy recorded.** See [`ARTIFACT_SCHEMA_CATALOG.md`](ARTIFACT_SCHEMA_CATALOG.md). Core versioned artifacts and legacy grounded-evidence regeneration are covered by the Phase 0 audit; command-specific and legacy formats remain explicitly identified rather than implied to be uniformly versioned. | Catalog plus [Phase 0 audit evidence](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894).
| Deterministic output | **Phase 0 repeatability audit passed.** The golden Java fixture is analyzed repeatedly and normalized output is compared; scanner/hotspot ordering is stable. Broader cross-platform/shuffled-input expansion remains future hardening, not a claim made by this baseline. | [Phase 0 audit evidence](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894).
| Golden test repositories | **Java golden fixture passed.** `testdata/phase0/golden-java` is manifest-backed; expected source/graph structure and repeated normalized analysis passed. Kotlin-specific golden fixtures are a documented future extension. | [Phase 0 audit evidence](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894).
| Benchmark fixtures | **Initial correctness baseline recorded.** The Phase 0 audit passed 76 cases in 30,475.45 ms on GitHub-hosted `ubuntu-24.04`; candidate SHA and runner context are recorded in [`PHASE0_BENCHMARK_BASELINE.md`](PHASE0_BENCHMARK_BASELINE.md), with the full JSON retained as a GitHub Actions artifact. This is not a comparative performance result. | [Baseline result artifact](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894/artifacts/11667097810); [workflow run](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894).
| Supported/unsupported boundaries | **Consolidated in docs/SUPPORT_MATRIX.md.** It explicitly documents Java/Kotlin scope, trusted local MCP, unauthenticated local REST, build-command limits, and unimplemented agent/enterprise integrations. | Verify every support statement has matching tests or clearly marked limitations; keep matrix in parity/release review. |
| Public verification benchmark | **Phase 0 correctness baseline published in-repository.** Design, versioned cases, measured initial result, exact candidate SHA, methodology, and limitations are documented in [`PHASE0_BENCHMARK_BASELINE.md`](PHASE0_BENCHMARK_BASELINE.md). The full machine-readable run is retained as a workflow artifact for its retention period; the summary is version-controlled. Phase 8's expanded benchmark remains out of scope. | [`PHASE0_BENCHMARK_BASELINE.md`](PHASE0_BENCHMARK_BASELINE.md) and linked CI artifact.

## Confirmed defects and high-priority regression candidates

These were the initial findings that drove Phase 0 work. The relevant hardening is now merged and covered by unit/regression and Phase 0 audit checks on the recorded closeout SHA; future regressions remain subject to those tests.

### Verification command execution

The initial validator checked several shell metacharacters but missed the single ampersand and embedded line breaks. A source-equivalent reproduction showed that a second shell command could execute after the allowed build command. The hardening change must reject unsafe separators, preserve valid nested build directories inside the repository, and prove that rejected commands execute nothing. It must also retain cross-platform regression coverage.

### Preparation/evidence freshness

AnalysisSnapshot carries a repository source-state digest, but preparation's initial cache-reuse path compared Git HEAD alone. Uncommitted edits can change source content without changing HEAD. Cached grounded evidence also lacked explicit commit/digest binding. Reuse should be allowed only when repository identity, commit, current source digest, and evidence-to-snapshot binding all match; legacy evidence can be decoded for compatibility but should not be trusted as fresh.

### Parser diagnostics

CodeParallelParser counts parseWarning values, including cases where a parser returns a ParsedFile with warnings. prepare and repo-qa computed parse failures as input file count minus returned parsed file count, which can undercount warning-bearing results. Use one documented definition and test identical warning semantics across analyze, prepare, repo-qa, and verify.

### Parse-cache compatibility

The parse-cache key is based on canonical path and source contents, not a parser/cache schema version. Parser behavior can therefore change while an old cached ParsedFile remains addressable for unchanged input. Add an explicit cache schema version to the key and a regression test proving prior-format entries are ignored.

## Closeout decision

**Phase 0 foundation exit criteria are met for the scope defined by this checklist** on closeout commit `abd55294d94f7cdc47e755d88138885e9f33d010`: the exact commit's full Release Audit, Push on main, Documentation Parity, and Pages deployment are green; the schema/golden correctness audit reports 76 passing cases; the first benchmark baseline is recorded; the artifact catalog exists; and PR-vs-post-merge certification policy is documented.

Explicit non-goals are not blockers to Phase 0 closeout: Vericore v1.0.0 release certification, Marketplace publication, and Phase 8's expanded comparative benchmark remain separate future gates. Repository ruleset UI state is not verifiable from this file and is not claimed as confirmed.

## Definition of done

Phase 0 completion is evidence-based and scoped to the foundation exit objective. A green CI run alone is insufficient; this closeout ties each criterion to reproducible test code, retained workflow evidence, versioned benchmark metadata, or an explicit policy decision. Do not infer that Phase 0 closeout certifies v1.0.0 or proves unsupported capabilities.
