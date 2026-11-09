# Server and support code

Use for server settings, `testingClient`, model tests, custom providers, mail, crypto, utilities, and module dependencies. Source-checked against local Keycloak revision `46cc4b37a7adea2df471d839a51f2ec10e6d4f1c` on 2026-11-09. Paths refer to the target checkout; recheck APIs and module ownership on another revision. This is source evidence, not an executed migration or authorization for broader framework changes.

## Translate server requirements

Determine what each legacy annotation enabled, disabled, or excluded before removing it. Use a compatible existing `KeycloakServerConfig` or the smallest necessary implementation. At the checked revision:

| Requirement | Framework mechanism |
| --- | --- |
| Enable or disable a feature | `KeycloakServerConfigBuilder.features(...)` / `featuresDisabled(...)` |
| Startup option, including vault configuration | `option(key, value)` with verified current option names and required resources |
| SPI provider setting | `spiOption(spi, provider, key, value)`; inspect the current option for selecting a default provider separately |
| Deploy a provider artifact | `dependency(groupId, artifactId)`; the version is resolved from project POMs |

Translate execution conditions using [JUnit and execution](junit-and-execution.md#preserve-execution-conditions). A feature requirement and a test of feature absence need different configurations.

Evidence: `test-framework/core/src/main/java/org/keycloak/testframework/server/KeycloakServerConfigBuilder.java`; `test-framework/docs/{WRITING_TESTS,RUNNING_TESTS}.md`. Deployment example: `tests/base/src/test/java/org/keycloak/tests/admin/authentication/RequiredActionsTest.java`, `CustomProvidersServerConfig`.

## Replace each testing-client responsibility

| Legacy responsibility | Decision |
| --- | --- |
| Realm/resource operation exposed by the admin API | Prefer the corresponding managed admin resource while preserving the identity and permissions under test. |
| Event polling or clearing | Use the framework event resources; preserve assertions and window semantics in [OAuth and events](oauth-and-events.md). |
| Server URL lookup | Use `@InjectKeycloakUrls` and `KeycloakUrls`, or the managed realm's URL; do not retain hard-coded ports or an assumed `/auth` prefix. |
| Time manipulation | Inspect `TimeOffSet` and `TimeOffsetSupplier`: `set()` changes test-process and server time; the supplier resets a changed offset on close. Verify lifecycle and cache-time options before removing manual reset. |
| Entire method needs a `KeycloakSession` | Consider `@TestOnServer` only if its remote execution semantics preserve the test. |
| Mixed local and server operations | Use `RunOnServerClient.run()` or `fetch()` for the required server portion. |

`RunOnServerSupplier` binds its endpoint to the `ManagedRealm` selected by `realmRef`; do not assume it lacks realm binding. With multiple realms, select the intended one and verify session context. Check serialization, available classes, and any required permitted-package configuration for remote code; keep captured values small and avoid capturing browser resources or the local test instance.

`RunTestOnServer` constructs a fresh instance on the server and invokes a method taking `KeycloakSession`. Local injected fields and setup state are not copied into that instance. Inherited methods, constructor accessibility, and code dependent on local hooks need explicit inspection before translating `@ModelTest`. Prefer a mixed local/server test when setup or assertions require local resources.

Evidence: `test-framework/remote/src/main/java/org/keycloak/testframework/remote/runonserver/{InjectRunOnServer,RunOnServerSupplier,RunOnServerClient,RunTestOnServer}.java`, `annotations/TestOnServer.java`, `RemoteTestFrameworkExtension.java`, and `timeoffset/{TimeOffSet,TimeOffsetSupplier}.java`. Example of scoped server operations: `tests/base/src/test/java/org/keycloak/tests/admin/authentication/AuthenticatorConfigTest.java`.

## Reuse or migrate necessary utilities

Search the framework, `tests/utils`, and `tests/utils-shared` by called method and behavior before copying a class. Some shared classes retain `org.keycloak.testsuite.*` packages, so a blanket import rename is incorrect. At this revision:

- `FlowUtil` and `AccountHelper` are in `tests/utils-shared` with their legacy package names.
- `org.keycloak.testframework.util.ApiUtil` supplies `getCreatedId()`; broader admin helpers are in `org.keycloak.tests.utils.admin.AdminApiUtil`.
- `@InjectSimpleHttp` provides managed `SimpleHttp` for HTTP helpers; `@InjectHttpClient` serves lower-level HTTP operations. Preserve cookies, headers, TLS settings, and ownership before replacing a custom client.
- Token-helper substitutions require the behavior checks in [OAuth and events](oauth-and-events.md).

Inline a small, test-specific operation when no reusable equivalent is needed. For substantial support code, choose `tests/utils` for new-framework utilities or `tests/utils-shared` when legacy consumers still need it. Preserve existing consumers and keep shared utilities free of new-only dependencies. If moving or modifying the shared helper changes other tests' behavior, stop for the broader scope decision required by the entrypoint.

Evidence: `tests/docs/README.md`; the classes above; `test-framework/core/src/main/java/org/keycloak/testframework/http/{SimpleHttpSupplier,HttpClientSupplier}.java`.

## Complete provider packaging

A provider migration requires its implementation, factory, service registration, supporting resources, and deployment. First search `tests/custom-providers` by provider ID and factory interface; the implementation and factory may be the same class.

Preserve the provider ID and configuration keys referenced by flows or realm fixtures. Register the factory's fully qualified class name in the appropriate `META-INF/services/<SPI-interface>` file. Verify resources and module dependencies, then add the required artifact to server configuration. A test-module compile dependency alone does not deploy the provider into the server. Script providers belong to the repository's `tests/custom-scripts` module when applicable; follow its existing packaging.

Reference example: `tests/custom-providers/src/main/java/org/keycloak/tests/providers/forms/ClickThroughAuthenticator.java` implements both authenticator and factory, with registration under `tests/custom-providers/src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory`.

Compile changed support modules as well as the test. Depending on the selected server mode, deployment may need an installed artifact; inspect the build and runtime configuration before choosing reactor builds, installation, or supported hot deployment. Do not infer build readiness from an existing `target/` directory. Limit POM changes to demonstrated migration dependencies and use [failure diagnosis](migration-failures.md) to distinguish missing dependencies from stale artifacts.

## Mail and crypto resources

For `GreenMailRule`, inspect `@InjectMailServer MailServer`. At this revision `GreenMailSupplier` intercepts realm creation to configure SMTP; `MailServer.runCleanup()` purges received mail. This does not establish equivalent configuration for an attached realm, custom SMTP authentication, TLS, or a remote server's network access. Preserve those requirements explicitly. Wait for the expected message count before inspecting recipients, headers, bodies, or links; `getLastReceivedMessage()` can return null and `getReceivedMessages()` returns an array. Preserve negative delivery assertions and timing rather than merely checking that one message arrived.

For `CryptoInitRule`, distinguish initialization from crypto operations and FIPS assumptions. `CoreTestFrameworkExtension.alwaysEnabledValueTypes()` includes `CryptoHelper`; its supplier initializes the default provider only if not already initialized. Do not add an otherwise unused injection solely to trigger initialization. Use `@InjectCryptoHelper` when its keystore or FIPS-aware expectations are needed, and preserve algorithm/provider-specific requirements. Test-process initialization does not establish the server's crypto mode.

Evidence: `test-framework/email-server/src/main/java/org/keycloak/testframework/mail/{GreenMailSupplier,MailServer}.java`; `test-framework/core/src/main/java/org/keycloak/testframework/CoreTestFrameworkExtension.java` and `crypto/{CryptoHelperSupplier,CryptoHelper}.java`.
