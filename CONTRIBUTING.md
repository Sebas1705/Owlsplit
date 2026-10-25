# Contributing

## Branch naming

- `feature/<name>` — new features
- `fix/<name>` — bug fixes
- `chore/<name>` — tooling, deps, refactor
- `docs/<name>` — documentation only

## Commit style

```
<type>(<scope>): <short description>

feat(home): add pull-to-refresh
fix(auth): prevent crash on empty token
chore(deps): upgrade Ktor to 3.1.0
```

## PR checklist

Use the PR template — all boxes must be checked before merge.

## Code style

Enforced automatically by Detekt. Run `./gradlew detekt` before pushing.
Max line length: 140. No wildcard imports in non-Compose files.

## Coverage

Kover merges the debug unit tests of every library module (`./gradlew koverHtmlReportUnitTests`). CI fails when line
coverage drops below the `minBound` in the root `build.gradle.kts`; raise it when coverage grows, never lower it.

## Adding a module

Follow MODULE_MAP.md#adding-a-new-feature-module.

## Semantic versioning

- `MAJOR` — breaking public API change
- `MINOR` — new feature, backward-compatible
- `PATCH` — bug fix

Tag releases with `v<MAJOR>.<MINOR>.<PATCH>`. The release workflow publishes binaries automatically.
