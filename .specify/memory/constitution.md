# Vericore Engineering Constitution

The foundational engineering, architectural, and governance rules governing all Vericore software development, AI agent interactions, and release certification.

<!--
Sync Impact Report:
- Version change: [TEMPLATE] -> 1.0.0
- Replaced unpopulated template placeholders with 10 ratified Vericore core principles.
- Grounded toolchain policy in checked-in build configuration and wrapper properties, with managed upgrade criteria.
- Encoded accurate verification verdict semantics matching EngineeringVerification (PASS, FAIL, REVIEW_REQUIRED).
- Distinguished discrete checkpoint digests as detecting observed state differences, not guaranteeing absence of transient in-flight changes.
- Clarified that REVIEW_REQUIRED mandates documented, policy-authorized human risk acceptance, cannot silently become PASS, and a genuine FAIL cannot be overridden.
- Established clear network, execution, and security trust boundaries.
- Formally recognized single-maintainer governance, DCO sign-offs, and human approval authority.
- Scoped change contract requirements to behavioral changes, exempting documentation and read-only maintenance.
-->

## Core Principles

### I. Deterministic Evidence First (Honest & Bounded Claims)
Repository ground truth and deterministic code analysis always take precedence over model reasoning, human assumptions, or marketing claims. All reported findings, intelligence signals, and verification outcomes must be backed by inspectable, repository-relative evidence with explicit provenance. Claims must be strictly categorized into verifiable states: `IMPLEMENTED`, `DOCUMENTED`, `PROPOSED`, `UNKNOWN`, and `VERIFIED-BY-TEST`. Verification is strictly bounded by the checks actually performed; an agent or reviewer must never assert general program correctness, security guarantees, or complete natural-language requirement fulfillment based on partial or passing tests.

### II. Contract-Bound Change Verification (Fail-Closed Loop)
For all changes introducing, modifying, or refactoring application behavior or compiled bytecodes, verification must evaluate strictly against the original persisted `AgentChangeContract` established before the mutation (`vericore prepare` -> mutate -> `vericore verify`). Verification must never dynamically reconstruct a synthetic replacement contract from a mutated repository or mutable plan. The verification engine operates fail-closed: missing contracts, tampered fingerprints, mismatched repository identities, stale Git HEADs, baseline candidate drift, or unexpected file touches outside the planned scope automatically yield `SafetyStatus.FAIL`.

### III. Proportional Change Governance (Scoped Contract Applicability)
The pre-to-post `AgentChangeContract` lifecycle is mandatory for behavioral code modifications, architectural changes, public contract updates, and AI agent edits to production source trees. To maintain engineering velocity, scoped maintenance tasks—such as documentation-only updates, typo corrections, read-only repository audits, and repository governance or skill adjustments that do not alter compiled JVM bytecodes or public interfaces—do not require a synthetic change contract lifecycle, provided they satisfy their respective Definition of Done and execute in a dedicated, reviewable branch.

### IV. Public Interface Stability & Explicit Versioning
Public surfaces—including Ajalt Clikt CLI commands, JSON serialization schemas (contracts, receipts, analysis snapshots), local Ktor REST routes, and stdio MCP tools—are formal contracts. Every machine-readable artifact must declare an explicit `schemaVersion`. Breaking contract modifications, removed options, or altered exit/verdict semantics are strictly prohibited without an approved Architecture Decision Record (ADR), formal version migration plan, compatibility tests, and an appropriate semantic version increment.

### V. Backward Compatibility & Managed Migration
Client workflows, existing MCP consumers, and CI action invocations must remain functional across minor and patch updates. When schema evolutions occur (e.g., contract schema 2.0 to 3.0/3.1), legacy contracts must fail closed safely with actionable guidance rather than crashing or guessing state. New fields must be optional or provide deterministic default fallback initializers.

