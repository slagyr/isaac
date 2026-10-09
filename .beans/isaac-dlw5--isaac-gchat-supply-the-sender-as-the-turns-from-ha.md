---
# isaac-dlw5
title: 'isaac-gchat: supply the sender as the turn''s :from handle'
status: completed
type: feature
priority: normal
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-09T18:01:35Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

Part of the contacts epic (isaac-zt1x). Planned with Micah 2026-10-08/09. The attribution fields it needed (isaac-v403) have landed.

## Problem

Google Chat knows exactly who sent each message and tells the agent only through free-form data: the user message is rendered `[thread:xxxx] Name <email>: text`, and the origin map carries `:user`, `:display-name` and `:email` in a shape only gchat understands. The turn record's `:from` does not say who spoke.

## What to build

When gchat dispatches a turn it sets `:from` to a handle:

```clojure
{:kind :handle :comm :gchat :id "users/<id>" :name "<display name>" :email "<email>" :authenticated true}
```

- `:id` is the Google `users/<id>`, the same value origin's `:user` carries.
- `:name` and `:email` are included when gchat has them (it already resolves them through `isaac.google.people`); they are left out, not blank, when it does not.
- `:authenticated` is always true: Google vouches for a Chat sender.
- `:comm` is the comm type `:gchat`, not the config slot id. Google user ids are global across tenants.

Nothing else changes: the rendered message text, the origin map and the allow-list gate stay as they are. gchat does not depend on `isaac-contacts`.

## Notes for the implementer

- New step: `the latest turn on session "<name>" has from:` — a key/value table matched against the `:from` of the newest turn record for that session. Put it beside the existing `session "..." has origin:` in `feature-steps/isaac/comm/gchat/gchat_steps.clj`. A comm-started turn has a generated id, so the scenario cannot name the turn.
- The agent already carries `:from` on the direct dispatch path (`isaac.agent.bridge.core`). Bump the `isaac-agent` pin to a main sha that has isaac-v403 if the current pin predates it.
- Fixtures use the Marigold cast only.

## Likely repo scope

`isaac-gchat`.

## Acceptance

Run from `isaac-gchat`, with `@wip` removed from the scenario:

- `bb features features/comm/gchat/inbound.feature`
- The repo's full verification green.
- The gchat handbook chapter says the turn's `:from` is a handle, lists its fields, and says a Chat sender is always authenticated.

feature-baseline: isaac-gchat c32e8e91ba34917ddea93d59d6615ba96b0fa742
feature-blob: isaac-gchat features/comm/gchat/inbound.feature e44993e0a674d702ec01e511836a99c7e3076862 271

## Landed on main (2026-10-09)

main-sha: isaac-gchat fb171478499389f62b3f23dcb93db0d7847ca180

## Done / next (2026-10-09)

Done: gchat sender handle dispatch, persisted-turn feature step, optional-field spec, isaac-agent v403 pin and handbook; inbound feature and `bb ci` green; bean gate PASS on main squash.
Next: delete the bean branch and complete the bean. Resume at `git worktree remove ../isaac-gchat-dlw5` in the isaac-gchat checkout, then `beans update isaac-dlw5 --status=completed` here.
