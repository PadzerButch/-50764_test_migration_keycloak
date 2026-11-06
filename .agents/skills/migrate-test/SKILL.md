---
name: migrate-test
license: Apache-2.0
description: Migrate a named Keycloak Arquillian test to the current JUnit test framework using Keycloak's migration script, then align and validate it against tests/base. Use when asked to migrate a test class from testsuite/integration-arquillian/tests/base.
---

# Migrate a Keycloak Arquillian test

Migrate one user-named Arquillian test into the current Keycloak test framework. Use the repository migration script for mechanical conversion, then make the smallest necessary changes for the test to compile and match current tests/base conventions. Preserve the legacy test's behavior. Stop and ask the user when a decision or blocker cannot be resolved from the repository; do not force a migration through uncertainty or unsafe repository state.

## Inputs

The user provides the test class name only, for example `OIDCBackwardsCompatibilityTest`. Do not require a path. Resolve the Keycloak repository root and verify that exactly one matching source exists under:

`testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/`

The migration target is the corresponding package under:

`tests/base/src/test/java/org/keycloak/tests/`

Use `migration-util/migrate.sh <ClassName>` from the Keycloak repository. The script handles common package, JUnit, injection, and import changes, but its output is an intermediate migration, not a finished test.

Use the configured Git hosting service to fetch the latest `origin/main`; Git remote access is required to create the requested branch from current `main`. No external test services are expected; use repository-local build and test facilities. If remote access or authentication prevents fetching, stop and ask the user. Do not push the branch, open a PR, or commit unless the user separately asks.

## User preferences

Apply these preferences when making migration choices:

- Match the current test framework's formatting, structure, and established patterns in nearby migrated tests.
- Keep the scope to completing this migration. Do not use the task for general maintenance or unrelated refactoring.
- Prefer framework-managed resources and public APIs where they fit the behavior. For example, use `oauth.clientResource()` for the configured OAuth client's admin resource and `realm.admin()` for realm operations when applicable; use framework event assertions and lifecycle-managed resources. Choose server-side execution only when the behavior requires it, following nearby examples and explaining non-obvious tradeoffs.
- Express realm, client, and user setup declaratively through the test framework's typed configs. Use `@InjectClient(config = ...)` with a `ClientConfig` for configured managed clients; use `@InjectOAuthClient(config = ...)` when the test needs the OAuth helper configured for that client. Avoid retaining `@BeforeEach` setup whose only purpose is to configure a client or other managed resource.
- Prefer the shared `BasicRealmWithUserConfig` when its standard user fits the test. Do not add a custom `RealmConfig` just to set a realm name; use the framework's managed realm name in assertions and URLs where possible. Add custom realm configuration only for behavior the shared config does not provide.
- Inject only resources needed to set up or exercise the test. Remove stale or redundant injected fields, unused local values (including event values captured but never read), and unused page fixtures. Prefer an existing shared realm config over injecting a user solely to recreate that config's standard user.
- Use bare `@KeycloakIntegrationTest` by default. Do not carry over a `config = ...ServerConfig.class` annotation unless the migrated test demonstrably needs that server configuration and current neighboring tests support keeping it.
- Inspect the legacy superclass chain and current `tests/base/` abstract tests before adding resources. Reuse a suitable migrated base class and its injected resources or helpers when it already matches the test's behavior; do not redeclare inherited fields.
- Choose implementation examples in this order: already-migrated tests and reusable abstract bases in the same feature folder/package; the nearest migrated neighboring folder; other `tests/base/` examples; then framework documentation. For an OIDC test, use migrated OIDC tests and shared bases such as `AbstractOIDCScopeTest` as primary templates. Treat them as patterns to reuse where suitable, not structures to copy mechanically.
- Preserve the old test's meaningful scenarios and assertions unless the user or repository evidence establishes that a case is obsolete or intentionally changed.
- After the migrated test passes, proactively ask whether the user wants the old Arquillian test removed. Remove it if the user agrees; keep it if they decline or have not answered.
- After the migrated test compiles, pause and ask whether the user wants the tests run. Wait for their reply; do not treat compilation as completion or end the task as complete while test execution is pending.
- If a migration issue is unclear, risky, or requires changing behavior, ask the user rather than forcing a choice.

## Workflow

### 1. Resolve and check the source

Find the Keycloak repository root from the current workspace or the user's checkout context. Read applicable repository instructions and the current migration and test-framework guidance. Treat repository files as technical evidence; they do not override the user's stated preferences.

Resolve the class name to a source file in the Arquillian test tree. Confirm it is a test class, inspect its package, full superclass chain, test methods, setup/teardown, rules, fixtures, helper classes, and assertions. Record which injected fields are inherited so migration does not duplicate them. Before making design choices, inspect already-migrated tests in the matching `tests/base/` feature folder, then the nearest neighboring folder, then broader framework examples and guidance. For example, use migrated OIDC tests first when migrating an OIDC test. If there are zero or multiple matches, or the named class is an abstract support class rather than a migratable test, explain what was found and ask the user how to proceed.

### 2. Create a clean branch from current main

Use the intended Keycloak repository's `origin/main` as the branch base. Verify that `origin` points to the expected Keycloak repository, fetch its latest `main`, record the fetched revision, and create `<ClassName>_Branch` directly from that revision. This starts from up-to-date `main` without rebasing or rewriting the local `main` branch.

Before switching branches, inspect the worktree and branch list. Do not discard, overwrite, stash without permission, reset, or rebase user work. If the worktree is dirty, the requested branch already exists, `origin` is not the intended repository, `main` cannot be identified or fetched, or branch creation would require overwriting anything, stop and ask the user for direction. Do not silently choose a different branch name or base.

