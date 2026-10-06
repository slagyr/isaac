---
# isaac-oas8
title: Queue worker creates a missing named session with the crew its frequencies name
status: in-progress
type: bug
priority: high
created_at: 2026-10-06T20:23:23Z
updated_at: 2026-10-06T20:28:57Z
---

Likely repo: **isaac-agent**. Found by Foreman pilot 1 (isaac-8uno on zanebot), 2026-10-06.

## Why

The bean-work machine addresses its worker turn as
`{:crew "scrapper" :session "bean-<id>" :create :if-missing}`. The resolver is right: for a
missing named session it returns the key plus `:create-identity {:crew "scrapper"}`
(`frequencies.clj` explicit-session branch). The queue worker drops it:
`worker.clj` `wake-charge` only creates a session when the target has NO `:session-key`
(`(or (:session-key target) (when (:create? target) …create…))`). A named session always has a
key, so nothing is created, and the turn is built for a session that does not exist with the
default crew (zane). On zanebot it logged `:session/behavior-resolved :crew "zane"` every tick and
never ran.

`:crew` picks the session; when `:create` makes one, `:crew` is the new session's crew.
`:with-crew` stays a per-turn override and is not the fix.

## Design

- In `wake-charge`, a target with `:create? true` is created whether or not it has a key — a
  named key creates that session, with `:create-identity` (crew, tags), then the turn runs there.
- New step for the scenario: `a turn with input "<text>" is submitted with frequencies:` with a
  `key | value` table (`session`, `crew`, `create`, …), submitted the way the existing
  `is submitted to crew` steps do, capturing `#turn-id`.

## Acceptance

- isaac-agent `features/turn/session_selection.feature:193`.
- The rest of the isaac-agent features stay green.

feature-baseline: isaac-agent 45b19b48ccb55fd91fe609654907c8f818555b65
feature-blob: isaac-agent features/turn/session_selection.feature 4e9f86bb5467d3695e021cee2cf65a9ca5154e5d 193

## Implementation blocked (2026-10-06)

`bean/isaac-oas8` in isaac-agent at `0c02abb` implements the named-session create path and the table step; `bb features` passes (876 examples, 0 failures, 1 existing pending); focused worker specs pass (22 examples, 0 failures); `bb bean-gate verify isaac-oas8 --dir isaac-agent=../isaac-agent-oas8` passes on this branch. Landing requires a green rebase `bb ci`, but full `bb spec` consistently fails on the unrelated `session feature steps a parked send that completes during admission does not await the running turn` example (`isaac.foundation.fs/instance: no filesystem available`). Reproduced on clean `origin/main` in a separate detached worktree: `bb spec` has the same failure (1887 examples, 1 failure), whereas the same test focused with `bb spec --focus spec/isaac/agent/session/session_steps_spec.clj:71` passes on both trees. This is an existing order-dependent suite failure outside this bean's scope. No edits to that unrelated spec were made. Need planner guidance / a separate fix on main before the required green `bb ci` landing step.
