# GitHub Actions CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four GitHub config files (`.github/dependabot.yml` and three workflows under `.github/workflows/`) to give the project automated CI on push/PR, tag-driven releases that attach the fat jar, weekly Dependabot updates with grouped minor/patch PRs, and CodeQL Java security scanning.

**Architecture:** Each file is independent — no shared composite action or reusable workflow (duplication of the 4-line JDK-setup block is smaller than the abstraction would cost). Every workflow targets `ubuntu-latest` with Temurin JDK 25 via `actions/setup-java@v4` (built-in Maven cache). Release workflow uses `softprops/action-gh-release@v2`. Dependabot groups minor + patch Maven updates into a single weekly PR; major updates stay isolated.

**Tech Stack:** GitHub Actions YAML, `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 25 + cache: maven), `softprops/action-gh-release@v2`, `github/codeql-action/{init,analyze}@v3`, Dependabot v2.

**Reference spec:** `docs/superpowers/specs/2026-04-14-github-actions-ci-cd-design.md`

**TDD note for YAML config:** GitHub Actions and Dependabot files cannot be unit-tested locally — they only really run on GitHub. The closest local validation is YAML syntax parsing via Python. Each task therefore replaces "write failing test → run → fail" with "write file → validate syntax → expect parse success". The true behavioral validation lives in the post-merge checks listed at the end of the plan.

---

### Task 1: Add Dependabot configuration

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Create `.github/dependabot.yml` with the exact content below**

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "06:00"
    open-pull-requests-limit: 5
    groups:
      maven-minor-and-patch:
        update-types:
          - "minor"
          - "patch"
    ignore:
      # Spring AI 2.0.0-Mx are milestone releases; bump manually.
      - dependency-name: "org.springframework.ai:*"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "06:00"
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/dependabot.yml'))"`
Expected: no output, exit code 0 (silent success means valid YAML).

- [ ] **Step 3: Commit**

```bash
git add .github/dependabot.yml
git commit -m "$(cat <<'EOF'
chore: configure Dependabot for Maven and GitHub Actions updates

Weekly schedule, Monday 06:00 UTC. Maven minor+patch updates grouped
into a single PR to avoid Spring Boot transitive dep PR-spam. Spring AI
ignored (milestone releases bumped manually). GitHub Actions ecosystem
also tracked so workflow action versions stay current.
EOF
)"
```

---

### Task 2: Add CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create `.github/workflows/ci.yml` with the exact content below**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    name: Build & test
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Build and test
        run: mvn -B verify
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
Expected: no output, exit code 0.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "$(cat <<'EOF'
chore: add CI workflow for build and tests on push/PR to main

Runs mvn verify on ubuntu-latest with Temurin JDK 25. Uses the built-in
Maven cache from setup-java@v4 (keyed on pom.xml hash). Triggers on
every push to main and every PR targeting main — no path filters, so
the status check is always present and usable for branch protection.
EOF
)"
```

---

### Task 3: Add Release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Create `.github/workflows/release.yml` with the exact content below**

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write

jobs:
  release:
    name: Build and publish release
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Build and test
        run: mvn -B verify

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: target/wow-raiderio-mcp.jar
          generate_release_notes: true
          draft: false
          prerelease: false
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`
Expected: no output, exit code 0.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "$(cat <<'EOF'
chore: add release workflow that attaches jar on v* tag push

When a tag matching v* is pushed, runs mvn verify (full test suite) and
then attaches target/wow-raiderio-mcp.jar to a GitHub Release with
auto-generated notes (commit list since the previous tag). Versioning
model: bump pom.xml manually, tag vX.Y.Z, push the tag.
EOF
)"
```

---

### Task 4: Add CodeQL workflow

**Files:**
- Create: `.github/workflows/codeql.yml`

- [ ] **Step 1: Create `.github/workflows/codeql.yml` with the exact content below**

```yaml
name: CodeQL

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 6 * * 0'

permissions:
  security-events: write
  actions: read
  contents: read

jobs:
  analyze:
    name: Analyze (java-kotlin)
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Initialize CodeQL
        uses: github/codeql-action/init@v3
        with:
          languages: java-kotlin

      - name: Build (skip tests)
        run: mvn -B -DskipTests package

      - name: Perform CodeQL analysis
        uses: github/codeql-action/analyze@v3
        with:
          category: "/language:java-kotlin"
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/codeql.yml'))"`
Expected: no output, exit code 0.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/codeql.yml
git commit -m "$(cat <<'EOF'
chore: add CodeQL Java security scan workflow

Runs on push to main, on PRs targeting main, and weekly (Sunday 06:00
UTC). Uses Temurin JDK 25 + manual mvn package (skipping tests, since
those run in ci.yml — CodeQL only needs the bytecode for analysis).
Findings surface in the repo's Security tab.
EOF
)"
```

---

## Post-merge validation (manual, after pushing all four commits)

These are not part of the implementation steps — they happen on GitHub once the commits are pushed. Listed here so the engineer knows what to check.

1. **CI** — open a no-op PR (e.g. README whitespace change). The `Build & test` check should appear and turn green within ~3 minutes.
2. **CodeQL** — after the first push to `main`, the Security → Code scanning tab should populate. First run takes ~5 minutes.
3. **Dependabot** — Insights → Dependency graph → Dependabot tab should show the file as detected and active. First scheduled run is the next Monday at 06:00 UTC.
4. **Release** — bump `pom.xml` `<version>` to `1.0.1`, commit, then `git tag v1.0.1 && git push origin v1.0.1`. Within ~3 minutes the Releases page should show `v1.0.1` with `wow-raiderio-mcp.jar` attached and auto-generated notes listing commits since the previous tag (or all commits if first release).

If any of these fail, inspect the workflow logs in the Actions tab and adjust the relevant file in a follow-up commit.