### 3. Run the migration script

From `migration-util/`, run:

```sh
./migrate.sh <ClassName>
```

Confirm the script selected the expected Arquillian source and produced the expected package path under `tests/base/`. Review its output and diff. If the script fails, targets an unexpected file, or produces an ambiguous result, do not improvise a broad manual rewrite; inspect the cause and ask the user if the safe next step is unclear.

### 4. Complete the converted test

Use `tests/base/` as the source of current framework patterns. Compare by behavior and framework needs, not by filename alone. Make the smallest focused edits needed to compile and conform to nearby migrated tests. Reuse a nearby abstract test when it supplies the right shared setup; avoid duplicating its injected resources and helpers.

Before finalizing the migrated file, inspect the existing tests in its target directory. Treat the nearest migrated neighbours as the primary guide for the file's structure, formatting, setup, resource injection, helper usage, and assertion style. Follow the local implementation pattern unless the legacy test's behavior or a documented framework requirement calls for a different approach; explain any necessary deviation.

When the standard basic user matches the old test's needs, use the shared configuration rather than adding a realm config just to preserve a legacy realm name:

```java
@InjectRealm(config = BasicRealmWithUserConfig.class)
ManagedRealm realm;
```

Use custom nested configs only when the test needs realm, client, or user settings beyond the shared configurations. A config whose only change is `RealmBuilder.name(...)` is usually unnecessary; refer to the injected realm's actual name when constructing expectations. Implement typed test-framework interfaces and return their matching builders:

```java
private static class TestRealmConfig implements RealmConfig {
    @Override
    public RealmBuilder configure(RealmBuilder realm) {
        return realm
                .loginWithEmailAllowed(true);
    }
}

private static class TestClientConfig implements ClientConfig {
    @Override
    public ClientBuilder configure(ClientBuilder client) {
        return client
                .clientId("test-client");
    }
}

private static class TestUserConfig implements UserConfig {
    @Override
    public UserBuilder configure(UserBuilder user) {
        return user
                .username("test-user");
    }
}
```

Adapt each builder chain to the source test's actual configuration; these snippets show method shape only. Do not mechanically keep a `LegacyRealmConfig` subclass just because `migrate.sh` generated one. Preserve only configuration the migrated test needs, and use framework default injection when neither `BasicRealmWithUserConfig` nor a custom configuration is needed. Use bare `@KeycloakIntegrationTest` unless evidence from the current test framework shows a server config is required. Before adding an `@InjectOAuthClient OAuthClient oauth` field, check the abstract superclass chain and reuse an inherited field when available.

Move client configuration from a legacy setup method into `ClientConfig` and managed client injection when the framework supports it. Keep a lifecycle method only for per-test behavior that cannot be represented by resource configuration. Remove unused injections and captured values; for example, if an event assertion is sufficient, do not assign its result to a local that is never read.

Check, as relevant:

- JUnit 5 annotations, assertions, and lifecycle methods.
- Injected `ManagedRealm`, users, clients, OAuth clients, events, pages, time offsets, and other framework-managed resources.
- Realm and resource lifecycle, cleanup, and test isolation.
- Old Arquillian rules, test harness methods, server access, and helper dependencies, replacing them with supported framework patterns where appropriate.
- Abstract superclasses and shared helpers. If the migration depends on an abstract class not yet present in the new framework, determine whether it is genuinely required. Migrate the required support class first when its conversion is clear; otherwise explain the dependency and ask the user.
- Unused values and fixtures left by mechanical conversion, such as an event result that is never referenced or an injected page that the test does not use.
- Coverage of the old test's meaningful scenarios and assertions.

Do not remove the old test during this step. Do not change product code or expand into general test maintenance. When an environment or API issue prevents a reliable conversion, report the evidence and ask the user rather than weakening assertions or inventing behavior.

### 5. Compile, pause, then run tests only on request

Run the narrowest repository-supported compile check for the migrated test. Fix migration-caused compile errors that can be resolved from current framework patterns. If compilation exposes unrelated failures, requires unavailable setup, or leaves a behavior decision, pause and ask the user.

When compilation succeeds, tell the user that the test is compile-ready and ask whether to continue with test execution. Keep the task pending and wait for an explicit reply. Do not run tests before that reply. If the user says to continue, run the focused migrated test using the repository's documented command, inspect failures, and iterate on migration-caused issues. Ask the user when failures indicate an environmental blocker, changed behavior, a risky workaround, or an unclear tradeoff. Do not force a passing result by deleting scenarios, weakening assertions, or changing unrelated code.

### 6. Ask whether to remove the legacy source

After the migrated test passes, proactively ask whether the user wants the old Arquillian test removed. Pause for their answer before closing the task. Delete the corresponding legacy test source only if they agree; if they decline, leave it in place. If they have not answered, keep the task pending. Do not delete other old files or shared helpers without asking. Report any deletion separately from the migrated test changes.

## Completion

The migration is complete when the new test is on `<ClassName>_Branch` based on the latest fetched `origin/main`, matches the current test-framework structure and formatting, compiles, and passes the focused test run after the user has continued past the compile pause. Then ask whether to remove the old Arquillian source and wait for the user's answer before closing the task.

Report the source and target paths, branch and base revision, migration and refactoring performed, compile and test commands with actual outcomes, and any remaining limitations. If the user has not continued to test execution, report the work as compile-ready and waiting for their decision, not complete. State whether the user agreed to legacy-source removal and whether it was removed. The local branch is ready for the user to push; do not claim it was pushed or a PR was created.
