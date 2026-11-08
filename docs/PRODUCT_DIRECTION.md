# Vericore Product Direction

> **AI writes the code. Vericore verifies the change.**

Vericore is an open-source, local-first change assurance engine for JVM (Java/Kotlin) repositories. It establishes verifiable boundaries and deterministic evidence for AI-assisted code mutations before changes are accepted.

---

## 1. Product Purpose & Core Lifecycle

AI-assisted changes need inspectable evidence against an agreed scope. Vericore records that scope before editing and evaluates the candidate against the original persisted contract.

Vericore introduces a contract-bound **prepare → mutate → verify** lifecycle:

1. **Before the edit (`vericore prepare`)**: Captures developer intent, planned path boundaries, and declared verification commands into an immutable, persisted **Agent Change Contract** with candidate state fingerprints.
2. **During the edit**: The AI coding agent or developer mutates the codebase. Vericore does not author source code.
3. **After the edit (`vericore verify`)**: Evaluates the candidate repository strictly against the **original persisted contract**, validating path compliance, candidate stability, deterministic analysis signals, build command results, and named test evidence.

The verification engine emits an explicit verdict:
- **`PASS`**: PASS means the implemented automated checks succeeded for the observed candidate under the recorded scope and assumptions. It does not establish complete behavioral correctness, comprehensive test coverage, or absence of defects.
- **`REVIEW_REQUIRED`**: Verification detected conditions requiring human inspection (such as deleted source files, `CRITICAL` risk signals, or criteria evaluated as `INCONCLUSIVE` or `NOT_EVALUATED`). A `REVIEW_REQUIRED` verdict cannot silently become `PASS`. Merging requires documented maintainer risk acceptance.
- **`FAIL`**: Triggered by an inviolable safety boundary failure (unexpected path touches outside plan, baseline candidate drift, contract tampering, missing plan, or failing verification commands/specification criteria). A genuine `FAIL` represents an inviolable safety barrier and cannot be overridden.

---

## 2. Implementation & Trust Boundaries

Vericore's verification guarantees are bounded by the checks actually performed:

- **Command Grammar vs. Sandboxing**: The verification command runner restricts commands to an allowlisted build grammar (Gradle/Maven). It is a command grammar filter, not an operating system sandbox; permitted build tools execute with the invoking user's permissions.
- **Local Interfaces**: REST binds to loopback by default; non-loopback binding requires explicit opt-in. MCP communicates over local standard input/output. These interfaces assume trusted callers and do not provide a multi-tenant security boundary.
- **Integrity vs. Identity**: SHA-256 fingerprints establish content-integrity digests over canonical representations; they are not cryptographic signatures, user identity credentials, or execution barriers.
- **Network Boundaries & Offline Execution**: Deterministic repository analysis requires no AI service or outbound network connection. Verification may invoke build tools that resolve dependencies over the network. Optional AI integrations make configured provider requests.

---

## 3. Core Engineering Principles

- **Deterministic Evidence First**: Repository ground truth and executable test evidence take precedence over model reasoning or human assumptions.
- **Contract-Bound Verification**: Mutations are evaluated strictly against the original persisted pre-edit contract, never against dynamically reconstructed or mutable plans.
- **Fail-Closed Operation**: Missing contracts, altered fingerprints, candidate drift, or unexpected file touches fail closed.
- **Public Contract Stability**: CLI flags, JSON schemas, MCP tools, and exit codes are versioned contracts governed by strict backward-compatibility rules.

---

## 4. Authoritative Documentation

For active capabilities, configuration, and interfaces, refer to living documentation:

- [CLI Reference](CLI.md) — Comprehensive command guide and usage
- [Implementation Status](IMPLEMENTATION_STATUS.md) — Shipped capabilities and current status
- [Support Matrix](SUPPORT_MATRIX.md) — Verified JDK, Kotlin, and OS toolchain baselines
- [MCP Reference](MCP.md) — Local stdio Model Context Protocol integration
- [GitHub Action Reference](GITHUB_ACTION.md) — CI/CD integration guide
- [Repository Governance](REPOSITORY_GOVERNANCE.md) — Release and contribution policies
- [Engineering Constitution](../.specify/memory/constitution.md) — Ratified engineering standards and principles
