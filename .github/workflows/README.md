# GitHub Actions Workflows

This directory contains all CI/CD workflows for the Grant project.

## Workflows Overview

### 🔨 CI Workflow (`ci.yml`)

**Trigger:** Push to `main`, Pull Requests, Manual

**Purpose:** Main continuous integration pipeline

**Steps:**
1. Checkout code
2. Set up JDK 17
3. Build `grant-core` module
4. Run all tests with coverage (Kover)
5. Build `grant-compose` module
6. Build demo Android app
7. Upload artifacts:
   - Build reports (7 days)
   - Test results (7 days)
   - Coverage reports (14 days)
   - Demo APK (14 days)
8. Upload coverage to Codecov

**Artifacts:**
- `build-reports`: All build and test reports
- `coverage-reports`: Kover coverage reports (XML + HTML)
- `demo-apk`: Debug APK of the demo app

---

### 🔍 Code Quality Workflow (`code-quality.yml`)

**Trigger:** Push to `main`, Pull Requests, Manual

**Purpose:** Static code analysis and linting

**Jobs:**

#### 1. Detekt
- Runs Detekt static analysis
- Uploads detekt reports (7 days)
- Continues on error to allow viewing results

#### 2. Android Lint
- Runs Android Lint on all modules
- Uploads lint reports (7 days)
- Continues on error to allow viewing results

**Artifacts:**
- `detekt-reports`: Detekt analysis results
- `lint-reports`: Android Lint results (HTML + XML)

---

### 🍎 iOS Build Workflow (`ios.yml`)

**Trigger:** Push to `main`, Pull Requests, Manual

**Purpose:** Build and test iOS frameworks

**Steps:**
1. Checkout code
2. Set up JDK 17 and Xcode
3. Build iOS frameworks:
   - `linkDebugFrameworkIosArm64` (physical devices)
   - `linkDebugFrameworkIosSimulatorArm64` (simulator)
4. Run iOS tests on simulator
5. Build Compose iOS frameworks (if available)
6. Upload artifacts:
   - iOS frameworks (7 days)
   - iOS test results (7 days)

**Requirements:**
- Runs on `macos-latest` runner
- Requires Xcode

**Artifacts:**
- `ios-frameworks`: Built iOS frameworks
- `ios-test-results`: iOS test results

---

### 💬 PR Comment Workflow (`pr-comment.yml`)

**Trigger:** When CI workflow completes

**Purpose:** Automatically comment on PRs with build results

**Features:**
- Posts build status (✅ passed / ❌ failed)
- Links to full build log
- Lists available artifacts
- Only comments on PRs (not direct pushes)

**Permissions Required:**
- `pull-requests: write`
- `contents: read`

---

### 🚀 Release Workflow (`release.yml`)

**Trigger:**
- Push tags matching `v*` (e.g., `v1.0.1`)
- Manual trigger with version input

**Purpose:** Automated release process

**Steps:**
1. Checkout code with full history
2. Build and test all modules
3. Create GitHub Release with:
   - Generated release notes
   - Library JARs attached
4. Publish to Maven Central (requires secrets)

**Required Secrets:**
- `GITHUB_TOKEN` (auto-provided)
- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

**Usage:**
```bash
# Create and push a tag
git tag v1.0.1
git push origin v1.0.1

# Or use manual trigger from GitHub Actions UI
```

---

## Configuration Files

### Dependabot (`dependabot.yml`)

**Purpose:** Automatic dependency updates

**Configuration:**
- **Gradle dependencies**: Weekly updates (Monday)
- **GitHub Actions**: Weekly updates (Monday)
- Auto-assigns to `@brewkits` reviewer
- Labels: `dependencies`, `gradle`/`github-actions`
- Commit prefix: `deps:` or `ci:`

### Code Owners (`CODEOWNERS`)

**Purpose:** Automatic PR review assignment

