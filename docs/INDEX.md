# Vericore documentation

This hub is organized by **what you are trying to do**. Most users only need the first two sections; the remaining pages are reference material for integrators, contributors, and release maintainers.

## Start here

| If you want to… | Start with |
|---|---|
| Understand what Vericore does and why it exists | [Why Vericore?](WHY_VERICORE.md) |
| Install it and run your first analysis | [Getting Started](GETTING_STARTED.md) |
| Verify a change made by an AI coding agent | [Change Safety](CHANGE_SAFETY.md) |
| Find a command's exact syntax | [CLI Reference](CLI.md) |

**Recommended first session:** [README](../README.md) → [Getting Started](GETTING_STARTED.md) → [CLI Reference](CLI.md).

## Integrate Vericore

| Guide | Use it for |
|---|---|
| [MCP / AI-agent integration](MCP.md) | Connect a local coding agent to Vericore |
| [REST API](API.md) | Request and response contracts, routes, and trust boundaries |
| [GitHub Action](GITHUB_ACTION.md) | Run Vericore in GitHub Actions |
| [Data & Privacy](DATA_PRIVACY.md) | Understand local behavior and optional provider-backed data flow |
| [Support Matrix](SUPPORT_MATRIX.md) | Check supported languages, platforms, interfaces, and known limitations |

## Technical reference

Use these pages when you need implementation detail rather than a first-run tutorial.

| Reference | Scope |
|---|---|
| [Architecture](ARCHITECTURE.md) | Components, data flow, and trust boundaries |
| [Repository Map](REPOSITORY_MAP.md) | Source folders, packages, tests, and automation |
| [Engineering Reality](ENGINEERING_REALITY.md) | Repository-state identity and compatibility across artifacts |
| [Artifact Schema Catalog](ARTIFACT_SCHEMA_CATALOG.md) | Versioned artifact contracts and schema ownership |
| [Semantic Evidence Graph](SEMANTIC_EVIDENCE_GRAPH.md) | Evidence provenance, stable graph identity, and relationship constraints |
| [CLI Output Contracts](CLI_OUTPUT_CONTRACTS.md) | Machine-readable and human-readable output behavior |
| [Deterministic Evidence Evaluators](DETERMINISTIC_EVIDENCE_EVALUATORS.md) | Supported criterion syntax and evaluation limits |
| [PR Intelligence](PR_INTELLIGENCE.md) | Deterministic pull-request analysis |
| [Verification Benchmark](VERIFICATION_BENCHMARK.md) | Benchmark methodology and interpretation limits |
| [JSON schemas](schemas/) | Machine-readable interface contracts |

## Contribute and maintain the project

| Page | Audience |
|---|---|
| [Development Guide](DEVELOPMENT.md) | Contributors building, testing, and extending Vericore |
| [Implementation Status](IMPLEMENTATION_STATUS.md) | What is implemented, what is limited, and what remains |
| [Product Direction](PRODUCT_DIRECTION.md) | Longer-term roadmap; roadmap items are not proof of shipped behavior |
| [Repository Governance](REPOSITORY_GOVERNANCE.md) | Repository policy, CI, and release process |

## Engineering records and release history

These files are intentionally more detailed than user guides. They exist to preserve test baselines, acceptance criteria, and release-specific evidence; most new users do not need to read them.

<details>
<summary>Foundation and acceptance records</summary>

- [Phase 0 Foundation Checklist](PHASE0_FOUNDATION_CHECKLIST.md)
- [Phase 0 Benchmark Baseline](PHASE0_BENCHMARK_BASELINE.md)
- [Phase 1 Acceptance Checklist](PHASE1_ACCEPTANCE_CHECKLIST.md)
- [Images documentation](images/README.md)

</details>

<details>
<summary>Release-readiness records (historical or candidate-specific)</summary>

Release evidence applies only to the version and exact commit it names. It does not certify later commits. The current published release is shown on [GitHub Releases](https://github.com/sonii-shivansh/Vericore/releases).

- [v1.0.0 release gate](RELEASE_READINESS_1.0.0.md) — candidate checklist, not a claim that v1.0.0 is published
- [v0.9.0 release readiness](RELEASE_READINESS_0.9.0.md)
- [v0.8.2 release readiness](RELEASE_READINESS_0.8.2.md)
- [v0.8.1 release readiness](RELEASE_READINESS_0.8.1.md)
- [v0.8.0 release readiness](RELEASE_READINESS_0.8.0.md)
- [Changelog](../CHANGELOG.md) — user-visible changes by version

</details>

## Community and repository policies

These files stay at the repository root so GitHub and contributors can discover them:

- [Contributing](../CONTRIBUTING.md)
- [Security Policy](../SECURITY.md)
- [Code of Conduct](../CODE_OF_CONDUCT.md)
- [DCO](../DCO.md)
- [Trademarks](../TRADEMARKS.md)
- [MIT License](../LICENSE)

## Keep documentation useful

1. **One canonical contract per interface.** CLI behavior belongs in [CLI Reference](CLI.md), REST in [API](API.md), MCP in [MCP](MCP.md), and artifact compatibility in [Artifact Schema Catalog](ARTIFACT_SCHEMA_CATALOG.md). Other pages should link instead of duplicating the full contract.
2. **Describe shipped behavior honestly.** Code, executable tests, and public contracts take precedence over roadmaps and plans.
3. **Keep user journeys short.** The README introduces the product; Getting Started helps a user succeed; reference pages answer specific questions.
4. **Preserve release evidence.** Do not rewrite a historical readiness record or benchmark to match later code. Add a new record for a new candidate.
5. **Prefer focused documents.** Merge duplicate explanations when possible, but retain records that provide unique compatibility, safety, test, or release evidence.
