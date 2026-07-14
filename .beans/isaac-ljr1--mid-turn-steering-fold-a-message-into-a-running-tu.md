---
# isaac-ljr1
title: 'Mid-turn steering: fold a message into a running turn''s tool loop'
status: draft
type: feature
priority: normal
created_at: 2026-07-14T22:36:56Z
updated_at: 2026-07-14T22:36:56Z
---

## Goal

Let a human (or agent) inject a message that folds into a RUNNING turn's tool loop — mid-turn steering — so a worker can be unblocked or redirected WITHOUT cancelling its progress or waiting for the turn to end.

## Motivating case (2026-07-14, tonotop)

A tono worker was mid-turn, blocked on an external prerequisite (needed a human to `aws login`). The human completed the login on zanebot, but the worker — still in its tool loop — doesn't know. A normal hail to a busy session is REFUSED (`:session-in-flight`, bridge/core.clj:52) and only lands as the NEXT turn. So the human's only levers today are: wait for the turn to end (then hail), or cancel (loses the turn's progress). Neither folds "the prerequisite is ready — continue" into the work in flight.

## Mechanism (the seam already exists)

The tool loop (`isaac-agent/src/isaac/llm/tool_loop.clj`) checks a `cancelled?` predicate between every cycle. Add a parallel check for a pending STEER message for this session:
- Between cycles, if a steer message is queued for the session, inject it as a `{:role "user" :content …}` message into the next cycle's context (the model sees it before its next action).
- Steers fold into the live turn; the turn's progress is preserved.
- Multiple steers apply in order.

## Delivery (design — settle with Micah)

How the steer arrives is the main open question:
- A hail-like send with a `:steer` (or `:interrupt`) intent that, for a BUSY session, appends to that session's in-flight steer queue instead of waiting/refusing. For a FREE session it could no-op/error or fall back to a normal hail (TBD).
- Storage: the steer queue lives in the session's in-flight state (durable, like the turn marker) so it survives a mid-turn read and is drained by the loop.
- Addressing: by session id (you're steering a specific in-flight worker), or by band+bean (steer whoever holds this bean). Session-id is the crisp case.
- Reachable over the HTTP surface (POST /hail/send or a sibling /hail/steer) so remote agents can steer too (see HAILING.md).

## Scenarios (spec with Micah)

Coverage to settle: a steer queued for a busy session is injected into the next tool-loop cycle (folded, turn continues); the worker sees it before its next tool call; multiple steers apply in order; a steer to a FREE/absent session behaves per the settled policy (no-op/error/normal-hail); the steer queue is durable across the mid-turn read; a steered turn still ends normally.

## Out of scope

Cancel already exists (aborts, loses progress) — steer is the non-destructive complement, not a replacement.
