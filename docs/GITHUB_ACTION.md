# GitHub Action

Vericore provides a reusable composite GitHub Action for running deterministic repository verification and intelligence commands in CI.

## Quick start

The action installs a published Vericore release, verifies its SHA-256 checksum, and runs the selected command.

~~~yaml
name: Vericore

on:
  pull_request:

jobs:
  vericore:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: sonii-shivansh/Vericore@v1.0.0
        with:
          command: analyze
~~~

The examples target `v1.0.0`. Use that tag only after it appears on the [GitHub Releases page](https://github.com/sonii-shivansh/Vericore/releases); until publication, use the latest available published tag. For stricter supply-chain controls, pin the action to a reviewed immutable commit SHA.

## PR review automation

Vericore can publish deterministic PR intelligence as a sticky pull-request comment without executing the pull request with write credentials.

The recommended pattern is two-stage:

1. A `pull_request` workflow checks out the merge commit with a read-only token and runs `pr-intelligence`.
2. The result is uploaded as a short-lived artifact.
3. A trusted `workflow_run` workflow reads only that artifact and updates one PR comment.

This separation is intentional. The privileged comment workflow never checks out or executes pull-request source code.

The reference implementation in this repository is:

- `.github/workflows/vericore-pr-review.yml`
- `.github/workflows/vericore-pr-review-comment.yml`

The review comment reports risk, change size, impacted files, cross-package impact, test candidates, and the highest-priority deterministic findings. Subsequent pushes update the existing Vericore comment instead of creating an unbounded comment stream.

### Consumer analysis workflow

A consumer can use the reusable Action directly:

~~~yaml
name: Vericore PR Review

on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read

jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
          persist-credentials: false

      - uses: sonii-shivansh/Vericore@v1.0.0
        with:
          command: pr-intelligence
          args: --base ${{ github.event.pull_request.base.sha }} --head ${{ github.event.pull_request.head.sha }} --json
~~~

The reusable Action produces `output/pr-intelligence.json`. A separate trusted workflow can consume that artifact and comment on the PR.

## Supported commands

- analyze — deterministic repository analysis; the default.
- prepare — persist an engineering plan and Agent Change Contract before coding. Requires `change-summary`.
- verify — verify the persisted Agent Change Contract.
- pr-intelligence — inspect change impact and PR risk signals.
- architecture-contract — enforce an explicit architecture contract.

Additional command arguments can be supplied with args:

~~~yaml
- uses: sonii-shivansh/Vericore@v1.0.0
  with:
    command: verify
    args: --contract-only
~~~

## Verification step summary

When `command: verify` runs, the action writes a concise GitHub Actions step summary with the overall outcome, changed/unexpected path counts, criterion status counts, and criterion IDs/statuses. It deliberately omits arbitrary criterion statements and free-form reasons from the summary to reduce Markdown-injection and sensitive-output risks; use the verification logs and JSON report for detailed reasons.

If the JSON report or Python 3 is unavailable, the action emits a warning and preserves the original Vericore exit status. A caller-supplied `--output` path is respected.

This behavior is present in the source branch. Consumers receive it only from the published action tag they choose.

For `command: prepare`, the action passes `change-summary` as the required summary and `path` as the repository. Optional `agent-provider`, `agent-id`, `agent-session-id`, and `agent-event-id` inputs are forwarded as opaque correlation metadata. When preparation runs in GitHub Actions, Vericore records the trusted source as `github-action`; the source is not an input and cannot be supplied by the caller. IDs must not contain prompts, source code, credentials, or private model reasoning. Supplying an ID without `agent-provider` fails closed. The installed Vericore version must support Agent Context v1 for these options to work.

## Inputs

| Input | Default | Purpose |
|---|---|---|
| command | analyze | Vericore command to execute: analyze, prepare, verify, pr-intelligence, architecture-contract |
| path | . | Repository path |
| version | latest | Published release tag, such as v1.0.0 |
| args | empty | Additional command arguments |
| change-summary | empty | Required for prepare; summary of the planned change |
| agent-provider | empty | Optional provider slug for prepare |
| agent-id | empty | Optional opaque agent identity |
| agent-session-id | empty | Optional opaque session correlation ID |
| agent-event-id | empty | Optional opaque event correlation ID |

## Security and portability

The action downloads only published Vericore release archives from GitHub Releases and verifies the archive against the published SHA256SUMS file before execution.

The PR review pattern deliberately separates unprivileged analysis from privileged commenting. Do not replace it with a `pull_request_target` workflow that checks out and executes an untrusted pull-request revision.

Current action targets are Linux x64, macOS x64, and macOS arm64. Windows support is intentionally not claimed by this first action slice.

The action is fail-closed: unsupported commands, unsupported runner platforms, missing checksum entries, checksum mismatches, download failures, and Vericore command failures fail the workflow.

For consumer workflows, use an explicit published release tag or, for stronger integrity guarantees, a reviewed immutable commit SHA.

