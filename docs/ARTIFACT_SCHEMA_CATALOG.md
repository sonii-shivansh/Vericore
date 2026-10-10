# Artifact Schema Catalog

This catalog is the source of truth for persisted, versioned core artifacts. Schemas live under docs/schemas; JSON Schema validation uses Draft 2020-12. When a serialized shape changes incompatibly, add a migration/compatibility test and bump that artifact's schema version instead of silently changing an existing version.

## Canonical artifacts

| Artifact | Producer / default path | Version | Schema | Notes |
|---|---|---:|---|---|
| Analysis snapshot | analyze / scan -> output/analysis-snapshot.json | 1.1 | schemas/analysis-snapshot.schema.json | Stable source-analysis contract; path and observation time are environment-specific |
| Grounded evidence | repo-qa -> output/grounded-evidence.json | 1.1 | schemas/grounded-evidence.schema.json | Carries commit/source digest/schema binding when available; legacy 1.0 can be decoded but cannot be reused as fresh evidence |
| Engineering context snapshot | context-snapshot --json -> output/engineering-context.json | 1.0 | schemas/engineering-context-snapshot.schema.json | This path is owned only by context-snapshot |
| Engineering context diff | context-diff -> output/engineering-context-diff.json | 1.0 | schemas/engineering-context-diff.schema.json | Deterministic list of added/removed/modified paths |
| Engineering plan | plan / prepare -> output/engineering-plan.json | 1.0 | schemas/engineering-plan.schema.json | Bound to contract fingerprint in preparation flow |
| Agent Change Contract | prepare -> output/agent-change-contract.json | 2.0 | schemas/agent-change-contract.schema.json | SHA-256 fingerprint is an integrity checksum, not a signature |
| Preparation result | prepare / MCP vericore_prepare_change -> output/engineering-preparation.json | 1.2 | schemas/engineering-preparation.schema.json | Dedicated path avoids collision with EngineeringContextSnapshot |
| Semantic evidence graph | evidence-graph --json -> output/semantic-evidence-graph.json | 1.0 | schemas/semantic-evidence-graph.schema.json | Ordered nodes/edges, cardinality and digest |
| Architecture analysis | architecture <path> --json -> output/architecture.json | 1.1 | schemas/architecture-analysis.schema.json | Sorted findings, cycles, layers and dependency edges |
| Change impact | impact <path> <changed-path> --json -> output/change-impact.json | 1.0 | schemas/change-impact.schema.json | Deterministic reachable impact nodes and summary |
| PR intelligence | pr-intelligence <path> --json -> output/pr-intelligence.json | 1.0 | schemas/pr-intelligence.schema.json | Deterministic rule findings and aggregate severity |
| Engineering risks | analyze -> output/engineering-risks.json | core risk model | schemas/engineering-risks.schema.json | Sorted risk list; schema tracks the serialized risk shape |
| Product inspection | inspect --json -> output/inspection.json | 1.0 | schemas/inspection.schema.json | Snapshot-derived user-facing inspection summary |
| Verification result | verify -> output/verification.json unless overridden | 1.2 | schemas/engineering-verification.schema.json | Caller can choose another output path |
| Verification receipt | full verify -> output/verification-receipt.json | 1.0 | schemas/verification-receipt.schema.json | Derived from the same verification result, not a second decision |
| Release-readiness certificate | Release Audit job | 1.0 | docs/release-readiness.schema.json | Existing schema outside docs/schemas |

## Remaining schema inventory

Architecture-contract outputs, architecture-drift outputs, Engineering Reality, temporal/evolution summaries, ProductCommandResult envelopes, and command-specific error payloads are not all covered by dedicated schemas yet. Their model/version definitions remain in source; add explicit schemas and generated-artifact validation if they become durable interoperability contracts. Phase 0 currently schemas and validates the core persisted artifacts and primary analysis/inspection outputs listed above, rather than claiming every JSON string emitted by every command is schema-governed.

## Compatibility rules

1. A consumer must reject an unsupported major artifact version or fail closed if it is used to establish a verification boundary.
2. Additive optional fields may be introduced only under a documented compatibility policy. Update the corresponding schema and fixtures in the same change.
3. Legacy evidence without repository/source-state binding can be read for migration but must not be reused as current evidence.
4. Absolute repository paths, commit IDs, timestamps, and digests are intentionally variable; golden comparisons normalize only explicitly documented variable fields.
5. Never update a schema to match a broken artifact without independent review of the artifact's intended contract.

## Validation

Install the pinned schema validator and run the Phase 0 foundation audit:

~~~
python3 -m pip install -r scripts/audit/requirements-schema.txt
python3 scripts/audit/phase0-foundation-audit.py --cli ./build/install/vericore/bin/vericore --output /tmp/vericore-phase0-benchmark-results.json
~~~

The audit validates the syntax of schemas, representative generated artifacts, golden-fixture expectations, and emits a machine-readable benchmark result. It does not claim performance superiority over another product.
