# Contributing to Vericore

Thank you for contributing to Vericore. Contributions should improve analysis accuracy, developer experience, reliability, or security without weakening the project's local-first safety model.

Vericore is open to community contributions under the MIT License. See [DCO.md](DCO.md) for contribution provenance requirements.

## Before you start

1. Search existing issues and pull requests.
2. For significant behavior or API changes, open an issue or discussion first.
3. Never include credentials, private source code, generated reports, `.vericore/`, or build output in a contribution.
4. For security vulnerabilities, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.

## Development setup

Requirements:

- JDK 21+
- Git
- Kotlin-capable editor

```bash
git clone https://github.com/sonii-shivansh/Vericore.git
cd Vericore
./gradlew --no-daemon clean test
./gradlew --no-daemon installDist
```

For exact Vericore command syntax and output contracts, use [docs/CLI.md](docs/CLI.md).

## Development workflow

1. Create a focused branch from `main`.
2. Understand the existing contract before changing behavior.
3. Implement the smallest coherent change.
4. Add or update tests, especially for public APIs and security boundaries.
5. Update the relevant documentation in the same change.
6. Sign every contribution commit with `git commit -s`.
7. Run local validation where available.
8. Push the branch and open a pull request.
9. Inspect the complete GitHub Actions matrix before merge.

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon build installDist
```

For release-candidate work, the authoritative product certification is the `0.8.0 Release Audit`; reusable CI workflows are composed by that audit rather than being treated as independent release decisions.

## Developer Certificate of Origin

Every contribution commit must contain a `Signed-off-by:` trailer confirming that the contributor has the right to submit the work under the project's licensing terms.

```bash
git commit -s -m "Describe the change"
```

If a commit was created without the sign-off, amend it before opening or updating the pull request:

```bash
git commit --amend -s
```

See [DCO.md](DCO.md) for details.

## Coding expectations

- Follow Kotlin coding conventions and existing formatting.
- Prefer structured concurrency; do not introduce `runBlocking` inside suspend code paths.
- Validate input at system boundaries.
- Keep errors stable and sanitized for API consumers.
- Do not expose absolute server filesystem paths, stack traces, provider response bodies, or credentials.
- Use configuration rather than duplicating operational limits.
- Preserve cancellation, bounded concurrency, and deterministic tests.
- Add KDoc for public APIs where useful.

## Tests

Tests are grouped under `src/test/kotlin/com/vericore` and include core analysis, server/security, verification, property/edge-case coverage, and end-to-end behavior.

Security-sensitive changes should include tests for traversal, sibling-prefix paths, symlinks where applicable, malformed input, and error sanitization.

## Pull request checklist

- [ ] The change is focused and documented.
- [ ] Tests cover the changed behavior.
- [ ] Every contribution commit has a `Signed-off-by:` trailer.
- [ ] `./gradlew --no-daemon clean test` passes.
- [ ] `./gradlew --no-daemon build installDist` passes.
- [ ] Required GitHub Actions checks pass.
- [ ] DCO passes.
- [ ] No secrets or generated files are included.
- [ ] CLI/API/architecture/security documentation is updated when applicable.
- [ ] The complete diff against `main` has been reviewed.

## Reporting bugs and requesting features

Use the GitHub issue templates where available. Include the Vericore version, JDK version, operating system, exact command, sanitized logs, and a minimal reproduction. Do not publish sensitive source code or security vulnerabilities in a public issue.
