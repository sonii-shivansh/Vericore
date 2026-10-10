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
      - uses: sonii-shivansh/Vericore@v0.8.2
        with:
          command: analyze
~~~

The examples below use `v0.8.2`, the latest published Vericore release at this revision. Update the reference deliberately when a newer release is published. For stricter supply-chain controls, pin the action to a reviewed immutable commit SHA.

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

      - uses: sonii-shivansh/Vericore@v0.8.2
        with:
          command: pr-intelligence
          args: --base ${{ github.event.pull_request.base.sha }} --head ${{ github.event.pull_request.head.sha }} --json
~~~

The reusable Action produces `output/pr-intelligence.json`. A separate trusted workflow can consume that artifact and comment on the PR.

## Supported commands

- analyze — deterministic repository analysis; the default.
- verify — verify the persisted Agent Change Contract.
- pr-intelligence — inspect change impact and PR risk signals.
- architecture-contract — enforce an explicit architecture contract.

Additional command arguments can be supplied with args:

~~~yaml
- uses: sonii-shivansh/Vericore@v0.8.2
  with:
    command: verify
    args: --contract-only
~~~

## Inputs

| Input | Default | Purpose |
|---|---|---|
| command | analyze | Vericore command to execute |
| path | . | Repository path |
| version | latest | Published release tag, such as v0.8.2 |
| args | empty | Additional command arguments |

## Security and portability

The action downloads only published Vericore release archives from GitHub Releases and verifies the archive against the published SHA256SUMS file before execution.

The PR review pattern deliberately separates unprivileged analysis from privileged commenting. Do not replace it with a `pull_request_target` workflow that checks out and executes an untrusted pull-request revision.

Current action targets are Linux x64, macOS x64, and macOS arm64. Windows support is intentionally not claimed by this first action slice.

The action is fail-closed: unsupported commands, unsupported runner platforms, missing checksum entries, checksum mismatches, download failures, and Vericore command failures fail the workflow.

For consumer workflows, use an explicit published release tag or, for stronger integrity guarantees, a reviewed immutable commit SHA.