### VI. Trust Boundaries, Least Privilege & Execution Realism
Vericore local interfaces and execution mechanisms operate within explicit, realistic trust boundaries:
- Local Ktor REST endpoints and stdio MCP servers are designed exclusively for trusted local execution. They reject remote repository URLs and do not provide remote tenant isolation, user authentication, or TLS.
- Repository file access must be canonicalized and strictly constrained within configured allowed root boundaries.
- Verification command execution (`VerificationCommandExecutor`) restricts commands to a strict whitelist grammar (Maven/Gradle); arbitrary shell execution is rejected.
- A passing build tool execution runs with the invoking user's OS permissions. The command validator is a grammar filter, NOT an OS security sandbox.
- SHA-256 fingerprints are strictly content-integrity digests over canonical representations; they must never be represented as cryptographic signatures, identity credentials, authorization tokens, or execution barriers.

### VII. Offline Core Analysis & Controlled Network Boundaries
Core repository scanning, AST/regex parsing, graph analysis, intelligence generation, and verification must remain strictly offline-capable without outbound network calls. Network access is constrained to explicit, isolated boundaries:
- Optional AI reasoning (Gemini, Claude) makes outbound API calls only when explicitly configured and enabled.
- Build tool dependency resolution may access configured package repositories during local verification commands when dependencies are not cached.
- Future isolated, containerized, or sandboxed execution runners are strategic capabilities and must not be asserted as present until implemented and verified by tests.

### VIII. Reproducible Verification & Verdict Semantics
Verification decisions must be reproducible, candidate-bound, and preserve the engine's explicit verdict semantics:
- `PASS`: Achieved when the contract is valid, candidate state remains stable across observation checkpoints, planned scope boundaries are respected (zero unexpected paths outside plan), all declared verification commands execute and exit zero, all evaluated specification criteria pass, no deleted paths require review, and PR intelligence aggregate severity is not `CRITICAL`. Non-critical informational diagnostics or advisory warnings may still be present. A `PASS` proves only that declared automated checks succeeded against the candidate state; it does not guarantee semantic correctness, full requirement coverage, or total absence of defects.
- `REVIEW_REQUIRED`: Triggered when the verification engine detects conditions requiring explicit human inspection—such as deleted source paths, `CRITICAL` PR risk signals, or specification criteria evaluated as `INCONCLUSIVE` or `NOT_EVALUATED`. A `REVIEW_REQUIRED` verdict must never silently be coerced into `PASS`. Merging a candidate with a `REVIEW_REQUIRED` verdict requires documented, policy-authorized human risk acceptance by the maintainer in the pull request record.
- `FAIL`: Triggered by an inviolable safety boundary failure (unexpected paths outside plan, missing plan scope when source mutated, baseline candidate drift, contract tampering, stale Git HEAD, failed verification commands, or failed specification criteria). A genuine `FAIL` verdict represents an inviolable safety barrier and cannot be overridden by human waiver. Missing or stale evidence must never be coerced into `PASS`.
- Candidate digests before analysis, before verification commands, and after verification commands detect *observed state differences* across discrete checkpoints. While they confirm that the observed candidate state remained stable across phases, they do not guarantee that no transient, temporary in-flight filesystem modification occurred between checkpoints.
- Explicit JUnit evidence bindings (`junit-test-case:<class>#<name>`) require fresh report bytes from conventional Gradle/Maven test XML directories matching the exact candidate state.

### IX. Managed Toolchain Baseline & Architectural Discipline
The repository's build toolchain is anchored in checked-in build configuration and wrapper properties (currently Java 25 LTS bytecode target, Kotlin 2.4.21, and Gradle 9.7.0 wrapper). Exact toolchain versions are not immutable constitutional dogma, but toolchain upgrades require:
1. An approved change issue or ADR.
2. Cross-platform build and test verification on supported operating systems.
3. Simultaneous synchronization of build scripts, launcher scripts, CI workflows, and documentation.
- Architectural layering must be strictly maintained: Core owns deterministic domain algorithms and state; Adapters (CLI, REST, MCP) orchestrate; AI integrations remain behind explicit provider boundaries.
- Stable, specified canonicalization (e.g. alphabetical sorting of paths and keys) is required for digests and identity calculations, but universal reordering of public JSON schemas is prohibited unless specified by the schema contract.

