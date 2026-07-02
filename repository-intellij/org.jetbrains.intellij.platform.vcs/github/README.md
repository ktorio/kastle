# GitHub Pack

Generates `.github/` files for IntelliJ Platform plugin projects hosted on GitHub.

## What it generates

- `.github/workflows/build.yml` — CI workflow: builds, tests, and verifies the plugin on every push and pull request; creates a draft GitHub release on pushes to `main`
- `.github/workflows/release.yml` — Release workflow: triggered when a GitHub Release is published; patches the CHANGELOG, publishes the plugin to JetBrains Marketplace, uploads the ZIP artifact, and opens a PR to commit the updated CHANGELOG
- `.github/dependabot.yml` — Keeps Gradle dependencies and GitHub Actions up to date automatically (weekly schedule)
- `.github/ISSUE_TEMPLATE/bug-report.yml` — Structured bug report template
- `.github/ISSUE_TEMPLATE/feature-request.yml` — Structured feature request template

## Required secrets

To enable publishing from the release workflow, configure these repository secrets in **Settings → Secrets and variables → Actions**:

| Secret | Description |
|---|---|
| `PUBLISH_TOKEN` | JetBrains Marketplace token |
| `CERTIFICATE_CHAIN` | Plugin signing certificate chain |
| `PRIVATE_KEY` | Plugin signing private key |
| `PRIVATE_KEY_PASSWORD` | Password for the private key |
