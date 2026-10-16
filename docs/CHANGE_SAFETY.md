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
vericore prepare "add OAuth login" --planned-path src/main/java/com/example/AuthController.java
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
| `output/engineering-preparation.json` | Preparation result, evidence, plan, contract, and provenance |
| `output/engineering-context.json` | Context snapshot from `context-snapshot`; never overwritten by `prepare` |
| `output/engineering-plan.json` | Deterministic, evidence-backed implementation plan |
| `output/agent-change-contract.json` | Persisted repository-bound verification contract |

When the change is not already present in the working tree, supply explicit planned paths during preparation. For example, `--planned-path src/main/java/com/example/AuthController.java`. MCP agents can supply the same paths in `plannedPaths`.

The contract binds the planned change to repository identity and the Git `HEAD` observed during preparation when Git metadata is available. It also embeds a versioned, deterministic per-path candidate baseline captured before repository analysis. Preparation captures the manifest again before returning and aborts if candidate inputs changed during preparation.

Its SHA-256 fingerprint covers the meaningful persisted contract fields, including the change summary, repository identity, prepared `HEAD`, planned paths, expected components, verification commands, evidence IDs, architecture expectations, schema information, and the candidate baseline digest. The manifest is embedded in the contract so CLI and MCP consumers verify the same persisted inputs without trusting a separate mutable sidecar. An optional structured `specification` also binds required behaviors, forbidden behaviors, protected invariants, acceptance criteria, and required evidence. Empty specifications preserve historical fingerprints; non-empty specifications are fingerprint-bound. A fingerprint is an integrity checksum, not a signature or authorization mechanism.

The plan and contract are deterministic artifacts. Preparation does not modify source code.

The structured specification is caller-authored. Vericore validates its shape and size, normalizes whitespace and duplicate entries, and preserves it in both artifacts. Evidence bindings must reference a declared criterion ID and evidence IDs present in the prepared contract. A dangling evidence ID or unknown criterion fails closed. A valid evidence link is recorded as `INCONCLUSIVE` because the current system does not yet semantically evaluate arbitrary evidence content. The overall result remains `REVIEW_REQUIRED` until the criterion can be evaluated appropriately.

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
4. integrity of the prepare-time baseline manifest and detection of candidate inputs changed outside planned paths since preparation;
5. binding between the plan and persisted contract;
6. working-tree scope against planned paths;
7. deterministic dependency impact;
8. PR Intelligence signals;
9. Architecture Intelligence signals;
10. recommended verification commands;
11. verification provenance.

For a fast boundary check, `vericore verify --contract-only` validates the persisted contract, repository identity, prepared `HEAD`, prepare-time baseline integrity and drift, plan binding, and current mutation scope without rebuilding the full dependency analysis graph. It also reports the structured specification evaluation. Because this mode does not rebuild current-candidate evidence, supported metric criteria whose citation is unavailable in this mode remain `NOT_EVALUATED` rather than being incorrectly marked `FAIL`; the result remains `REVIEW_REQUIRED`. Use full `vericore verify` to evaluate metric criteria against current-candidate evidence.

A missing, tampered, mismatched, cross-repository, or stale contract produces `FAIL`. Verification does not silently reconstruct a replacement contract from a mutable plan.


## Prepare-time baseline

A new contract uses schema version `3.0` and embeds `baselineManifest` schema version `1.0`. Its sorted entries record each included repository-relative path, file kind (regular file or supported regular-file symlink), byte size, SHA-256 content digest, and executable state. The manifest digest is recomputed from all entries and metadata before it is trusted, and the contract fingerprint binds that digest. A digest is an identity check for the represented bytes and metadata, not proof that code is correct or that the build environment is fully attested.

Full verification and `vericore verify --contract-only` compare the current manifest to the original preparation baseline. Changes to explicitly planned files are allowed, and a planned directory authorizes descendants under that directory. CLI-selected preparation artifact paths inside the repository are recorded in the digest-bound exclusion list so generated artifacts do not become false drift. Unplanned changes, additions, and removals fail closed. This includes ignored and untracked inputs that may affect a build even when Git's ordinary changed-path list omits them. The current manifest uses the same exclusions described below, including the repository-root `output/` directory.

Legacy schema `2.0` contracts do not contain a prepare-time baseline. Vericore does not invent one at verification time: attempting to verify one now produces `FAIL` and asks the caller to run `prepare` again, which creates a baseline-bound contract. This is intentionally stricter than treating a verification-time snapshot as historical evidence.

## Candidate-state integrity

Full verification now records three candidate digests:

- `analysisInputDigest`: repository-input state at the start of analysis.
- `candidateStateDigest`: repository-input state immediately before declared verification commands.
- `postVerificationCandidateDigest`: repository-input state after those commands finish.

A stable candidate requires the three digests to match and Git HEAD to remain unchanged from analysis start through command completion. If the candidate changes during analysis, declared verification commands are not run. If it changes during verification, the result fails closed rather than claiming the commands verified a stable state. The receipt also records whether the state was stable and the file counts at command start and after execution. A timed-out verification command is terminated together with its captured child-process tree so a wrapper-spawned build or test process cannot continue mutating the repository after the post-verification snapshot.

The deterministic digest includes tracked and untracked repository files, except version-control metadata (`.git`, `.gradle`, `.idea`, `.vericore`, `.codecontext`, `node_modules`) and conventional generated/build-output directories. The repository-root `output/` directory is treated as Vericore-generated output; `build/` and `target/` directories are treated as generated outside source/test/fixture/input trees, so a source package or resource directory with one of those names is still hashed. File extensions do not create exclusions: an untracked root-level log is included because a build may read arbitrary repository files. Test harnesses that produce diagnostic output should write it under the generated `output/` directory. Regular-file symlinks include both the link target and the referent's content in the digest, including when the referent is outside the repository. Dangling symlinks and symlinks to directories or other non-regular targets fail snapshotting closed because their consumed input tree cannot currently be represented safely. This is repository-input integrity, not a complete build-environment attestation: external services and toolchain identity require separate controls. Snapshot read errors fail verification rather than being interpreted as a stable candidate.

## Status semantics

| Status | Meaning |
|---|---|
| `PASS` | The persisted contract is valid and detected changes remain within the planned scope. |
| `REVIEW_REQUIRED` | The contract is valid, but explicit engineering review is required, including deleted files, critical deterministic findings, or specification criteria that remain `NOT_EVALUATED` or `INCONCLUSIVE`. |
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
- repository restoration after mutation;
- timed-out build wrappers whose child processes attempt to mutate files after timeout.

The goal is to fail closed when the prepared verification boundary is no longer trustworthy.

### Specification evaluation status

A non-empty structured specification is expanded into stable per-criterion records. Current criteria are explicitly `NOT_EVALUATED` because no criterion-specific evidence evaluator is implemented yet. Vericore does not infer that a behavior passed merely because test commands passed or the repository stayed in scope. The overall verification result is `REVIEW_REQUIRED` until each obligation can be evaluated against appropriate evidence. Empty specifications preserve the prior status behavior.

## AI-agent workflow

An MCP-compatible agent can use the same boundary. MCP `prepare` persists the exact artifacts required by subsequent `get_change_contract` and `verify` calls; the agent does not need to switch to the CLI between preparation and verification.

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
