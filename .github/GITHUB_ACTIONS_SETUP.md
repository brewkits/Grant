# GitHub Actions Setup Summary

Complete CI/CD setup for Grant - Kotlin Multiplatform Permission Library

## 📋 Overview

This repository now includes a comprehensive GitHub Actions workflow setup with:

- ✅ Continuous Integration (Android & iOS)
- ✅ Code Quality & Linting
- ✅ Code Coverage with Codecov integration
- ✅ Automated dependency updates
- ✅ Release automation
- ✅ Security scanning (CodeQL)
- ✅ PR automation & labeling
- ✅ Issue templates & management

---

## 🚀 Workflows Created

### Core Workflows

| Workflow | File | Purpose | Triggers |
|----------|------|---------|----------|
| **CI** | `ci.yml` | Build, test, coverage | Push, PR, Manual |
| **Code Quality** | `code-quality.yml` | Detekt & Lint | Push, PR, Manual |
| **iOS Build** | `ios.yml` | iOS frameworks & tests | Push, PR, Manual |
| **Release** | `release.yml` | GitHub releases & Maven | Tags `v*`, Manual |
| **CodeQL** | `codeql.yml` | Security analysis | Push, PR, Weekly, Manual |

### Automation Workflows

| Workflow | File | Purpose | Triggers |
|----------|------|---------|----------|
| **PR Comment** | `pr-comment.yml` | Comment build results on PRs | CI completion |
| **Labeler** | `labeler.yml` | Auto-label PRs | PR open/update |
| **Stale** | `stale.yml` | Close inactive issues/PRs | Daily |
| **Greetings** | `greetings.yml` | Welcome first-time contributors | Issue/PR open |

---

## 📁 Files Created

```
.github/
├── workflows/
│   ├── ci.yml                    # Main CI pipeline
│   ├── code-quality.yml          # Detekt & Lint
│   ├── ios.yml                   # iOS builds
│   ├── release.yml               # Release automation
│   ├── codeql.yml                # Security scanning
│   ├── pr-comment.yml            # PR automation
│   ├── labeler.yml               # Auto-labeling
│   ├── stale.yml                 # Stale management
│   ├── greetings.yml             # First-time greetings
│   └── README.md                 # Workflow documentation
├── ISSUE_TEMPLATE/
│   ├── bug_report.md             # Bug report template
│   ├── feature_request.md        # Feature request template
│   └── config.yml                # Issue template config
├── PULL_REQUEST_TEMPLATE.md      # PR template
├── CODEOWNERS                    # Code ownership
├── dependabot.yml                # Dependency updates
├── labeler.yml                   # Labeler configuration
└── GITHUB_ACTIONS_SETUP.md       # This file

Root files:
├── detekt.yml                    # Detekt configuration
```

---

## ⚙️ Configuration Required

### 1. Repository Secrets (for Release workflow)

Go to: `Settings → Secrets and variables → Actions`

Add these secrets:

```
MAVEN_USERNAME      = Your Maven Central username
MAVEN_PASSWORD      = Your Maven Central password
SIGNING_KEY         = GPG signing key
SIGNING_PASSWORD    = GPG key password
```

**Note:** `GITHUB_TOKEN` is automatically provided by GitHub.

### 2. Codecov Integration (Optional)

