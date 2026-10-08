# Vericore Repository Governance

This document is the version-controlled source of truth for the GitHub repository's intended governance. It describes intended GitHub settings as well as repository-file controls; it does not claim that every UI setting is currently enforced unless the repository configuration confirms it.

## Repository identity

- **Name:** Vericore
- **Public description:** The verification layer for AI coding agents. AI writes the code. Vericore verifies the change.
- **Default branch:** main
- **Visibility:** Public
- **Homepage:** https://sonii-shivansh.github.io/Vericore-Website/
- **Canonical icon:** docs/images/vericore-icon.svg
- **Current published release:** v0.8.2

The canonical icon is the project icon used by the website. Do not create a second logo variant unless branding is intentionally changed.

## Repository-file controls

- DCO verification runs independently on pull requests targeting main.
- Release Audit is the authoritative PR release-certification workflow for main.
- Reusable CI, verification, platform, onboarding, regression, and live-repository workflows should not be treated as independent release decisions when composed by Release Audit.
- Dependabot configuration covers Gradle and GitHub Actions.
- Security vulnerability reporting is governed by SECURITY.md.
- Issue templates and the pull-request template are the contribution intake contract.
- Intentional CodeContext migration/compatibility references must not be removed merely to make a text search return zero results.

## GitHub Settings target

Apply these settings in Repository → Settings. Where a setting is already correct, leave it unchanged.

### Features

- Issues: ON
- Projects: ON
- Discussions: ON
- Wiki: OFF when documentation is maintained under docs/
- Preserve public visibility and the GitHub Pages website

### Pull requests

Preferred merge policy:
- Allow squash merging: ON
- Allow merge commits: OFF
- Allow rebase merging: OFF
- Automatically delete head branches: ON
- Auto-merge: OFF unless deliberately adopted later
- Require conversation resolution through the main ruleset

### main ruleset

- Require a pull request before merging.
- Do not require an approving review while this remains a single-maintainer repository; revisit when independent maintainers are available.
- Require conversation resolution.
- Require branches to be up to date before merging.
- Block force pushes.
- Block branch deletion.
- Allow administrators to bypass rules for emergency maintenance.

### Required status checks

The intended merge gates are:
1. Release Audit — authoritative candidate certification on pull requests targeting main.
2. Check commit sign-offs — independent DCO governance.

Do not add every reusable workflow as an individual required check when Release Audit already composes them.

Do not require CodeQL yet. It should become merge-blocking only after its configuration is deliberately reviewed and its check is confirmed stable for this repository.

## Releases

- Do not rewrite published release tags or artifacts.
- v0.8.2 is the current published release.
- main contains post-v0.8.2 development and is not automatically a release candidate.
- Future releases should originate from the release workflow after normal verification gates pass.
- Release artifacts should remain reproducible and accompanied by checksums where the release workflow provides them.

## Security and quality

- Keep SECURITY.md as the private vulnerability-reporting path.
- Keep dependency automation enabled.
- Review GitHub Actions dependencies during routine maintenance.
- Never add credentials, private source material, or generated local state to the repository.

## Branding / migration rule

CodeContext is retained only where it represents intentional compatibility or migration behavior. Public product copy, repository identity, and current documentation should use Vericore.

## Change checklist

Before merging a repository-governance change:
- [ ] Product identity is Vericore.
- [ ] Canonical icon is unchanged unless branding was intentionally updated.
- [ ] No intentional compatibility path was removed.
- [ ] PR and issue templates remain valid.
- [ ] DCO passes.
- [ ] Release Audit passes when required.
- [ ] Release tags/artifacts were not rewritten.
- [ ] Final GitHub Settings state matches this document.
