---
# isaac-je45
title: 'Limbo detector: hail turns that end with no outbound hail and no ledger change auto-continue'
status: draft
type: feature
priority: high
created_at: 2026-07-10T22:25:32Z
updated_at: 2026-07-10T22:25:32Z
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

## Scenarios

Spec with Micah before dispatch.
