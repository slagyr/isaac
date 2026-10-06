---
# isaac-oas8
title: Queue worker creates a missing named session with the crew its frequencies name
status: todo
type: bug
priority: high
created_at: 2026-10-06T20:23:23Z
updated_at: 2026-10-06T20:23:23Z
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
