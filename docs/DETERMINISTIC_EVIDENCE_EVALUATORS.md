# Deterministic Evidence Evaluators

Vericore evaluates only criteria whose meaning can be checked deterministically from current-candidate evidence. A citation ID or free-form statement is not proof by itself.

## Command-success criteria

For a `requiredEvidence` criterion only, an evidence binding may set `rule` to `ALL_BOUND_COMMANDS_SUCCEEDED` and reference declared verification commands using stable IDs such as `verification-command-001`. The evaluator defaults `currentCandidateEvidenceAvailable` to `false`; callers must explicitly confirm that evidence was generated for the current candidate before a metric or command result can pass. IDs are one-based positions in the exact verification-command list persisted in the Agent Change Contract; the list is executed and reported in that same order. Vericore must not independently sort the list when assigning IDs. The evaluator returns `PASS` only when every bound command result exists, was executed, did not time out, and exited with code zero. A non-zero exit is `FAIL`; a missing result is `NOT_EVALUATED`; a timeout or inconclusive execution is `INCONCLUSIVE`. If current-candidate evidence is unavailable, command results are ignored and the criterion is `NOT_EVALUATED` even if a caller accidentally supplies a successful prior result. Contract-only verification never treats a declared-but-unexecuted or stale command result as a pass.

This rule establishes command-execution outcomes only. A passing build or test command does not prove that tests cover the requested behavior, that the test suite ran non-empty tests, or that the implementation is semantically correct. Criterion-level test-quality and regression-proof evaluators remain future work.

## Explicit JUnit test-case criteria

A criterion may explicitly bind to named test cases using `ALL_BOUND_JUNIT_CASES_PASSED` and selectors of the form `junit-test-case:<class>#<test-name>`. The selector identifies a test case by the class name and test name recorded in the JUnit XML; the evaluator does not infer that an arbitrary test is relevant to the requirement.

The collector recognizes conventional Gradle reports under `<module>/build/test-results/test/*.xml` and Maven Surefire reports under `<module>/target/surefire-reports/TEST-*.xml`. A report is fresh only when its bytes differ from the pre-verification snapshot and the Git candidate SHA and repository candidate-state digest remain unchanged across verification. Evidence preserves its normalized repository-relative path, SHA-256 report digest, parser version, execution ID, candidate SHA, and state digest. Report discovery and parsing are bounded, and DTD/external-entity access is disabled.

A uniquely matched passing test case yields `PASS`; a failed case yields `FAIL`; skipped or duplicate matches yield `INCONCLUSIVE`; missing matches or unavailable current-candidate evidence yield `NOT_EVALUATED`. Reports whose bytes are unchanged from the pre-run snapshot are not fresh evidence.

This rule proves only that the explicitly selected test case appears in a fresh report and is marked passed on a stable candidate. It does **not** prove that the test exercises or fully covers the natural-language criterion. The verifier is also not a sandbox for repository build logic: permitted build/test commands execute with the invoking user's permissions.

## Numeric and boolean metric criteria

The first supported form is:

```text
metric:<citation-id>.<metric-key><operator><number-or-boolean>
```

Supported operators: `>=`, `<=`, `==`, `!=`, `>`, `<`.

Example:

```text
metric:repo.metrics.files>=10
metric:architecture.summary.cycles==false
```

To evaluate a criterion, the prepared change specification must bind its criterion ID (for the first acceptance criterion, `acceptance-criterion-001`) to the citation ID named in the expression. During verification, Vericore reads the named metric from evidence generated for the candidate currently being verified. The value must exist and have the same supported type as the threshold. Numeric values support `>=`, `<=`, `==`, `!=`, `>`, and `<`. Boolean values must be exactly `true` or `false` and support only `==` and `!=`. A passing comparison yields `PASS`. In full verification, a failed comparison, malformed metric expression, missing metric, unavailable current-candidate citation, or unbound citation yields `FAIL`. In `verify --contract-only`, candidate evidence is deliberately not rebuilt, so otherwise evaluable metric criteria remain `NOT_EVALUATED` and require review instead of treating unavailable evidence as a failed comparison.

The versioned regression matrix in `benchmark/agent-change-v0.2/evaluator-cases.json` exercises numeric threshold success/failure, decimal equality normalization, boolean equality and inequality, wrong-type evidence, missing metrics, absent current-candidate citations, unbound citations, and free-form criteria. Duplicate citation IDs in current-candidate evidence are treated as ambiguous and cannot satisfy a metric criterion; the benchmark includes a conflicting duplicate-ID regression. The full Gradle unit suite executes these vectors against the real evaluator; they are not counted as CLI audit cases.

The evaluator does not interpret natural-language criteria, does not treat unrelated evidence as satisfying a metric, and does not use stale prepared metric values to decide the candidate's result. Other criterion forms retain their explicit `NOT_EVALUATED` or `INCONCLUSIVE` status until a suitable evaluator exists.

This evaluator supports numeric and boolean metrics only. It does not establish semantic correctness, test relevance, runtime behavior, or truth beyond the exact deterministic metric comparison.
