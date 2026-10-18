# Vericore Agent Change Verification Benchmark — v0.2

## Purpose

The benchmark is a public, reproducible set of controlled repository states for measuring behaviors Vericore intends to verify. It begins as a correctness/contract benchmark, not a claim that Vericore outperforms another agent or product.

The current task catalog lives in [benchmark/agent-change-v0.2/cases.json](../benchmark/agent-change-v0.2/cases.json); the original v0.1 catalog remains unchanged for historical baseline interpretation. The starter golden repository is testdata/phase0/golden-java. A candidate run uses scripts/audit/phase0-foundation-audit.py and records a JSON result artifact.

## Case families

- Deterministic analysis: same fixture and commit produce the same normalized source/graph output.
- Evidence freshness: source edits without a commit invalidate cached analysis/evidence binding.
- Expected mutation: a change within prepared path scope with successful declared commands should be eligible for PASS, or REVIEW_REQUIRED where existing deterministic review rules require it.
- No mutation: verification must not claim success when the planned source change did not occur.
- Unexpected path: an out-of-scope source edit must fail.
- Stale contract: changing HEAD after preparation must fail.
- Command injection: shell separators/newlines must be rejected before command execution.
- Artifact compatibility: generated core artifacts validate against published schemas; legacy evidence is readable but not eligible for fresh reuse.

## Deterministic evaluator regression vectors

`benchmark/agent-change-v0.2/evaluator-cases.json` contains candidate-independent vectors for numeric/boolean comparisons, failed thresholds, unbound or missing metrics, free-form criteria, bound-command success/failure/timeout/missing results, and contract-only evidence freshness. `ChangeSpecificationEvaluatorTest` executes each vector and writes `build/reports/vericore-evaluator-benchmark.json` before asserting expected outcomes. Freshness is explicit for vectors that require current-candidate evidence; omitted freshness fields deliberately omit the evaluator argument and exercise its fail-closed default rather than assuming evidence is current. The report records candidate SHA (when supplied by CI), fixture SHA-256, environment, generated timestamp, per-vector expected/observed statuses, pass/fail, elapsed time, and aggregate counts. The Phase 0 audit validates the report schema, summary consistency, and candidate-SHA binding; CI uploads it alongside the CLI audit artifact. Evaluator vectors remain separate from CLI audit case counts. These vectors guard against false acceptance and false rejection, but do not measure semantic correctness for arbitrary natural-language requirements.

## Metrics

Every run records candidate SHA, Vericore version, operating system, fixture/task versions, per-case expected and observed outcomes, pass/fail, and elapsed time. Correctness is reported as counts of expected outcomes matched/mismatched; latency is recorded independently and must not be compared across unlike runner profiles.

The run artifact is evidence of the cases actually executed on that candidate. Do not turn unrun tasks into passes or advertise a benchmark score based solely on a task catalog.

## Initial baseline policy

- The original v0.1 catalog is retained unchanged. The v0.2 catalog adds deterministic evaluator regression vectors for false acceptance and false rejection.
- CI produces a machine-readable result for each candidate tested by the Phase 0 Foundation workflow.
- The recorded Phase 0 baseline is documented in [Phase 0 Benchmark Baseline](PHASE0_BENCHMARK_BASELINE.md), including the closeout SHA, workflow run, result count, elapsed time, artifact digest, and limits. Treat that record as the initial baseline; later runs must append new candidate/SHA-specific evidence rather than overwriting it.
- Expand to more repositories/languages only after current cases are stable. Broader coverage for cross-agent and cross-repository omissions belongs to the later ecosystem phase in the master roadmap.

## Limitations

The starter fixture is small and Java-focused. It validates defined contract behaviors; it does not represent the full variety of real-world Java/Kotlin repositories, arbitrary shell commands, every agent, every build system, or all semantic correctness failures. Public conclusions must state these limits.
