# Documentation Hub

> Find the shortest path to the information you need.

## Start here

| You are... | Read |
|---|---|
| New to Vericore | [Getting Started](GETTING_STARTED.md) |
| Learning the architecture | [Architecture](ARCHITECTURE.md) |
| Building a feature | [Development](DEVELOPMENT.md) |
| Building an AI-agent integration | [MCP](MCP.md) |
| Preparing or verifying a code change | [Change Safety](CHANGE_SAFETY.md) |
| Integrating the REST API | [API](API.md) |
| Understanding repository state | [Engineering Reality](ENGINEERING_REALITY.md) |
| Reviewing PR/change risk | [PR Intelligence](PR_INTELLIGENCE.md) |
| Reviewing privacy/data flow | [Data & Privacy](DATA_PRIVACY.md) |
| Checking shipped capabilities | [Implementation Status](ENTERPRISE_ROADMAP.md) |
| Reviewing the 0.8.0 release candidate | [Release Readiness](RELEASE_READINESS_0.8.0.md) |

## Recommended paths

### New developer

```text
Getting Started
      ↓
Architecture
      ↓
Development
      ↓
Change Safety
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

### Contributor adding a deterministic capability

```text
Architecture
      ↓
Development
      ↓
PR Intelligence
      ↓
API / MCP
```

### Security review

```text
Data & Privacy
      ↓
Architecture
      ↓
API
      ↓
MCP
      ↓
Change Safety
```

### 0.8.0 release certification

```text
Release Readiness
      ↓
Release Readiness Plan
      ↓
0.8.0 Release Audit
      ↓
Machine-readable readiness certificate
      ↓
Release workflow
```

The release-readiness documents are pre-release operational documents. They are updated before publication so the public 0.8.0 documentation does not depend on a post-release catch-up pass.

## Documentation conventions

Each document should answer, in this order where applicable:

1. **Purpose** — why the document exists.
2. **Audience / when to read** — who needs it.
3. **Quick start** — the shortest useful path.
4. **Concepts and contracts** — behavior that must remain true.
5. **Examples** — copyable commands or payloads.
6. **Failure modes** — what happens when inputs or state are invalid.
7. **Related documents** — where to go next.

Use:

- Mermaid for system and sequence diagrams;
- tables for stable contracts and comparisons;
- fenced code blocks for copyable commands;
- `<details>` only for genuinely optional deep dives;
- explicit **Implemented**, **Current limitation**, and **Future direction** labels when status could otherwise be ambiguous.

## Documentation rules

- Document the current Vericore implementation, not an intended future architecture.
- Keep examples executable or clearly label them as illustrative.
- Avoid repeating the same contract in multiple documents; link to the authoritative document instead.
- Update documentation in the same pull request as behavior or contract changes.
- Never include credentials, private repository content, generated reports, or machine-specific paths.
- Prefer short sections and progressive disclosure over one large reference document.

## Source of truth

When documents disagree, use this order:

1. implemented code and executable tests;
2. public API/schema contracts;
3. architecture and safety documentation;
4. roadmap/future-direction documents.

A roadmap must never be used as evidence that an unimplemented feature exists.
