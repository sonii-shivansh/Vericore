# Vericore — Engineering Operating Model, Requirements Baseline & Sprint 0

**Status:** Proposed operating agreement and audit; NOT instructions to implement without approval.  
**Date:** 2026-11-07 (Asia/Kolkata)  
**Verified repository:** https://github.com/sonii-shivansh/Vericore  
**GitHub main SHA:** `da3514f90b9053d19b1a002872460a586e459a75`  
**Product strategy input:** User-provided `plan.md` (master plan; 18 VC backlog work packages).  
**Permissions:** No files, code, branch settings, or releases were changed on GitHub. This document exists outside the repository for review.

## 1. Decision record (proposed)

1. **Process:** Adopt lightweight **Scrumban**: a prioritized GitHub Issues/Projects backlog, 1-week iterations during initial stabilization (later 2 weeks if predictable), 15-minute planning, async daily progress, end-of-iteration demo and retrospective. Do not imitate large-company Scrum ceremonies for a small human + AI team.
2. **Authority:** The human maintainer is final product owner/approver. ChatGPT can act as a strategy and independent review partner. Antigravity is the implementation tool. CI/test evidence is separate from an agent's self-assessment.
3. **Development control:** One approved GitHub issue per bounded implementation task; short-lived feature branch, PR, review, required CI checks, then human-approved merge. No direct writes to `main` for normal work; use a protected branch/ruleset after assessing settings and repository constraints.
4. **Scope:** Vericore is initially a reliable JVM/Java-Kotlin, local-first **AI change assurance** product. Long-term product thesis = requirement-to-evidence Change Passport and evidence freshness. Do not advertise universal correctness proof or universal agent support.
5. **Spec Kit:** Adopt **conditionally after the documentation/baseline audit**, first in a disposable branch for one bounded feature. Keep the strategic master `plan.md` distinct from per-feature Spec Kit-generated `plan.md`. Spec Kit manages *feature specification workflow*, not the sprint board or project authority.
6. **Antigravity configuration:** Prefer repository-root `AGENTS.md` (short, agent-neutral), optional triggered `.agents/rules/*.md`, and a small number of task-specific `.agents/skills/<skill>/SKILL.md`. Do not invent a repository `.antigravity/` convention; Antigravity's verified docs use `.agents/` and `~/.gemini/`. Do not duplicate the same instructions across `AGENTS.md` and `GEMINI.md`.
7. **Agent concurrency:** Start with one writing implementer and one independently reviewing/read-only agent. Avoid parallel writing in one checkout. When parallelism becomes justified, isolate tasks using Antigravity **New Worktree Mode**, different issues/branches and separate acceptance gates. Human approves merges.
8. **First deliverable:** A **Sprint 0 read-only reality and docs audit** with discrepancy ledger, requirements baseline, threat boundary, CI/release checks and proposed PR-sized fixes. No feature or documentation edits until the specific PR scope is authorized.

## 2. The 1-week delivery cadence

| Event | What happens | Record |
|---|---|---|
| Backlog refinement, before iteration | Prioritize by user impact, severity, confidence, dependency, effort | GitHub issues with IDs, priority, owner, acceptance criteria |
| Sprint planning (15–30 min) | Commit only work that fits; define objective; no mandatory estimates in hours | Sprint goal + 1–3 ready issues |
| Daily async check (3–5 min) | What changed, what is blocked, which evidence exists | Issue comment; no meeting necessary |
| Work | Antigravity investigates first, proposes a design, then implements only on approval | One branch and PR per cohesive issue |
| Quality gate | Reviewer checks valid/invalid cases, documentation, compatibility, tests and security | PR checklist + exact-SHA CI |
| Sprint demo/review | Execute real end-user journey, compare acceptance criteria | Demo transcript, pass/fail, follow-up issues |
| Retrospective (10–15 min) | Identify one or two process improvements | Small decision log / ADR |

**Proposed board columns:** Backlog → Ready → In progress → In review → Blocked → Done. Use WIP limit = 1 implementation task per writing agent. Treat incomplete work as carried forward, not auto-Done.

### Definition of Ready (before code)
- User problem, target user, expected outcome and non-goals are explicit.
- Existing behavior and source files identified from the *current SHA*.
- Acceptance criteria testable, including negative and valid-alternative cases.
- Risks, compatibility and documentation impact identified.
- User authorizes the bounded issue, not the entire strategy file.

