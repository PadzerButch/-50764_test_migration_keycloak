---
name: migrate-test
license: Apache-2.0
description: Migrate or resume migrating one named Keycloak Arquillian test to the current JUnit framework using the repository migration script, preserving behavior, assertions and suite coverage with minimal changes and user-approved test execution.
---

# Migrate a Keycloak Arquillian test

Act as a test lead preserving observable behavior while adopting the checked-out framework's conventions. Migrate one concrete test and only its necessary support code and suite registrations. Make the smallest changes supported by the checked-out framework and nearby migrated tests. Do not change product code, perform unrelated refactoring, commit, push, or open a PR without separate instructions. Honor the user's preferences and authorization already given.

## 1. Investigate

Locate the intended Keycloak checkout, read applicable instructions, and resolve the named class under `testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/`. Ask for direction for missing, ambiguous, or abstract-only matches.

For a new migration, inspect branches, `origin`, and worktree status including untracked files. Verify the intended remote and require a clean worktree; fetch `origin/main`, record its revision, and create `<ClassName>_Branch` directly from it. If fetching fails, the branch exists, or switching is unsafe, stop and ask rather than substituting a base/name. Never discard, overwrite, reset, rebase, or stash user work without permission.

For an explicitly resumed migration, inspect the existing branch/base, diff, partial output and prior validation instead of creating a branch or rerunning conversion. Continue from the last evidenced stage. After any revision switch, re-resolve source, instructions, script and framework evidence.

Inspect `migrate.sh` and its relevant helpers in the checked-out migration utility (often `tests/migration-util/`): invocation, working directory, source selection, destination and build side effects. Respect execution authorization for any tests the script runs. Check tracked, untracked and ignored output collisions and equivalent migrations, including renamed classes. Compare an existing target's coverage and ask how to proceed; do not overwrite or duplicate it. Resolve a missing script or unsupported routing before conversion.

Build a compact scenario map: **source method/variant → fixture and identity → action → assertions → target equivalent → validation status**. Include superclass tests, helpers' implicit checks, parameters, negative paths, rules, hooks and execution conditions. Preserve original purpose, behavior and assertion strength. For a negative case, identify why it fails and which fixture restrictions make that failure meaningful; a generic error from a different cause is not equivalent.

Search the repository for the class name, fully qualified name, superclass and relevant package selectors in suite definitions, POMs, profiles and CI configuration. Establish whether JVM, cluster or other advanced suites register or select it; record actual membership and required updates. Do not infer membership from OIDC or a class name containing “Advanced”.

## 2. Plan the smallest implementation

Search same-feature migrated tests and abstract bases, then nearby folders, broader examples and framework source/docs only as needed. Prefer an existing abstract test class when its fixtures, inherited tests, helpers and hook ordering preserve the scenario map. Trace inherited resources before adding or shadowing fields. Stop exploration once API, lifecycle and runner decisions have supporting source locations.

For **every added injection, fixture, annotation, helper, configuration or lifecycle change**, establish:

- Which original behavior or demonstrated framework requirement needs it?
- Does the framework, a compatible base or an existing fixture already provide it?
- Can defaults or a simpler supported operation preserve the behavior?

Keep a short rationale beside non-obvious decisions in working notes. Omit additions without a demonstrated purpose. Never inject WebDriver unless the test genuinely requires direct browser operations or explicit browser configuration that existing resources do not supply. Using an OAuth helper or page alone does not justify a driver field.

Use bare `@KeycloakIntegrationTest` and framework defaults when sufficient. Do not add scopes, custom realms, browser cleanup, lifecycle overrides or helpers merely because an example uses them. Check required support classes, providers, resources, dependencies, consumers and output collisions; ask before broader migrations or shared behavior changes.

## 3. Consult relevant references

Load a reference only for a requirement identified above, or an observed failure. These are conditional decision aids, not implementation templates. Verify relevant APIs and defaults on the checked-out revision; do not copy every resource or configuration in an example.

