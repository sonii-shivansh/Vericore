# PR Intelligence

PR Intelligence converts a local Git change set into a deterministic engineering assessment. It combines Git diff metadata, dependency impact, existing engineering-risk signals, package boundaries, architecture signals, and likely test candidates.

## CLI

The complete command syntax is maintained in [CLI Reference](CLI.md). The PR Intelligence-specific forms are:

```bash
vericore pr-intelligence /path/to/repository --json

vericore pr-intelligence /path/to/repository \
  --base main \
  --head feature/my-change \
  --json
```

The JSON report is written to `output/pr-intelligence.json` when JSON output is requested.

`--base` and `--head` must be supplied together. If neither is supplied, the current working tree is analyzed.

## REST

`POST /pr-intelligence` accepts a local repository path and optionally a base/head revision pair. Remote repository URLs are deliberately rejected by the current local endpoint.

```json
{
  "repoPath": "/workspace/example",
  "baseRevision": "main",
  "headRevision": "feature/my-change"
}
```

Omit both revisions to analyze the working tree. Supplying only one revision is rejected.

## Result

The result is versioned and machine-readable:

- `changeSummary`: file and line change counts;
- `impactedFiles`: dependency-aware blast radius;
- `impactedPackages`: affected package count;
- `crossPackageImpacts`: impact crossing changed package boundaries;
- `testCandidates`: likely tests, explicitly described as candidates rather than coverage proof;
- `findings`: deterministic rule-based evidence;
- `aggregateSeverity`: highest finding severity.

Every finding has a stable rule identifier, severity, affected repository-relative paths, evidence values, and a human-readable reason.

## Rules

| Rule | Meaning |
| --- | --- |
| `CHANGE_UNRESOLVED` | A changed/deleted path could not be resolved in the analyzed graph. |
| `IMPACT_BROAD` | The dependency blast radius is substantial. |
| `ARCH_CROSS_PACKAGE` | Impact crosses the package boundary of changed files. |
| `CHANGED_HIGH_RISK_COMPONENT` | A high/critical deterministic risk component was modified. |
| `TEST_CANDIDATE_MISSING` | No likely test candidate was identified. |
| `CHANGE_LARGE` | The change is large by file or line count. |

These are review signals. They do not prove runtime correctness, test sufficiency, or production impact.

## Evidence-first workflow

```text
Git working tree / revision pair
              ↓
       GitChangeSetBuilder
              ↓
       PRIntelligenceAnalyzer
              ↓
       Impact / Risk / Architecture / Test signals
              ↓
        Versioned PR result
              ↓
      Grounded evidence / planner / AI
```

The deterministic core has no dependency on an LLM or GitHub API. The CLI and server can therefore use the result as a stable review signal even when AI is disabled. This makes it suitable for CI and allows AI layers to explain evidence without becoming the source of truth.

## Current boundary

PR Intelligence currently produces deterministic review signals and machine-readable evidence. It does **not** claim to prove runtime correctness, test coverage, deployment safety, or production impact, and it does not autonomously approve, merge, or modify a change.
