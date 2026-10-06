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
      - uses: sonii-shivansh/Vericore@main
        with:
          command: analyze
~~~

For stable consumer workflows, pin the action to a release tag or immutable commit rather than main.

## Verify a prepared change

If your workflow has already created an Agent Change Contract in the checked-out working tree, run:

~~~yaml
- uses: sonii-shivansh/Vericore@main
  with:
    command: verify
~~~

The action does not create a replacement contract. verify uses Vericore's persisted contract boundary and fails when verification fails.

## Supported commands

- analyze — deterministic repository analysis; the default.
- verify — verify the persisted Agent Change Contract.
- pr-intelligence — inspect change impact and PR risk signals.
- architecture-contract — enforce an explicit architecture contract.

Additional command arguments can be supplied with args:

~~~yaml
- uses: sonii-shivansh/Vericore@main
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

Current action targets are Linux x64, macOS x64, and macOS arm64. Windows support is intentionally not claimed by this first action slice.

The action is fail-closed: unsupported commands, unsupported runner platforms, missing checksum entries, checksum mismatches, download failures, and Vericore command failures fail the workflow.

For consumer workflows, GitHub recommends pinning third-party actions to a specific release or immutable commit.
