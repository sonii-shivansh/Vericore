# Change Safety

> Prepare a repository-bound change contract before editing code, then verify the original persisted contract after the change.

## Purpose

The Change Safety Loop protects developers and AI coding agents from silent scope expansion, stale repository state, contract tampering, and cross-repository verification.

```text
prepare → contract → change → verify
```

The workflow is deterministic. It does not authorize a change and it does not decide whether a change is good. It verifies whether the repository still matches the prepared change boundary.

## Quick start

From the repository you intend to change:

```bash
vericore prepare "add OAuth login"
```

Make the change, run your normal tests, then verify the persisted contract:

```bash
vericore verify
```

For an explicit repository and artifact path:

```bash
vericore verify \
  --path /path/to/repository \
  --plan output/engineering-plan.json \
  --contract output/agent-change-contract.json \
  --output output/verification.json
```

## 1. Prepare

`prepare` creates three repository-scoped artifacts:

| Artifact | Purpose |
|---|---|
| `output/engineering-context.json` | Repository state, evidence, and context used by planning |
| `output/engineering-plan.json` | Deterministic, evidence-backed implementation plan |
| `output/agent-change-contract.json` | Persisted repository-bound verification contract |

The contract binds the planned change to repository identity and the Git `HEAD` observed during preparation when Git metadata is available.

Its SHA-256 fingerprint covers the meaningful persisted contract fields, including the change summary, repository identity, prepared `HEAD`, planned paths, expected components, verification commands, evidence IDs, architecture expectations, and schema information.

The plan and contract are deterministic artifacts. Preparation does not modify source code.

## 2. Change the code

Use your normal development workflow or an AI coding agent.

Do **not**:

- replace the persisted contract;
- regenerate a new contract after preparation and use it as the verification authority;
- move the change to another repository and expect verification to accept it;
- treat a `PASS` result as a substitute for tests or code review.

## 3. Verify

Verification loads the persisted contract and validates:

1. contract fingerprint integrity;
2. repository identity;
3. prepared Git `HEAD` freshness;
4. binding between the plan and persisted contract;
5. working-tree scope against planned paths;
6. deterministic dependency impact;
7. PR Intelligence signals;
8. Architecture Intelligence signals;
9. recommended verification commands;
10. verification provenance.

For a fast boundary check, `vericore verify --contract-only` validates the persisted contract, repository identity, prepared `HEAD`, plan binding, and current mutation scope without rebuilding the full dependency analysis graph.

A missing, tampered, mismatched, cross-repository, or stale contract produces `FAIL`. Verification does not silently reconstruct a replacement contract from a mutable plan.

## Status semantics

| Status | Meaning |
|---|---|
| `PASS` | The persisted contract is valid and detected changes remain within the planned scope. |
| `REVIEW_REQUIRED` | The contract is valid, but the change contains a condition that needs explicit engineering review, such as a deleted file or critical deterministic finding. |
| `FAIL` | The contract is invalid or stale, or a detected change falls outside the prepared scope. |

These statuses are **review signals**, not proof of software correctness. Tests, review, and human engineering judgment remain required.

## Adversarial cases

The release and E2E test suites exercise the safety boundary against:

- contract-field tampering;
- schema-version tampering;
- plan/contract mismatch;
- repository mismatch;
- stale prepared `HEAD`;
- unexpected source changes;
- missing contract;
- malformed contract;
- repository restoration after mutation.

The goal is to fail closed when the prepared verification boundary is no longer trustworthy.

## AI-agent workflow

An MCP-compatible agent can use the same boundary:

```text
Vericore evidence
        ↓
prepare
        ↓
persist Agent Change Contract
        ↓
agent changes repository
        ↓
verify original persisted contract
        ↓
agent runs tests / responds to findings
```

The MCP verification path uses the persisted `output/agent-change-contract.json` when no explicit contract object is supplied.

`vericore_get_change_contract` is a **retrieval operation**: it reads the persisted `output/agent-change-contract.json` and does not generate a replacement contract.

## Security boundary

The workflow is local-first. Repository paths use Vericore's path-safety boundary. The workflow does not upload repository contents to Vericore infrastructure.

AI is optional. The deterministic prepare/verify workflow does not require an external model.

## Related documents

- [Architecture](ARCHITECTURE.md) — system boundaries and data flow
- [Engineering Reality](ENGINEERING_REALITY.md) — repository-state identity
- [MCP](MCP.md) — agent integration contract
- [Development](DEVELOPMENT.md) — contribution and validation workflow
