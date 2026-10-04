# Documentation Hub

> The shortest path to the right Vericore documentation.

## Start here

| Need | Read |
|---|---|
| Install and run Vericore | [Getting Started](GETTING_STARTED.md) |
| **Use a command** | **[CLI Reference](CLI.md)** |
| Understand the system | [Architecture](ARCHITECTURE.md) |
| Analyze or review a change | [Change Safety](CHANGE_SAFETY.md) |
| Integrate REST | [API](API.md) |
| Integrate an AI agent | [MCP](MCP.md) |
| Understand repository identity | [Engineering Reality](ENGINEERING_REALITY.md) |
| Understand deterministic PR review | [PR Intelligence](PR_INTELLIGENCE.md) |
| Review data handling | [Data & Privacy](DATA_PRIVACY.md) |
| Contribute to Vericore | [Development](DEVELOPMENT.md) |
| Check implemented capabilities | [Implementation Status](IMPLEMENTATION_STATUS.md) |
| Review the current 0.8.1 release candidate | [Release Readiness](RELEASE_READINESS_0.8.1.md) |
| Review the previous 0.8.0 release record | [0.8.0 Release Readiness](RELEASE_READINESS_0.8.0.md) |

## Recommended reading paths

### New user

```text
Getting Started
      ↓
CLI Reference
      ↓
Architecture
```

### Developer

```text
Getting Started
      ↓
CLI Reference
      ↓
Architecture
      ↓
Development
```

### Safe code-change workflow

```text
CLI Reference
      ↓
Engineering Reality
      ↓
Change Safety
      ↓
prepare → change → verify
```

### AI-agent integration

```text
Architecture
      ↓
Engineering Reality
      ↓
MCP
      ↓
Change Safety
```

### Release review

```text
Release Readiness / Release Record
      ↓
Release certification
      ↓
Release workflow
```

## Documentation rules

The documentation set has four purposes:

1. **User documentation** — installation, commands, APIs, and integrations.
2. **Engineering documentation** — architecture, safety boundaries, development, and implementation status.
3. **Release documentation** — release certification evidence and the current release record.
4. **Project policy** — contribution, security, DCO, conduct, licensing, and trademarks at the repository root.

Historical implementation notes may explain a design decision, but they must not be presented as current product instructions.

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

- Keep the README concise and link to the CLI reference instead of duplicating every command.
- Keep Getting Started task-oriented; do not turn it into a second command reference.
- Keep API and MCP documents focused on their respective protocols.
- Prefer links to authoritative contracts instead of copying the same rules into multiple documents.
