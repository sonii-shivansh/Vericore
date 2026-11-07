---
trigger: model_decision
description: Apply when changing change contracts, verification receipts, candidate digests, criterion evaluators, provenance, evidence schemas, verification statuses or trust boundaries.
---
# Evidence contract integrity
- Inspect current schema versions, serializers, compatibility/golden fixtures and existing regression tests before edits.
- Contract validity, candidate binding, evidence freshness, and verified scope must remain explicit. Do not infer unverified requirements passed.
- Distinguish integrity fingerprints from independent authentication or sandboxing.
- Preserve backward compatibility unless the approved issue includes migration and consumer tests.
- Test accepted, rejected, stale, malformed, tampered, unsupported and inconclusive outcomes as applicable.
- Do not change current verdict semantics silently; record rationale and impacted public contracts.