1. Go to [codecov.io](https://codecov.io)
2. Sign in with GitHub
3. Add your repository
4. Copy the token
5. Add `CODECOV_TOKEN` to repository secrets

**Note:** CI workflow will continue even if Codecov upload fails (`fail_ci_if_error: false`).

### 3. Enable GitHub Features

Go to: `Settings → General`

- ✅ Enable Issues
- ✅ Enable Discussions (recommended)
- ✅ Enable Projects (optional)

Go to: `Settings → Security → Code security and analysis`

- ✅ Enable Dependabot alerts
- ✅ Enable Dependabot security updates
- ✅ Enable CodeQL analysis (or it will auto-enable on first workflow run)

### 4. Branch Protection (Recommended)

Go to: `Settings → Branches → Add rule`

For branch: `main`

Enable:
- ✅ Require a pull request before merging
- ✅ Require status checks to pass before merging
  - Select: `build-and-test`, `detekt`, `lint`
- ✅ Require branches to be up to date before merging
- ✅ Require conversation resolution before merging

---

## 🎯 Quick Start Guide

### Running Workflows Locally

```bash
# Install act (https://github.com/nektos/act)
brew install act

# Run CI workflow locally
act -W .github/workflows/ci.yml

# Run specific job
act -j build-and-test
```

### Manual Workflow Triggers

All main workflows support manual triggers. Go to:
`Actions → Select workflow → Run workflow`

### Creating a Release

```bash
# Create and push a tag
git tag v1.0.1
git push origin v1.0.1

# Or use GitHub UI: Releases → Create a new release
```

### Viewing Coverage Reports

After CI runs:
1. Go to Actions → Select run
2. Download `coverage-reports` artifact
3. Open `index.html` in browser

Or view on Codecov after integration.

---

## 📊 Workflow Status Badges

Add these to your README.md:

```markdown
[![CI](https://github.com/brewkits/Grant/actions/workflows/ci.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/ci.yml)
[![Code Quality](https://github.com/brewkits/Grant/actions/workflows/code-quality.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/code-quality.yml)
[![iOS Build](https://github.com/brewkits/Grant/actions/workflows/ios.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/ios.yml)
[![CodeQL](https://github.com/brewkits/Grant/actions/workflows/codeql.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/codeql.yml)
[![codecov](https://codecov.io/gh/brewkits/Grant/branch/main/graph/badge.svg)](https://codecov.io/gh/brewkits/Grant)
```

---

## 🔄 How It Works

### Pull Request Flow

1. Developer opens PR
2. **Greetings** workflow welcomes first-time contributors
3. **Labeler** auto-assigns labels based on changed files
4. **CI** workflow runs:
   - Builds all modules
   - Runs tests with coverage
   - Builds demo APK
5. **Code Quality** workflow runs (parallel):
   - Detekt static analysis
   - Android Lint
6. **iOS Build** workflow runs (if iOS changes)
7. **PR Comment** workflow posts results
8. After 30 days of inactivity: **Stale** workflow marks PR as stale

### Release Flow

1. Maintainer creates tag: `v1.0.1`
2. **Release** workflow triggers:
   - Builds all modules
   - Runs all tests
   - Creates GitHub Release with notes
   - Uploads library JARs
   - Publishes to Maven Central (if configured)

### Dependency Management

1. **Dependabot** checks weekly (Monday)
2. Creates PRs for:
   - Gradle dependencies
   - GitHub Actions versions
3. **CI** runs on Dependabot PRs
4. Auto-assigns `@brewkits` as reviewer

### Security Monitoring

1. **CodeQL** scans weekly (Monday)
2. Reports findings in Security tab
3. **Dependabot** alerts for vulnerable dependencies
4. Maintainer reviews and fixes

---

## 📈 Monitoring & Maintenance

### Check Workflow Status

```bash
# Using GitHub CLI
gh run list
gh run view <run-id>
gh run watch

# View specific workflow
gh run list --workflow=ci.yml
```

### Debugging Failed Workflows

1. Go to Actions tab
2. Click on failed run
3. Click on failed job
4. Expand failed step
5. Review logs

Common fixes:
- Update deprecated actions
- Adjust detekt rules if too strict
- Update test assertions
- Fix merge conflicts

### Update Workflows

When updating workflows:
1. Make changes in PR
2. Test in PR environment
3. Review workflow run times
4. Optimize if needed (parallel jobs, caching)
5. Update documentation

---

## 🎨 Customization

### Adjust Detekt Rules

Edit `detekt.yml`:
```yaml
# Make rules less strict
complexity:
  LongMethod:
    threshold: 80  # Was 60
```

### Change Stale Timeouts

Edit `.github/workflows/stale.yml`:
```yaml
days-before-issue-stale: 90  # Was 60
days-before-pr-stale: 45     # Was 30
```

### Modify PR Labels

Edit `.github/labeler.yml` to add custom labels:
```yaml
'my-label':
  - changed-files:
    - any-glob-to-any-file: 'path/to/files/**/*'
```

### Add New Workflow

1. Create `.github/workflows/my-workflow.yml`
2. Add documentation to `.github/workflows/README.md`
3. Test with manual trigger first
4. Update this document

---

## 🛡️ Security Best Practices

1. **Secrets Management**
   - Never commit secrets
   - Use GitHub Secrets
   - Rotate keys periodically

2. **Workflow Permissions**
   - Use minimal permissions
   - Avoid `write-all` access
   - Review third-party actions

3. **Dependency Security**
   - Enable Dependabot
   - Review dependency PRs
   - Use version pinning for critical dependencies

4. **Code Scanning**
   - Review CodeQL findings weekly
   - Fix high/critical issues immediately
   - Don't disable security checks

---

## 📚 Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions)
- [Gradle Build Action](https://github.com/gradle/gradle-build-action)
- [Detekt Documentation](https://detekt.dev)
- [Codecov Documentation](https://docs.codecov.com)
- [Dependabot Configuration](https://docs.github.com/en/code-security/dependabot)

---

## 🤝 Contributing to Workflows

When contributing workflow improvements:

1. Follow existing patterns
2. Add documentation
3. Test thoroughly
4. Consider performance impact
5. Update this document

---

## 📞 Support

For workflow issues:
- Check workflow logs in Actions tab
- Review documentation in `.github/workflows/README.md`
- Create issue with `ci-cd` label
- Contact: datacenter111@gmail.com

---

## ✅ Next Steps

After setup:

1. [ ] Add required secrets for Maven publishing
2. [ ] Enable Codecov integration
3. [ ] Add workflow status badges to README
4. [ ] Configure branch protection rules
5. [ ] Review and customize detekt rules
6. [ ] Test release workflow with a test tag
7. [ ] Review first workflow runs
8. [ ] Adjust timeouts/settings as needed

---

**Setup completed!** 🎉

All workflows are ready to use. Push your changes to trigger the first CI run.

```bash
git add .github/ detekt.yml
git commit -m "ci: Add comprehensive GitHub Actions setup"
git push origin main
```

---

*Last updated: 2026-01-30*
*Maintained by: Nguyễn Tuấn Việt (@brewkits)*
