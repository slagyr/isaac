---
# isaac-7mwt
title: 'isaac-agent: per-charge :tools {:deny …} overlay — a caller can withhold tools for one turn'
status: scrapped
type: feature
priority: high
created_at: 2026-09-25T02:47:03Z
updated_at: 2026-09-25T02:54:14Z
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

## Work notes (scrapper@isaac-work-1, 2026-09-24)

Implemented on isaac-agent branch `bean/isaac-7mwt` (commit 1f623c9):
- charge.clj: `:tools` schema key + `charge/build` carries it (request → dispatch! → build unchanged path).
- drive/turn.clj: `withhold-charge-tools` subtracts the charge's `:deny` list (names/allowed? matching — :ns/name, ns__name, :ns/*) from cascade + skill auto-tools in build-turn; logs `:turn/tools-withheld` (debug, :tools sorted wire names) only when something is removed.
- Specs: charge_spec (+2), turn_spec (+5: withhold + defs exclude lens__read + log; no :tools unchanged; already-excluded deny no-op/no log; wire spelling; glob).
- Version 0.1.82 → 0.1.83, CHANGELOG entry.
- bb spec: 1787/0. bb lint: 518 errors/146 warnings, same as baseline (none in touched code).
- bb jvm-spec: 1787/4. All 4 in spec/isaac/agent/manifest_spec.clj and pre-existing: test-resources/isaac-manifest.edn shadows resources/ on the JVM classpath. Not touched here.

## Landed on main (2026-09-24)

main-sha: isaac-agent 62533d4009bd518dfab11a119f5acc5a3e987da6

Verified by perceptor@isaac-verify: bb ci green on branch (1787 specs / 868 features, 0 failures, 1 pre-existing pending). bb lint 518 errors / 146 warnings identical to origin/main (pre-existing). bb jvm-spec 4 failures in manifest_spec comm berth, reproduced identically on origin/main 5ea0e4c (pre-existing). All acceptance bullets covered by turn_spec "per-charge tools deny overlay" + charge_spec; version 0.1.82 -> 0.1.83. (Worker notes were in a stray literal-glob file .beans/isaac-7mwt--*.md; merged into this body and removed.)



## Scrapped (2026-09-25 02:54Z)

Micah: not the right decision. Do not work, verify, or land this bean. Any branch for it is abandoned.
