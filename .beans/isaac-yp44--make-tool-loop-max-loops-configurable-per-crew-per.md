---
# isaac-yp44
title: "Make tool-loop max-loops configurable (per-crew / per-model / default)"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-30T03:59:14Z
updated_at: 2026-04-30T03:59:39Z
---

## Description

max-loops is hardcoded as 100 on three providers' chat-with-tools
fns (anthropic.clj, ollama.clj, openai_compat.clj). The dispatcher
doesn't thread an opts map, so the default always wins.

## Goal

Make max-loops configurable so a runtime change doesn't require a
code edit and restart, and so different crews/models can use
different caps.

## Possible config shape

Layered, with normal precedence:

  defaults.tool-loop.max-loops             100 (default)
  models.<id>.tool-loop.max-loops          override per model
  crew.<id>.tool-loop.max-loops            override per crew

dispatch/dispatch-chat-with-tools threads an opts map down to the
provider's chat-with-tools, with the resolved value.

## Why deferred

The three sibling deferred beads (cost budget, time budget,
no-progress detection) will need a similar config-shape design
for tool-loop limits. Better to design all four config knobs
together when one of them gets picked up — avoid premature
schema split.

## Status

Deferred. Lifts a hardcoded constant to a config value when
the broader budget redesign happens.

## Related

- isaac-ztnp: per-turn cost budget
- isaac-9udu: per-turn time budget
- isaac-t3tb: no-progress detection
- isaac-x4j2: wrap-up turn at cap

