# VERICORE — MASTER PRODUCT, ENGINEERING & GROWTH PLAN

> **Status:** Proposed strategic plan for owner approval; **not** an implementation record or instruction to modify code immediately.  
> **Prepared:** 2026-11-07 (Asia/Kolkata)  
> **Repository:** https://github.com/sonii-shivansh/Vericore  
> **Reviewed `main` SHA:** `da3514f90b9053d19b1a002872460a586e459a75`  
> **Latest verified release:** `v2.0.0`, published 2026-11-06 23:14:37 UTC (2026-11-07 IST)  
> **Execution model:** User retains ownership and approvals; Antigravity is the implementation environment; ChatGPT serves as product strategy, architecture, research and critical-review partner.  
> **Scope of this deliverable:** Strategy, verified repository findings, competitor research, priorities, acceptance criteria, technical designs, Antigravity handoff and growth plan. **No code or GitHub repository changes were made.**

---

## 0. How to use this document

1. **This is a proposal, not a claim that roadmap features are already implemented.** Labels mean: `VERIFIED IN CODE`, `DOCUMENTED`, `EXTERNAL VERIFIED`, `PROPOSED`, `RESEARCH REQUIRED`, and `CONDITIONAL`.
2. **Use the Antigravity knowledge document for orientation, not as unquestioned authority.** Verify behavior against the pinned repository code and regression tests before work begins; re-pin SHA after each approved merge.
3. **Execute one bounded phase/PR at a time.** Antigravity should return a plan, diffs, tests, risks and evidence for review, rather than perform a broad rewrite.
4. **Do not claim to prove all program correctness.** A test, static analyzer, concurrency checker, hash or signature has an explicitly bounded meaning.
5. **Only the user authorizes code changes, merges, tags, deployment, secrets access or external publication.** A roadmap is not blanket authorization.
6. **Preserve backwards compatibility** for current CLI commands, MCP clients, artifacts and established user workflows unless a separately approved migration explicitly changes them.
7. **This assessment is not a line-by-line security audit of every repository file and not a fresh local build execution.** The 328-file Git tree was enumerated; selected core implementations, configuration and workflows were inspected; recent GitHub Actions results were checked. Uninspected details must be verified when relevant.

### Decision summary

**Recommended product:** **Vericore Change Passport** — an **independent, evidence-linked, change-level verification record** with clear uncertainty, **evaluation of test quality**, and **freshness / invalidation tracking** as software and agents continue changing the project.

**Positioning:** “AI writes the change. Vericore shows what was checked, what was not, and when the evidence stops applying.”

**Primary wedge:** Java/Kotlin developers and teams using AI coding agents, who need to independently assess pre-agreed change scope and behavioral obligations before accepting changes. Do not claim to support every language or agent configuration until tested.

**First six strategic priorities (in order):**

- P0: Establish exact-candidate assurance baselines, honest status semantics, supported-environment boundary and release discipline.
- P1: Make first successful verification radically easy (init → prepare → verify → explain results; no new mandatory command proliferation).
- P1: Convert requirements into inspectable criterion-to-evidence evaluations with a **coverage and uncertainty map**.
- P1: Establish a protected, independently controlled verification runner for untrusted code when required.
- P2: Validate narrowly scoped, independently defined behavioral checks (targeted mutation, property tests, concurrency probes) *before* marketing them as protection.
- P2: Publish portable, interoperable Change Passports and a reproducible public benchmark with false-accept **and** false-reject rates.

**North-star outcome:** A developer can understand and independently inspect **why** an AI-assisted change was accepted, rejected or referred for review, without needing to trust the coding agent or Vericore marketing language.

---

## 1. Grounded baseline: what Vericore is today

### 1.1 Product and execution flow — VERIFIED IN CODE / DOCUMENTED

Current principal workflow:

```
Developer defines intent and allowed scope
       |
       v
vericore prepare: repository evidence + plan + persisted Agent Change Contract
       |            - prepared Git state
       |            - candidate baseline manifest
       |            - scope / verification commands / optional specification
       |            - SHA-256 contract fingerprint
       v
AI agent or developer edits repository (Vericore does not author source changes)
       |
       v
vericore verify: inspect original persisted contract
       |            - contract validity and repository/HEAD binding
       |            - candidate drift and unplanned paths
       |            - deterministic analysis and PR/architecture signals
       |            - declared build/test commands
       |            - fresh, candidate-tied JUnit reports
       |            - narrow structured criterion evaluator
       v
PASS / FAIL / REVIEW_REQUIRED + verification receipt + limits
```

Sources: [`EngineeringPreparation.kt`](https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/EngineeringPreparation.kt), [`EngineeringVerification.kt`](https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/EngineeringVerification.kt), [`AgentChangeContract.kt`](https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/AgentChangeContract.kt), [`README.md`](https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/README.md).

### 1.2 Technology baseline — VERIFIED IN CODE

- Kotlin/JVM source and Gradle Kotlin DSL; build script specifies Kotlin **2.4.21** and Java/JVM **25**.
- Clikt CLI; JavaParser for Java; regex-based Kotlin parser with documented limitations.
- JGit history/change analysis; JGraphT dependency graph and centrality; local caches and versioned JSON artifacts.
- Ktor REST server for trusted local/internal use; local stdio MCP gateway; GitHub composite action; release/installers.
- Optional AI integrations; deterministic checks do not require an AI provider.
- Verifier supports Gradle/Maven commands through a restrictive command grammar, **but a permitted build script still runs with host privileges when run locally**. Command validation is not sandboxing.
- `AgentChangeContract` can use schema **3.0** with a candidate baseline, **3.1** with baseline plus agent context; verification receipt defaults to **1.5** in inspected source.
- Existing `ChangeSpecificationEvaluator` evaluates bounded metric, declared-command and named-JUnit evidence; unsupported semantic obligations remain inconclusive/not evaluated.
- Main entry point registers **27 CLI subcommands**. The proposed simple workflow should be an opinionated surface over this core, not 27 equally prominent choices.

Source entry points:

| Concern | Primary code / current responsibility |
|---|---|
| CLI registration | `src/main/kotlin/com/vericore/Main.kt` — runtime requirement and command composition |
| Scanning / parsing | `core/scanner/RepositoryScanner.kt`, `core/parser/JavaRealParser.kt`, `core/parser/KotlinRegexParser.kt` |
| Dependency analysis | `core/graph/RobustDependencyGraph.kt`, `core/intelligence/ChangeImpactEngine.kt` |
| Context / evidence | `core/intelligence/EngineeringContext.kt`, `core/evidence/*`, `core/reality/EngineeringReality.kt` |
| Planning | `core/planner/EngineeringPlanner.kt`, `core/planner/ChangeSpecification.kt` |
| Pre-change artifact | `core/workflow/EngineeringPreparation.kt`, `AgentChangeContract.kt`, `CandidateStateSnapshotter.kt` |
| Post-change check | `core/workflow/EngineeringVerification.kt`, `VerificationCommandExecutor.kt`, `ChangeSpecificationEvaluator.kt`, `VerificationReceipt.kt` |
| PR intelligence | `core/intelligence/GitChangeSetBuilder.kt`, `PRIntelligence.kt`, `PRIntelligenceAnalyzer.kt` |
| MCP | `mcp/McpProtocol.kt`, `mcp/EngineeringContextGateway.kt` |
| REST | `server/VericoreServer.kt`, `server/RateLimiter.kt` |
| CI / distribution | `.github/workflows/*`, `action.yml`, `scripts/install.*` |

### 1.3 Release and CI verification — VERIFIED VIA CONNECTED GITHUB

