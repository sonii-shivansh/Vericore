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
| Verification result | verify -> output/verification.json unless overridden | 1.2 | schemas/engineering-verification.schema.json | Caller can choose another output path |
| Verification receipt | full verify -> output/verification-receipt.json | 1.0 | schemas/verification-receipt.schema.json | Derived from the same verification result, not a second decision |
| Release-readiness certificate | Release Audit job | 1.0 | docs/release-readiness.schema.json | Existing schema outside docs/schemas |

## Informational JSON outputs

The CLI also emits versioned informational artifacts such as architecture analysis, change impact, PR intelligence, engineering risks, architecture contracts, and product command results. Their schema versions are defined in their Kotlin serializable models. They remain on the inventory backlog until they have dedicated JSON Schemas and are included in the generated-artifact audit; a version field alone is not full schema validation.

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
