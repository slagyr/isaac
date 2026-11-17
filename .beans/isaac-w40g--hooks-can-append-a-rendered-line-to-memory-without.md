---
# isaac-w40g
title: Hooks can append a rendered line to memory without spending a turn
status: draft
type: feature
priority: normal
created_at: 2026-11-17T02:23:19Z
updated_at: 2026-11-17T02:23:19Z
---

Found by Zane Lab, 2026-11-16 (security finding
`location-stream-sent-to-openai`, experiment `pinky-local-model`).

## What is wrong

Every pinky hook (location and eight health metrics) spends a full model
turn whose only job is to call `memory_write` with a fixed JSON line. The
line is a pure function of the payload. In 7 days: 198 location dispatches
and about 600 model requests, each sending a precise location or health
reading to a model provider for a template fill. Interim since 2026-11-16:
pinky runs on the local `qwen25` model (50/50 correct in the lab, about 10 s
per ping). A 30-line script produced the same line 50/50 in microseconds.

## Design

A hook front-matter key, e.g. `memory-append`, holding a template. When
present the hooks handler renders it with each value JSON-encoded, parses
the result as one JSON object, appends it to the crew's daily memory file
through the same seam `memory_write` uses, and returns 202. No turn. An
invalid render logs `:hook/memory-append-rejected` with the hook name and
missing keys (never values) and writes nothing. The prompt body stays
optional for hooks that still want a turn.

## Acceptance (to be written as scenarios)

- A location hook POST produces exactly one memory line equal to the
  rendered template and no `:turn/request-sent` event.
- A string value containing a quote produces valid JSON.
- A payload missing a referenced key produces no line and one rejected log
  entry.

Reference: `/Users/zane/agents/zane-lab/experiments/pinky-local-model/PROPOSAL.md`
on zanebot.
