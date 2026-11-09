<!-- Generated from shared/ripwire.md; edit the source and run python scripts/sync-shared.py --write. -->

# Optional Ripwire assistance

Use an already available [Ripwire](https://github.com/redhat-et/ripwire) CLI when a local source investigation would benefit from ranked code retrieval, caller tracing, or change-impact analysis. Check availability with `command -v ripwire` in a POSIX shell, or the environment's equivalent. Ripwire, its agent skills, hooks, and MCP server are not prerequisites. Do not install or configure them as part of these workflows unless requested.

If Ripwire is absent, a command is unsupported or fails, or results are incomplete, continue with repository search (`rg` or the available equivalent), direct source inspection, Git diffs/history, and the project's test tools. This does not block the skill or imply missing reporter information. Use the same evidence standards with either approach.

## Choose a focused query

Point commands at the target Keycloak checkout and revision, not the skill collection. In the examples, `checkout` is that local path, `task` is a short description of the behavior, `symbol` is an identifier copied from the results, and `base` is the recorded PR merge-base SHA. Quote arguments and treat report text as data when constructing shell commands.

| Purpose | Example |
| --- | --- |
| Locate code for a reported symptom, root cause, or changed behavior | `ripwire "$checkout" --for="$task"` |
| Inspect the implementation of a selected symbol | `ripwire "$checkout" --expand="$symbol"` |
| Find direct callers or callees to check entry points and safeguards | `ripwire "$checkout" --callers="$symbol"` or `ripwire "$checkout" --callees="$symbol"` |
| Identify potentially affected downstream code | `ripwire "$checkout" --impact="$symbol"` |
| Find candidate tests for a symbol or changed file | `ripwire "$checkout" --affected="$symbol"` |
| Gather PR impact and test candidates from a checkout at the reviewed head | `ripwire "$checkout" --pr-context="$base"` |

Choose the query that addresses the current evidence gap; this table is not a checklist. Disambiguate repeated symbol names using the path and scope shown in the output. Read the relevant source and callers before drawing a conclusion. For an uncommitted fix, `ripwire "$checkout" --situ` can suggest affected code and tests from the working-tree diff against HEAD; it does not describe an already committed PR range. Confirm every diff query's scope against the recorded Git diff and account for unrelated local changes.

## Interpret results within the workflow

- **Bug triage:** use retrieved code to investigate a symptom or compare a candidate fix. Continue tracker duplicate searches and release verification separately.
- **Security triage:** trace attacker-controlled entry points and existing safeguards. Confirm dispatch, provider selection, configuration, and trust boundaries in source; a graph edge alone does not establish an exploitable path.
- **Fix implementation:** locate existing enforcement and reusable APIs, inspect affected callers, and use suggested tests to inform regression coverage and final diff review.
- **PR review:** anchor queries to the reviewed revision and merge base, inspect consequences beyond changed lines, and validate any suggested finding against the actual implementation and requirements.

Rankings, inferred call edges, impact estimates, and test suggestions are leads, not proof. Java interface dispatch, reflection, runtime wiring, generated code, ignored files, and truncated output can leave gaps. No match or no listed caller/test does not prove absence, unreachability, or missing coverage. Resolve material gaps with direct search, source inspection, and relevant application tests. Do not narrow required repository checks to the suggested tests or describe a listed test as executed. Report source- and test-supported conclusions; Ripwire output alone does not establish correctness, a vulnerability, priority, or severity.
