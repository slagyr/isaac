---
# isaac-ir44
title: "Reject turns on sessions with unknown crew"
status: completed
type: bug
priority: high
created_at: 2026-04-23T17:11:20Z
updated_at: 2026-04-25T02:38:45Z
---

## Description

A session whose :crew references a name not in the loaded config currently flows through config/loader.clj:519 resolve-crew, which silently returns {:model <defaults.model>} for any unknown crew. resolve-crew-context then fills the soul from read-workspace-file or the built-in default. No error surfaces — the user gets a crew-shaped thing indistinguishable from a lightly-defanged main.

Fix: the bridge must validate the session's crew against the loaded cfg on every turn. Unknown crew rejects the turn and pauses the session (no LLM call, no transcript append, session record untouched) until the user switches to a valid crew via /crew or re-adds the missing crew to config.

Spec: features/bridge/unknown_crew.feature

Implementation contract (pinned by the spec):
- Reject path emits :turn/rejected (warn) with :session, :crew, :reason :unknown-crew
- Reply text includes 'unknown crew: <name>' and 'use /crew <name> to switch, or add <name> to config'
- /crew command emits :session/crew-changed (info) with :session, :from, :to
- Successful turn emits :turn/accepted (info) with :session, :crew
- Turns are evaluated independently — no sticky 'paused' flag on the session; the check re-runs each turn

Introduces a new step: 'the reply contains X' — comm-neutral reader for the memory-comm reply (analogous to the existing 'the output contains' but name-scoped). Related refactor bead isaac-iea2 will later rename the CLI-scoped usage to 'the stdout contains'.

Acceptance:
1. Remove @wip from both scenarios in features/bridge/unknown_crew.feature
2. bb features features/bridge/unknown_crew.feature passes
3. bb features and bb spec pass

