# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| `0.8.x` | Yes — current Vericore release line |
| `0.7.x` | Limited; upgrade to Vericore `0.8.2` recommended |
| `0.6.x` and older | No |

## Reporting a vulnerability

Do not disclose security vulnerabilities in a public issue. Report them privately to **shivanshsoni568@gmail.com** with:

- a concise description;
- affected version, commit, or deployment mode;
- reproduction steps or proof of concept;
- security impact and possible mitigations;
- relevant logs with secrets removed.

Do not include API keys, credentials, private source code, or personal data unless absolutely necessary.

The project aims to acknowledge reports within 48 hours and provide an initial assessment within seven days. Fix and disclosure timelines depend on severity, reproducibility, and release risk.

## Security model

Vericore is designed primarily for local analysis:

- analyzed source files are parsed and never executed;
- Git operations are read-only from the application's perspective;
- server paths are canonicalized and restricted to allowed roots;
- configurable file limits and rate limiting reduce resource abuse;
- generated reports use random identifiers rather than source directory names;
- provider credentials are sent in headers and are not intentionally logged;
- AI features are disabled by default and may send repository-derived context to an external provider when enabled;
- grounded evidence uses repository-relative paths and bounded context;
- generated HTML reports are self-contained and do not require a third-party browser asset at open time.

The REST server does **not** provide authentication, tenant isolation, report authorization, TLS termination, report expiration, or a complete public-internet deployment boundary. The MCP stdio server also assumes a trusted local caller. Add those controls before exposing it outside a trusted local or internal network.

## Agentic security boundary

Autonomous code modification is not a current default capability. Before any agent can modify a repository, Vericore should enforce:

- isolated workspaces;
- explicit repository and directory allowlists;
- protected-file policies;
- operation-level permissions;
- maximum change-size limits;
- secret and sensitive-data boundaries;
- required tests and CI gates;
- human approval for configured high-risk actions;
- complete action/provenance logging;
- deterministic post-change verification.

A model's confidence must never substitute for an authorization or verification decision.

## Deployment guidance

- Bind the server to `127.0.0.1` for local use.
- Configure `VERICORE_ALLOWED_PATHS` to the smallest required set of directories.
- Keep `.vericore.json` and API keys out of source control.
- Place the server behind authentication, TLS, request quotas, and a trusted-origin policy when deployed remotely.
- Review reports before sharing them because they may contain file names, paths, Git authors, commit messages, and source-derived descriptions.
- Keep dependencies and the JDK patched.

## Contact

Security reports: **shivanshsoni568@gmail.com**
