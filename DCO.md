# Developer Certificate of Origin

Vericore welcomes community contributions under the project's open-source license.

To keep the contribution history clear, Vericore uses the Developer Certificate of Origin (DCO) for contributions.

By adding a `Signed-off-by:` trailer to a commit, you certify that you have the right to submit the contribution under the project's licensing terms. The sign-off is a provenance and authorship safeguard; it does not transfer ownership of your contribution.

The required trailer has this form:

```text
Signed-off-by: Your Name <you@example.com>
```

Use Git's sign-off option when creating commits:

```bash
git commit -s -m "Describe the change"
```

The DCO check is enforced independently by CI for pull requests targeting `main`. If a commit is missing the trailer, amend it with `git commit --amend -s` and push the updated branch.

See the official [Developer Certificate of Origin](https://developercertificate.org/) for the full text.
