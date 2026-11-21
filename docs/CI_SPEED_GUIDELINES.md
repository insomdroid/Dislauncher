# CI Speed and Reliability Guidelines

This document preserves the source instructions supplied for this repository and records how they apply to Dislauncher.

## Original source text

Make this repo's CI as fast as possible without letting it lie. Target: a PR gets its result in under 1 minute at the median and under 2 at p95.

Three rules outrank speed:
- The full suite runs before anything reaches main, in a merge queue. Selection only narrows PR feedback.
- Never hide failures. No retries, wider timeouts or skipped tests. A check that needs a retry has a bug: find it.
- Never check less. Lint rules and required checks only stay or grow.

First, audit. Pull the last 150 runs and split every job into waiting, setup and running, at p50 and p95. Fix the biggest bucket first.

Then hit these budgets:
- Waiting, under 5 seconds: move every job off GitHub's hosted runners to Blacksmith and keep machines warm. Cancel a branch's in-progress run when a new commit lands.
- Setup, under 15 seconds: bake the toolchain into the runner image. Get PR cache hits above 95% by seeding caches from main, and cache node_modules itself. Use a blobless clone (`filter: blob:none`).
- Slowest job, under 35 seconds. No required job waits on another.
- Lint, under 1 second: `bun lint`. It reads the existing `.oxlintrc.json`. Turn on every rule you can.
- Format, under 1 second: `bun fmt`.
- Typecheck, under 5 seconds: `bun check` on TypeScript 7.
- Tests, under 30 seconds per shard: `bun test`. Test files keep their `vitest` imports. Isolate modules, not processes, reset state after every file, and run in random order to flush out leaks.
- Install and scripts: bun. Delete npm, yarn, pnpm, Prettier, ESLint, oxlint, Vitest and Jest. If a tool only exists in a canary, pin that exact canary.
- Shards: buy cores before shards, match workers to cores, and give each shard its own throwaway database. Split any test file over 20 seconds. Balance shards from file times that main records on every full run, and prove the shards together ran every file.
- Fewer tests, on evidence: for two weeks, log every test failure you fix as a caught regression or a change detector. Delete the files that only detect change and ban their patterns in AGENTS.md. Never delete tests for authorization, privacy, migrations or concurrency.
- Shape: run lint, typecheck, build and tests in parallel. Fold the small checks into one job that lists every failure.
- Selection: on PRs, run only the tests the diff can reach, and list the inputs that rerun everything. Then hide a real change from it on purpose and confirm CI goes red.
- Local, under 9 seconds: hk hooks. Pre-commit formats and lints staged files; pre-push runs the same check as CI. A hook, not a line in AGENTS.md, because agents skip steps.
- Keep it fast: fail any job that exceeds its budget.

The tools above are defaults; you need a measured reason to keep an old one. Before each switch, run old and new side by side and list anything the new one misses.

One small PR per change, with before and after numbers from real runs. Do everything you can without me first; bring anything that needs an account, a secret or a bigger bill as one list with expected monthly cost. End with total CI time and cost, before and after.

## Dislauncher-specific application

Dislauncher is an Android/Kotlin/Jetpack Compose project built with Gradle. The Bun, TypeScript, Node.js, node_modules, Vitest, and TypeScript-specific commands above do not apply and must not be added to this Android build.

The equivalent principles for this repository are:

- Keep the complete Android verification suite: `lintRelease`, `testDebugUnitTest`, and the installable `assembleRelease` task.
- Run checks for pull requests and merge-queue candidates. A merge-queue candidate must receive the full required checks before it can enter `main`.
- Keep failures visible. Do not add retries, skip tests, or relax test assertions to make CI green.
- Keep Gradle's build cache enabled. The repository already has `org.gradle.caching=true` and uses the Gradle Actions setup/cache step; measure cache restore/save time before changing providers.
- Avoid cloning the complete Git history when the build only needs release tags. Fetch a shallow source checkout and fetch the required tags separately.
- Pin actions to immutable commit SHAs, keep the Android SDK/JDK versions aligned with the project, and update the Gradle Wrapper when the Android Gradle Plugin minimum version changes.
- Do not add third-party hosted runners or paid warm-machine services without measuring the hosted-runner baseline and presenting the monthly cost first.
- Do not select tests by changed files until dependency coverage is proven. The current required unit-test task remains the full `testDebugUnitTest` suite.
- Keep release signing secrets out of pull-request and merge-queue builds. Those events still run lint, unit tests, and APK assembly with CI/debug signing.

## Initial repository findings (21 November 2026)

- The latest successful Nightly run inspected was run #999. It completed in 247 seconds (4 minutes 7 seconds).
- The inspected pull-request run #1000 failed before Gradle tasks could execute because the Wrapper was 9.5.0 while the Android Gradle Plugin required at least 9.6.0.
- The workflow already uses Gradle Actions caching and `org.gradle.caching=true`. The successful run restored about 627 MB of Gradle cache entries; cache restore time was about 23 seconds according to the action summary.
- The workflow previously used `actions/checkout` with `fetch-depth: 0`, downloading full Git history even though version resolution only reads release tags.
- The available Actions API response in this audit exposed only five recent runs, not 150. Therefore a valid 150-run p50/p95 breakdown is not yet available and no percentile target is claimed as achieved.

## Changes made from this audit

1. Upgrade the Gradle Wrapper to 9.6.0, the minimum version reported by the failing Android Gradle Plugin check.
2. Change checkout to depth 1 and fetch release tags separately, preserving the existing development-version calculation without fetching full history.
3. Add the `merge_group` event so merge-queue candidates run the same full verification tasks.
4. Keep the existing Gradle cache, full lint/test/build tasks, pinned action SHAs, and failure behavior. No tests are removed or skipped.

## Measurement policy

Record total run duration and per-job setup/run durations from real Actions runs. Compare at least 10 successful comparable runs before setting a new time budget; use 150 runs when the API/history is available. Do not claim the screenshot's sub-minute median or sub-two-minute p95 targets until measured data proves them. Revisit runner providers, cache strategy, task parallelism, and test sharding only with side-by-side measurements and a documented correctness check.