### Definition of Done (before merge)
- Acceptance criteria demonstrated; source diff limited to approved scope.
- Relevant existing tests and targeted new tests pass; test command/exit code recorded.
- Release/CI check evidence is for the actual PR commit or exact reviewed candidate.
- No ignored security, performance or backwards-compatibility regressions.
- User-facing docs accurately describe **shipped** features; proposed features labelled.
- Changelog/release notes updated when relevant; no secret or user data leakage.
- Independent review completed; human approves merge. Release is separately approved.

## 3. Proposed repository guidance layout (do not create yet)

```text
Vericore/
├── AGENTS.md                       # brief, evergreen, agent-neutral instructions
├── .agents/
│   ├── rules/
│   │   ├── kotlin-quality.md       # only if project-specific patterns warrant it
│   │   ├── evidence-contracts.md   # triggered on contract/schema changes
│   │   └── docs-accuracy.md        # triggered on documentation work
│   └── skills/
│       ├── vericore-repo-audit/
│       │   └── SKILL.md            # audited read-only workflow
│       └── vericore-pr-verification/
│           └── SKILL.md            # run and report tests/negative controls
├── .specify/                       # only if Spec Kit pilot is approved
├── specs/                          # Spec Kit per-feature specs/plans/tasks
├── docs/
│   ├── CURRENT_CAPABILITIES.md     # proposed evidence-linked current state
│   ├── decisions/                  # small ADRs; optional until first real decision
│   └── ...                         # existing docs preserved and reconciled
└── ... existing code and workflows
```

Use **root `AGENTS.md`** for short permanent rules: code/tests first, no unsupported claims, never write to main, no secrets, preserve CLI/MCP/JSON schema compatibility, plan before editing, run negative tests, cite exact code references. Keep under a few KB. Do **not** inline the 90-KB strategy plan in global instructions. The plan is reference material on demand.

**Rule syntax:** `.agents/rules/*.md` files require YAML frontmatter with a valid `trigger` (`always_on`, `model_decision`, `glob`, `manual`). Prefer `model_decision` or `glob` for specialized content, rather than copying a giant always-on constitution. An `AGENTS.md` file does not require frontmatter. Skill files need a `SKILL.md` with YAML `description`; keep each skill narrow. Verify generated Spec Kit skills before adding custom skills under the same directory.

**Global vs repository:** `~/.gemini/AGENTS.md` is for personal preferences that truly apply to *all* projects. Vericore architecture/trust rules belong in Vericore's own repository so other contributors and agents see the same controls.

Official references: https://antigravity.google/docs/rules/ ; https://antigravity.google/docs/skills ; https://antigravity.google/docs/projects ; https://github.github.com/spec-kit/integrations/agy.html

## 4. Spec Kit adoption decision

**Why it fits:** It produces reviewable spec → clarify → plan → tasks → analyze → implement → converge artifacts; this complements Vericore's independent verifier rather than replacing it. Existing-project adoption is officially supported. Antigravity integration is available as `agy` and installs skills under `.agents/skills/speckit-<command>/SKILL.md`.

**Boundaries:** Spec Kit outputs are *proposals and design artifacts*, not proofs; tests/CI remain necessary. Do not retroactively invent specifications for all existing features. Don't let an agent run `/speckit-implement` without explicit issue approval. `specify init --here --force --integration agy` may overwrite managed files; use only in an approved feature branch after reviewing the diff. Start with a feature-spec experiment, not a repository-wide migration. Do not grant `--dangerously-skip-permissions` or equivalent unrestricted agent approvals as a routine shortcut.

**Pilot success gate:** On one bounded non-security feature, a newcomer can read `spec.md`, `plan.md`, `tasks.md` and understand the acceptance criteria; the generated artifacts are consistent, paths and tests match the current repository, no unrelated files are overwritten, and the overhead is justified. If not, use a lighter handwritten PRD template instead.

## 5. Requirements baseline v0.1 (proposed; not a validated exhaustive specification)

The master `plan.md` contains strategic waves, not an approved exhaustive product requirements document. The requirements below translate it into testable candidates. IDs are durable once approved. **Current** means an existing implementation needs regression coverage; **New** means proposed. Priority and timing require user sign-off; do not treat a proposal as shipped.

