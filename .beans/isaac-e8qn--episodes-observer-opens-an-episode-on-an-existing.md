---
# isaac-e8qn
title: Episodes observer opens an episode on an existing session without re-creating it
status: completed
type: bug
priority: high
created_at: 2026-10-07T13:58:04Z
updated_at: 2026-10-07T14:04:47Z
---

Yopp, 2026-10-07 11:00Z: `:session/observer-error :observer "episodes" :error "session already exists: roving-sextant"`, delivered to Micah as an attention notice. Regression from the isaac-ka10 port.

## Cause (diagnosis by Micah's other agent, confirmed)
`isaac.session.episodes.lifecycle/open-episode!` always calls `session-ctx/create-with-resolved-behavior!`. The observer runs it on `:session-opened` / `:turn-started`, after the turn has already created the session, so the store throws. The old policy path skipped the create when `get-session` found one; the observer's lifecycle path does not.

## Fix
In `open-episode!`, create only when the store has no session for that thread:
```clojure
(cond
  (nil? thread) (log/warn :episodes/open-without-session-id :episode id :crew crew)
  (nil? ss)     (log/warn :episodes/open-without-store :episode id :crew crew)
  (session-store/get-session ss thread) nil
  :else         (session-ctx/create-with-resolved-behavior! thread create-opts))
```
Still write the episode record either way. Do not swallow the error in the observer: that would hide a real duplicate create.

## Acceptance (gated)
- The @wip scenario at the end of isaac-episodes `features/episodes/context_mode_and_observer.feature` passes with @wip removed.
- Spec: opening an episode on a session that already exists does not throw; the episode is `:open`; the session id is unchanged.
- `bb ci` green.

Likely repo scope: isaac-episodes. Deploy: yopp and zanebot (episodes pin).

feature-baseline: isaac-episodes beedc19e8c2444080a6a10a3a431a23a2e2f1ac4
feature-blob: isaac-episodes features/episodes/context_mode_and_observer.feature ef5da117a85d0bb3f3dd7304d500d86c2f8d2037

## Landed on main (2026-10-07)

main-sha: isaac-episodes 4f91100230a95f2047b50cb2eb2838211fcada51
