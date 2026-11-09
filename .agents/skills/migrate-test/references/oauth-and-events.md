# OAuth and events

Use for OAuth injection, token helpers, client/realm/browser binding, dynamic client registration, and event assertions.

Source-checked against Keycloak revision `46cc4b37a7adea2df471d839a51f2ec10e6d4f1c` on 2026-11-09, a local migration branch. Examples were inspected in source, not independently compiled or run. Paths are relative to the target Keycloak checkout; recheck the relevant implementation on another revision.

## Identify the client that owns each operation

At this revision, `OAuthClientSupplier.getValue()` obtains the managed realm, browser, and `TestApp`, applies a `ClientConfig`, adds the callback redirect URI, creates a client, and configures the OAuth helper with its ID, secret, realm, and callback. `OAuthClient.close()` removes that created client.

- Avoid creating the same client in both realm setup and OAuth injection. When an imported fixture collides with the injected client, first decide whether the scenario requires that existing identity or an independent client.
- `ref` suffixes the configured client ID; `realmRef` and `webDriverRef` select the realm and browser dependencies. A new reference must not silently change the client identity, audience, roles, or session relationships exercised by the test.
- Use `@InjectOAuthClient(config = ...)` with a `ClientConfig` for configured OAuth clients. Custom client IDs are supported here: the supplier reads the configured ID and wires the callback afterwards. A custom config replaces `DefaultOAuthClientConfiguration`; preserve required defaults explicitly or extend that configuration where appropriate.
- Check the actual default configuration before changing a grant or secret. At this revision it enables direct grants and uses `test-secret`; historical `test-app` assumptions differ.
- Use `oauth.clientResource()` for the client created by this injection. Calling `oauth.client(...)` changes the request configuration; it does not retarget the stored admin resource. Resolve another client's admin resource explicitly when switching clients.

For multiple browser sessions, verify the OAuth helper and injected pages bind to the intended browser and realm. Retain intentional cross-client or cross-realm operations.

Evidence: `test-framework/oauth/src/main/java/org/keycloak/testframework/oauth/{OAuthClientSupplier,OAuthClient,DefaultOAuthClientConfiguration}.java` and `annotations/InjectOAuthClient.java`. Search nearby migrated tests for the matching `config`, `realmRef`, or `webDriverRef` pattern.

## Preserve request and token behavior

The OAuth implementation shares request/response APIs through `tests/utils-shared/src/main/java/org/keycloak/testsuite/util/oauth/AbstractOAuthClient.java`. For example, `doPasswordGrantRequest()` and `doAccessTokenRequest()` execute and return responses; the corresponding request builders require `.send()`. Verify unfamiliar method signatures rather than adding `.send()` mechanically.

The legacy `testsuite/integration-arquillian/tests/base/src/main/java/org/keycloak/testsuite/util/TokenUtil.java` caches a token, tracks expiry, and refreshes it. Repeated password grants can create different sessions, events, and tokens. Before replacing it, record whether the scenario needs token reuse, refresh, expiry, or simply one authenticated request. Preserve those behaviors with supported OAuth APIs and verify the chosen client's grant configuration. A fresh grant on every call is acceptable only when those differences are immaterial to the scenario.

Retain explicit flow steps when the test asserts an intermediate page, response, or event; replacing them with a convenience login may skip the behavior being tested.

## Preserve dynamic registration behavior

When `ClientRegistration` is the API under test, keep its authentication, registration/access-token handling, response assertions, and cleanup; admin-API client creation is not an equivalent operation. `OAuthClient.clientRegistration()` creates a helper using the OAuth helper's base URL, configured realm, and HTTP client. It does not choose the registration identity or grant permissions. Inspect a compatible migrated `tests/base/src/test/java/org/keycloak/tests/client/AbstractClientRegistrationTest.java` before recreating its setup; it sets registration authentication separately and clears it after some operations. Preserve intentional anonymous, restricted, and privileged requests. If constructing the helper directly, derive URLs from managed resources and check HTTP-client ownership before adding closure.

Evidence: `OAuthClient.clientRegistration()` in the framework path above and the migrated abstract base. Verify their current state and hooks before reuse.

## Map assertions, including hidden checks

Inspect the legacy helper implementation and the target assertion helper. Similar names do not prove equivalent checks. Record expected event type, success/error, user/client/session IDs, details, absent details, order, and any remaining-event expectation. For admin events, include operation, resource type/path, representation, and authentication identity where asserted.

At this revision, `EventAssertion` includes `expectLoginSuccess()` and related helpers that check more than success and event type. Use the appropriate helper or explicit assertions after comparing its checks to the original; avoid adding requirements that exclude valid legacy variants. `AdminEventAssertion` supplies operation/path/representation checks. `AbstractEvents.poll()` also verifies the fetched event's realm and positive timestamp.

Polling consumes events. Retain a polled value when subsequent assertions need that same event; a second poll obtains another event. Preserve intentional queue boundaries and order.

### Absence is an assertion

`poll()` returns the oldest unprocessed event in the current window or null when none is fetched. For an already completed operation whose expected window has no events:

```java
Assertions.assertNull(events.poll());
```

This pattern appears in `tests/base/src/test/java/org/keycloak/tests/oidc/OIDCAdvancedRequestParamsTest.java`, method `promptNoneNotLogged`. It checks the current window, not all future asynchronous events. Preserve the original waiting/completion conditions when delayed events are possible; a single immediate null result cannot replace a timed absence check.

Never replace a negative assertion with `skip()`, `skipAll()`, `clear()`, or omitting the poll. Those operations discard or hide evidence. `skipAll()` advances the window; `clear()` deletes local and server events. Changing time offset also affects polling. Check these boundaries when a migrated assertion unexpectedly sees no event.

Evidence: `test-framework/core/src/main/java/org/keycloak/testframework/events/{AbstractEvents,EventAssertion,AdminEventAssertion}.java`. Compare inherited legacy expectations in the named test's assertion helpers; the pattern above is source evidence, not proof of an executed migration.
