# API Reference

Vericore exposes two local integration surfaces:

1. the CLI, documented completely in [CLI Reference](CLI.md);
2. the Ktor REST API, documented below.

The REST API is intended for trusted local/internal use. The application does not provide deployment-grade authentication, authorization, tenant isolation, or TLS.

## Start the server

```bash
vericore server --host 127.0.0.1 --port 8080
```

Keep the bind address on loopback for local development.

## CLI

For exact CLI syntax, options, defaults, artifacts, and command workflows, use **[CLI Reference](CLI.md)** rather than duplicating command details here.

## REST endpoints

### `GET /`

Returns the service banner.

### `GET /health`

Returns service health and the current application version.

### `GET /health/live`

Returns the liveness state.

### `GET /health/ready`

Returns the readiness state.

### `GET /reports/{id}.html`

Serves a generated report from the local `output/` directory. Treat generated reports as potentially sensitive because they can contain repository-derived information.

### `POST /analyze`

Analyze an existing local repository and generate an HTML report.

Request:

```json
{
  "repoPath": "/workspace/example"
}
```

The endpoint rejects remote repository URLs. The path must resolve to a readable directory within a configured allowed root. Keep `VERICORE_ALLOWED_PATHS` narrow; by default the server working directory and JVM temporary directory are candidates, but filesystem roots (such as `/` or a Windows drive root) are always excluded. If you need a narrower boundary, set `VERICORE_ALLOWED_PATHS` explicitly.

Response shape:

```json
{
  "fileCount": 42,
  "hotspots": [
    {"file": "PaymentService.kt", "score": 0.42}
  ],
  "reportUrl": "/reports/<id>.html"
}
```

### `POST /impact`

Analyze dependency impact for changed repository-relative paths.

Request:

```json
{
  "repoPath": "/workspace/example",
  "changedPaths": [
    "src/main/kotlin/com/example/PaymentService.kt"
  ]
}
```

At most 100 changed paths are accepted per request.

### `POST /architecture`

Run deterministic Architecture Intelligence.

Request:

```json
{
  "repoPath": "/workspace/example"
}
```

### `POST /pr-intelligence`

Analyze the working tree or an explicit Git revision pair.

Working tree:

```json
{
  "repoPath": "/workspace/example"
}
```

Revision pair:

```json
{
  "repoPath": "/workspace/example",
  "baseRevision": "main",
  "headRevision": "feature/payment-retry"
}
```

`baseRevision` and `headRevision` must either both be present or both be omitted. Each revision is limited to 256 characters.

### `POST /ask`

Ask the configured AI provider a repository question.

Request:

```json
{
  "repoPath": "/workspace/example",
  "question": "Where is authentication configured?"
}
```

AI must be enabled in the effective Vericore configuration. Questions are limited to 16,000 characters. Provider failures are sanitized before they reach the client.

### `POST /analyze-org`

Analyze multiple local repositories with bounded concurrency.

Request:

```json
[
  "/workspace/service-a",
  "/workspace/service-b"
]
```

At most 20 repository paths are accepted per request. Each path is subject to the same repository path-safety boundary.

## Path security

Repository paths are resolved with `toRealPath()` and must be readable directories under one of the configured allowed roots.

Configure allowed roots with:

```text
VERICORE_ALLOWED_PATHS
```

The legacy `CODECONTEXT_ALLOWED_PATHS` variable remains supported only as a migration path. Use the canonical `VERICORE_ALLOWED_PATHS` name for new deployments.

Keep the allowed-root set as narrow as practical.

## Rate limiting

Rate limiting is enabled by default and is applied at the HTTP application boundary. The response includes `Retry-After`, `X-RateLimit-Limit`, and `X-RateLimit-Remaining` headers when the limiter is active.

Example configuration:

```json
{
  "rateLimit": {
    "enabled": true,
    "requestsPerMinute": 60,
    "requestsPerHour": 1000
  }
}
```

## Error contract

Public API errors use a stable shape:

```json
{
  "error": "Invalid or unsafe repository path"
}
```

Internal exception details, provider response bodies, stack traces, credentials, and unnecessary absolute paths are not returned as public API errors.

## AI and privacy boundary

AI is optional. Deterministic analysis does not require an external provider. When `/ask` is invoked with AI enabled, bounded repository-derived context is sent to the configured provider.

See [Data & Privacy](DATA_PRIVACY.md) for the application-level data boundary.

## Current REST boundary

The REST server is a local/internal integration surface. It does not currently provide:

- authentication;
- authorization or tenant isolation;
- deployment-level TLS;
- remote repository cloning;
- autonomous source-code mutation.

For a shared deployment, place an appropriate authenticated service boundary in front of Vericore.