| ID | Requirement / acceptance intent | Priority | Status | Primary verification |
|---|---|---|---|---|
| GOV-01 | Protected `main` and clear PR/merge policies; no direct routine pushes | P0 | New governance action | GitHub API/settings and attempted-rule checks |
| GOV-02 | One approved issue, PR and documented tests per change | P0 | New process | PR/issue traceability sample |
| GOV-03 | Release note and support claims tied to actual release tag and tested SHA | P0 | Existing practice to strengthen | Automated docs/releases audit |
| GOV-04 | Agent permissions least-privilege, no automatic secrets/merge/publish authorization | P0 | New operating rule | Permission audit / PR template |
| DOC-01 | Every present-tense public feature claim backed by source/test or labelled unknown | P0 | New continuous process | Code-to-doc audit ledger |
| DOC-02 | CLI/MCP/API/schemas examples validated by executable/generated fixtures | P0 | Existing partial, expand | Docs-parity + schema/example CI |
| DOC-03 | Stale releases/versions automatically detected or avoided through generated data | P0 | New | v2.0.0 negative audit fixture |
| SEC-01 | Trusted-local CLI/MCP/REST threat model documented, unsupported deployment modes explicit | P0 | Existing limitations, formalize | Security review with test matrix |
| SEC-02 | Untrusted build execution never misrepresented as sandboxed | P0 | Current boundary | Execution trust-mode tests and docs |
| SEC-03 | Contract cannot be claimed independently authoritative if same agent can edit authority | P0 | Partial | Separate trust-principal design and attack-model tests |
| CORE-01 | `prepare` freezes correct repository/candidate state and planned scope | P0 | Current | Negative drift/tamper fixtures |
| CORE-02 | `verify` compares against original contract and keeps missing evidence non-passing | P0 | Current | Valid/invalid/stale/unsupported tests |
| CORE-03 | Hashes, paths, ignores, symlinks, Git HEAD and JUnit freshness behave as documented | P0 | Current | Adversarial regression corpus |
| CORE-04 | JSON schemas, CLI exit codes and MCP tools preserve compatibility | P0 | Current | Golden fixtures and versioned migration tests |
| DX-01 | One reliable supported JVM onboarding journey from installation to meaningful verify | P1 | Improve current | Usability tests p50/p90 |
| DX-02 | Only `init`, `prepare`, `verify`, proposed `show` are prominently taught; 27 old commands remain | P1 | Proposal | Documentation/CLI regression audit |
| DX-03 | Clear errors for unsupported languages, stale contracts, missing tools and wrong permissions | P1 | Improve current | End-user scenario fixtures |
| DX-04 | Optional, consent-based one-step integrations for initially 2 tested agent environments | P1 | New | E2E per agent/version, rollback |
| EVD-01 | Every acceptance criterion has a stable ID and evaluator evidence/limitations | P1 | Extend current | Criterion-to-evidence contract tests |
| EVD-02 | Unsupported, not evaluated, stale, failed and inconclusive claims never appear as blanket success | P1 | Extend current | Verdict matrix, mixed-case tests |
| EVD-03 | Human-readable per-criterion summary and linked detailed artifacts | P1 | New UX | User interpretation test |
| EVD-04 | Accepted human exceptions are explicit, attributable and do not rewrite raw evidence | P2 | New | Policy/permission tests |
| EXEC-01 | Isolated execution option for untrusted code with defined filesystem/network/resources | P1 | Research/prototype | Independent threat and escape tests |
| EXEC-02 | Capture command versions, limits, outputs and bounded logs/provenance | P1 | Extend current | Exact candidate and truncation tests |
| PASS-01 | Portable versioned Change Passport, offline-readable with candidate/evaluator binding | P2 | New | Schema golden fixtures and third-party reader |
| PASS-02 | Attestation/signature issued by separate trusted principal when enabled | P2 | New | External verify + tamper fixture |
| BENCH-01 | Measure false accepts, false rejects, inconclusive, latency and repeatability | P1 | Extend existing benchmark | Frozen public fixture protocol |
| ADV-01 | Evaluate targeted mutation/property testing with independently defined oracles | P2 | Experiment | Positive/negative controls and runtime budget |
| ADV-02 | Pilot narrow concurrency/SQL checks without blanket proof claims | P2 | Experiment | Controlled fixtures and explicit unsupported states |
| FRESH-01 | Re-evaluate evidence freshness after candidate/dependency changes | P2 | New | Dependency-change invalidation fixtures |
| FRESH-02 | Evidence Debt view is useful in user trials, not just another notification list | P3 | Research | Pilot decision quality and noise counts |
| LANG-01 | Java/Kotlin analyzer limitations explicit; parser accuracy measured | P1 | Current / research | Known-correct parser corpus |
| LANG-02 | Any new language requires pilot demand and accuracy gates | P3 | New | Compatibility benchmark |
| GROW-01 | Reproducible 90-second demo from published release and clear first-use docs | P1 | New marketing work | Cold-start walkthrough |
| GROW-02 | Public feedback, contributors, lightweight support triage and opt-in adoption measures | P2 | New | Issue/project metrics, consent review |

