# Deterministic Evidence Evaluators

Vericore evaluates only criteria whose meaning can be checked deterministically from current-candidate evidence. A citation ID or free-form statement is not proof by itself.

## Numeric metric criterion

The first supported form is:

```text
metric:<citation-id>.<metric-key><operator><number>
```

Supported operators: `>=`, `<=`, `==`, `!=`, `>`, `<`.

Example:

```text
metric:repo.metrics.files>=10
```

To evaluate this criterion, the prepared change specification must bind the criterion ID (for the first acceptance criterion, `acceptance-criterion-001`) to `repo.metrics`. During verification, Vericore reads the named metric from evidence generated for the candidate currently being verified. The value must exist and be numeric. A passing comparison yields `PASS`; a failed comparison, malformed metric expression, missing metric, unavailable current-candidate citation, or unbound citation yields `FAIL`.

The evaluator does not interpret natural-language criteria, does not treat unrelated evidence as satisfying a metric, and does not use stale prepared metric values to decide the candidate's result. Other criterion forms retain their explicit `NOT_EVALUATED` or `INCONCLUSIVE` status until a suitable evaluator exists.

This initial evaluator supports numeric metrics only. It does not establish semantic correctness, test relevance, runtime behavior, or truth beyond the exact deterministic metric comparison.
