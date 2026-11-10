# UI migration

Use for browser sessions, page objects, navigation, and UI assertions. Source-checked against local Keycloak revision `46cc4b37a7adea2df471d839a51f2ec10e6d4f1c` on 2026-11-09. Paths refer to the target checkout. Examples were inspected against source, not compiled or executed; recheck the relevant APIs and theme on another revision.

## Bind each session deliberately

First check inherited resources and supplier dependencies. At the checked revision, `OAuthClientSupplier` and `PageSupplier` obtain `ManagedWebDriver` themselves. Browser-backed login does not require an otherwise unused `@InjectWebDriver` field. Add one only for necessary direct operations or explicit configuration not already supplied; do not add lifecycle or cookie cleanup without a scenario requirement.

`@InjectWebDriver` supplies `ManagedWebDriver`; its `driver()` method exposes Selenium when the managed helpers are insufficient. At this revision its default lifecycle is GLOBAL. Distinct `ref` values create distinct managed browser resources; tabs within one browser are not a replacement for independent cookie sessions.

For independently required browser sessions, bind OAuth helpers and pages to the intended `webDriverRef`.

Use a distinct page `ref` when injecting the same page type more than once; setting only `webDriverRef` is insufficient to distinguish every request because `PageSupplier.compatible()` compares page references. Confirm realm binding and client identity using [OAuth and events](oauth-and-events.md).

`ManagedWebDriver.runCleanup()` closes extra tabs; it does not clear cookies. Choose explicit cookie cleanup or a suitable lifecycle only when the scenario requires a fresh session. Preserve cookies between steps that test SSO, logout, remember-me, or session continuity. Realm recreation and browser cleanup address different state; see [resources and lifecycle](resources-and-lifecycle.md).

Evidence: `test-framework/ui/src/main/java/org/keycloak/testframework/ui/annotations/{InjectWebDriver,InjectPage}.java`, `page/PageSupplier.java`, and `webdriver/ManagedWebDriver.java`. Example: `tests/base/src/test/java/org/keycloak/tests/forms/SSOTest.java` binds two drivers, OAuth helpers, and login pages with distinct references.

## Preserve navigation and assertions

For login flows, use the configured OAuth helper to open the intended request; for other pages use managed navigation and URLs from `KeycloakUrls` or the managed realm. Retain request parameters, redirects, and intermediate observations.

At the checked revision, `LoginPage.fillLogin()` fills fields and `submit()` submits them. `AbstractPage.assertCurrent()` waits for the expected `data-page-id`; it does not establish that every asynchronous element or operation has finished. Use `webDriver.waiting().until(...)` for the specific completion condition when needed. Replace a fixed delay only after identifying what it was waiting for, including intentional expiry or timeout behavior.

For error assertions, inspect the rendered theme and page API. `LoginPage.getUsernameInputError()` returns a nullable string, `getPasswordInputError()` returns an optional string, and `AbstractLoginPage.getErrorMessage()` reads a page-level message. They are not interchangeable. Keep the asserted text, location, and presence/absence semantics. A negative page assertion must remain a negative assertion or be replaced by a justified assertion of the actual destination.

Evidence: `test-framework/ui/src/main/java/org/keycloak/testframework/ui/page/{AbstractPage,AbstractLoginPage,LoginPage}.java` and `webdriver/{WaitUtils,PageUtils}.java`. Check actual theme templates under `themes/` when choosing selectors or page identifiers.

## Resolve a missing page

Search existing pages by behavior and selectors as well as class name; legacy and current names can differ. Reuse an existing operation or compose supported methods before adding a convenience method to a shared page.

When a required page is absent and its conversion fits the authorized support-code scope:

1. Inspect its legacy implementation and existing consumers; retain the interactions and assertions this migration needs.
2. Follow a nearby page: extend `AbstractLoginPage` or `AbstractPage`, provide a constructor taking `ManagedWebDriver`, and implement `getExpectedPageId()`. `PageUtils.createPage()` invokes that constructor and initializes Selenium fields.
3. Determine the ID from the actual template. The checked login templates render `data-page-id="login-${pageId}"`; the suffix must be established from the relevant page, not guessed from a Java class name.
4. Preserve necessary waiting, click, and text-extraction behavior when replacing legacy UI helpers. Similar method names alone do not establish equivalence.
5. Compile the owning module and its consumer using the repository build. If the change requires altering a product template, changing shared page behavior, or a broader framework migration, report the dependency and ask for direction.

Evidence: `test-framework/ui/src/main/java/org/keycloak/testframework/ui/webdriver/PageUtils.java`; `themes/src/main/resources/theme/{base,keycloak.v2}/login/template.ftl`. New shared pages belong beside the relevant existing framework pages; determine ownership from the checkout rather than copying a legacy directory layout.