**Open requirements questions:** Which specific JVM persona is first? Which two Antigravity/agent integrations matter most? Does support mean CLI-only, or should isolated untrusted-PR CI be mandatory? How should required-but-unverifiable criteria affect merge policy? Which service controls are trustworthy enough to sign receipts? Answer through pilot and architecture decisions, not guessing.

## 6. Verified documentation drift snapshot — 2026-11-07

Method: GitHub `main` code and docs were inspected via the connected GitHub API; the recursive tree listed 328 blobs including Markdown and schemas. This is a **targeted and broad text-pattern audit, not a complete manual semantic validation of every document, JSON schema or codepath**. Ratings below are finding-level.

| Finding | Evidence | Assessment | Recommended correction |
|---|---|---|---|
| **DRIFT-01:** latest release in `docs/IMPLEMENTATION_STATUS.md` | Says v1.0.1 (2026-10-20); actual latest GitHub release is v2.0.0 (2026-11-06 UTC) | CONFIRMED stale | Update against release metadata; prevent fixed stale latest-release sentences |
| **DRIFT-02:** `docs/CLI.md` header | Claims v1.0.1 latest while v2.0.0 published | CONFIRMED stale | Update source-of-truth/reference status |
| **DRIFT-03:** `docs/REPOSITORY_GOVERNANCE.md` | Calls v0.9.0 current (lines 13, 79–80). Actual latest v2.0.0 | CONFIRMED stale | Update current vs intended settings and dated historical context |
| **DRIFT-04:** GitHub Action quickstart | `docs/GITHUB_ACTION.md` examples target v1.0.1 and retain pre-publication warning. Tag exists but no longer latest | OUTDATED example, not proof of broken action | Show current tested release/immutable commit pin policy, verify action compatibility first |
| **DRIFT-05:** Gradle wrapper/version claims | `gradle-wrapper.properties` URL is Gradle **9.7.0**, while `CHANGELOG.md` explicitly says wrapper upgraded to **9.7.1**; `docs/SUPPORT_MATRIX.md` says official Gradle 9.7.1+ | CONFIRMED mismatch, determine intended baseline | Decide whether docs or wrapper should change only after a build/toolchain review |
| **DRIFT-06:** Kotlin compiler release note | `CHANGELOG.md` v2.0.0 says Kotlin compiler 2.3.0; `build.gradle.kts` uses 2.4.21 | CONFIRMED version difference; historical note may describe an intermediate step | Verify actual release tag build and clarify final shipped toolchain without rewriting genuine history |
| **DRIFT-07:** unrendered citation in docs | `docs/MCP.md` contains literal `citeturn1search2` | CONFIRMED docs quality issue | Replace with proper stable source link or remove unsupported citation |
| **GOV-01:** branch protection | GitHub API `main.protected=false`; `GET /rulesets` returned empty array. Governance doc explicitly labels settings as intended, not verified | CONFIRMED absent protection/rulesets at time queried | Propose minimal main ruleset, required checks and bypass policy; human config approval required |
| **TEST-01:** docs parity blind spot | `scripts/audit/docs-parity.sh` compares CLI command **names** parsed from help to `docs/CLI.md` headings only. `docs-parity.yml` runs it | CONFIRMED narrow test coverage | Extend checks for release/version claims, usage examples, schemas, MCP/API, current outputs; avoid pretending all docs are validated |

**Verified sources:**
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/IMPLEMENTATION_STATUS.md
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/CLI.md
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/REPOSITORY_GOVERNANCE.md
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/GITHUB_ACTION.md
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/docs/MCP.md
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/gradle/wrapper/gradle-wrapper.properties
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/build.gradle.kts
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/CHANGELOG.md
- https://github.com/sonii-shivansh/Vericore/blob/da3514f90b9053d19b1a002872460a586e459a75/scripts/audit/docs-parity.sh
- https://github.com/sonii-shivansh/Vericore/releases/tag/v2.0.0

### Documentation audit method to apply across *all* docs

For each README, `docs/*.md`, schema, release guide, configuration sample and installer reference:

