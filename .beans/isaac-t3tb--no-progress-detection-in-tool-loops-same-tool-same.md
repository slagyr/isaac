---
# isaac-t3tb
title: "No-progress detection in tool loops (same tool, same args, N times)"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-30T03:50:13Z
updated_at: 2026-04-30T03:50:44Z
---

## Description

A pathological tool loop pattern: the LLM keeps calling the same
tool with the same arguments expecting different results. The
classic example is repeatedly reading a file that hasn't changed,
or grepping the same pattern over and over.

## Goal

Detect this pattern and intervene before max-loops or budget caps
fire. When detected, either:
1. Inject a system note into the next LLM request (\"you've already
   called grep('foo', 'src') 3 times with the same result; consider
   a different approach\").
2. Force a wrap-up turn (same path as isaac-x4j2's fix #1).

## Design questions

- Detection threshold: how many repeats before intervention.
- Whether to compare on tool+args alone, or include result hash too
  (results changing means progress).
- Interaction with intentional retry patterns (e.g. exec a flaky
  command three times).

## Status

Deferred. Captures the design alongside cost-budget and
time-budget siblings.

## Related

- isaac-x4j2: wrap-up turn at cap
- (deferred sibling) Per-turn cost budget
- (deferred sibling) Per-turn time budget

