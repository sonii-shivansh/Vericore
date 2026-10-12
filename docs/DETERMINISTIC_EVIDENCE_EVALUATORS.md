# Deterministic Evidence Evaluators

Vericore evaluates only criteria whose meaning can be checked deterministically from current-candidate evidence. A citation ID or free-form statement is not proof by itself.

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

To evaluate a criterion, the prepared change specification must bind its criterion ID (for the first acceptance criterion, `acceptance-criterion-001`) to the citation ID named in the expression. During verification, Vericore reads the named metric from evidence generated for the candidate currently being verified. The value must exist and have the same supported type as the threshold. Numeric values support `>=`, `<=`, `==`, `!=`, `>`, and `<`. Boolean values must be exactly `true` or `false` and support only `==` and `!=`. A passing comparison yields `PASS`; a failed comparison, malformed metric expression, missing metric, unavailable current-candidate citation, or unbound citation yields `FAIL`.

The versioned regression matrix in `benchmark/agent-change-v0.2/evaluator-cases.json` exercises numeric threshold success/failure, decimal equality normalization, boolean equality and inequality, wrong-type evidence, missing metrics, absent current-candidate citations, unbound citations, and free-form criteria. The full Gradle unit suite executes these vectors against the real evaluator; they are not counted as CLI audit cases.

The evaluator does not interpret natural-language criteria, does not treat unrelated evidence as satisfying a metric, and does not use stale prepared metric values to decide the candidate's result. Other criterion forms retain their explicit `NOT_EVALUATED` or `INCONCLUSIVE` status until a suitable evaluator exists.

This evaluator supports numeric and boolean metrics only. It does not establish semantic correctness, test relevance, runtime behavior, or truth beyond the exact deterministic metric comparison.
