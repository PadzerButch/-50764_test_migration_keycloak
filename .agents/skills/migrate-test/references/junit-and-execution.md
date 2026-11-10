# JUnit and execution

Use for runners, rules, inherited hooks, parameters, conditions, expected exceptions, or timeouts. Keycloak source locations below were inspected at local revision `46cc4b37a7adea2df471d839a51f2ec10e6d4f1c` on 2026-11-09; no migration was executed for this reference. Inspect the target revision's managed JUnit version, extensions, and examples before choosing an API.

## Preserve the test contract

| Legacy construct | Migration decision |
| --- | --- |
| Assertions and assumptions | Jupiter places failure messages last. Preserve expected/actual order, numeric tolerances, collection ordering, and identity versus equality. Compatible Hamcrest assertions can remain. |
| Expected exception or exception rule | Use the supported exception assertion around the operation intended to fail; keep setup outside it. Preserve message, cause, status, and forbidden-side-effect checks where present. An unrelated setup exception must not satisfy the test. |
| Timeout | Preserve duration, units, and whether the limit covers one operation or the whole test. Inspect thread/interruption behavior before choosing an annotation or timeout assertion; server transactions and thread-local state can make them inequivalent. |
| Parameterized runner | Map every original input combination and its setup/cleanup to supported Jupiter parameterization. Constructor-driven class fixtures may require a different solution from method parameters. Do not collapse variants to one representative value. |
| Resource or behavior rule | Identify what the rule creates, observes, resets, or suppresses. Managed injection replaces resource ownership only when its lifecycle matches; behavior rules may need hooks or an extension. Do not merely delete a rule that does not compile. |

Consult JUnit migration and timeout documentation matching the version in the target build. Keycloak's local `tests/docs/MANUAL_MIGRATION.md` covers basic imports and assertions, not every semantic consequence.

## Preserve inheritance and hook effects

When replacing an Arquillian superclass, trace inherited tests as well as fields and methods. A target abstract base is suitable only if its realm, users, clients, hooks, and assertions match the needed behavior. If none fits, carry forward the required behavior explicitly; do not copy the entire legacy hierarchy or discard an inherited test because it is absent from the concrete source file.

Check hook order, overrides, static/instance requirements, and per-method versus per-class state under the selected Jupiter lifecycle. Remove ordering annotations only after eliminating accidental dependencies; if an ordered sequence is itself the scenario, preserve it explicitly rather than splitting it into supposedly independent tests. Use [resource lifecycle](resources-and-lifecycle.md#preserve-reset-timing-and-ownership) for fixture ownership and [server execution](server-and-support-code.md#replace-each-testing-client-responsibility) for model tests whose local state cannot move to a remote instance.

## Preserve execution conditions

Translate ignored tests, assumptions, feature requirements, server/database/browser exclusions, and expected server-error handling by purpose. Do not silently enable excluded combinations, suppress unexpected errors, or disable failing scenarios. A missing equivalent is a coverage gap to resolve.

At the checked revision, framework conditions include `DisabledForServers` and `DisabledForDatabases` under `test-framework/core/src/main/java/org/keycloak/testframework/conditions/`. Verify their supported values and conditions in source. Enable a feature through server configuration only if the scenario requires it; a disabled-feature test needs its absence preserved.

Use the script's actual destination and that module's runner configuration. WebAuthn, clustering, and other specialized tests may depend on distinct modules, services, tags, or server topology. A passing base-module run cannot establish their coverage. Consult the selected module's POM/examples and `test-framework/docs/RUNNING_TESTS.md`; verify inherited and parameterized discovery through the entrypoint's approved-run process.

## Advanced suite registrations

Use this section only when investigation finds suite membership or an unresolved selector. Search class/FQCN references and package-based selectors across the checkout, including legacy and target suites, POM profiles and CI jobs. JVM and cluster registrations may be XML include/exclude lists, Java suite classes, tags or inherited wrappers; verify the actual mechanism before editing. Paths and membership vary by revision.

For each match, record the old selector, destination, required topology and conditions. Update moved-class imports/selectors or exclusions according to a nearby migration in that suite. Preserve deliberate legacy/new separation while the legacy source remains; after authorized removal, eliminate stale registrations. Check that registered subclasses still inherit the required tests and that no case is silently omitted or run twice. Do not register every OIDC test in advanced suites.

Compile affected suite code where available and verify selectors resolve. Execute only the relevant approved configurations; a base-module pass does not demonstrate JVM-option or clustered execution. Report registration inspection, compilation and actual execution separately, including unavailable services or topology.
