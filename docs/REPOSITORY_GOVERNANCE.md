# Vericore Repository Governance

This document is the version-controlled source of truth for the GitHub repository's intended governance. It deliberately separates repository-file controls from GitHub UI controls that are not represented in the source tree.

## Repository identity

- **Name:** `Vericore`
- **Description:** `Evidence-grounded engineering intelligence for your codebase.`
- **Default branch:** `main`
- **Visibility:** Public
- **Homepage:** `https://sonii-shivansh.github.io/Vericore-Website/`
- **Canonical icon:** `docs/images/vericore-icon.svg`
- **Current published release:** `v0.8.0`

The website favicon SVG is the canonical project icon. Do not create a second logo variant in the repository unless the website branding is intentionally changed first.

## Repository-file controls

These controls live in Git and should be reviewed like code:

- DCO verification runs independently on pull requests targeting `main`.
- The `Release Audit` is the authoritative PR release-certification workflow for `main` and composes the reusable CI, verification, platform, onboarding, regression, and live-repository workflows.
- `ci.yml`, `verification.yml`, `platform.yml`, `onboarding-e2e.yml`, `phase1-regression.yml`, `phase4-regression.yml`, `live-repository-gate.yml`, and `live-output-quality.yml` are reusable workflows and are not independent PR-triggered merge gates.
- Dependabot configuration covers Gradle and GitHub Actions.
- Security vulnerability reporting remains governed by `SECURITY.md`; vulnerabilities should not be disclosed through public issues.
- Issue templates and the pull-request template are the contribution intake contract.
- Intentional `CodeContext` migration/compatibility references must not be removed merely to make a text search return zero results.

## GitHub Settings target

Apply these settings in **Repository → Settings**. Where a setting is already correct, leave it unchanged.

### Features

- Issues: **ON**
- Projects: **ON**
- Discussions: **ON**
- Wiki: **OFF** when repository documentation is maintained exclusively under `docs/`
- Preserve the existing public visibility and GitHub Pages website

### Pull requests

Preferred merge policy:

- Allow squash merging: **ON**
- Allow merge commits: **OFF**
- Allow rebase merging: **OFF**
- Automatically delete head branches: **ON**
- Auto-merge: **OFF unless deliberately adopted later**
- Require conversation resolution through the `main` ruleset

Squash merging keeps `main` history focused on reviewable changes while preserving the complete development history in pull requests.

### `main` ruleset

Create or update a ruleset for `main` with these requirements:

- Require a pull request before merging.
- **Do not require an approving review while this remains a single-maintainer repository.** Revisit this when independent collaborators/maintainers are available.
- Require conversation resolution.
- Require branches to be up to date before merging.
- Block force pushes.
- Block branch deletion.
- Allow administrators to bypass rules for emergency maintenance.

#### Required status checks

The current workflow architecture has one authoritative product/release certification check plus independent DCO governance:

1. **Release Audit** — authoritative candidate certification on pull requests targeting `main`;
2. **Check commit sign-offs** — independent DCO governance check.

The reusable workflows called by the release audit should not be added individually as required status checks, because that would duplicate the certification graph at the branch-ruleset layer.

Do **not** require CodeQL yet. CodeQL is currently managed by GitHub's dynamic code-scanning workflow and should only become merge-blocking after its configuration is deliberately reviewed and its check is confirmed stable for this repository.

### Releases

- Do not rewrite the published `v0.7.0` tag or its artifacts.
- `v0.8.0` is now the current published release and was created by the repository's release workflow after all verification gates passed.
- Future releases should originate from the repository's release workflow after the normal verification gates pass.
- Release artifacts must remain reproducible and accompanied by checksums where the release workflow provides them.

## Security and quality

- Keep `SECURITY.md` as the private vulnerability-reporting path.
- Keep dependency automation enabled.
- Review GitHub Actions dependencies during routine maintenance.
- Never add credentials, private source material, or generated local state to the repository.

## Branding / migration rule

`CodeContext` is retained only where it represents intentional compatibility or migration behavior. Examples include legacy configuration/environment variables, legacy directory names, migration tooling, and backward-compatible aliases. Public product copy, repository identity, and current documentation should use **Vericore**.

## Change checklist

Before merging a repository-governance change:

- [ ] Product identity is Vericore.
- [ ] Canonical icon is unchanged unless branding was intentionally updated.
- [ ] No intentional compatibility path was removed.
- [ ] PR template and issue templates remain valid.
- [ ] DCO passes.
- [ ] Release Audit passes when release or governance changes require certification.
- [ ] Release tags/artifacts were not rewritten.
- [ ] The final GitHub Settings state matches this document.
