# Phase 6 — Semantic Evidence Graph Foundation

> **Status:** Implemented and integrated into the current 0.8.0 release candidate. This document records the original Phase 6 design foundation; the follow-up phases listed below are now represented by the shipped implementation.

## Goal

Make deterministic evidence identity, provenance, and relationships explicit without replacing existing analysis, Repo-QA, AI grounding, or Engineering Reality contracts.

## Scope delivered

The Phase 6 implementation provides a bounded, immutable semantic evidence graph over already-produced evidence. It:

- preserves existing evidence IDs and contracts;
- binds nodes to repository identity and observed commit/state;
- carries producer and content digest metadata;
- models explicit `DERIVED_FROM` and `SUPPORTS` relationships;
- provides deterministic node/edge ordering;
- rejects duplicate node IDs, dangling edges, cross-repository nodes, stale evidence, and invalid self-relationships;
- resolves cross-feature evidence by stable source reference;
- materializes a deterministic `semantic-evidence-graph.json` artifact;
- exposes the graph through the `evidence-graph` CLI command;
- makes graph identity available to grounded AI and Engineering Reality;
- binds graph identity/cardinality into engineering preparation provenance;
- validates the graph in the release-gate E2E suite.

## Non-goals

The semantic evidence graph does not replace the dependency graph, add a generic graph database, or make AI conclusions authoritative.

## Invariants

1. A graph has one repository identity.
2. Every node has a stable ID, evidence type, producer, repository identity, observed commit/state, and content digest.
3. Nodes are immutable after construction.
4. Edge endpoints must exist in the graph.
5. Cross-repository edges are invalid.
6. Node and edge serialization is deterministic.
7. Empty graphs are valid.
8. Existing evidence remains the source of truth; the graph is a structured provenance and relationship layer.
9. Graph identity is deterministic and uses a stable SHA-256 digest.

## Delivered integration phases

- **6A:** bounded immutable graph foundation.
- **6B:** architecture, hotspot, temporal, and Repo-QA evidence connectivity.
- **6C:** graph-backed cross-feature evidence resolution in Repo-QA.
- **6D:** graph identity and relationships available to AI grounding.
- **6E:** planner/prepare provenance and Engineering Reality binding.

These phases are documented here as historical implementation milestones. Future work must be recorded separately as future direction rather than leaving completed phases marked as pending.
