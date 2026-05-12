---
# isaac-htv1
title: "Delete 25 unused step definitions"
status: completed
type: task
priority: low
created_at: 2026-04-24T14:01:13Z
updated_at: 2026-04-25T02:24:45Z
---

## Description

Static analysis of spec/isaac/features/steps vs features/** found 25 registered step definitions with no feature referencing them. Deleting removes dead surface area.

Delete list (file:line  type  phrase):

Session management rename detritus (8) — neither old-agent nor new-crew variants are used:
- session.clj:410  given  agent {agent:string} has sessions:
- session.clj:427  given  crew {crew:string} has sessions:
- session.clj:523  when   sessions are created for agent {agent:string}:
- session.clj:543  when   sessions are created for crew {crew:string}:
- session.clj:763  then   regex: agent "([^"]+)" has (\d+) sessions?
- session.clj:768  then   regex: crew "([^"]+)" has (\d+) sessions?
- session.clj:773  then   agent {agent:string} has sessions matching:
- session.clj:779  then   crew {crew:string} has sessions matching:

Reply variants added but never adopted (4) — isaac-iea2 shipped, these were speculative:
- cli.clj:162  then  the reply eventually contains {expected:string}
- cli.clj:215  then  the reply lines contain in order:
- cli.clj:228  then  the reply lines match:
- cli.clj:241  then  the reply has at least {int} lines

Discord Gateway variants superseded by 'Discord sends' (2):
- discord.clj:174  when  the Gateway sends HELLO:
- discord.clj:183  when  the Gateway sends READY:

Genuinely dead (11):
- acp.clj:433      given  the ACP proxy is connected via loopback
- providers.clj:89  given  Claude Code is logged in
- providers.clj:98  then   the request includes header {header:string}
- providers.clj:135 then   the last provider request contains a function_call_output item
- providers.clj:143 then   the last provider request does not contain any role:tool input item
- providers.clj:149 then   regex: the request header "(.+)" matches #"(.+)"
- providers.clj:157 then   the request header {header:string} is {expected:string}
- session.clj:307  given  the agent has tools:
- session.clj:347  given  the LLM server is not running
- session.clj:885  then   regex: session "([^"]+)" has cwd
- tools.clj:322    then   the tool result contains:

Method: bb script walks defgiven/defwhen/defthen forms in spec/isaac/features/steps/, extracts phrase literals and regex patterns, cross-references with features/**/*.feature via literal-substring or regex match. Script at /tmp/find-dead-steps.clj during the investigation (not committed).

Scope:
- Delete the step forms.
- Delete any now-orphaned helper fns used only by those steps (verify with grep before deletion).
- Run bb features + bb spec — full suite must still pass.

Acceptance:
1. 25 step definitions listed above are removed.
2. No helper fns are left orphaned (greppable).
3. bb features and bb spec pass.

## Notes

bb unused (gherclj v0.9.0) reported 28 unused steps — 3 more than my earlier static analysis missed:
- cli.clj:384 the isaac file {path:string} exists
- discord.clj:237 the EDN file "{path}" contains:
- discord.clj:253 the EDN file "{path}" does not exist

Add these to the delete list (29 total). After deletion, 'bb unused' should report 0.

Line numbers above are post-docstring-pass; re-verify with 'bb unused' before deleting.

