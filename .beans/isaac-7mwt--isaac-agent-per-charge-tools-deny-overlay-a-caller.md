---
# isaac-7mwt
title: 'isaac-agent: per-charge :tools {:deny …} overlay — a caller can withhold tools for one turn'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-09-25T02:47:03Z
updated_at: 2026-09-25T02:49:46Z
---

## Why

Micah, 2026-09-25: "On the GChat and Gmail comms the model should not need
to use the comms tool. It should just respond to the prompt and that
response will go back to the user." Today the yopp crew allows `:gchat/*`
and `:gmail/*` (needed for proactive sends from hail/task turns), so a
comm-originated turn also sees `gchat__send` / `gmail__send` aimed at the
very thread it is answering — and uses them, producing double replies
(isaac-mw27 / isaac-3t0z dedupe the symptom; this removes the cause).

Tool policy today is global + crew only (`allowed-tool-names`, isaac-da0r).
A caller cannot withhold a tool for one turn.

## Design (drive stays generic — no comm knowledge)

- Charge schema gains `:tools {:deny [...]}` — an optional per-charge overlay
  (same shape as `:cycle`): tool tokens (`:gchat/send`, `gchat__send`, or
  a prefix pattern the cascade already understands) withheld for this
  turn only. Description: "Tools withheld for this turn; applied after the
  global/crew cascade."
- `bridge/dispatch!` request → `charge/build` carries `:tools` through
  unchanged.
- Turn context: `allowed-tools` = cascade result minus the charge's deny
  list. Log `:turn/tools-withheld` (debug) with the names removed when
  non-empty. Nothing else changes; a charge without `:tools` behaves as
  today.
- The withheld tool is not offered to the model, not merely refused when
  called.

## Acceptance (isaac-agent spec)

- [ ] Charge with `:tools {:deny [:lens/read]}` and a crew that allows
  `lens/*` → the turn's tool definitions exclude `lens__read`, include the
  crew's other tools; `:turn/tools-withheld` logged with `["lens__read"]`.
- [ ] Charge without `:tools` → identical tool set to today (existing specs
  unchanged).
- [ ] Deny of a tool the cascade already excludes → no-op, no log.
- [ ] Both token spellings (`:lens/read`, `"lens__read"`) work.
- [ ] Version bump, bb spec / bb jvm-spec (protocol untouched, but run it) /
  bb lint green.

Likely repo scope: isaac-agent (charge.clj, bridge/core.clj, drive/turn.clj,
spec). Consumers: isaac-gchat and isaac-gmail beans (blocked by this one).
