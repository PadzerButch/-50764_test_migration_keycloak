# Migration failures

Consult only the entry matching the observed failure. These are diagnostic leads: establish the failing phase and supporting evidence before applying a fix. Framework-specific observations were source-checked against local Keycloak revision `46cc4b37a7adea2df471d839a51f2ec10e6d4f1c` on 2026-11-09; commands and fixes were not executed against a migrated test. Paths refer to the target checkout.

## Compilation and discovery

| Symptom | Distinguishing checks and next action |
| --- | --- |
| Missing class or annotation | Locate the declaration first. Check its package, owning module, source set, selected profiles, and target POM. OAuth, UI, mail, and remote annotations need not live in the core annotations package. Add a dependency only when the class exists and the consumer genuinely lacks it. |
| Missing method or incompatible builder return type | Inspect the interface and implementation on this revision. Check builder versus representation types and request builder versus executed response; use the relevant resource or OAuth recipe. Do not rename methods by analogy. |
| Main-source compile succeeds but the migrated test has errors | Confirm the selected command reaches the destination module's test source set (for example `tests/base/src/test/java`) and compiles the migrated source. Follow the entrypoint's compilation gate. |
| Missing artifact or old helper at runtime | Check reactor selection, artifact version, required supporting-module build, and server deployment mode. Rebuild the specific dependency or its required upstream modules; a repository-wide clean build is not the default diagnosis. |
| Successful command with zero or unexpected tests | Inspect fresh runner reports, class/method selectors, Jupiter imports or composed annotations, suite tags, profile filters, and skips. The checked root POM sets `failIfNoSpecifiedTests=false`, so exit status alone cannot establish execution. |

For assertion, parameterization, lifecycle, or condition changes, use [JUnit and execution](junit-and-execution.md); a discovery fix must preserve the scenario contract.

Evidence: `pom.xml`, `tests/{pom.xml,base/pom.xml}`, `tests/docs/{MIGRATING_TESTS,MANUAL_MIGRATION}.md`, and the relevant annotation/helper source. For execution configuration, consult `test-framework/docs/RUNNING_TESTS.md`.

## Runtime behavior

| Symptom | Distinguishing checks and recipe |
| --- | --- |
| Client creation returns 409 | Identify the colliding client and both creation paths. Decide whether the test needs the imported identity or a separate injected client before changing references or IDs. See [OAuth ownership](oauth-and-events.md#identify-the-client-that-owns-each-operation). |
| User roles rejected or missing | Determine whether creation used realm import, `@InjectUser`, or the Users API; inspect resulting role mappings. See [users and profiles](resources-and-lifecycle.md#users-with-roles-or-required-profile-fields). |
| Unexpected required-action page | Inspect the actual action, user profile, required-action configuration, and legacy fixture. Do not change OAuth or disable validation until the cause is established. See [users and profiles](resources-and-lifecycle.md#users-with-roles-or-required-profile-fields). |
| Wrong/null page ID or missing element | Check current URL, page ID, expected redirect/action, browser binding, and operation completion. Then inspect cookies and lifecycle if evidence points to a state leak. Use [UI migration](ui-migration.md); do not add unconditional resets or longer sleeps. |
| Error text is missing | Confirm the expected failure occurred, then inspect field-level versus page-level messages and the theme's selectors. Check the accessor's return type before changing the assertion. See [UI assertions](ui-migration.md#preserve-navigation-and-assertions). |
| Missing, extra, or differently ordered events | Trace the operation, event configuration, realm, consumption, time changes, and window boundaries. A new login replacing token reuse can itself add events. See [event assertions](oauth-and-events.md#map-assertions-including-hidden-checks). |
| Authentication changes after replacing a token helper | Compare grant type, client, token reuse, refresh, expiry, and session expectations. See [token behavior](oauth-and-events.md#preserve-request-and-token-behavior). |
| Realm/model or remote-call failure | Verify `realmRef`, remote endpoint, deployed remote support, server exceptions, and serialized classes. Distinguish the wrong realm from absent data or unsupported local state in a remote method. See [server execution](server-and-support-code.md#replace-each-testing-client-responsibility). |
| Custom provider not found | Verify the provider ID, factory registration, resources, artifact contents, server dependency, and selected server configuration. See [provider packaging](server-and-support-code.md#complete-provider-packaging). |
| Tests interfere with later methods | Compare resource scope, dirty/restoration timing, browser state, and manually created resources. See [lifecycle ownership](resources-and-lifecycle.md#preserve-reset-timing-and-ownership). |

Browser startup errors, missing distributions, occupied ports, unavailable containers, network failures, and toolchain problems require environment evidence. The checked framework supports configurable server, database, and browser modes; record the selected modes before comparing local and CI outcomes. A browser-specific failure is not evidence that the test password must change, and an unavailable service is not evidence that an assertion is wrong.

## Stop retries that add no evidence

For each corrective attempt, connect the diagnostic to a specific cause and change, then inspect the new result. When the same failure persists without new evidence, report the phase, command, relevant diagnostic, attempted fixes, and smallest unresolved check; ask for direction if progress requires broader setup or a behavioral choice. Continue to honor the entrypoint's execution approval and completion criteria. A workaround, ignored event, or omitted scenario is not proof of equivalence.

## Resume interrupted work

Inspect branch/base, full status, existing diff, script output and fresh reports before continuing. Separate generated, manually corrected and unrelated files. Compare partial output with the scenario map and resume from the last evidenced stage; do not recreate the branch, overwrite files or rerun conversion blindly. Preserve prior execution and legacy-retention decisions. If output ownership or the intended revision is unclear, ask before mutation. Compilation or a previously passing command does not establish execution of the current diff.
