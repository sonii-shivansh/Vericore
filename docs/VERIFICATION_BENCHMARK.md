# Vericore Agent Change Verification Benchmark — v0.1

## Purpose

The benchmark is a public, reproducible set of controlled repository states for measuring behaviors Vericore intends to verify. It begins as a correctness/contract benchmark, not a claim that Vericore outperforms another agent or product.

Task definitions live in benchmark/agent-change-v0.1/cases.json. The starter golden repository is testdata/phase0/golden-java. A candidate run uses scripts/audit/phase0-foundation-audit.py and records a JSON result artifact.

## v0.1 case families

- Deterministic analysis: same fixture and commit produce the same normalized source/graph output.
- Evidence freshness: source edits without a commit invalidate cached analysis/evidence binding.
- Expected mutation: a change within prepared path scope with successful declared commands should be eligible for PASS, or REVIEW_REQUIRED where existing deterministic review rules require it.
- No mutation: verification must not claim success when the planned source change did not occur.
- Unexpected path: an out-of-scope source edit must fail.
- Stale contract: changing HEAD after preparation must fail.
- Command injection: shell separators/newlines must be rejected before command execution.
- Artifact compatibility: generated core artifacts validate against published schemas; legacy evidence is readable but not eligible for fresh reuse.

## Metrics

Every run records candidate SHA, Vericore version, operating system, fixture/task versions, per-case expected and observed outcomes, pass/fail, and elapsed time. Correctness is reported as counts of expected outcomes matched/mismatched; latency is recorded independently and must not be compared across unlike runner profiles.

The run artifact is evidence of the cases actually executed on that candidate. Do not turn unrun tasks into passes or advertise a benchmark score based solely on a task catalog.

## Initial baseline policy

- The v0.1 fixture manifest and expected behaviors are version-controlled.
- CI produces a machine-readable result for each candidate tested by the Phase 0 Foundation workflow.
- The recorded Phase 0 baseline is documented in [Phase 0 Benchmark Baseline](PHASE0_BENCHMARK_BASELINE.md), including the closeout SHA, workflow run, result count, elapsed time, artifact digest, and limits. Treat that record as the initial baseline; later runs must append new candidate/SHA-specific evidence rather than overwriting it.
- Expand to more repositories/languages only after current cases are stable. Broader coverage for cross-agent and cross-repository omissions belongs to the later ecosystem phase in the master roadmap.

## Limitations

The starter fixture is small and Java-focused. It validates defined contract behaviors; it does not represent the full variety of real-world Java/Kotlin repositories, arbitrary shell commands, every agent, every build system, or all semantic correctness failures. Public conclusions must state these limits.
