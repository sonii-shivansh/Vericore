# Vericore Product Direction

> AI writes the code. Vericore verifies the change.

Vericore is being evolved into an engineering intelligence and verification layer for AI-assisted software development.

## Product pipeline

`repository facts → evidence → deterministic analysis → AI interpretation → recommendation → verification`

AI is an optional interpretation layer. Repository facts and verification decisions remain deterministic and auditable.

## First-run journey

1. `vericore init`
2. `vericore analyze .`
3. `vericore ask "..."`
4. `vericore prepare "..."`
5. An AI coding agent makes the change.
6. `vericore verify`
7. `vericore report`

## Public command direction

The existing specialized commands remain available for compatibility. The product-level surface should converge on:

- `init` — initialize the project
- `scan` — understand the repository
- `inspect` — inspect architecture, dependencies and risk
- `ask` — grounded repository questions
- `review` — review a Git diff/change
- `plan` — produce a deterministic engineering plan
- `prepare` — establish a change contract
- `verify` — verify an agent change
- `report` — produce a complete engineering session report
- `doctor` — diagnose installation and configuration

Advanced commands remain available underneath this layer.

## Output contract

Human-readable output is the default. Machine-readable JSON remains available for automation.

Product-level commands should converge on repository, result/status, key findings, AI interpretation when enabled, artifacts, duration and next step.

## Roadmap

### P0 — product foundation
- `init`
- unified command output
- `scan`
- durable session recording
- complete `report`
- consistent `--json` support

### P1 — trusted AI
- grounded AI explanations for analysis results
- evidence IDs and grounding score in AI answers
- AI-assisted change review
- feature-gap and recommendation engine

### P2 — agent ecosystem
- polished MCP auto-configuration
- verified integrations for Cursor, Claude Code, Gemini CLI, Codex and GitHub Copilot
- agent-facing skills/commands

### P3 — team control plane
- GitHub PR review and comments
- architecture policy enforcement
- project memory
- organization/cross-repository intelligence
- web dashboard and audit history

### P4 — commercial
- open-core developer CLI
- Pro/team capabilities around AI, history, policy and collaboration
- enterprise private deployment, SSO/RBAC and audit controls

Pricing should be validated against real developer and team usage before publication.
