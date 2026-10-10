# Phase 0 Verification Benchmark — Initial Baseline

**Status:** Recorded correctness/contract baseline; not a performance leaderboard.

## Candidate and environment

- Benchmark: `vericore-agent-change` version `0.1.0`
- Candidate SHA: `abd55294d94f7cdc47e755d88138885e9f33d010`
- Workflow: [Release Audit on the candidate](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894)
- Runner: GitHub-hosted `ubuntu-24.04`
- Measured audit elapsed time: **30,475.45 ms**
- Cases recorded as passing: **76**
- Overall result: **PASS**
- Machine-readable evidence: [Phase 0 foundation results artifact](https://github.com/sonii-shivansh/Vericore/actions/runs/38044925894/artifacts/11667097810)
- Artifact SHA-256: `1a25d72516d2db38059b332adab865b78939d7690c8499f1d0f1f926e76dd41b`

## What this baseline measures

The audit validates published JSON Schema syntax and representative generated artifacts, runs the manifest-backed Java golden repository, checks normalized repeated analysis output, and exercises evidence freshness/binding, legacy evidence regeneration, prepare/verify mutation boundaries, and related contract regressions. The versioned case definitions live at [`benchmark/agent-change-v0.1/cases.json`](../benchmark/agent-change-v0.1/cases.json); the runner is [`scripts/audit/phase0-foundation-audit.py`](../scripts/audit/phase0-foundation-audit.py).

The run reported 76 passing recorded cases in 30.475 seconds. That elapsed time is a single-run observation from a hosted CI runner, not a statistically controlled performance estimate.

## Reproduction

Run the Release Audit workflow on the desired candidate SHA (or push the candidate to `main` under the documented governance model). The deterministic-audit job builds the CLI, runs the versioned Phase 0 audit, and uploads `vericore-phase0-foundation-results`. Compare results only when the benchmark version, candidate fixture/case definitions, CLI build, and runner profile are equivalent. Retain the candidate SHA and artifact digest with every result.

## Limitations and interpretation

- This is a correctness/contract smoke benchmark, not a comparative agent leaderboard.
- The golden repository is intentionally small and Java-only. Kotlin and larger repository fixtures are future extensions.
- The 76 recorded cases include multiple assertions around the defined workflows; they must not be described as 76 independent real-world repositories.
- The reported duration includes CI-runner noise and must not be used as a product latency guarantee.
- The Actions artifact is retained according to GitHub's configured artifact retention period; this version-controlled summary preserves the result metadata and digest beyond that period.
- No broad performance, accuracy, or market-comparison claims are supported by this baseline.

## Next benchmark phase

Phase 8 may expand this foundation into a broader Agent Change Benchmark with additional repositories, failure categories, repeat runs, and controlled comparisons. Do not rewrite this initial baseline when later results become available; append a new versioned result.
