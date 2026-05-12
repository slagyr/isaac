---
# isaac-ztnp
title: "Per-turn cost budget instead of (or alongside) tool-loop iteration cap"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-30T03:50:02Z
updated_at: 2026-04-30T03:50:43Z
---

## Description

The tool-loop iteration cap (max-loops) is a poor proxy for what
we actually care about: dollars spent and tokens burned per user
turn. A 200-iteration turn that costs \$0.20 is fine; a 5-iteration
turn that costs \$3.00 because it kept feeding huge tool results
to a premium model is not.

## Goal

Track per-turn token/cost spend; cap turns at a configurable budget.

## Design questions to resolve when picked up

- Where the budget is configured: per-crew, per-session, per-call,
  or all three with a precedence order?
- Whether to soft-cap (warn, let LLM wrap up) or hard-cap (kill
  the turn).
- How to project cost from token counts when provider pricing is
  not known statically (claude-sdk vs grover vs codex vary).
- Whether the cap interacts with isaac-x4j2's wrap-up turn.

## Status

Deferred. Bumping max-loops to 100 (already done) buys us time.
This bead captures the proper redesign.

## Related

- isaac-x4j2: wrap-up turn at the cap
- (deferred sibling) Per-turn time budget
- (deferred sibling) No-progress detection in tool loops

