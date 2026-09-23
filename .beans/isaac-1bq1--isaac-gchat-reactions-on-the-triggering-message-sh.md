---
# isaac-1bq1
title: "isaac-gchat: reactions on the triggering message show Yopp's progress — \U0001F440 working, ✅ answered, ⚠️ failed, ⏳ parked — one reply per message, no status posts"
status: completed
type: feature
priority: high
tags:
    - gchat
    - comm
created_at: 2026-09-23T18:24:53Z
updated_at: 2026-09-23T18:47:08Z
---

## Decision (Micah, 2026-09-23)

One response per message, and it must be a new message so it notifies. No
status message, no trace in the answer — Yopp reads like a person. Progress
is shown with reactions on the triggering message, which the Chat API lets a
user add and remove and which do not notify. Verified live on yopp
2026-09-23: 👀 added to Micah's DM message, held 20 s, removed, ✅ added — the
glyphs changed on his screen, no notification anywhere. The current token
(chat.messages scope) already allows reactions.create/delete; no new scope.

## Change (isaac-gchat)

- Reaction lifecycle on the message that triggered the turn, driven by the
  comm hooks already firing: on-turn-start → 👀; on-reply (answer posted) →
  remove 👀, add ✅; on-turn-end with :ended-by :error → ⚠️; parked on weather
  (:provider-unavailable) → ⏳ (kept until the resumed reply lands, then ✅).
  Remove-then-add for every change (there is no reaction update). Reaction
  names are remembered per turn so removal is exact.
- A message Yopp heard but was not addressed by (mention-only room, no
  mention) gets no reaction.
- Reaction failures are logged once at debug and never fail the turn.
- Glyphs configurable per comm (`gchat/reactions {:working "👀" :done "✅"
  :failed "⚠️" :parked "⏳"}`), default as above; `gchat/reactions false`
  turns the lifecycle off.
- Details on demand is conversational: one line in isaac.comm.gchat.guidance
  — when asked what you did or how you know, summarise the tools you used
  from the transcript in plain words; otherwise never include a trace.

## Scenarios (outbound.feature, stubbed Chat API records reaction calls)

- addressed message → 👀 on start, then removed and ✅ after the reply posts.
- error turn → 👀 then ⚠️ (and the h5v8 notice, unchanged).
- parked turn → ⏳; resumed reply → ✅.
- heard-only message → no reaction calls.
- reactions disabled → no reaction calls, everything else unchanged.

## Acceptance

bb spec / bb features / bb ci green in isaac-gchat; one-time on yopp: a DM
shows 👀 while Yopp works and ✅ when it answers, with exactly one reply.

## Related

isaac-h5v8 (notices), isaac-acou (guidance namespace), isaac-pq0b (Discord
live rendering — a different renderer for the same hooks).

## Handoff (worker, 2026-09-23)

Branch `bean/isaac-1bq1` pushed to `isaac-gchat` (worktree
`isaac-gchat-isaac-1bq1`, main 5262798 → one commit, gchat 0.2.10). `bb lint`,
`bb spec` (163/163), `bb features` (51/51), `bb ci` all green. Bean left
`in-progress`, no tags, per the dispatching agent's explicit instruction (not
the gated/ungated flow in this file — no `feature-baseline:` line here).

**Which hook drives each state.** `isaac.comm.protocol/on-turn-start` does
NOT carry `:origin` (its signature is `[comm session-key input]`), so the
lifecycle rides `on-cycle-start` instead, gated to the turn's first cycle
(`(= 1 (:n cycle))` — confirmed `:n` starts at 1 per turn in
`isaac-agent/src/isaac/drive/turn.clj`):
- `on-cycle-start*`, first cycle only → 👀 (`reaction-working!`).
- `on-reply*`, once text posts (normal post or the isaac-qry7 divert path) →
  remove current, add ✅ (`reaction-done!`).
- `on-turn-end*`, `:ended-by :error` → remove current, add ⚠️
  (`reaction-failed!`).
- `on-turn-end*`, weather (`:provider-unavailable`) and not already parked →
  remove current, add ⏳ (`reaction-parked!`).
- Heard-only messages (`gate/decide` → `:action :log`) never dispatch, so no
  cycle starts and no reaction calls happen — nothing extra needed there.

**Reaction call shapes** (`src/isaac/comm/gchat/chat_api.clj`):
- `create-reaction! {:message :emoji :token}` → `POST
  {chat-base}/{message}/reactions {:emoji {:unicode emoji}}`. Returns the
  reaction's own (relative) resource name for the later delete.
- `delete-reaction! {:reaction :token}` → `DELETE {chat-base}/{reaction}`.
- State lives in `isaac.comm.gchat/reaction-state*`, keyed by session-key:
  `{:message :reaction :emoji :kind}`, `:kind` one of
  `:working|:done|:failed|:parked`. Remove-then-add always (`set-reaction!`).
  A create/delete failure logs once at `:debug` —
  `:gchat.reaction/failed {:message :emoji :status}` — never retried, never
  fails the turn.
- **Same-message resume guard:** `reaction-working!` skips re-adding 👀 when
  the session's current reaction is `:parked` on the *same* message (a
  literal same-turn resume would otherwise flicker ⏳→👀). A genuinely new
  triggering message is not guarded — since state is per-session (one
  reaction shown at a time, not per-message), a new message's first cycle
  evicts whatever the session is currently showing (even a still-parked ⏳
  from an earlier, never-resumed message) and starts its own 👀→✅ lifecycle.
  This only matters in the case a follow-up message arrives while a prior
  one is still parked — worth a note to Micah if it ever looks surprising
  live.

**Config keys.** `gchat/reactions`: a map merging over
`{:working "👀" :done "✅" :failed "⚠️" :parked "⏳"}`, or `false` to disable
the whole lifecycle (checked in every reaction call site via
`reactions-cfg`). Declared in `resources/isaac-manifest.edn`'s `:extra-schema`
as `:type :one-of :specs [{:type :boolean} {:type :map ...}]`.

**Guidance line** (`src/isaac/comm/gchat/guidance.clj`, appended to `TEXT`,
verbatim): "If asked what you did or how you know something, summarise the
tools you used from the transcript in plain words; otherwise never include a
trace or list of tools in your reply."

**Files touched:** `src/isaac/comm/gchat.clj`,
`src/isaac/comm/gchat/chat_api.clj`, `src/isaac/comm/gchat/handler.clj`
(plumbs the triggering message's `:name` onto `decision`/`origin` as
`:message`), `src/isaac/comm/gchat/guidance.clj`,
`resources/isaac-manifest.edn` (0.2.9 → 0.2.10),
`feature-steps/isaac/gchat_steps.clj` (stub now answers `/reactions`
POST/DELETE with a deterministic relative reaction name; added the `no
reaction calls were made` Then step), `features/comm/gchat/outbound.feature`
(5 new scenarios), plus specs for all of the above. 11 files, +450/-12.

**Scenarios (outbound.feature, spaces RX1–RX5):** addressed message
(👀 then ✅, exact URLs/`#index`); error turn (👀 then ⚠️, h5v8 notice
unchanged); parked turn (⏳ shown; the next message's reply lands ✅ — see
the resume-guard note above for why message 1's ⏳ isn't independently
re-asserted after message 2); heard-only (no reaction calls); `gchat/reactions
false` (no reaction calls, reply still posts once).

## Landed on main

main-sha: isaac-gchat 389b71fc8239f8ab41e83ededf176ee7cbcf1e35 (0.2.10). Planner reran bb spec (163/0) and bb features (51/0) on the branch, squash-pushed to main, deleted bean/isaac-1bq1, repinned the registry.