| Identified requirement | Reference |
| --- | --- |
| OAuth flows, client ownership, tokens or events | [OAuth and events](references/oauth-and-events.md) |
| Realm/user/client fixtures, inherited resources or reset timing | [Resources and lifecycle](references/resources-and-lifecycle.md) |
| Direct browser operations, cookies, independent sessions or page migration | [UI migration](references/ui-migration.md) |
| Rules, parameters, conditions, discovery or advanced suite membership | [JUnit and execution](references/junit-and-execution.md) |
| Server operations, custom providers or necessary support code | [Server and support code](references/server-and-support-code.md) |
| Failed or interrupted migration | [Migration failures](references/migration-failures.md) |

Use [evidence guidance](references/evidence.md) for uncertain external claims and [optional Ripwire](references/ripwire.md) only if difficult dependency tracing warrants it and it is already available.

## 4. Migrate

Run the verified script for a new migration and inspect its output/diff; generated code is intermediate. Diagnose failures or unexpected output before further edits. Do not blindly rerun over partial output or substitute a broad manual rewrite.

Match local formatting, JUnit conventions and assertion style; verify signatures in source. Prefer managed public APIs such as `oauth.clientResource()` and `realm.admin()` when they preserve the identity and operation under test. Express necessary configuration-only setup through supported typed configs; retain lifecycle hooks only for genuine per-test behavior. Reuse default or existing realm fixtures where suitable. Derive URLs from the managed realm instead of introducing a config solely to keep its name, except when the literal name is behavior under test.

Preserve success and failure assertions, side effects, event details and absence checks. Preserve restrictive fixtures, grants, scopes, redirects and actor permissions; do not broaden them to make a test pass. Verify inherited helper assertions before replacing explicit checks. Reconcile every scenario and variant with its target equivalent; unresolved differences block claiming equivalence.

Update evidenced suite registrations using this revision's conventions, preserving topology and execution conditions. Retain the legacy source pending the removal decision below.

## 5. Review

Review the full diff against the scenario map and each addition's purpose. Remove unused injections (especially WebDriver), page fixtures, locals, captured event results, helpers and redundant configuration after checking inherited use and lifecycle effects. Retain a captured event if later assertions need that same consumed event.

Check fixture creation, reset and disposal at method/class scope, including inherited hooks and failure cleanup. Ensure realms, clients, users, events, sessions, server settings and time offsets cannot leak state or introduce order dependence. Do not add blanket resets: verify managed ownership and preserve intentional cookie/session continuity. Confirm every negative scenario still fails for its intended reason, with equally strong assertions.

Recheck suite selectors/imports for obsolete package names, omitted variants and duplicate discovery. Explain required registrations left unchanged while legacy sources remain; report unresolved coverage rather than silently dropping it.

## 6. Validate and report

Derive the narrowest test-compilation and focused execution commands from this revision's destination module, POMs and runner guidance. Check engine, selectors, profiles, annotations, parameters, conditions, fixtures and services. Compile the actual migrated test sources and necessary support code with test execution disabled; verify skip flags and phases rather than assuming main-source compilation is sufficient.

Fix evidenced migration errors within scope. Distinguish environmental, toolchain, network and unrelated-module failures from migration defects. Ask before broader setup changes or uncertain workarounds. Never weaken assertions or remove cases to obtain a pass.

After compilation, report its result and the focused command; explicitly ask before running tests unless that execution was already authorized. Wait for approval when required. If declined or unanswered, leave the migration compile-ready and unvalidated.

Inspect fresh runner reports for the intended class, inherited tests, parameterized variants, executions and skips. Zero discovered tests, stale reports or excluded scenarios are not a pass. Reconcile execution with the scenario map and relevant suite coverage; a focused base run does not validate JVM/cluster behavior. Report unexecuted configurations and other gaps.

After the migrated scenarios pass, honor an existing explicit legacy-source retention/removal decision; otherwise ask whether to remove the corresponding legacy test and wait. Approval covers only that source. Check consumers before deletion, pause for broader dependencies, review the deletion diff, and recheck affected suite registrations. Retain the source if declined or unanswered.

Completion requires preserved coverage, successful compilation, an approved focused run with verified discovery/execution, and a resolved legacy-removal decision. Otherwise report the pending stage. Report source/target and necessary support paths, branch/base, substantive decisions, suite changes, commands and actual results, discovered/executed/skipped coverage, legacy-source status and limitations. Distinguish source inspection from observed execution.
