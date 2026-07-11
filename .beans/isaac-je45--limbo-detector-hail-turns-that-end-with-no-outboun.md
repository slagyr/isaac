---
# isaac-je45
title: 'Limbo detector: hail turns that end with no outbound hail and no ledger change auto-continue'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-07-10T22:25:32Z
updated_at: 2026-07-11T03:13:30Z
---

## Goal

A hail-driven bean turn that ends in limbo — no outbound hail sent, no beans-repo change committed, bean not completed — must not strand silently. The delivery worker detects the shape and re-queues the delivery as a continuation (existing isaac-5ru9 machinery, session-pinned), with a prompt telling the agent its previous turn ended without a terminal action.

## Evidence (three specimens in three days)

1. isaac-gnji (2026-07-07): tool-loop limit, 'ask me to continue' to an empty room (led to isaac-5ru9).
2. isaac-o14c (2026-07-08): worker ended with 'Continuing the /status investigation' — work complete, no handoff; 10 silent minutes until human nudge.
3. isaac-tzgb (2026-07-10): VERIFIER rendered 'Verified and passed', posted the green discord notification, then ended without updating the bean or committing — ledger lied for 4.5 hours until human nudge.

Skill rules ('never end a turn in limbo') exist in all three band skills and demonstrably cannot bind a model that emits a sign-off sentence and stops.

## Design sketch

- Detection at delivery completion (hail worker owns the delivery lifecycle): the turn produced (a) no hail-send tool call, and (b) no push to the bean repo during the turn... simplest reliable proxy: consult the turn's executed tools for hail-send AND beans-repo mutation (or bean status change via pull). Exact signals to be specced — candidate: worker inspects the turn result's tool-call list (already returned by the drive) for hail-send; if absent and the delivery came from a bean band, re-queue as continuation with a 'your turn ended without a terminal action' prompt.
- Budget: rides the 5ru9 continuations counter (cap applies, dead-letter reason :continuations-exhausted -> now loud via jx7u).
- Session-pinned re-queue (avoid the cold-sibling takeover observed on isaac-wpny).
- Out of scope: judging whether the terminal action was CORRECT — only that one exists.

## Detection signal (planner-decided, Micah-approved 2026-07-11)

At delivery completion the hail worker checks: (a) the turn executed NO
hail-send tool call (the drive already returns the executed tool list), AND
(b) the delivery's bean (params :bean-id, when present) is not `completed`
after a beans-repo pull. Both true => re-queue as a session-pinned
continuation (5ru9 counter, existing cap; exhaustion dead-letters loudly per
isaac-jx7u) with prompt: "your previous turn ended without a terminal action
(no handoff hail, bean not completed) — complete the handoff or escalation
your skill prescribes." Deliveries without a :bean-id are exempt (chat/ad-hoc
hails legitimately end silently).

## Scenarios (worker writes; required coverage)

1. Bean-band turn ends with no hail-send and bean not completed: delivery
   re-queued as continuation to the SAME session, continuations counter
   incremented, prompt contains "terminal action".
2. Turn ends with a verify-band handoff hail sent: delivered normally, no
   continuation (terminal action present).
3. Turn completes the bean (status completed, no outbound hail): delivered
   normally — completion is terminal.
4. Continuation budget exhaustion via repeated limbo: dead-letters with
   :reason :continuations-exhausted and posts attention (jx7u machinery,
   regression assert).
5. Delivery without :bean-id ends silently: no continuation (exemption).

## Verify fail (attempt 1, 2026-07-11): canonical branch adds only turn-result plumbing; the required hail-worker limbo detector and acceptance coverage are still missing

Evidence:
- Canonical implementation found only in `isaac-agent` `origin/bean/isaac-je45` at `430c6d8`, based on current `origin/main` `92775a4`.
- That branch's only bean-specific commit is `430c6d8` (`isaac-je45: expose executed-tool-names on successful turn result`). It adds `:executed-tool-names` in `src/isaac/drive/turn.clj:971`.
- The bean's required behavior lives in the hail delivery worker, not just the turn result: completion-time check for **no** `hail-send` and bean not `completed`, then session-pinned continuation with prompt text containing `your previous turn ended without a terminal action`.
- Current `isaac-hail/src/isaac/hail/delivery_worker.clj` still only queues continuations for `:tool-loop-limit` (`= :tool-loop-limit (:ended-by result)` -> `queue-continuation!`) at lines 401-402; there is no bean-status / no-handoff check.
- Search across sibling repos found no `isaac-je45` implementation branch or commit in `isaac-hail`, and `rg` over `isaac-hail` found no occurrence of the required prompt text and no code checking `:executed-tool-names` for limbo detection.
- Existing hail acceptance still covers only the old loop-limit continuation path in `isaac-hail/features/delivery.feature:640-713`; the new required scenarios from the bean body are not implemented there.
- `clojure -M:spec` on the canonical `isaac-agent` branch passed (`1214 examples, 0 failures, 2425 assertions`), so this is not a syntax/CI issue. Verification fails because the actual bean acceptance/behavior is incomplete.
