---
name: migrate-test
license: Apache-2.0
description: Migrate a named Keycloak Arquillian test class to the current JUnit test framework using the repository migration script. Use when asked to port, migrate, or resume migrating a legacy Keycloak test, preserving scenario coverage, fixtures, and assertions with user-approved test execution.
---

# Migrate a Keycloak test

Apply a test lead's judgment: preserve observable behavior, use the checked-out framework's conventions, and distinguish conversion from demonstrated equivalence. The unit of migration is one concrete test class, including inherited scenarios and necessary support code, not merely one file. Do not change product code, perform unrelated refactoring, commit, push, or open a PR without separate instructions. Honor explicit user preferences and authorization already given in the conversation.

## Establish the working revision

Accept a class name; locate the Keycloak checkout, read applicable instructions, and resolve it under `testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/`. Ask for direction for missing, ambiguous, or abstract-only matches.

For a new migration, inspect branches, the full worktree status, and `origin`. Verify the intended remote, require a clean worktree, fetch `origin/main`, and create `<ClassName>_Branch` directly from its recorded revision. A fork's `origin/main` is not necessarily upstream main. Do not silently change remotes, base, or branch name. If fetching fails, the branch exists, or switching is unsafe, explain the blocker and ask; never discard, overwrite, stash, reset, or rebase user work without authorization.
    
For an explicitly resumed migration, inspect worktree status, the existing diff, and recorded base instead of creating another branch or regenerating its files. Account for unrelated changes and continue from the last evidenced stage. Re-resolve source, instructions, and framework evidence after any revision change.

Inspect the repository migration script and relevant helpers (usually `tests/migration-util/migrate.sh`): invocation, working directory, build side effects, source selection, and destination routing. Respect execution authorization for any tests its build would run. Verify the target module from this revision; do not assume every class belongs in `tests/base`. Search the target feature for equivalent scenarios, including renamed tests, and check tracked, untracked, and ignored output collisions. Compare partial migrations before asking how to proceed; never overwrite or duplicate them. Unsupported routing or a missing script is a blocker to resolve, not permission to invent a conversion tool.

## Map behavior and choose supporting guidance

Keep a compact scenario map in working notes: **source method/variant → fixture and identity → action → observable assertions → target method/variant → validation status**. Include inherited and parameterized tests, negative paths, setup/teardown, rules, and execution conditions. Track unresolved differences explicitly; generated method counts do not establish coverage.

Search by needed symbols and behavior: same-feature migrated tests and abstract bases, nearest neighbors, then framework source/docs. Trace superclass hooks, injections, and implicit helper behavior before choosing inheritance or flattening. Reuse a compatible migrated base without shadowing its resources. Record useful paths beside decisions and stop exploring when API, lifecycle, and runner choices are supported. [Optional Ripwire](references/ripwire.md) can assist difficult dependency tracing if already available; ordinary repository search remains sufficient.

Read only references matching the source patterns, then verify the relevant symbols on the target revision. Their dated examples are search leads, not fixed APIs or proof of execution.

| Source pattern or decision | Guidance |
| --- | --- |
| Realm imports, users/roles, admin identities, mutations, cleanup | [Resources and lifecycle](references/resources-and-lifecycle.md) |
| OAuth helpers, token reuse, client binding/registration, event expectations | [OAuth and events](references/oauth-and-events.md) |
| Browsers, multiple sessions, page objects, waits, UI assertions | [UI migration](references/ui-migration.md) |
| Server configuration, model tests, providers, mail, crypto, utilities | [Server and support code](references/server-and-support-code.md) |
| JUnit rules/runners, inherited hooks, parameters, conditions, timeouts | [JUnit and execution](references/junit-and-execution.md) |
| An observed compilation, discovery, or runtime failure | [Migration failures](references/migration-failures.md) |

Identify required support classes, resources, and dependencies before conversion. Check their consumers and output collisions too. Convert necessary support code when behavior is clear and scope remains local to this migration; ask before broader migrations or changes to shared behavior. Unresolved behavioral differences block claims of equivalence.

## Convert and reconcile

Run the verified script once for a new migration and review output/diff against the expected files. Diagnose partial output or an unexpected destination before proceeding; do not blindly rerun over generated files. Follow local formatting and Jupiter conventions, verify signatures in source, and prefer public managed-resource APIs. Use bare `@KeycloakIntegrationTest` unless the scenario needs custom server configuration.

Apply the selected references to preserve fixture ownership, identities, setup/reset timing, helper side effects, and assertion strength. Account for cleanup on failure and state shared across methods. Remove unused code only after checking inherited use and lifecycle effects. Reconcile every scenario with its target implementation; retain the legacy source through validation.

## Verify and resolve completion

Derive the narrowest supported compilation and focused execution commands from the target module's build and runner configuration. Compile the migrated **test sources** and changed support modules with test execution disabled; plain main-source compilation is insufficient. Check engine, selectors, profiles, conditions, parameters, and required services before proposing execution.

After compilation succeeds, report its result and the proposed focused command, then **ask before running tests** and wait, unless the user has already explicitly authorized that execution. If declined or unanswered, leave the migration compile-ready and unvalidated. Within an approved run's scope, correct evidenced migration defects; distinguish environment/unrelated failures and consult the matching failure entry. Do not weaken assertions or remove scenarios to obtain a pass.

Inspect fresh runner reports for the intended class, inherited cases, parameterized variants, skips, and actual executions. Reconcile them with the scenario map. Zero discovered tests, stale reports, or excluded scenarios do not establish equivalence. Legitimate skips preserve conditions but leave those variants unverified. Report blocked configurations and remaining coverage honestly; apply [evidence guidance](references/evidence.md) when assessing external claims or incomplete verification.

After the migrated scenarios pass, honor an explicit retain/remove decision already given; otherwise **ask whether to remove the legacy test source** and wait. Before approved deletion, check consumers and review its diff; approval does not extend to other legacy helpers. Retain the source when declined or unanswered.

Report source/target and changed support paths, branch/base revision, substantive decisions, commands actually run, discovered/executed/skipped coverage, legacy-source decision, and limitations. Completion requires preserved coverage, successful test compilation, an approved focused run with verified execution, and a resolved removal decision. Otherwise name the pending stage or blocker. Code inspection, compilation, and observed test execution are separate evidence.
