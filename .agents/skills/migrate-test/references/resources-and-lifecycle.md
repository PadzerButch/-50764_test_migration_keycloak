# Resources and lifecycle

Use when a migration changes realm imports, user/client setup, admin identities, role assignment, required actions, or resource cleanup.

Source-checked against Keycloak revision `46cc4b37a7adea2df471d839a51f2ec10e6d4f1c` on 2026-11-09. This is a local migration branch, not a claim about the latest upstream release. Paths below are relative to the target Keycloak checkout. Examples were checked against source; they were not compiled or executed as part of this reference's preparation. Recheck the relevant symbols when the target revision differs.

## Choose the smallest equivalent fixture

Account for resources supplied by the entire superclass chain and imported JSON: users, credentials, roles/groups, clients, profile fields, required actions, and settings used implicitly by assertions. Reuse a compatible migrated base and its injections before adding fields.

- Use default injection when its defaults suffice. Prefer `BasicRealmWithUserConfig` when its standard user fits; it includes a password, email, and first/last name. Verify these values instead of recreating the fixture through extra injections.
- Use a custom `RealmConfig`, `ClientConfig`, or `UserConfig` only for required differences. Use `@InjectClient(config = ...)` when the test needs a configured managed client; OAuth-owned clients are covered in [OAuth and events](oauth-and-events.md).
- Retain JSON import when the test depends on its structure or credentials and reducing it would obscure or change the scenario. For a small fixture, express the necessary data declaratively. Do not select JSON solely by user count.
- Use the managed realm's name in URLs and assertions rather than creating a configuration solely to retain a legacy name. Preserve a literal name when the test specifically exercises that name or its encoding.

At the checked revision, `RealmConfig.configure` takes/returns `RealmBuilder`; the client and user interfaces similarly use `ClientBuilder` and `UserBuilder`. These live in `test-framework/builders`, under package `org.keycloak.testframework.realm`. Historical `*ConfigBuilder` names and nested `addUser().build()` recipes do not describe this revision. Verify imports and return types before reusing an example.

Evidence: `tests/base/src/test/java/org/keycloak/tests/common/BasicRealmWithUserConfig.java`; `test-framework/core/src/main/java/org/keycloak/testframework/realm/RealmSupplier.java`; `test-framework/builders/src/main/java/org/keycloak/testframework/realm/{RealmBuilder,ClientBuilder,UserBuilder}.java`.

## Users with roles or required profile fields

`UserSupplier.getValue()` rejects non-null realm-role or client-role lists when creating an `@InjectUser`. For a static role-bearing user, use an existing suitable realm fixture or import the required user and roles together through realm configuration. Do not add unrelated roles or profile fields from examples.

For a user created during a test, create the user and assign roles explicitly through the admin API, retaining cleanup. `ManagedRealm.addUser(UserBuilder)` calls `users().create()` and registers deletion; it is not realm import and does not establish that embedded roles were assigned. Verify the resulting mappings when the scenario depends on them.

Before changing login code to fix an unexpected required-action page, inspect the user's profile and configured required actions. `VerifyUserProfile.evaluateTriggers()` adds the action when profile validation fails. Supply valid fixture fields for an ordinary login, but retain incomplete fields when profile validation is the behavior under test. Do not disable required actions merely to obtain a passing login.

Evidence: `test-framework/core/src/main/java/org/keycloak/testframework/realm/{UserSupplier,ManagedRealm}.java`; `services/src/main/java/org/keycloak/authentication/requiredactions/VerifyUserProfile.java`. Example of realm-imported roles: `tests/base/src/test/java/org/keycloak/tests/oidc/LightWeightAccessTokenTest.java`.

## Preserve reset timing and ownership

Configuration-only setup can move into typed configs, but per-method setup is not equivalent to a shared class-scoped resource. Inspect the annotation lifecycle, inherited hooks, and mutations before removing setup or teardown.

| Situation | Decision at the checked revision |
| --- | --- |
| Temporary realm-setting change | `updateWithCleanup()` reads the current representation, registers restoration, and applies a builder-based update. Check that this covers the exact state changed. |
| Additional resource created during a test | Register its cleanup; realm ownership alone does not imply deletion between methods. |
| Fixture cannot be restored reliably | `dirty()` marks it for destruction after the method; it does not immediately reset the resource in the current method. |
| Method-scoped realm | Recreated for subsequent methods; consider JSON-import cost and dependent resources before selecting this scope. |
| Attached existing realm | `@InjectRealm(attachTo = ...)` ignores configuration and does not delete that realm on close. Retain explicit restoration of changes. |

`Registry.afterEach()` destroys method-scoped resources, then destroys dirty managed resources or runs their cleanup tasks. `getCreatedRepresentation()` is creation-time data; use `admin().toRepresentation()` when asserting current server state. Realm restoration does not establish browser-cookie, session, or external-resource cleanup. Diagnose state leaks before adding blanket cookie resets or realm recreation.

Evidence: `test-framework/core/src/main/java/org/keycloak/testframework/annotations/InjectRealm.java`; `test-framework/core/src/main/java/org/keycloak/testframework/injection/{Registry,ManagedTestResource}.java`; `ManagedRealm` and `RealmSupplier` above.

## Preserve the actor behind admin operations

`realm.admin()` is appropriate for fixture administration; it must not replace a restricted user's client in a permission test. Record both the authentication realm/identity and the resource realm. A bootstrap admin, a realm administrator, and a service account can reach the same API with different permissions.

- Use `@InjectAdminClient` for the framework bootstrap client. For `MANAGED_REALM` mode, inspect `client`, `user`, and `realmRef`: at this revision the supplier requires a client in the realm's creation-time representation, optionally selecting an imported user for a password grant. Independently injected or subsequently created users/clients do not meet that lookup contract.
- Use `@InjectAdminClientFactory AdminClientFactory` when the scenario creates clients with distinct credentials, grants, or bearer tokens. Configure the authentication realm and identity explicitly; factory creation supplies the server connection, not a test-specific account.
- Preserve closure: `AdminClientBuilder.build()` does not register automatic closure unless `.autoClose()` was selected. Use try-with-resources for short-lived clients or verify the factory lifecycle before choosing automatic closure. Do not close an injected shared client manually.

Evidence: `test-framework/core/src/main/java/org/keycloak/testframework/admin/{AdminClientSupplier,AdminClientFactory,AdminClientBuilder}.java`, `annotations/InjectAdminClient.java`; examples in `test-framework/tests/src/test/java/org/keycloak/testframework/tests/{RealmSpecificAdminClientTest,AdminClientFactorySupplierTest}.java`.
