# GitHub Actions CI/CD — Design

**Date**: 2026-04-14
**Status**: Approved (brainstorming phase)
**Repo**: `bnjdpn/wow-raiderio-mcp` (public)

## Goal

Add GitHub Actions to the project for:

1. **CI** — automated build + tests on every push to `main` and every pull request.
2. **Release** — when a `v*` tag is pushed, build the fat jar and attach it to a GitHub Release with auto-generated notes.
3. **Hygiene** — Dependabot for weekly Maven + GitHub Actions updates, and CodeQL for Java security scanning.

The repo is public, so all GHA usage (CI, CodeQL, Dependabot) is free and unmetered on `ubuntu-latest`.

## Non-goals

- Publishing to Maven Central, GitHub Packages, or any artifact registry.
- Deploying anywhere — the artifact is a STDIO MCP server, distributed via README-driven local install or now via Releases.
- Multi-OS / multi-JDK matrix builds — JDK is pinned to 25, runner is Linux only.
- Branch protection rule configuration — that is a GitHub UI concern outside the scope of these workflows. The CI status check it produces will be available if the user later wants to require it.
- Conventional-commits / release-please style automation — kept out for simplicity.

## Architecture

```
.github/
├── workflows/
│   ├── ci.yml        # Build + tests on push to main / PR
│   ├── release.yml   # Tag v* → build + attach jar to GitHub Release
│   └── codeql.yml    # Java security scan (push, PR, weekly cron)
└── dependabot.yml    # Weekly updates: Maven + github-actions
```

Four files. No shared composite action, no reusable workflow — duplication of the JDK-setup block across `ci.yml`, `release.yml`, `codeql.yml` is small enough (4 lines) that an abstraction would cost more than it saves.

## Workflow specifications

### `ci.yml`

**Triggers**
- `push` on `main`
- `pull_request` targeting `main`

No `paths-ignore` — README/docs PRs run a full build too. Keeps the status check unconditional, which matters if branch protection is enabled later.

**Job: `build`**
- Runner: `ubuntu-latest`
- Steps:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` with `distribution: temurin`, `java-version: '25'`, `cache: maven`
  3. `mvn -B verify`

The built-in `cache: maven` of `setup-java@v4` handles `~/.m2/repository`. No separate `actions/cache` step.

`-B` (batch mode) suppresses Maven's interactive download progress bars, keeping the log readable.

`verify` runs unit + integration tests via Surefire and any post-test verifications (none configured today, but future-proof).

### `release.yml`

**Trigger**
- `push` of a tag matching `v*` (e.g. `v1.1.0`, `v2.0.0-beta1`)

**Job: `release`**
- Runner: `ubuntu-latest`
- Permissions: `contents: write` (needed to create the release)
- Steps:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (same JDK 25 + maven cache as CI)
  3. `mvn -B verify` — runs the full test suite before publishing. Adds ~1 min to the release flow but prevents publishing a broken jar.
  4. `softprops/action-gh-release@v2` with:
     - `files: target/wow-raiderio-mcp.jar`
     - `generate_release_notes: true` (uses GitHub's native commit-list generator, zero maintenance)
     - `draft: false`, `prerelease: false`
     - Uses the implicit `${{ github.token }}` for auth

**Versioning model**: the developer manually bumps `pom.xml` `<version>`, commits, tags `vX.Y.Z`, pushes the tag. The workflow does not rewrite `pom.xml`. This is safe here because `<finalName>wow-raiderio-mcp</finalName>` produces a versionless jar name, so pom-version drift never affects the artifact name attached to the release.

### `codeql.yml`

Standard GitHub-recommended template for Java, adapted to the project's JDK.

**Triggers**
- `push` on `main`
- `pull_request` targeting `main`
- `schedule`: weekly cron, Sunday 06:00 UTC (`0 6 * * 0`)

**Job: `analyze`**
- Runner: `ubuntu-latest`
- Permissions: `security-events: write`, `actions: read`, `contents: read`
- Steps:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (JDK 25 Temurin + maven cache) — required because CodeQL must compile the project to extract its database
  3. `github/codeql-action/init@v3` with `languages: java-kotlin`
  4. `mvn -B -DskipTests package` — explicit build step so CodeQL can observe compilation. Skipping tests here is correct: CodeQL only needs the bytecode, and tests already run in `ci.yml`.
  5. `github/codeql-action/analyze@v3`

### `dependabot.yml`

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
        update-types: ["minor", "patch"]
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

**Rationale**:
- **Grouping**: minor + patch Maven updates land in a single grouped PR per week. Without this, Spring Boot's transitive dep tree generates 5–10 PRs/week.
- **Major updates**: stay ungrouped (one PR each), since they require manual review.
- **Spring AI ignore**: `2.0.0-M3` is a pre-release. Dependabot already skips pre-releases by default for normal version progressions, but the explicit ignore makes the intent obvious to anyone reading the file and prevents accidental bumps if Spring AI pushes a `2.0.0` GA before the project is ready.

## Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| JDK 25 not yet available in `actions/setup-java@v4` | Temurin 25 is GA; verified before merge. Fallback: pin to a specific patch (e.g. `java-version: '25.0.1'`). |
| Maven cache eviction makes builds slow | `setup-java@v4` cache is keyed by `pom.xml` hash; only invalidates when deps change. Acceptable. |
| Release workflow runs on tags pushed accidentally to forks | Workflow runs in the fork's own GHA context; no access to upstream repo's token. Non-issue. |
| `softprops/action-gh-release@v2` is third-party | Pinned to `v2` major; widely used and the de-facto standard for attaching artifacts to releases. |
| CodeQL build fails because Maven needs the milestone repo | The `pom.xml` already declares `spring-milestones` repository. CodeQL's `mvn package` will resolve through it. |

## Testing the workflows

After merge:

1. **CI** — verify by opening a trivial PR (e.g. README typo) and confirming the `build` check turns green.
2. **CodeQL** — verify by checking the Security tab populates after the first push to `main`.
3. **Dependabot** — verify by checking that the Insights → Dependency graph → Dependabot tab shows it as enabled, and that the first weekly run produces grouped PRs.
4. **Release** — verify by tagging a no-op `v1.0.1` (after a trivial pom bump) and confirming the jar appears under Releases.

## File-by-file deliverables

1. `.github/workflows/ci.yml` — ~25 lines
2. `.github/workflows/release.yml` — ~30 lines
3. `.github/workflows/codeql.yml` — ~40 lines (standard template)
4. `.github/dependabot.yml` — ~25 lines

Total: ~120 lines of YAML across 4 new files. No source code changes.