### X. Human Authority, Single-Maintainer Governance & Certification
Vericore operates under single-maintainer human ownership. The human maintainer retains sole authority to approve task scopes, accept risk on `REVIEW_REQUIRED` verdicts, authorize PR merges, and certify releases:
- AI-assisted review is an advisory diagnostic tool; it does not substitute for human maintainer review and approval.
- Developer Certificate of Origin (DCO) sign-offs (`git commit -s`) are required for all contribution commits.
- All code changes must arrive via bounded feature branches and reviewable pull requests.
- Pull requests must pass targeted required CI checks on the exact PR commit SHA. Full platform matrices and release audits are reserved for candidate certification via `release-audit.yml` prior to tagging.

## Non-Negotiable Engineering Standards

1. **Toolchain Adherence**: Code must build cleanly via `./gradlew test installDist` under JDK 25 LTS with zero unaddressed compiler errors or fatal warnings.
2. **Deterministic Canonicalization**: Hashes, candidate digests, and identity calculations must use documented canonicalization rules (alphabetical path sorting, normalized separators) and exclude wall-clock timestamps.
3. **Parser Safety & Transparency**: `JavaRealParser` (JavaParser 3.28.2) handles Java source; `KotlinRegexParser` limitations must be acknowledged and never documented as a complete compiler AST.
4. **Verdict Integrity**: No test or verification runner may convert missing, malformed, or unexecuted criteria into `PASS`. A genuine `FAIL` verdict cannot be overridden.
5. **No Direct Push**: Direct pushes to `main` are strictly forbidden. All changes flow through reviewable pull requests.
6. **Contribution Provenance**: All commits must include a valid DCO `Signed-off-by:` trailer.

## Development Workflow & Quality Gates

### Definition of Ready (DoR) — Before Implementation
Before any task implementation begins (human or agent), the following must be established:
1. An approved GitHub issue defining the user problem, target scope, and explicit non-goals.
2. An isolated feature branch created from the latest confirmed `main` (never work on `main`).
3. Concrete, testable acceptance criteria, including failure and boundary conditions.
4. Scope classification: **Behavioral** (requires prepare/verify change contract) or **Maintenance/Docs** (exempt from change contract).
5. For behavioral changes: identified target paths, components, and verification commands.
6. Explicit human maintainer authorization to proceed.

### Definition of Done (DoD) — Before Merge
Before any pull request is merged into `main`:
1. Source diff is strictly confined to the approved issue scope (zero unrelated touches).
2. Focused regression tests (Kotest/JUnit 5) cover all added or altered behaviors.
3. Targeted PR CI checks pass with exit code 0 on the exact candidate commit SHA.
4. For behavioral changes: `vericore verify` reports `SafetyStatus.PASS`, or `REVIEW_REQUIRED` accompanied by documented, policy-authorized maintainer risk acceptance.
5. All commits carry DCO sign-offs (`git commit -s`).
6. Relevant living documentation (`ARCHITECTURE.md`, `SUPPORT_MATRIX.md`, `IMPLEMENTATION_STATUS.md`) is updated in the same PR.
7. Explicit review and merge approval by the human maintainer.

## Governance & Amendment Procedure

- **Authority**: This Constitution is the authoritative engineering policy for Vericore.
- **Enforcement**: Pull requests and AI workflows must comply with these principles. Non-compliant contributions will be referred for revision.
- **Amendment Classification**:
  - **MAJOR (X.0.0)**: Removal or fundamental redefinition of a Core Principle, alteration of trust boundaries, or changing human approval authority.
  - **MINOR (1.X.0)**: Adding a new principle, adopting a new supported language/platform, or expanding mandatory quality gates.
  - **PATCH (1.0.X)**: Non-semantic clarifications, typo fixes, or documentation synchronization.
- **Ratification**: Amendments require a formal pull request modifying this document and explicit approval by the human maintainer.

**Version**: 1.0.0 | **Ratified**: 2026-11-07 | **Last Amended**: 2026-11-07