1. Create a ledger: file/path, owner, intended audience, implementation claim, verified code/test/SHA, last reviewed SHA, current status (`MATCH`, `STALE`, `AMBIGUOUS`, `PLANNED`, `HISTORICAL`, `NOT_REVIEWED`).
2. Cross-check commands against actual CLI help and machine-output contracts; MCP tool definitions against `McpProtocol.kt`; REST routes against `VericoreServer.kt`; JSON examples against schemas and serializers; installer commands against release archive assets.
3. Distinguish historical release notes (preserve history), living documentation (update current facts), and roadmap proposals (must remain explicitly future-tense).
4. Add validation that detects stale literal release/version references or generated examples, but don't force all old-tag examples to update if intentionally demonstrating compatibility.
5. Make one reviewable, documentation-only PR first; keep code/toolchain changes separate unless the mismatch requires a deliberately scoped fix.

## 7. Sprint 0 — only the first review package is authorized when approved

**Goal:** Establish release truth, documentation and governance reality, and future feature-spec process **without changing source code**.

| Sprint 0 issue | Scope | Expected output | Completion gate |
|---|---|---|---|
| S0-01 / master VC-001 | Living code/release/docs audit | `DOCS_DRIFT_REPORT.md`, capabilities table, exact SHA evidence links | Every examined claim labelled, critical docs mismatches enumerated; nothing silently rewritten |
| S0-02 / master VC-002 | Local/MCP/REST/CI threat boundaries | Trust model, risk ranking, remediation proposal | No security guarantee without demonstrable boundary |
| S0-03 | CI/release/governance review | Current workflow matrix, ruleset absence, candidate gating plan | Proposed required checks precisely named and testable |
| S0-04 | Requirements and Spec Kit pilot design | PRD v0.1, architecture constitution draft, 1 bounded pilot selection | User signs off; no initialization yet |
| S0-05 | Antigravity customization proposal | Minimal `AGENTS.md`, 0–2 rule and skill drafts | No duplicate or always-on giant prompt; no edits unless approved |

**Not Sprint 0:** full AST parser rewrite, new language support, runtime JDBC firewall, automatic adversarial sandbox, release publication, or wholesale docs rewriting without an evidence ledger.

### First prompt to give Antigravity (read-only; paste verbatim)

```text
You are auditing Vericore, not implementing a feature.

Repository: sonii-shivansh/Vericore
Baseline expected at start: da3514f90b9053d19b1a002872460a586e459a75. Verify the current main HEAD and record any difference before proceeding.
Reference: the attached VERICORE master plan.md and Antigravity knowledge document. Treat them as secondary context; code, tests, workflows, tagged release assets and GitHub settings are the authoritative evidence.

Task: Sprint 0 / VC-001 read-only documentation, release, and capability audit.

1. Inventory all tracked Markdown docs, JSON schemas, CLI/MCP/REST references, release workflows and major source entry points. Report inspected and uninspected paths.
2. Compare every present-tense claim and example to the current code/tests/CLI output and tagged v2.0.0 release artifacts where possible. Label MATCH, STALE, AMBIGUOUS, PLANNED, HISTORICAL or NOT_REVIEWED; include file+line evidence and confidence.
3. Specifically investigate latest-release claims in docs/IMPLEMENTATION_STATUS.md, docs/CLI.md, docs/REPOSITORY_GOVERNANCE.md, docs/GITHUB_ACTION.md; Gradle 9.7.0 wrapper vs 9.7.1 documentation; Kotlin 2.4.21 build vs release notes; the literal citation artifact in docs/MCP.md; and the coverage of scripts/audit/docs-parity.sh.
4. Review intended versus effective GitHub main branch protection/rulesets; never assert rules from docs are enforced without API/settings confirmation.
5. Produce an ordered discrepancy register with suggested minimal corrections, their test strategy, affected files and estimated risk.
6. Deliver a read-only report and one proposed docs-only pull request plan. DO NOT edit files, run potentially destructive commands, create commits/branches/PRs, configure GitHub rules, or publish anything. Ask for explicit approval before executing a bounded change.
```

## 8. Decision gates before Sprint 1

Before implementation begins, the human maintainer should approve:
- First target audience and concrete success metric (for example, measured first meaningful verification on JVM repo).
- Meaning and limits of PASS/FAIL/REVIEW_REQUIRED, plus support/trust modes.
- One approved issue/PR with Definition of Done, tested SHA, compatibility and rollback.
- Whether Spec Kit's Antigravity `agy` integration trial is worth the change to workspace-managed files.
- Github ruleset policy and bypass credentials; never let an AI agent silently change security/governance settings.

## 9. Evidence summary / limits

This is an operating model and candidate requirements baseline, not a completed code/release audit. Specific documentation mismatches are supported by source and GitHub metadata. I did not run a clean local Gradle build, test actual CLI release archives, install Spec Kit or Antigravity, check all 53 docs/schema files' semantics, or establish branch protections. Every implementation and governance change remains unapproved.
