---
name: migrate-test
license: Apache-2.0
description: Migrate one named Keycloak Arquillian test from testsuite/integration-arquillian/tests/base to the current JUnit framework using the repository migration script, then compile and validate it with user-approved test execution.
---

# Migrate a Keycloak Arquillian test

Act as a test lead preserving observable behavior while adopting the checked-out framework's conventions. Migrate one named test with the repository script and only the support code it requires. Repository examples inform implementation; they do not override the user's stated preferences. Do not change product code, perform unrelated refactoring, commit, push, or open a PR without separate instructions. Resolve uncertainty from repository evidence; ask when a behavioral decision or unsafe state remains.

## 1. Establish the revision and protect existing work

Accept a class name without requiring a path. Locate the intended Keycloak checkout, read applicable repository instructions, and resolve exactly one concrete test under `testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/`. For missing, ambiguous, or abstract-only matches, explain the candidates and ask for direction.

Inspect worktree status (including untracked files), branches, and `origin`. Require the intended Keycloak remote and a clean worktree; do not discard, overwrite, reset, rebase, or stash user work without permission. Fetch the latest `origin/main`, record its revision, and create `<ClassName>_Branch` directly from it. If fetching/authentication fails, the branch exists, or switching is unsafe, stop and ask; do not silently substitute a branch name or base.

After switching revisions, re-resolve the source and recheck applicable instructions, the migration script, and relevant framework evidence. Earlier paths, API assumptions, and examples are provisional until verified on this revision; repeat this check after any later revision switch.

Before conversion, inspect `migration-util/migrate.sh` and its relevant helpers to establish invocation, source selection, output paths, and side effects. The expected invocation is `./migrate.sh <ClassName>` from `migration-util/`, targeting the corresponding package under `tests/base/src/test/java/org/keycloak/tests/`. If the checked-out script differs, resolve the discrepancy before running it. Check its prospective outputs for existing tracked, untracked, or ignored files and search the target feature for an equivalent migration, including renamed classes. If a target already exists, compare coverage and report whether it is complete or partial; ask how to proceed rather than overwrite it or create a duplicate.

## 2. Establish behavior and dependencies

Build a compact scenario map from the source, its superclass chain, and relevant helpers: inputs/variants, fixtures, action, observable assertions, and planned target equivalents. Include inherited tests, parameterized cases, negative paths, rules, setup/teardown, and enabled/disabled conditions. Preserve meaningful cases and assertion strength; mark omissions or changed expectations only when user direction or revision-specific repository evidence justifies them. An unresolved behavioral difference blocks claiming equivalence.

Use focused file and symbol search. Start with migrated tests and reusable abstract bases in the same feature/package, then the nearest neighboring folder, broader `tests/base/` examples, and framework documentation only as needed. For example, when migrating an OIDC test, inspect relevant abstract test classes in that feature if present. Stop expanding exploration once the needed API, lifecycle, and runner decisions are supported. Record useful source locations beside decisions so conversion does not repeat discovery.

Trace inherited injections, fixture ownership, hooks, and helpers before choosing a target base. Check whether a migrated base supplies the same behavior and resource configuration, including hook ordering and overrides. Reuse compatible inherited resources without shadowing or redeclaring fields. Identify missing support classes, providers, test resources, and module dependencies before conversion. Convert required support code only when its behavior is clear and the change stays within this migration; inspect its existing consumers and output collisions too. If it requires a broader migration, affects shared behavior, or cannot be resolved reliably, explain the dependency and ask for direction.

## 3. Convert and align with the framework

Run the verified repository migration script. Review its output and diff against the expected files and scenario map; generated code is an intermediate result. Stop to diagnose a failure, unexpected target, or ambiguous output before editing further. Do not blindly rerun the script over partial output or replace it with a broad manual rewrite.

Make the smallest changes supported by the checked-out framework and nearby migrated tests:

- Match local formatting, structure, JUnit annotations, and assertion style. Verify API signatures and configuration behavior in source; examples are patterns, not guarantees across revisions.
- Prefer framework-managed resources and public APIs: `oauth.clientResource()` for the configured client's admin resource, `realm.admin()` for realm operations, and framework event assertions where supported. Use server-side execution only when the tested behavior needs it and explain the tradeoff.
- Express managed realm/client/user setup through typed `RealmConfig`, `ClientConfig`, and `UserConfig` implementations and their matching builders. Use `@InjectClient(config = ...)` for configured clients and `@InjectOAuthClient(config = ...)` when configuring the OAuth helper. Move configuration-only setup into these configs; keep lifecycle methods for genuine per-test behavior.
- Prefer `BasicRealmWithUserConfig` when its standard user fits; otherwise use default injection or the smallest necessary custom config. Do not create a config solely to preserve a realm name: use the managed realm's actual name in URLs and assertions. Replace generated legacy configuration only after accounting for behavior it supplied.
- Use bare `@KeycloakIntegrationTest` unless the test demonstrably needs a server configuration supported by current framework evidence.
- Check fixture creation, reset, and disposal at method/class scope, including inherited hooks. Ensure shared realms, clients, users, events, sessions, server settings, and time offsets cannot leak state or require test ordering. Verify cleanup on failure and ownership before replacing manual teardown with managed lifecycle.
- Retain assertions for outcomes, side effects, and expected failures, including relevant event contents. Remove unused injections, page fixtures, locals, and captured event results only after checking inherited use and lifecycle effects.

Reconcile every scenario with its target method/variant and assertions. Keep the legacy source until the removal decision in step 5.

## 4. Compile and obtain execution evidence

Derive the narrowest supported test-compilation and focused execution commands from this revision's build configuration and guidance. Check module selection, JUnit engine, annotations, runner includes/excludes, profiles, parameter sources, and required fixtures/services. Ensure compilation actually includes the migrated source and required support code while disabling test execution; do not assume a Maven skip flag or phase has the desired behavior.

Compile and fix migration-caused errors supported by framework evidence. Classify failures by the failing phase, diagnostic, and affected code: missing artifacts/services, toolchain or network problems, and unrelated module failures do not establish a migration defect. Resolve clear in-scope conversion errors; report environmental/unrelated blockers separately and ask before broader setup changes or uncertain workarounds. Do not weaken assertions or remove scenarios to obtain a pass.

After successful compilation, report the command and propose the focused test command, then explicitly ask whether to run it. Wait for the user's reply; compilation is not completion. If execution is declined or unanswered, leave the migration compile-ready and unvalidated.

When approved, run the focused test and inspect fresh runner reports, not just the exit status. Confirm the intended class, inherited tests, parameterized variants, and expected scenarios were discovered and executed; account for skips and disabled conditions. Zero discovered tests, stale reports, or silently excluded scenarios are not a pass. Diagnose discovery and migration defects within scope; use the failure classification above for blockers. Compare runtime coverage with the scenario map and report anything unverified.

## 5. Resolve legacy removal and report

After the migrated scenarios pass, proactively ask whether to remove the corresponding legacy test source and wait for the answer. Delete it only with approval; retain it if declined or unanswered. Approval for that source does not cover other legacy files or shared helpers. Before deletion, check references and consumers; pause if removal would break dependencies or require broader changes, then review the deletion diff separately.

Completion requires the recorded branch/base, preserved scenario coverage, successful compilation, an approved focused run with verified discovery and execution, and a resolved legacy-removal decision. Otherwise report the specific pending decision or blocker rather than declaring completion.

Report source/target and necessary support paths, branch/base revision, substantive changes and their evidence, compile/test commands and actual outcomes (including discovered/executed/skipped coverage), legacy retention/removal, and remaining limitations. Distinguish code-based expectations from observed execution.
