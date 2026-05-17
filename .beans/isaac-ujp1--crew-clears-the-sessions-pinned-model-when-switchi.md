---
# isaac-ujp1
title: /crew clears the session's pinned :model when switching
status: draft
type: task
created_at: 2026-05-17T00:20:54Z
updated_at: 2026-05-17T00:20:54Z
---

## Problem

Sessions can carry a pinned `:model` on the sidecar — set via `/model <name>`, persists across turns, useful for outage continuity. But when `/crew <name>` switches the active crew, the pinned `:model` persists too. The new crew "loses their mind" — they get whatever model the previous crew pinned, not their own preferred model.

Concrete: alice pins `/model opus`. Conversation continues. `/crew bob`. Bob is now stuck using opus instead of his preferred model. Confusing.

## Approach

`/crew <name>` acquires a side effect: clears the session sidecar's `:model` field (and `:effort` / other session-pinned behavioral overrides if any). The cascade then falls through to the new crew's defaults on the next turn.

This preserves the legitimate use cases:
- **Outage continuity within a crew**: `/model opus` keeps opus active for as long as you're staying with that crew. Turn-after-turn, model is pinned.
- **Crew sanity**: switching crews resets to the new crew's preferred model. If the user wants the new crew also pinned to opus, an explicit `/model opus` makes the intent visible.

## Scope

- `src/isaac/slash/builtin.clj` (or wherever `crew-command` lives) — extend to clear pinned overrides
- Decide which fields get cleared: just `:model`, or all behavioral session-level overrides (`:effort`, `:context-mode`)? My instinct is **just `:model`** because the model pinning is the load-bearing UX risk. Other overrides (effort, context-mode) are smaller bumps if they persist; can revisit later.
- State-defining locked fields (`:history-retention`, `:cwd`) are NOT cleared. Those represent the session's identity.

## Out of scope

- Crew-scoped per-crew model overrides (`{crew → model}` map on the session). More state, more complexity. Only do this if usage shows the current proposal isn't enough.
- Cron / hook / ACP entry points that switch the active crew — out-of-band crew switches happen through different code paths and may need their own handling. Punt to a follow-up if needed.

## Relationship

- Independent of `isaac-bv48` (the funnel). Funnel design treats `:model` as behavioral; this bean is just a slash-command side effect.
- Could land before or after the funnel ships; doesn't depend on either direction.

## Feature file

`features/slash/crew.feature` (likely already exists; this bean adds 1–2 @wip scenarios for the clear-on-switch behavior).
