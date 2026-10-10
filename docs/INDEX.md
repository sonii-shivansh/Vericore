# Documentation Hub

> The shortest path to the right Vericore documentation.

## Start here

| Need | Read |
|---|---|
| **Understand Vericore quickly** | **[README](../README.md)** |
| **Understand why Vericore exists** | **[Why Vericore?](WHY_VERICORE.md)** |
| **Understand the master roadmap** | [Product Direction](PRODUCT_DIRECTION.md) |
| Track foundation work | [Phase 0 Foundation Checklist](PHASE0_FOUNDATION_CHECKLIST.md) |
| Install and run Vericore | [Getting Started](GETTING_STARTED.md) |
| **Use a command** | **[CLI Reference](CLI.md)** |
| Understand the system | [Architecture](ARCHITECTURE.md) |
| Analyze or review a change | [Change Safety](CHANGE_SAFETY.md) |
| Integrate an AI agent | [MCP](MCP.md) |
| Integrate REST | [API](API.md) |
| Understand repository identity | [Engineering Reality](ENGINEERING_REALITY.md) |
| Understand deterministic PR review | [PR Intelligence](PR_INTELLIGENCE.md) |
| Review data handling | [Data & Privacy](DATA_PRIVACY.md) |
| Contribute to Vericore | [Development](DEVELOPMENT.md) |
| Check implemented capabilities | [Implementation Status](IMPLEMENTATION_STATUS.md) |
| Review the latest published release record | [0.8.2 Release Readiness](RELEASE_READINESS_0.8.2.md) |
| Review the previous release record | [0.8.1 Release Readiness](RELEASE_READINESS_0.8.1.md) |
| Review the earlier release record | [0.8.0 Release Readiness](RELEASE_READINESS_0.8.0.md) |

## Recommended reading paths

### New user

~~~text
README
   ↓
Why Vericore?
   ↓
Getting Started
   ↓
CLI Reference
~~~

### Developer

~~~text
README
   ↓
Getting Started
   ↓
CLI Reference
   ↓
Architecture
   ↓
Development
~~~

### Safe code-change workflow

~~~text
README
   ↓
Why Vericore?
   ↓
Change Safety
   ↓
prepare → change → verify
~~~

### AI-agent integration

~~~text
README
   ↓
Why Vericore?
   ↓
MCP
   ↓
Change Safety
~~~

### Release review

~~~text
Release Readiness / Release Record
      ↓
Release certification
      ↓
Release workflow
~~~

## Documentation rules

The documentation set has four purposes:

1. **User documentation** — installation, commands, APIs, and integrations.
2. **Engineering documentation** — architecture, safety boundaries, development, and implementation status.
3. **Release documentation** — release certification evidence and the current release record.
4. **Project policy** — contribution, security, DCO, conduct, licensing, and trademarks at the repository root.

Historical implementation notes may explain a design decision, but they must not be presented as current product instructions. The main branch may contain unreleased hardening after the latest published tag.

### Source of truth

When documents disagree, use this order:

1. implemented code and executable tests;
2. public CLI/API/MCP contracts;
3. architecture and safety documentation;
4. implementation-status and release documents;
5. historical design notes.

A roadmap or historical design note never proves that an unimplemented capability exists.

### Command documentation rule

Every user-facing CLI command must have its syntax, options, defaults, outputs, and important failure semantics documented in [CLI Reference](CLI.md). Update that document in the same change whenever the CLI contract changes.

### Avoid duplication

- Keep the README focused on product behavior and the fastest path to first use.
- Keep Getting Started task-oriented; do not turn it into a second command reference.
- Keep API and MCP documents focused on their respective protocols.
- Keep product explanation in [Why Vericore?](WHY_VERICORE.md), rather than duplicating it across technical documents.
- Prefer links to authoritative contracts instead of copying the same rules into multiple documents.

### Automated parity

The CI workflow in `.github/workflows/docs-parity.yml` builds the CLI and compares its runtime `--help` command surface with the command headings in [CLI Reference](CLI.md). A newly added or removed user-facing CLI command therefore fails CI until the reference is updated in the same change.