**Owners:**
- Default: `@brewkits`
- Core modules: `@brewkits`
- Documentation: `@brewkits`
- Workflows: `@brewkits`
- Build configs: `@brewkits`

### Detekt Configuration (`../detekt.yml`)

**Purpose:** Kotlin static analysis configuration

**Features:**
- Complexity checks (max 15 lines per method)
- Naming conventions enforcement
- Coroutines best practices
- Potential bug detection
- Code style enforcement (120 char line limit)
- Empty block detection
- Exception handling rules

---

## Badges

Add these badges to your README:

```markdown
[![CI](https://github.com/brewkits/Grant/actions/workflows/ci.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/ci.yml)
[![Code Quality](https://github.com/brewkits/Grant/actions/workflows/code-quality.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/code-quality.yml)
[![iOS Build](https://github.com/brewkits/Grant/actions/workflows/ios.yml/badge.svg)](https://github.com/brewkits/Grant/actions/workflows/ios.yml)
[![codecov](https://codecov.io/gh/brewkits/Grant/branch/main/graph/badge.svg)](https://codecov.io/gh/brewkits/Grant)
```

---

## Local Testing

### Run tests locally with coverage
```bash
./gradlew :grant-core:allTests koverXmlReport koverHtmlReport
# View coverage: open grant-core/build/reports/kover/html/index.html
```

### Run detekt locally
```bash
./gradlew detekt
# View report: open build/reports/detekt/detekt.html
```

### Run Android Lint locally
```bash
./gradlew lintDebug
# View reports in **/build/reports/lint-results-*.html
```

### Build iOS frameworks locally
```bash
./gradlew :grant-core:linkDebugFrameworkIosSimulatorArm64
```

---

## Troubleshooting

### CI Failing on iOS
- Ensure Xcode version is compatible
- Check Info.plist has required keys
- iOS tests may need real device or specific simulator

### Coverage Not Uploading to Codecov
- Ensure `CODECOV_TOKEN` is set in repository secrets
- Check XML report is generated at expected path

### Dependabot PRs Failing
- Review gradle.properties for version compatibility
- May need to update multiple dependencies together

### Detekt Failing
- Review detekt.yml configuration
- Some rules can be disabled if too strict
- Run locally first: `./gradlew detekt`

---

## Workflow Maintenance

### Updating Gradle Version
Dependabot will automatically create PRs for:
- Gradle plugin updates
- Kotlin version updates
- Library dependency updates

Review and merge these PRs regularly.

### Updating GitHub Actions
Dependabot will automatically update action versions:
- `actions/checkout`
- `actions/setup-java`
- `gradle/actions/setup-gradle`
- etc.

### Adding New Modules
When adding new modules, update:
1. `ci.yml` - Add build steps
2. `code-quality.yml` - Add lint steps
3. `ios.yml` - Add iOS framework builds (if applicable)

---

## Performance Tips

1. **Gradle Caching**: Workflows use `setup-gradle` with cache
2. **Cache Strategy**: Read-only cache for PR branches, read-write for main
3. **Parallel Jobs**: Code quality runs in parallel with main CI
4. **Artifact Retention**: Shorter retention for frequent artifacts

---

## Security

### Secrets Management
Never commit secrets. Use GitHub Secrets:
- Repository Settings → Secrets and variables → Actions
- Add required secrets for Maven publishing

### Dependency Scanning
Dependabot also provides security updates for vulnerable dependencies.

### Code Scanning
Consider adding CodeQL workflow for security scanning:
- GitHub Security tab → Enable CodeQL

---

## Contributing

When modifying workflows:
1. Test locally using `act` (GitHub Actions local runner)
2. Create a PR to test in CI environment
3. Update this README with changes
4. Review workflow run times and optimize if needed

---

## Support

For issues with workflows:
- Check [GitHub Actions documentation](https://docs.github.com/en/actions)
- Review workflow run logs in Actions tab
- Create issue in repository