- `main` baseline: `da3514f90b9053d19b1a002872460a586e459a75`.
- Published GitHub release: **v2.0.0**, release time **2026-11-06 23:14:37 UTC**, with Linux x64, macOS x64/ARM64, Windows x64 archives plus checksums.
- Connected GitHub Actions reports completed **success** on this exact SHA for Release Audit, Documentation Parity and End-to-End Test Suite (e.g., [Release Audit run](https://github.com/sonii-shivansh/Vericore/actions/runs/42360394726), [E2E run](https://github.com/sonii-shivansh/Vericore/actions/runs/42360394651)).
- The GitHub branches API reported `main.protected=false` at review time. Evaluate repository rulesets and branch protection before relying on merge integrity; absence of the legacy `protected` flag is not a complete audit of all rulesets.
- `docs/IMPLEMENTATION_STATUS.md` still says latest confirmed release is v1.0.1: documentation is stale relative to the actual published v2.0.0 release. A passing docs-parity workflow does not guarantee every release statement is current.

### 1.4 Current strengths — VERIFY THROUGH TESTS, DON'T OVERSELL

1. Pre-edit boundary and candidate digest anchored to an original persisted contract.
2. Deterministic repository/architecture/evidence modeling separated from optional AI opinions.
3. Multi-interface access: CLI, MCP, GitHub Action and internal REST.
4. Conservative handling of unsupported natural-language criteria and stale/missing evidence.
5. Broad repository regression, audit and release workflows already present.

### 1.5 Current limitations and prioritized investigation

| Risk | Verified observation | Severity / recommended action |
|---|---|---|
| Local command trust | `VerificationCommandExecutor` launches permissible Gradle/Maven commands through `ProcessBuilder`; a build can execute user/repository code | **High for untrusted repos**: introduce a real isolation boundary before marketing agent-proof execution |
| REST trust | `VericoreServer.kt` lacks built-in authentication and tenant isolation; local path sanitization has a default list including working directory **and JVM temp directory** | **High for exposed/shared usage**: retain trusted-loopback boundary; review default roots; require authentication/segregation for any remote scenario |
| Kotlin correctness | Kotlin parser is regex-based | **Medium/high for graph precision**: build a public accuracy fixture suite and evaluate parser alternatives |
| Semantic claims | JUnit success and metrics cannot generally prove arbitrary natural-language behavior | **High for trust**: explicit per-criterion evidence and uncertainty semantics |
| Contract authority | SHA-256 fingerprint detects inconsistencies but is **not authentication or an external trust boundary** | **High for adversarial agent sharing same privileges**: isolate authoritative contract/policy and signing authority from build/agent principal |
| Integrations | MCP exists; native adapters for every agent do not | **Medium for adoption**: verified adapter matrix, compatibility tests, fallback CLI |
| Language coverage | First-class parsing of Java/Kotlin, not TS/Python/Go | **Market limitation**: focus on JVM wedge before broad claims |
| Test evidence | Existing fresh JUnit evidence ties test outcomes to candidate, not to complete requirement correctness | **High for core positioning**: test relevance/oracle quality and negative controls |
| Maintainer governance | Observed `main.protected=false`; not a complete ruleset review | **Medium/high**: release/PR permissions and required checks review |

**Production-readiness definition matters:** Vericore can be made reliable for *supported local CLI/CI usage* before it is safe for arbitrary untrusted workloads or a hosted multi-tenant service. These are separate launch gates.

---

## 2. Review of the uploaded ideas: adopt, modify, reject

Reviewed artifacts:
- `ANTIGRAVITY_VERICORE_PROJECT_KNOWLEDGE(2).md` — preferred onboarding reference.
- `Chatgpt suggestion for my project(1).pdf` — Change Passport, independent evidence, test adequacy, benchmark, developer experience, runtime, agent compatibility, memory and execution security.
- `vericore_2_0_pitch_and_integration_blueprint(2).pdf` — adversarial invariants, Lincheck, Kotest, SQL AST, runtime interceptor and four-week roadmap.
- Earlier Gemini and ChatGPT knowledge files used as secondary context; repository is authoritative when documents disagree.

| Proposal from documents | Decision | Why / required correction |
|---|---|---|
| Make Change Passport the central product | **ADOPT, P0 thesis** | Already in `docs/PRODUCT_DIRECTION.md`; turn existing receipt into a coherent product, not a new unrelated subsystem |
| Requirements → independent evaluator → visible proof limit | **ADOPT, P1** | Highest value and a defensible reason for users to trust results |
| Fewer commands and agent-neutral setup | **ADOPT, P1** | Start with CLI/MCP common engine; verify specific adapters, don't claim automatic support for every IDE |
| Targeted mutation and property testing | **PILOT, P2** | Helpful evidence but computationally expensive; equivalent mutants and weak test oracles limit interpretation |
| Lincheck for JVM concurrency | **PILOT, P2** | Real framework for exploring interleavings; requires suitable operations, assertions and model/oracle; never claim exhaustiveness across full app |
| Parse SQL with JSQLParser to **prove** tenant isolation | **REJECT THE PROOF CLAIM** | AST can identify certain patterns and counterexamples, but cannot prove DB access control across ORM, triggers, dynamic SQL, stored procedures or app-context behavior; combine with DB policy and runtime tenant fixtures |
| Automatic verifier-generated tests from same agent | **MODIFY** | Same assumptions can produce false confidence. Independently frozen requirements/oracles, negative controls and separate trusted runner needed |
| “Information Wall” because contract sits in `.vericore` | **REJECT AS GUARANTEE** | If agent has same OS permissions it may alter contract/evidence; signature or external storage must use **separate trust principal**, not merely another path in the same user's home directory |
| HMAC key in `~/.vericore` as full solution | **RESEARCH / CONDITIONAL** | Helps only if builder cannot read or use that key. Prefer CI/OIDC trusted attestations or well-defined separate key authority; don't invent cryptographic guarantees |
| Runtime JDBC transaction firewall in 30 days | **DEFER / POTENTIALLY REJECT AS CORE** | High integration, data integrity, failure and maintenance risk. Start with opt-in non-mutating checks in isolated test environment; do not intercept production commits by default |
| “Mathematical runtime/concurrency proof” | **REJECT OVERCLAIM** | Finite exploration, fuzzing and example tests have bounded coverage; require documented assumptions and counterexample scope |
| Universal language support immediately | **DEFER** | Would trade trustworthy JVM results for broad, unvalidated behavior |
| Immutable, signed, portable evidence | **ADOPT STAGED** | First correct schema and candidate binding; then sign via a separate authority and use standards (in-toto/SLSA/Sigstore) where appropriate |
| New v2.0.0 release as week-four goal | **REPLACE** | v2.0.0 has already been published; future versioning requires its own compatibility/release decision |
| JDK 21+ baseline | **CORRECT** | Current main requires **Java 25+**; PDF's JDK 21 language is obsolete |
| Public benchmark and frank limitations | **ADOPT, P1/P2** | Improves credibility; must count **false rejection**, valid alternatives, uncertainty, cost and repeatability, not only caught defects |
| Vericore is the only independent/agent-neutral/run-the-code product | **REJECT EXCLUSIVITY** | Competitors now make overlapping claims; market position must be precise and evidence-backed |
| “No other product works on these future problems” | **UNVERIFIABLE** | We can identify under-served combinations and test user demand; cannot prove global absence of competitors |

**External validation highlights:**
- [JetBrains Lincheck docs](https://kotlinlang.org/docs/lincheck-guide.html): schedules/concurrency exploration with explicit test assertions.
- [PIT mutation testing limitations](https://pitest.org/quickstart/basic_concepts/): equivalent mutants and test interpretation issues.
- [JSQLParser docs](https://jsqlparser.github.io/JSqlParser/usage.html): explicitly warns AST-derived verdicts are syntactic, not semantic guarantees.
- [VerifierBench preprint](https://arxiv.org/abs/2610.28679): verifiers can reject valid alternatives; test both acceptance and rejection.
- [2026 software testing study](https://arxiv.org/abs/2609.09315): faults and deficient test oracles can evade common coverage/mutation criteria.

---

## 3. Market and competitor map — research snapshot 2026-11-07

**Interpretation rule:** vendors' published feature descriptions are claims about their own products, not independent performance validation. “Gap” below means opportunity for Vericore to test, **not** proof that the competitor cannot do it.

| Product / category | Publicly documented strength | Overlap / threat to Vericore | Credible opportunity for Vericore to test |
|---|---|---|---|
| [GitHub Spec Kit](https://github.github.com/spec-kit/) | Structured spec → plan → tasks → implementation/convergence, agent integrations and extensible quality gates | Simplicity and agent-agnostic workflow are **not unique** | Complement specs with independently executed, candidate-bound evidence; ship Spec Kit interoperability |
| [Greptile / TREX](https://www.greptile.com/trex) | Sandbox runs, UI/API interactions, logs, traces, screenshots, runtime findings | “We execute code” and “we show evidence” are **not unique** | Open, local-first, verifiable requirement-by-requirement receipts and freshness tracking |
| [Greptile Independence](https://www.greptile.com/independence) | Explicit agent-agnostic independent validation with MCP workflow | Agent neutrality cannot be sole moat | Demonstrate stronger machine-checkable *claim-to-evidence contracts* and offline verification |
| [CodeRabbit](https://www.coderabbit.ai/) | PR review, triage, prioritization, agent feedback loops and post-merge actions | Crowded code-review/PR-triage arena | Be a complementary objective evidence gate, not another comment bot |
| [Cursor Bugbot](https://cursor.com/docs/bugbot) | PR review, incremental feedback, rules and autofix; CLI/agent use | Instant AI review is a commodity | Require trustworthy frozen criteria and independent evidence rather than review opinions alone |
| [Qodo](https://www.qodo.ai/solutions/testing/) | Test generation, coverage gap analysis, regression testing, PR review, multi-repo context | Test gap detection and AI-generated tests have competitors | Measure **test effectiveness** relative to pre-agreed obligations and independent negative controls |
| [SonarQube AI Code Assurance](https://www.sonarsource.com/solutions/ai/ai-code-assurance/) | Quality/security analysis, AI-code quality gates and governance | Static quality and policy gates are mature categories | Focus on the intent-to-candidate-to-evidence chain rather than competing as another SAST engine |
| [Kiro](https://kiro.dev/faq/) | Specs plus IDE/CLI/Web, correctness workflows, cloud sandboxes, parallel agents | Not limited to a proprietary IDE as the PDF implied; spec+verification is competitive | Independent verifier that works with Kiro *and* other coding environments |
| [Sigil](https://fielding.github.io/sigil/) | Intent graph, architecture constraints, CI gates, drift detection | “Review intent not diffs” is **already an adjacent product** | Runtime/check provenance and claim-limited Change Passport with cross-change evidence freshness |
| [Antithesis](https://antithesis.com/docs/resources/deterministic_simulation_testing/) | Advanced deterministic simulation and replay of distributed-system failures | Deep adversarial runtime testing is not unclaimed territory | Integrate results from specialized tools instead of attempting to replace advanced simulation stacks |
| [GitHub attestations / SLSA](https://docs.github.com/en/actions/concepts/security/artifact-attestations) | Verifiable build provenance and supply-chain integrity | Signing/provenance standards exist | Include behavioral obligation evidence *alongside*, not instead of, established provenance formats |

### 3.1 What this research actually establishes

- **Overcrowded:** AI PR comments, generic “review with another model,” agent-agnostic messaging, spec-to-code workflows, static security gates, and runtime browser/API testing.
- **Potential differentiated combination, still unproven:** portable **requirement-level verification receipts**, explicit strength/limits of each evaluator, independently controlled evidence provenance, and **continuing evidence invalidation after later changes**.
- **Not a defensible claim:** “Nobody works on future verification,” “Vericore uniquely runs code,” “agents cannot tamper with local contracts,” or “all Java/Kotlin concurrency safety can be proven automatically.”
- **Competitive approach:** integrate mature linters/SAST/mutation/concurrency/simulation providers instead of rebuilding them. Compete on *coordination, correctness of evidence claims, trust boundary, ease of adoption and interoperability*.

### 3.2 Evidence of a real user problem, with caveats

- The [2025 Stack Overflow Developer Survey](https://survey.stackoverflow.co/2025/) reports **46%** of respondents distrust the accuracy of AI tools versus **33%** who trust it, and **66%** cite “almost right” AI solutions as a frustration. This indicates plausible demand, not demand specifically for Vericore.
- [DORA 2025](https://dora.dev/research/2025/dora-report/) finds AI amplifies existing organizational capabilities; workflow quality and strong platforms matter more than a standalone model.
- Informal Reddit discussions describe review fatigue and passing tests that fail to reflect user flows: [review bottleneck](https://www.reddit.com/r/softwareengineer/comments/1weh83t/has_code_review_become_the_whole_job_for_anyone/), [unclear independent verification](https://www.reddit.com/r/ClaudeAI/comments/1ws8tv3/the_code_review_gap_i_see_in_the_aiagent_era/), [developer concern about agent self-testing](https://www.reddit.com/r/ClaudeAI/comments/1xdfd8w/when_can_we_trust_claude_ai_for_coding_and_how/). **Anecdotes are not representative statistics.**
- Therefore run product interviews and actual onboarding studies; don't treat either surveys or social posts as product-market fit.

---

## 4. Positioning, users and defensible wedge

### 4.1 Positioning statement

**For developers and teams who use AI coding agents to change software, Vericore is an independent, local-first change assurance layer that compares a candidate against a pre-agreed contract and produces claim-limited evidence. Unlike a generic AI PR reviewer, it explicitly records what was checked, how, against which exact state, and which obligations still need human review.**

Marketing one-liner: **“Don't trust a green check without its evidence.”**

Friendly summary: **“AI wrote it. What actually verifies it?”**

**Avoid:** “mathematically proves every AI change,” “bug-free,” “only verification platform,” “guaranteed zero bugs,” or “works with every IDE today.”

### 4.2 Primary customer and job-to-be-done

**Initial persona:** Java/Kotlin lead/developer using Claude Code, Codex, Gemini/Antigravity, Cursor or Copilot to modify a JVM repository; responsible for safely merging changes.

Their job: *“In minutes, tell me whether the agent stayed within agreed scope, what behavior was tested, which tests are meaningful, and what still requires judgment — with a record I can use in CI and future reviews.”*

**Secondary:** DevEx/platform lead who wants standardized local/CI evidence and policy without mandating a particular agent or editor.

**Later, not MVP:** enterprise compliance manager, hosted multi-tenant customer, cross-repository agent fleet manager.

### 4.3 Unique product mechanism, not just a slogan

**Claim–Evidence Graph** (proposed):

```
Intent / criteria / protected invariants
           |
           v
 Frozen typed obligations [ID, revision, owner, policy, evaluator expectation]
           |
           v
 Candidate state [commit + source/state digest]
           |
           v
 Independent checks [type, version, command, environment, artifacts, limits]
           |
           v
 Result per claim [PASS-BOUNDED | FAIL | NEEDS-REVIEW | NOT-EVALUATED | STALE]
           |
           v
 Open Change Passport + evidence debt + freshness dependencies
```

**Value proposition:** Vericore is not valuable because it shows lots of checks; it is valuable when it prevents **incorrect confidence** while keeping developer effort low.

### 4.4 Evidence Debt: a hypothesis worth testing

Evidence Debt is **not** a global “trust score.” It is a ledger of important contract obligations that have no appropriate current check, rely on a weak oracle, or lost validity after changes. Each entry has a reason, responsible owner (when known), severity policy, related evidence and recommended review action. Definitions are needed so it does not become a noisy second backlog.

Test: In 10 pilot repositories, do developers resolve more meaningful gaps and make better merge decisions with an evidence-debt view than with a PASS/FAIL summary alone? **Stop the feature** if the result is mostly noise or no one takes action.

---

## 5. Developer experience: reduce visible complexity without losing capability

### 5.1 Target happy path (PROPOSED syntax; not today's confirmed commands)

```bash
# One-time setup, from any terminal inside a Git repo
vericore init --agent auto

# State the intent; optionally point to a spec or let Vericore suggest scope for approval
vericore prepare "Add payment validation; keep authentication behavior unchanged"

# Agent makes the code change in the IDE/terminal of choice

# One check that returns a concise explanation and keeps machine-readable artifacts
vericore verify

# Optional: inspect the last receipt, including gaps and evidence
vericore show
```

**Important:** Current verified workflow supports `init`, `prepare`, `verify` and `mcp-config`. `--agent auto`, automatic scope approval, and `show` in this sketch are proposals; build only after CLI/compatibility design approval. Current `prepare` can require explicit `--planned-path` depending on intended boundaries. No speculative command should appear in installation docs as shipped.

### 5.2 Public command budget

Only **four prominently taught commands**: `init`, `prepare`, `verify`, `show` (last one proposed). Continue to expose current specialized commands in an **Advanced** reference, preserving scripts and MCP clients. Do **not** delete 27 existing command registrations to achieve a marketing number.

- `init`: detect supported repo, explain supported languages, configure optional agent integrations, privacy and trust mode; no surprise writes to unrelated IDE configs; preview before modification.
- `prepare`: conversational/CLI intent to structured criteria, with explicit scope and verification assumptions for developer approval; freeze baseline **before** agent modification.
- `verify`: invoke conservative verification policy, show short status and per-claim explanation; surface missing evaluator, stale report and unsupported language clearly.
- `show`: human receipt with exact inputs/outputs; JSON and HTML export as options. In CI, use existing JSON modes and action integration.

### 5.3 Installation and setup acceptance criteria

- Time-to-first-*meaningful* verification: provisional pilot target **p50 < 5 minutes, p90 < 10 minutes** for supported Java/Kotlin repos with working build tools; record failures and distinguish environmental failures.
- No AI API key required for deterministic mode.
- Installers and archives remain checksum-verified; use the published binaries, not a mandatory toolchain rebuild.
- First-run messages accurately detect unsupported Java version, Git state, permission boundary and repository language.
- One-command MCP setup for **tested** adapters; include a generic `vericore` CLI fallback, no vendor dependence.
- Default command output: 5–10-line useful summary with **next action**, not unreadable JSON; machine-readable output remains stable.
- For novice pilot: at least 80% successfully complete a supported task without reading full documentation (hypothesis/target; measure, don't assert).
- No interactive prompt in CI; noninteractive flags and exit codes documented; no unintended writes to developer repo until user opts in.

### 5.4 Agent compatibility matrix (tests before badges)

| Environment | Initial integration | Acceptance gate |
|---|---|---|
| Terminal / any editor | Existing CLI | Works without agent integrations or network for deterministic checks |
| Generic MCP client | Existing stdio MCP | Protocol conformance, tool-schema tests, readable errors, trusted local safety |
| Claude Code | MCP config; optional documented lifecycle hooks | Real prepare+verify E2E, permission/rejection semantics verified, no assumed fail-closed hook |
| Cursor | MCP and optional repo rules | Tested on current supported version; conflict-free setup and clear fallbacks |
| Codex | MCP or CLI plus documented instruction file | E2E; no assumption native hooks exist without verifying current API |
| Gemini CLI / Antigravity | MCP, CLI and supported configuration | Actual Antigravity integration test with user-approved environment |
| GitHub Copilot | CLI/CI/GitHub Action; MCP where supported | Tested repo workflow and action status visibility |
| Spec Kit | Optional process extension / interoperable spec import | Import a spec, produce no misleading claim of semantic proof, preserve existing Spec Kit files |

Start with **2 or 3** actively maintained adapters, expand from real usage; do not build dozens of independent integrations. All adapters invoke one canonical engine.

### 5.5 User-visible result design

```
VERICORE — REVIEW REQUIRED
Candidate: <commit-or-worktree-digest>  Contract: <fingerprint>
Scope: PASS — no unplanned paths detected
Build/Test: PASS — named checks completed (limited claim)
Requirements: 2 evaluated, 1 failed, 2 not evaluated
Evidence: candidate-bound; no independent behavioral oracle for criterion 004
Next action: review criterion 004 or attach an appropriate test
Receipt: output/verification-receipt.json
```

Do not use words such as **PROVEN** without stating which narrow formal property was proven and under what assumptions. The output should make **uncertainty as visible as success**.

---

## 6. Verification trust model: the most important technical design

### 6.1 Define threat actors and what we claim to resist

1. **Accidental agent scope drift**: modified unplanned files or stale contexts; existing manifest/contract can help.
2. **Unreliable agent self-reporting**: “tests passed” without actual execution; independent runner/receipt reduces reliance on claims.
3. **Malicious or buggy repository build logic**: a build script can execute arbitrary code under local user identity; restricted command syntax does not isolate it.
4. **Agent with same filesystem credentials as Vericore**: may alter on-disk contract, report, keys or build output; a local hash cannot constitute an independent authorization boundary.
5. **Compromised runner and fake evidence**: signatures issued *within* a compromised runner may authenticate falsified claims; independent protected builder and attestation identity matter.
6. **Incorrect evaluator assumptions / weak oracles**: even genuine test execution may not establish a requirement.
7. **A later change invalidating earlier proof**: candidate-bound evidence must be marked stale under an explicit dependency/freshness policy.

### 6.2 Architectural requirements

- **Contract source of authority:** distinguish developer-authored intent from agent-editable candidate files. Local mode can be advisory; protected CI mode requires separate OS/CI trust principal or immutable off-repo authority for frozen contracts and policies.
- **Default safety:** no host-execution of untrusted build scripts without explicit warning/consent; if a sandbox is not active, label mode `trusted-local`.
- **Runner abstraction (`ExecutionProvider`)** with `trusted-local` and `isolated` implementations; environment manifest and limits. Do not add remote shared-server support until the security model is reviewed.
- **Secrets:** least privilege, sealed/allowlisted environment, no default network egress for isolated untrusted jobs, redact evidence logs, tests for leak cases.
- **Isolation:** explicit filesystem/network, resource/CPU/memory/time, subprocess-tree cleanup, access to code checkout vs contract authority, cross-platform behavior. OCI containers are not equivalent to a universal security guarantee; document supported threat model and escape risk.
- **Outputs:** capture stdout/stderr with size limits and deterministic truncation, hashes, command exit status, timeout, execution identity and environment provenance.
- **Tamper evidence:** distinguish content-hash consistency, HMAC with protected key, digital signatures with separate authority, and supply-chain attestations.
- **Versioning:** preserve old schema readers and define a migration/deprecation policy; never silently reinterpret old `PASS` receipts with new semantics.
- **No silent verifier failure:** missing check is `NOT_EVALUATED`/`REVIEW_REQUIRED`; unexpected execution/infra failure is not success.

### 6.3 Criterion and verdict semantics — PROPOSED

Define the **internal typed status** independently from the existing CLI's backwards-compatible top-level statuses:

| Status | Meaning | Merge / UX interpretation |
|---|---|---|
| `EVALUATED_PASS` | Named evaluator accepted a clearly scoped criterion on matching candidate state, under recorded assumptions | Not “whole feature proven”; inspect scope and strength |
| `EVALUATED_FAIL` | Named evaluator found evidence the specified criterion failed | Fail policy gate when required |
| `NOT_EVALUATED` | No suitable evaluator, unsupported language/criterion, or check deliberately skipped | Require review or configured exception |
| `INCONCLUSIVE` | Evaluator executed but cannot decide within its scope, insufficient oracle/coverage or ambiguous evidence | Require review or additional checks |
| `STALE` | Evidence is no longer bound to current candidate/dependencies/environment per policy | Re-run affected checks; never reuse as current PASS |
| `ERROR` | Verification infrastructure or runner failed | Do not interpret as candidate correctness |

Map to existing `PASS`, `FAIL`, `REVIEW_REQUIRED` in a backwards-compatible aggregator, with a clear statement of **which required obligations gate merging**. Never let an empty criterion set accidentally imply full behavioral correctness. Coverage counts count checks, not proofs.

### 6.4 Evidence quality contract

Each evaluation should record:

- `criterionId`, specification text/hash, author/source, frozen revision, required/optional and risk category.
- Candidate identity: repo, base commit, head (if available), relevant working-tree digest and checked path set.
- Evidence IDs and type: test, mutation, API/schema, architecture, security, static metric, manual decision, etc.
- Evaluator identity: implementation/version/inputs, command, configuration and capability descriptor.
- Execution identity: start/end, protected runner identity/mode, sandbox status, network access, toolchain and environment fingerprint.
- Result, unsupported conditions, assumptions, missing coverage, counterexamples, logs/artifact hashes and retention policy.
- Freshness dependencies and invalidation reasons; no timestamps alone as proof of freshness.
- Human review / exception with identity and reason when supported; exceptions must not rewrite raw evaluator results.

### 6.5 Independent oracle principle

Freeze the **expected behavior and negative examples** before or outside the agent's implementation context. An agent may *suggest* tests, but a test generated from its own incorrect implementation should not be labeled independent evidence without additional checks. Sources of independence include pre-existing externally authored tests, reference implementation, independently defined invariants, human-reviewed fixtures, an external benchmark or property oracle. Different LLM vendor ≠ automatic independent correctness.

---

## 7. Proposed technical architecture, extending the current code

Keep the proven Kotlin core and interfaces. Add capabilities behind stable contracts instead of a rewrite.

```
CLI / MCP / GitHub Action / future editor adapters
                        |
             Unified Vericore Workflow
  init / prepare / verify / show; old commands retained
                        |
              Contract & Policy Engine
    Frozen intent, scope, baseline, obligations, provenance
                        |
        Candidate Identity + Evidence Graph
                        |
      Evaluator Registry / Capability Descriptors
  | repo | build/test | API | policy | security | ... |
                        |
       ExecutionProvider / Trust Boundary
    trusted-local (explicit) | isolated (future)
                        |
           Claim–Evidence Evaluations
                        |
  Change Passport + Evidence Debt + Freshness Index
                        |
           Open Schema / Export / CI Gate
```

### 7.1 Potential modules (PROPOSED, names illustrative)

- `core/assurance/`: typed obligations, evaluator capability model, result aggregation, strength semantics, policy profiles.
- `core/verification/`: `Evaluator` interface, registry, narrow adapters wrapping existing checks; avoid transplanting all old code.
- `core/execution/`: `ExecutionProvider`, sandbox/trust-profile metadata and bounded output.
- `core/passport/`: passport builder, versioned reader, canonical serialization, integrity validation, source-candidate binding and export.
- `core/freshness/`: dependency/impact-based evidence invalidation, `STALE` reasons, sensitivity and precision.
- `integrations/`: thin adapter/config layers only when necessary; keep MCP as the common programmatic boundary.

**Implementation rule:** the actual package naming, whether new packages are needed, and dependency feasibility must be decided by Antigravity after a focused architecture design review. These are logical components, not a request to create folders immediately.

### 7.2 Evaluator interface — conceptual contract

```
EvaluatorDescriptor:
  id, version, supportedLanguages, supportedClaims
  requiredInputs, requiredPermissions, resourceBudget
  deterministicOrProbabilistic, oracleSource, limitations

EvaluationInput:
  frozenCriterion, candidateIdentity, evidenceInputs
  policy, trustedExecutionContext

EvaluationResult:
  status, limitedClaim, evidenceArtifacts, coverageLimits
  confidenceLabel [not a statistical probability unless calibrated]
  candidateBinding, executorIdentity, replayInstructions
```

Design principles:
- Distinguish **evidence produced** from **claim established**.
- Untested evaluator types must return unsupported/inconclusive, not `PASS`.
- Versioned schema, safe plugin discovery and no arbitrary plugin execution without explicit trust policy.
- Results should be canonicalized and stable where deterministic; record nondeterministic seeds and reproduction instructions where applicable.
- A failing evaluator should fail only its intended criterion/policy; avoid hiding broader errors in generic `PASS` output.

### 7.3 Evaluator sequencing

1. Wrap existing repository/contract/architecture and fresh-JUnit evaluators without regression.
2. Add explicit `API schema compatibility` and `test-to-changed-code relevance` *only with measurable precision*.
3. Pilot targeted mutation testing in an isolated test fixture, with equivalent-mutant classification and bounded time budget; do not assert all surviving mutants are bugs.
4. Pilot property-based tests for selected pure/domain logic with independent properties and counterexample shrinking.
5. Pilot Lincheck for **well-defined concurrent classes** with known operations and reference semantics; distinguish `test found no issue` from `proven linearizable`.
6. SQL guard as a **heuristic/static check** for supported query shapes, returning `UNKNOWN` for dynamic/ORM/opaque cases; add DB row-level security and behavioral tenant fixtures where relevant.
7. Later, integrate specialized fuzzing, SAST, schema and runtime providers as **complementary external evidence** rather than rewriting each engine.

### 7.4 Evidence freshness and impact model

- Every evidence record declares dependency keys: code paths or symbols, build files, test files, tool versions, relevant configuration, external fixture assumptions and declared external services.
- At candidate change, invalidate if any known dependency changes; unknown dependencies lead to conservative `REVIEW_REQUIRED`, not fabricated freshness.
- Begin with **file-level precision** using existing `GitChangeSetBuilder`, `ChangeImpactEngine`, `EngineeringContext` and candidate digests; assess false invalidations before symbol-level or cross-repo improvements.
- Add cheap `is-passport-current?` checks in local CLI and CI, with no requirement to re-run expensive tests merely to discover the receipt is stale.
- History of evaluations must preserve earlier truth: prior valid evidence becomes `STALE`, not retroactively false.
- Cross-repository invalidation is a later explicit federation protocol, not an assumption from Git commit hashes alone.

### 7.5 Portable Change Passport design

First milestone: **versioned JSON with a stable schema**; a plain-language/HTML interpretation; external artifact hashes; exact candidate binding; explicit evaluator claim and limitations; backwards-compatible readers.

Later: optional **in-toto/SLSA-compatible statement and Sigstore/GitHub attestation integration** with independent signing authorities. Signing means a trusted identity made a claim about an artifact; it does **not** prove the claims are semantically correct. Read [GitHub's attestation warning](https://docs.github.com/en/actions/concepts/security/artifact-attestations) and [SLSA provenance](https://github.com/slsa-framework/slsa/blob/main/spec/build-provenance.md).

For enduring compatibility, preserve portable artifacts and simple documented parsing rules so verification history can be audited even if Vericore, a model provider or an IDE disappears.

---
## 8. Execution roadmap and release gates

**Roadmap is ordered by dependency and evidence, not by an arbitrary marketing version number.** Duration ranges below are rough planning envelopes for a focused engineering team, **not a commitment by ChatGPT or a claim of work underway**. A phase exits only when its gate passes; if the gate fails, do not skip ahead.

### Wave 0 — Truth and production baseline (P0; initial engineering gate)

**Goal:** Know exactly what the current v2.0.0 line guarantees and where it does not. Map current contract/receipt semantics and establish a stable, safe foundation.

**Deliverables:**
- [ ] Pin intended working SHA and produce a living `CURRENT_CAPABILITIES.md` based on code + tests, not marketing.
- [ ] Verify clean JDK 25/Gradle builds, actual versioned release binaries, installers and supported OS matrix; capture reproducible commands and CI artifact links.
- [ ] Review `docs/IMPLEMENTATION_STATUS.md` release discrepancy and ensure docs checks compare the actual published release when appropriate.
- [ ] Write threat model covering local trusted development, untrusted PR/agent, shared REST, MCP and CI; document which configurations are unsupported.
- [ ] Review default REST allowed roots (including temp), localhost enforcement, report access, symlink/path traversal, credential/log exposure and resource controls. Avoid exposing REST to untrusted networks.
- [ ] Audit contract fingerprints, baseline exceptions, ignored/untracked paths, symlinks, TOCTOU, `HEAD` changes and JUnit report freshness; add regression tests for actual discovered gaps.
- [ ] Check repository branch rulesets/protections, mandatory checks, release/tag immutability, OIDC/signing policies and audit retention.
- [ ] Establish reproducible benchmark harness baseline: known-correct, known-wrong, valid alternatives, tampered and stale cases.
- [ ] Run controlled onboarding tests with developers outside the project; record friction instead of assuming CLI is easy.

**Key files to inspect first:** `Main.kt`, `VericoreServer.kt`, `McpProtocol.kt`, `AgentChangeContract.kt`, `CandidateStateSnapshotter.kt`, `EngineeringPreparation.kt`, `EngineeringVerification.kt`, `VerificationCommandExecutor.kt`, `ChangeSpecificationEvaluator.kt`, `VerificationReceipt.kt`, `build.gradle.kts`, `docs/IMPLEMENTATION_STATUS.md`, `.github/workflows/*`.

**Exit gate:** Release candidate reproducibly builds on supported platforms; relevant tamper/stale/negative fixtures never yield falsely successful verification; exact known limitations published; no untriaged high-risk issue in the supported local/CI trust boundary. Report **what was not tested**. CI-success alone is not the gate.

**Do not:** Rewrite the parser, ship a hosted REST API, add automatic repair, change schema semantics or publish a new release as a substitute for this review.

### Wave 1 — The easiest change verifier for JVM developers (P1)

**Goal:** Make the existing product useful to a newcomer in minutes, with no hallucinated claims or verbose command maze.

**Deliverables:**
- [ ] Validate and streamline the `init`, `prepare`, `verify` flow; consider a proposed `show` alias/view only if users need it.
- [ ] Build a single happy-path README with one 90-second screen recording and real copy-paste transcript produced by a released binary.
- [ ] Add non-breaking `init --agent` setup only for verified integrations; print a clear preview and require consent before writing IDE configuration.
- [ ] Introduce guidance for “no Java/Kotlin source found,” dirty working tree, missing build tool, stale contract, unsupported criterion, and no network.
- [ ] Preserve detailed existing commands under Advanced, including `mcp-config`; avoid removing or breaking current API/CLI.
- [ ] Distinguish `trusted-local` from any genuinely isolated runner in `doctor` / configuration / output.
- [ ] Produce concise terminal, machine-JSON and CI summary views from one canonical result.
- [ ] Document supported and unsupported combinations in a continuously tested adapter matrix.

**Exit gate:** Conduct at least 10 recorded usability sessions; p50/p90 first verification measured, no user silently mistakes `PASS` for semantic correctness; current CLI/MCP/Action compatibility regression suite green; agent setup does not overwrite config without approval.

**Stop criteria:** If most friction is installation or ambiguous errors, fix those first; do not add new UI surfaces to conceal them.

### Wave 2 — Evidence Coverage Graph and honest verdicts (P1; product differentiation)

**Goal:** Transform a contract and receipt into a reviewable mapping from intent to appropriately scoped checks.

**Deliverables:**
- [ ] Versioned `criterionId`, normalized obligation categories and policy obligation levels; stable migrations for current contract schemas.
- [ ] Evidence type descriptors with scope/limits and explicit mismatch handling.
- [ ] Human view showing every criterion, its evaluator, evidence link, coverage caveat, and `NOT_EVALUATED`/`INCONCLUSIVE` reason.
- [ ] Configurable merge policy that does not treat unsupported required criteria as passed, but permits explicit reviewed exceptions with rationale.
- [ ] Support non-AI, human-supplied criteria and test bindings; don't require AI to generate acceptance criteria.
- [ ] Define and test conservative aggregation into the existing `PASS`, `FAIL`, `REVIEW_REQUIRED` API contracts.
- [ ] Introduce **candidate-staleness checks**, beginning with existing manifest and analyzed source digest.
- [ ] Keep evidence and evaluation explanations readable in command output and portable artifacts.

**Exit gate:** A benchmark of known mismatches, stale/missing evidence, valid alternative implementations, mixed passing/failing claims, and unsupported free text yields the correct scoped outcomes. Independent reviewers can explain the difference between overall status and per-criterion evidence without assistance.

### Wave 3 — Independent execution boundary (P1 before adversarial claims)

**Goal:** Define real separation between code generation and verification execution.

**Deliverables:**
- [ ] Threat-model-approved `ExecutionProvider` design: trusted local vs isolated execution, with explicit mode identity in receipt.
- [ ] Default use on existing trusted-local repos remains compatible but clearly warns about host execution risk.
- [ ] Protected contract and policy authority for CI/isolated mode; no builder access to verifier signing authority.
- [ ] Isolated job input checkout, no default secret inheritance, explicit network policy, resource/time limits, log truncation/redaction, process cleanup and safe artifact return.
- [ ] Windows/Linux/macOS support policy: document where isolation is actually enforceable; no imaginary cross-platform guarantee.
- [ ] Adversarial test fixtures for attempts to modify contracts, evidence files, verification outputs or environment; validate detection and runner containment where supported.
- [ ] Add independent runner identity / command provenance in receipt.

**Exit gate:** Security review with a concrete attack/defense matrix; known hostile fixture suite cannot silently replace authoritative inputs in the supported isolated mode; out-of-scope environments explicitly rejected or marked trusted-local. If genuine isolation is not achievable on a platform, don't call it sandboxed.

### Wave 4 — Targeted test effectiveness, NOT universal theorem proving (P2; hypothesis-driven)

**Goal:** Verify that the evidence is relevant and capable of finding important known fault classes.

**Independent workstreams with separate gates:**

**4A. Test relevance**
- [ ] Determine what changed code relevant tests actually execute, initially on Gradle/JUnit Java/Kotlin fixtures.
- [ ] Differentiate a test that executes changed code from one that asserts correct behavior.
- [ ] Publish false-match and missed-match rates on curated fixtures.

**4B. Targeted mutation**
- [ ] Pilot on critical changed methods only, with explicit budget and runner isolation.
- [ ] Report killed/survived/unexecuted/timeouts/equivalent-or-unknown mutants; do not equate survival with a bug.
- [ ] Measure incremental value over conventional targeted tests and runtime cost; abandon if marginal or too noisy.

**4C. Property-based/domain invariants**
- [ ] Start with a few human-reviewed invariants in pure JVM functions or controlled repository fixtures.
- [ ] Preserve seeds, generator versions, independent oracle rationale and shrunk counterexamples.
- [ ] Return `INCONCLUSIVE` when inputs, oracles or environments cannot establish the claim.

**4D. Concurrency (Lincheck)**
- [ ] Pilot on 2–3 small structures with reference sequential spec or explicit assertions.
- [ ] Capture interleaving traces and resource budget; label tested schedules and limitations.
- [ ] Use repeated correct/broken implementations and identify false positives and silent misses.

**4E. Tenant isolation (SQL)**
- [ ] Treat JSQLParser AST scan as a *pattern check* on supported SQL, not proof of tenant isolation.
- [ ] Explicit unknown paths for dynamic SQL, ORM query generation, stored procedures/triggers and unsupported dialects.
- [ ] Test integration against a controlled database with at least two isolated tenants, adversarial queries and row-level security where available.
- [ ] Never enforce automatic production JDBC rollbacks from an immature scanner.

**Exit gate:** Each pilot independently demonstrates significant caught failures not covered by baseline, acceptable false rejections, bounded median/p95 latency, readable counterexamples and truthful documentation. If a pilot fails the gate, keep it experimental or remove it.

### Wave 5 — Portable Change Passport and external trust (P2)

**Goal:** Make evidence independently consumable, long-lived and suitable for CI policy.

**Deliverables:**
- [ ] Stable versioned JSON Passport, JSON Schema, schema golden fixtures and backwards-compatible parser.
- [ ] Exact candidate, contract, evaluator, environment, claim, evidence hashes and evidence freshness fields.
- [ ] Human-readable local HTML/text report and optional CI PR summary; one machine-readable artifact is authoritative.
- [ ] Standalone `passport verify` / equivalent **offline validation** capability only after it is designed, benchmarked and documented; avoid extra mandatory public commands.
- [ ] Optional in-toto statement output, Sigstore/GitHub Actions signing in trusted builder; clear split between authenticity/integrity and behavioral correctness.
- [ ] Compatibility evaluation with SLSA provenance without repurposing SLSA predicate to claim that software is bug-free.
- [ ] Artifact size/privacy budgets, redaction policy, retention and expiry/invalidation semantics.

**Exit gate:** Third party can validate artifact structure, hash integrity, candidate association and signature (when enabled) offline from documented test vectors; old fixture readers remain compatible; evaluator semantics are independently auditable.

### Wave 6 — Ongoing evidence validity, memory and multi-agent conflicts (P2/P3)

**Goal:** Make a verification record useful **after** the next agent change and across long-lived systems.

**Deliverables:**
- [ ] Evidence freshness dependency graph initially file-level, with measured false-invalidations; source/historical knowledge retains provenance and owner.
- [ ] `STALE` as a **derived view** on historical evidence, not rewrite of a signed historical result.
- [ ] Evidence Debt pilot: high-priority unverified/stale obligations, owner, remediation and accepted exceptions.
- [ ] Multiple prepared contracts in same repo: explicit overlap detection, conflicts, shared baselines, and merge ordering without assuming concurrent writes are safe.
- [ ] Cross-repo contract federation **only after** single-repo accuracy; maintain independent repository identity and permission scopes.
- [ ] Human decision log with expiry, review rationale, audit trail and exception policy.

**Exit gate:** Known interleaving and dependency-update fixtures invalidate exactly the expected claims, with measured false-positive rates. At least 5 pilot maintainers find Evidence Debt actionable; otherwise revisit product UI and definition.

### Wave 7 — Expand languages, distribution and ecosystem (P3; demand-led)

- [ ] First improve Kotlin dependency precision on realistic source examples; compare compiler PSI, Kotlin analysis APIs, tree-sitter and maintenance/footprint costs. No parser replacement until benchmark shows benefit and compatibility.
- [ ] Define language-neutral contracts and evidence adapters distinct from deep source parsing.
- [ ] Add a second language **only when pilot demand and trustworthy verifier support justify it**. Candidates: TypeScript or Python; choose based on real users and tool compatibility, not personal preference.
- [ ] More CI providers, IDE extensions, dashboard or hosted workflows only when a repeated use case demonstrates the need.
- [ ] Cross-repository/organization governance as an interoperable layer; avoid turning local-first core into a mandatory hosted service.
- [ ] Consider paid managed evidence governance only when security, audit, privacy and support capacity meet the needs of paying customers.

**Exit gate:** Each new surface has named customer demand, compatible schemas, documented threat model, reliable installation and a maintained end-to-end test. A broad but unreliable language/IDE matrix is a product failure.

### Relationship to Vericore's existing Phase 0–9 product direction

| Existing roadmap | Execution waves above | Principle |
|---|---|---|
| 0: Foundation Freeze & Truth | Wave 0 ongoing regression / release truth | Maintain prior foundation and close new evidence gaps |
| 1: The 60-Second Vericore | Wave 1 | Minimize developer effort |
| 2: Universal Agent Layer | Wave 1 and later | Thin tested integrations; one engine |
| 3: Change Contract 2.0 | Wave 2 | Explicit typed obligations, compatibility |
| 4: Independent Verification Engine | Waves 3–4 | Trust boundary and bounded evaluators |
| 5: Verification Receipt & Provenance | Wave 5 | Open Passport and independent provenance |
| 6: Multi-Agent & Cross-Repository | Wave 6 | Evidence invalidation and conflict detection |
| 7: Explain + Verified Project Memory | Waves 2 and 6 | Provenance and freshness for decisions |
| 8: Distribution, Ecosystem & Benchmark | Throughout, with Wave 7 expansions | Publish reproducible evidence and smooth setup |
| 9: Enterprise Change Assurance | After Waves 3/5/6 | Secure trust principals and real customer demand |

---

## 9. Exact Antigravity backlog — PR-sized tasks

The following is **suggested issue/PR order**, not permission to edit now. Each task should start with design/repository verification before any implementation.

| ID | Priority | Bounded deliverable | Source modules / test references | Definition of done |
|---|---|---|---|---|
| VC-001 | P0 | Current release and capability evidence audit | `README.md`, `docs/IMPLEMENTATION_STATUS.md`, release workflows | Supported matrix, reproduced v2.0.0 status, reconciled docs, no unverified claims |
| VC-002 | P0 | Trust/threat model and local-server boundary review | `VericoreServer.kt`, `ServerCommand.kt`, path-security tests | Auth/paths/loopback threat analysis and targeted tests; no shared deployment claim |
| VC-003 | P0 | Candidate/contract adversarial regression expansion | `AgentChangeContract.kt`, `CandidateStateSnapshotter.kt`, `EngineeringVerification.kt` | Missing/stale/tampered/unplanned fixtures fail or require review as specified |
| VC-004 | P1 | First-run UX and error taxonomy | `Main.kt`, `InitCommand.kt`, `DoctorCommand.kt`, `PrepareCommand.kt`, `VerifyCommand.kt` | Novice journey timed; stable machine output; old commands unchanged |
| VC-005 | P1 | Two tested agent integrations + generic fallback | `McpProtocol.kt`, `McpConfigCommand.kt`, adapters | Real E2E fixtures, documented compatible versions, no installer overwrite |
| VC-006 | P1 | Criterion identity and evidence descriptor schema | `ChangeSpecification.kt`, `ChangeSpecificationEvaluator.kt` | Versioned fixtures, bound claims and limits; old schema compatibility |
| VC-007 | P1 | Claim–Evidence Graph and readable receipt view | `EngineeringVerification.kt`, `VerificationReceipt.kt`, evidence modules | Every criterion visible including unsupported, with reproducible counterexamples |
| VC-008 | P1 | ExecutionProvider security design and first isolated prototype | `VerificationCommandExecutor.kt`, CI config | Threat review, protected input boundary, resource limits, runnable negative fixtures |
| VC-009 | P2 | Test-to-changed-code evidence pilot | JUnit collector/parser, change-impact modules | Correct attribution + measured false matches on curated fixtures |
| VC-010 | P2 | Bounded mutation/property experiment | Evaluator registry proposal; Kotest/PIT sandbox fixtures | Honest effectiveness/latency report; go/no-go decision |
| VC-011 | P2 | Lincheck + SQL tenant safety experiments (separate PRs) | New opt-in evaluators, never production firewall | Explicit oracles, limits, counterexamples; no blanket proof claims |
| VC-012 | P2 | Portable Passport format with hash checks | Receipt/export schema and verifier | Offline-readable fixtures, versioned reader, no invented signing guarantee |
| VC-013 | P2 | Signed trusted-runner attestation pilot | GitHub Action/release/security workflows | Independent issuer identity, verified signature, documented semantic limits |
| VC-014 | P2 | Evidence freshness and invalidation pilot | `EngineeringContext`, Git analysis, `ChangeImpactEngine` | Known dependency change invalidates evidence accurately |
| VC-015 | P2 | Public assurance benchmark + comparison scripts | `benchmark/*`, `scripts/audit/*` | False accept/reject/inconclusive/cost measured at exact candidate SHA |
| VC-016 | P2 | Public 90-second demo, starter templates and polished docs | README/site/docs examples | Copy-paste reproducibility from published release; 10 pilot participants |
| VC-017 | P3 | Kotlin parser precision evaluation | `KotlinRegexParser.kt`, parser/graph tests | Quantified regression/precision/cost; choose migration based on evidence |
| VC-018 | P3 | Multi-contract concurrency and Evidence Debt pilot | Contracts, context, metadata | Demonstrable benefit, no false promise of cross-repo authority |

**Non-negotiable PR checklist:** Problem → exact files affected → compatibility impact → existing tests preserved → new negative tests → manual user flow → CI links → performance delta → security boundary → docs → rollback path → explicit reviewer approval.

---

## 10. Verification benchmark: build credibility by measuring the verifier

### 10.1 Public benchmark tracks

1. **Legitimate changes:** different correct implementations (including unanticipated but valid approaches); verifier must not reject all valid solutions.
2. **Scope violations:** authorized and unauthorized path edits, file deletion, symlinks, ignored and untracked input changes.
3. **Contract integrity:** tampered, malformed, stale, mismatched, replayed and downgraded contracts.
4. **Behavior defects:** missing acceptance conditions, wrong boundary cases, side effects and security regressions with independent test oracles.
5. **Fake evidence:** stale JUnit XML, fabricated/duplicated reports, status without actual command execution, inconclusive environment failures.
6. **Test weakness:** untested changed code, surviving realistic mutations, weak assertions and equivalent mutants.
7. **Concurrency:** known-correct and deliberately faulty state machines with representative schedules.
8. **Language/parser correctness:** Java/Kotlin complex imports, aliases, multiline syntax, generated code and unsupported constructs.
9. **Performance/scale:** small repo, medium JVM service, large JVM monorepo and noisy input directories under explicit resource ceilings.
10. **Cross-change (later):** evidence made stale by a subsequent change, conflicting contracts, independently modified dependencies.

### 10.2 Benchmark protocol

- Freeze fixture repositories, expected outcomes, definitions, evaluator version, runner image, exact code SHA, timeouts and seeds.
- **Separate training/dev fixtures from hidden evaluation fixtures.** Avoid tuning metrics on examples that double as the public success story.
- Gold labels reviewed by humans/domain experts; disagreements adjudicated and recorded.
- Include **valid alternative** solutions and rule out trivial verifier that always returns `FAIL`.
- Run tests in isolation, no access to production secrets or uncontrolled network.
- Calculate: true/false accept, true/false reject, `REVIEW_REQUIRED` and `NOT_EVALUATED` rate, coverage per criterion, reproducibility, cost/time, flaky-run rate and unsupported rate. Distinguish abstention from true rejection.
- Compare **with vs without Vericore** against a fair baseline (normal Gradle/JUnit/CI and selected competitor category where reproducible), same machine and budget.
- Publish all unfavorable results, known limitations and fixed bugs; do not label a synthetic benchmark score as production fault reduction.

### 10.3 Proposed benchmark gate metrics (targets, not measured results)

| Metric | Pilot success gate | Why |
|---|---|---|
| Known contract tamper/stale fixture false acceptance | **0 on curated critical fixtures** | A baseline correctness invariant |
| Legitimate alternative acceptance | **No systematic rejection**; target numeric threshold defined after baseline | Avoid over-restrictive verifier |
| Per-criterion evidence coverage | Report count and usefulness, *not* a deceptive global confidence score | Trust calibration |
| Successful E2E reproducibility | >= 95% on supported deterministic fixtures; investigate every flaky case | CI reliability |
| Developer first verification | p50 < 5 min; p90 < 10 min in targeted supported pilot | Adoption friction |
| Incremental runtime overhead | Budget and report p50/p95 by evaluator type; non-essential heavy verifiers opt-in | Cost predictability |
| Test effectiveness pilot | Detect known targeted mutants missed by existing tests with acceptable false alarms | Actual incremental value |
| Freshness false invalidation | Quantify and improve before automatic blocking | Avoid noisy workflows |

Numbers above are **goals/hypotheses**, not descriptions of current Vericore performance. Benchmark data must determine whether they are achievable.

---

## 11. Growth and distribution: how to become known and genuinely used

### 11.1 Start with one compelling problem, not a universal headline

**Initial promise:** “For your AI-assisted Java/Kotlin change, get a trustworthy account of scope compliance and real verification evidence before merge.”

**The flagship demo:**

- Developer asks an agent to add a simple behavior and preserve an existing invariant.
- Agent makes a realistic plausible error: modifies an unrelated source path, omits a boundary case, or leaves a required assertion untested.
- Current tests pass in at least one example. Vericore shows a **specific** contract/evidence gap, not an invented semantic verdict.
- A fixed candidate is independently verified; output makes it clear what remains unknown.
- Everything runs with exact repository fixtures and a released binary; publish the script and expected output for others to reproduce.

Make the demo visually direct: a 60–90 second terminal-and-receipt comparison, **not** a giant architectural lecture or an exaggerated “caught all bugs” claim.

### 11.2 Open-source discovery funnel

**Discover → try → succeed → repeat → contribute.** Each transition needs evidence.

- **Discover:** clear GitHub description/topic tags, 1-line README value, short GIF/video of actual run, well-indexed docs, tutorial showing both a success and an honest unknown.
- **Try:** pinned release installer, optional package-manager distribution, Quickstart for a supported Gradle or Maven project, fail-fast `doctor`, no external account required.
- **Succeed:** copy-paste fixtures with clear expected results, troubleshooting by error code, minimal first-run command surface.
- **Repeat:** GitHub Action/PR template, optional project policy, evidence report reviewers actually read, stable formats.
- **Contribute:** `good first issue` bounded tasks, architecture map, golden fixture contribution guide, public roadmap decisions and issue response discipline.

**Channels with a reason to engage:** JVM/Kotlin developer communities, OSS maintainers who accept AI-written PRs, GitHub discussions, community presentations, docs tutorials, product launches (HN/Product Hunt only when the demo is genuinely useful), genuine technical posts on Reddit subject to community rules. Do not spam or buy stars.

### 11.3 First 100 real users, then 1,000

**0 → 20 pilot users**
- Recruit developers who already use coding agents in Kotlin/Java repos; at least a few who are skeptical of AI-generated tests.
- Observe first-run journey and ask what decision the tool changed; gather 10 anonymized friction incidents and top 3 verification blind spots.
- Measure repeated usage across **at least two distinct changes**, not a single star or install.

**20 → 100 active users**
- Publish 3 high-quality, reproducible case studies for different failure classes: scope breach, stale evidence, weak test oracle.
- Add starter workflows for Gradle and Maven, two tested agent integrations, a good docs FAQ and public benchmark fixtures.
- Invite integrations and contributions only after APIs are stable enough to support them.

**100 → 1,000 active users**
- Improve sustained adoption: recurring CI usage, reviewer-friendly passport summaries, low-noise project policies and compatible release upgrades.
- Partner with OSS JVM maintainers or AI-agent tool ecosystems on factual documentation and verified examples. Pursue community integrations rather than claiming vendor endorsements.
- Set a quarterly retrospective: what stopped users from verifying their **second** and **fifth** change?

**Don't confuse metrics:** 1,000 GitHub stars ≠ 1,000 active users; downloads ≠ successful verifications; passing tests ≠ software correctness.

### 11.4 Success metrics and measurement safeguards

| Metric | Definition | Ethical measurement approach |
|---|---|---|
| Time to first verified change | Installation start → first correctly interpreted valid result | Opt-in usability sessions and local measurements |
| Activation | User completes preparation and at least one meaningful subsequent verification | Aggregate opt-in telemetry or voluntary reports; default local-first privacy |
| Repeated usage | Same consented repo uses verification in 2+ development sessions | Anonymous aggregate / user-reported; no hidden source capture |
| Coverage quality | Portion of *required* criteria evaluated with adequate methods | Label evidence strength; never a magic trust score |
| Trust calibration | Can user accurately say what a PASS means? | Short usability questions, review decisions |
| False acceptance / rejection | Actual curated fixture outcome vs verdict | Public benchmark, exact SHA and conditions |
| Runtime cost | p50/p95 wall-clock and system use for each evaluator | Record hardware and environment |
| Integration compatibility | Real verified E2E for each documented agent version | Matrix with test logs |
| Community health | Contributor retention, issue resolution, documentation fixes | Public GitHub process |

**Privacy rule:** Vericore is local-first. Do not silently upload source files, prompts, receipts or identifiers to a hosted analytics service. Privacy-preserving opt-in diagnostics must be transparent and disabled by default unless separately approved.

### 11.5 Pricing and business model — optional, later

- Keep core deterministic CLI, local receipt verification, schemas and necessary trust evidence available as an open-source foundation.
- If real demand emerges, consider paid **hosted policy governance, organization-wide managed provenance, compliance workflows and support** rather than charging for the core ability to know if an agent went outside allowed scope.
- Validate willingness to pay with interviews; no pricing claim or revenue forecast is established by repository/competitor research.
- Do not launch a multi-tenant SaaS on the current local REST trust boundary.

---

## 12. Product longevity to 2040 / 2050: build for change, not clairvoyance

It is impossible to know what exact programming languages, agent frameworks or threats will dominate in 2040 or 2050. Product durability comes from **invariants and interoperability**, not forecasting a particular vendor's roadmap.

### Long-horizon stress scenarios

| Plausible scenario (not a prediction) | Architecture capability to build now, incrementally |
|---|---|
| Agents run continuously and change repos faster than humans can read them | Cheap candidate identity, independent policy and evidence-freshness invalidation |
| Multiple agents implement related services concurrently | Contract overlap, coordinated review and stale-proof detection; eventual multi-repo federation |
| Entire systems are re-generated or migrated by agents | Language-neutral obligations and compatibility evaluator adapters |
| An agent can fake local logs/test reports | Protected runner identities, independent oracles and verifiable attestation chain |
| Verification evidence survives for years | Stable open schema, backward readers, hashes, evaluator version retention and explicit invalidation |
| Model/IDE vendors disappear or protocols change | CLI fallback, open data formats, tested MCP evolution and no mandatory cloud backend |
| Requirements involve non-code artifacts (policies, migrations, infrastructure) | Typed obligation/evidence model that can host non-source-specific evaluators |
| Supply-chain rules and compliance become stricter | Identity-bound provenance and external standards; configurable human approval, no fake certification |
| Specialized verifiers improve significantly | Pluggable capability descriptors and common evaluation schema, avoid rebuilding their algorithms |
| AI produces behaviorally equivalent but structurally different code | Accept multiple legitimate solutions; favor observable contracts and robust oracles over diff matching alone |

### Architectural constitution

1. **Evidence before rhetoric.** Claims describe precise methods and limitations.
2. **Agent-agnostic core.** No vendor controls the meaning of PASS.
3. **Language-neutral obligation schema; language-specific evidence adapters.**
4. **No unbounded verifier execution.** Isolation, budgets and trust modes are explicit.
5. **Backward-readable data.** Old receipts retain exact historical meaning.
6. **Open portable formats.** No hosted service required to interpret a record.
7. **Human accountability.** Humans own policy, scope approvals and exceptions.
8. **Uncertainty is a first-class result.** Lack of evidence never silently becomes acceptance.
9. **Measured quality over feature counts.** False-rejection and developer time matter as much as defects caught.
10. **Local-first privacy and security.** Never assume a coding agent or its build script is trusted simply because it is local.

### High-upside research bets (explicitly speculative)

- **Counterfactual evidence:** track which exact assumptions would need to change before an earlier verification no longer applies. Start with file-level invalidation; research contract-level semantic impact only after precision benchmarks.
- **Proof obligation exchange:** a portable manifest describing what a verifier needs, so future external verification services or on-device tools can fulfill a check without replacing the core Change Passport.
- **Risk-adaptive verification:** choose deeper checks for risky changes using calibrated evidence from prior bugs, not an unvalidated AI confidence score.
- **Verification-of-verifiers:** run known-good and known-bad controls against evaluator plugins and detect verifiers that reject everything or rubber-stamp everything.
- **Long-lived engineering memory:** connect decisions, obligations, evidence and code-state validity with explicit owners/expiration instead of AI-generated narrative memory.
- **Federated policy and provenance:** independent teams can consume portable evidence without granting a central platform source-code access.

All bets require: a scoped prototype, control group, measured utility, explicit risks and a stop decision. Do not make them foundational dependencies until proven.

---

## 13. Risk register and de-scoping rules

| Risk | Consequence | Mitigation | Owner / review gate |
|---|---|---|---|
| Too many features too soon | Low adoption, fragile architecture | Focus on one JVM verification journey; small PRs | Product owner, every wave |
| Untrue proof claims | Lost credibility, unsafe merges | Typed bounded verdicts; publish limits | Product + security review |
| Same agent controls verifier artifacts | Fake independent assurance | Separate protected runner/policy/signing identity | Security architecture gate |
| Kotlin parser inaccuracies | Wrong impact signals | Corpus + parser precision benchmark; fail conservative | Core engineering |
| False rejections | Developers disable the tool | Valid alternative fixtures, explicit exception process, noise budgets | Benchmark owner |
| False acceptance | Real bugs are trusted | Independent negative controls, expert gold labels, no missing-evidence PASS | Benchmark + security |
| Expensive tests | CI bills, users abandon product | Risk-based optional evaluators, strict time/memory budget | DevEx owner |
| REST publicly reachable | Unauthorized repository access/code execution risk | Local-only now; protect before shared use | Security gate |
| Attestations mistaken for correctness | Incorrect security story | Separate integrity vs correctness statements | Documentation + schema review |
| Unvalidated agent compatibility | Onboarding failure | Published tested-version matrix | Integration owner |
| Overdependence on one vendor | Ecosystem shift breaks use | CLI and versioned open core | Architecture review |
| Hosted business premature | Privacy/support/security burden | Wait for demand and secure trust model | User approval |
| Marketing exaggeration | Poor reputation | Reproducible demos, no invented metrics or 'only' claims | Every publication |
| Maintainer overload | Reliability and security backlog grows | Smaller issue/PR scope, stable release cadence | User and maintainer |

**Hard stop:** Do not merge a feature that makes a broad correctness/containment claim without a falsifiable definition and the required tests. Do not expose an unprotected REST verifier to untrusted networks. Do not automatically invoke untrusted agent-generated code with sensitive credentials. Do not silently downgrade from isolated verification to trusted-local execution.

---

## 14. Immediate actionable 30-day product-owner agenda (proposed sequence)

This is a **sequenced agenda**, not a promise that all engineering will be completed in 30 days.

### First 1–7 days: establish truth

- Review v2.0.0 exact SHA and CI artifacts; reconcile outdated `IMPLEMENTATION_STATUS` release statement.
- Validate the release installation journey on at least two supported clean environments.
- Inventory main code/API/security assumptions and implement **only approved** foundation fixes in isolated PRs.
- Build the initial benchmark suite: correct, alternative correct, scope drift, stale contract, fake JUnit, unsupported spec.
- Draft one honest, short landing-page promise and an exact, reproducible demo scenario.

### Days 8–14: find and remove friction

- Observe newcomer installation, `prepare` and `verify` on JVM repos; measure time and errors.
- Finalize tiny public command surface and error-message taxonomy.
- Select first 2 target agent integrations based on observed users; create compatibility fixtures.
- Review the threat model and decide whether isolated verifier work is required before the first public demo.

### Days 15–21: prove a meaningful new behavior

- Prototype criterion-to-evidence output on existing JUnit/command/metrics; **do not** add untrusted runtime code execution yet.
- Show one case where verification correctly refuses a tempting but unjustified PASS.
- Publish a first benchmark baseline, including the cost of verification and any false rejection.
- Prepare versioned schema/design review with backwards-compatibility tests.

### Days 22–30: make it visible and choose next gate

- Polish the 90-second demo, docs, README, one CLI quickstart and an honest verification receipt sample.
- Publish or share benchmark methodology and known limits; invite small JVM OSS pilot users.
- Decide from actual pilot data whether the next investment should be **execution isolation**, evidence coverage UX, onboarding, or a targeted testing experiment.
- Approve the next one or two Antigravity PR tasks only after expected acceptance criteria and reviewer responsibility are clear.

**Explicit exclusions from this initial agenda:** no full JDBC runtime firewall, no “all SQL is safe” proof, no universal language support, no enterprise cloud launch, no guaranteed 2050 capabilities, and no v2.0.0 re-release (it already exists).

---

## 15. Standard Antigravity task brief and review protocol

### Copy/paste brief template for Antigravity

> **Task ID:** `VC-___`  
> **Purpose:** [One narrowly defined product problem, why it matters to users]  
> **Baseline:** `main` SHA `[current exact SHA]`; confirm it before editing.  
> **Source of truth:** current code, tests, published artifacts; Antigravity knowledge document only for orientation.  
> **Research before coding:** inspect referenced production files, tests, schemas, docs, call graph and latest CI.  
> **No assumptions:** explicitly label implemented / documented / planned / unavailable.  
> **Design:** propose minimal changes, alternatives, trust implications and backward compatibility before editing.  
> **Required tests:** valid cases, invalid cases, tamper/stale paths, valid alternative implementations where applicable, security and performance budgets.  
> **Acceptance:** [Objective observable outcomes and exact artifacts].  
> **Restrictions:** no broad refactoring, no unrelated feature additions, no publish/merge/tag/deploy, no network/secret access outside approved scope, no changes to tests merely to hide failures.  
> **Deliver:** summary, changed files, design rationale, test results with command and CI links, sample output, limitations, benchmarks and rollback plan.  
> **Approval:** stop before merging; user decides.

### Evaluation template for every Antigravity PR

- **Does it solve the user problem?** A screenshot/demo isn't enough without an actual successful workflow.
- **Evidence authenticity:** Were tests executed under the stated identity and trust mode? Is the output candidate-bound?
- **Negative case:** Does a known failure actually fail?
- **Positive alternative:** Does a legitimate implementation still pass?
- **Compatibility:** Are old CLI/MCP/output contracts preserved or explicitly migrated?
- **Performance:** Is overhead acceptable? Are timeouts bounded?
- **Security:** Does new plugin/running code/REST behavior expand permissions or leak secrets?
- **Documentation:** Are commands and support labels true for a published release or clearly marked proposed?
- **Decision:** approve, request changes, stage behind experimental flag, or reject. No automatic success because Antigravity says “done.”

### Product owner / implementer boundaries

- **User:** actual project owner, authorization of scope, decisions, access and publishing.
- **ChatGPT:** strategic product-owner role, competitor intelligence, architecture critique, benchmark design, review of Antigravity deliverables; cannot make binding corporate/legal decisions or promise unattended future work.
- **Antigravity:** implementation environment once the user explicitly approves a bounded task; should not presume this document authorizes code modifications.
- **CI / reviewers:** independently reported verification evidence with exact SHA, explicit limitations, and human merge authority.

---

## 16. Decision log: what we have chosen and what remains open

### Proposed decisions (need user approval)

- [ ] **D-01:** Position Vericore around bounded Change Passports and visible uncertainty, not general code review.
- [ ] **D-02:** First serious audience is Java/Kotlin AI-assisted development.
- [ ] **D-03:** Preserve existing 27 commands for advanced/backward compatibility while teaching ~4 public commands.
- [ ] **D-04:** Independently controlled trust boundary precedes adversarial verifier product claims.
- [ ] **D-05:** Invest in requirement evidence and actual test effectiveness only when benchmarks show value.
- [ ] **D-06:** Reject a default production JDBC firewall and broad automated SQL-isolation proof claim.
- [ ] **D-07:** Keep portable open schemas and local-first functionality as core product commitments.
- [ ] **D-08:** Publish a reproducible benchmark including false rejection and cost, not marketing-only scores.
- [ ] **D-09:** Use Antigravity for approved code work; require test evidence and human review before merge.

### Open technical questions / research gates

1. Should the protected contract/policy authority live in a separate worktree, CI service, OIDC-signed artifact, or an explicit permission-separated local service? Evaluate threat models and UX.
2. Which isolation mechanism can be safely maintained across supported operating systems? Is CI-only isolation the first credible option?
3. What would users prefer as default behavior when no suitable behavioral verifier exists: `REVIEW_REQUIRED`, nonblocking informational result, or a project policy choice? Must not present absence as proven success.
4. What is the concrete public format for obligations/evaluators and old schema compatibility?
5. How do we demonstrate criterion-to-test **relevance** without implying semantic coverage?
6. Which opt-in evaluator delivers measurable value relative to runtime cost: mutation, property checks, API schema compatibility, or Lincheck?
7. Which real agent and CI integrations are most requested by first 20 pilot developers?
8. How will new native language support be evaluated when full symbol resolution is expensive or impossible?
9. What evidence freshness dependency precision is useful before nuisance invalidations outweigh benefits?
10. Which data may safely be included in portable public Passport artifacts, especially paths, secrets, PII and proprietary logs?

### Source-of-truth reminder

A plan that survives to 2050 cannot be treated as a frozen list of features. Revisit positioning and gates against **new evidence** each quarter and reconsider the architecture constitution annually. Preserve historical decision rationale and provenance; do not silently rewrite old assessments to pretend foresight.

---

## 17. Sources and reproducibility

### Attached artifacts (user-provided)

- `ANTIGRAVITY_VERICORE_PROJECT_KNOWLEDGE(2).md` — preferred background document, not independently binding implementation evidence.
- `Chatgpt suggestion for my project(1).pdf` — 19-page proposal (Change Passport, independent verification, benchmark, developer experience, execution/security, product direction).
- `vericore_2_0_pitch_and_integration_blueprint(2).pdf` — 5-page adversarial invariant and runtime verification pitch; assessed critically above.
- Earlier Gemini and ChatGPT knowledge references and initial onboarding instructions.

### Exact repository references

- Repository: https://github.com/sonii-shivansh/Vericore
- Baseline main commit: https://github.com/sonii-shivansh/Vericore/commit/da3514f90b9053d19b1a002872460a586e459a75
- `README.md`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/README.md
- `docs/PRODUCT_DIRECTION.md`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/PRODUCT_DIRECTION.md
- `docs/IMPLEMENTATION_STATUS.md`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/IMPLEMENTATION_STATUS.md
- `docs/SUPPORT_MATRIX.md`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/SUPPORT_MATRIX.md
- `docs/ARCHITECTURE.md`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/ARCHITECTURE.md
- `build.gradle.kts`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/build.gradle.kts
- `Main.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/Main.kt
- `EngineeringPreparation.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/EngineeringPreparation.kt
- `EngineeringVerification.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/EngineeringVerification.kt
- `VerificationCommandExecutor.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/VerificationCommandExecutor.kt
- `CandidateStateSnapshotter.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/CandidateStateSnapshotter.kt
- `ChangeSpecificationEvaluator.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/ChangeSpecificationEvaluator.kt
- `AgentChangeContract.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/core/workflow/AgentChangeContract.kt
- `VericoreServer.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/server/VericoreServer.kt
- `McpProtocol.kt`: https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/src/main/kotlin/com/vericore/mcp/McpProtocol.kt
- Published release: https://github.com/sonii-shivansh/Vericore/releases/tag/v2.0.0
- Exact-SHA recent Release Audit: https://github.com/sonii-shivansh/Vericore/actions/runs/42360394726
- Exact-SHA recent E2E run: https://github.com/sonii-shivansh/Vericore/actions/runs/42360394651

### External primary research

- GitHub Spec Kit: https://github.github.com/spec-kit/
- Greptile TREX: https://www.greptile.com/trex ; https://www.greptile.com/blog/trex
- Greptile agent independence: https://www.greptile.com/independence
- CodeRabbit: https://www.coderabbit.ai/
- Cursor Bugbot: https://cursor.com/docs/bugbot
- Qodo testing: https://www.qodo.ai/solutions/testing/
- Sonar AI Code Assurance: https://www.sonarsource.com/solutions/ai/ai-code-assurance/
- Kiro FAQ/capabilities: https://kiro.dev/faq/
- Sigil intent gates: https://fielding.github.io/sigil/
- Antithesis deterministic simulation: https://antithesis.com/docs/resources/deterministic_simulation_testing/
- MCP 2026-07-28 spec announcement: https://blog.modelcontextprotocol.io/posts/2026-07-28/
- GitHub artifact attestations and security warning: https://docs.github.com/en/actions/concepts/security/artifact-attestations
- SLSA provenance spec: https://github.com/slsa-framework/slsa/blob/main/spec/build-provenance.md
- GitHub CLI attestation verifier caveats: https://cli.github.com/manual/gh_attestation_verify
- JetBrains Lincheck: https://kotlinlang.org/docs/lincheck-guide.html
- JSQLParser AST and syntactic limitation: https://jsqlparser.github.io/JSqlParser/usage.html
- PIT mutation testing concepts: https://pitest.org/quickstart/basic_concepts/ ; https://pitest.org/faq/
- VerifierBench preprint (2026): https://arxiv.org/abs/2610.28679
- LLM-generated fault/test adequacy research (2026): https://arxiv.org/abs/2609.09315
- OWASP secure coding with AI: https://cheatsheetseries.owasp.org/cheatsheets/Secure_Coding_with_AI_Cheat_Sheet.html
- Stack Overflow Developer Survey 2025: https://survey.stackoverflow.co/2025/
- DORA 2025: https://dora.dev/research/2025/dora-report/

### Research limitations

- Competitor functionality and vendor results may change after 2026-11-07; re-verify before sales claims, launch positioning or integration decisions.
- Search cannot establish the **absence** of another competitor or guarantee future uniqueness.
- Attached PDFs contain some unsupported market figures and product comparisons; this plan intentionally did not repeat them as established facts.
- No direct customer interviews or independent head-to-head performance study was conducted during preparation of this plan.
- The complete repo tree was enumerated and selected implementation files examined, but no claim of a line-by-line audit or locally executed full test suite is made. Recent GitHub CI evidence is distinguished from independent adversarial evaluation.

---

## 18. Final product-owner decision

Build **the most trustworthy, easiest-to-adopt evidence trail for AI-made code changes in JVM repos** before trying to become a universal AI reliability platform.

**The proposed differentiator is not a new AI model, prettier review comments or a giant framework. It is a precise relationship between a human-approved obligation, the exact candidate state, independent evidence, the evaluator's limited claim, and whether that evidence remains valid later.**

If Vericore reliably tells developers **“This is what the agent changed; this is what we actually verified; this is what we did not verify; and here is why you should—or should not—trust the result,”** it will solve a meaningful problem. Strong adoption, a trusted reputation and long-term relevance must then be earned through usability, independent benchmarks, careful security engineering and real developer experience.

**Next approval milestone:** Accept or amend the charter, then commission **VC-001 / Wave 0** from Antigravity as a read-only audit first. After review, approve the first bounded code PR. Nothing in this `plan.md` authorizes code modification by itself.
