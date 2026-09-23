---
# isaac-rfmh
title: gchat + gmail comms ignore defaults.crew and fall back to a hard-coded "main"
status: in-progress
type: bug
priority: high
created_at: 2026-09-23T19:57:08Z
updated_at: 2026-09-23T19:57:08Z
---

Micah 2026-09-23: "Yopp is the default crew. How could main ever get used by this page? … not specifying a crew should default to Yopp."

## What happens

Yopp's `isaac.edn` has `:defaults {:crew :yopp}` and `gchat/spaces` names a crew for only one space. A message in any other space resolved to crew **main** — `crew/main.md`, a soul with a model and no tools — so the turn ran with `:allowed-tools ["skill__load"]` and Yopp counted letters by hand instead of running code (log 19:44Z, session `gchat-tonotop-yopp-test-2`).

Cause: `isaac.comm.gchat.gate/decide` picks `(or space-crew (:crew slice) "main")` from the comm slice only; `isaac.comm.gmail.handler/crew` does the same with `(:gmail/crew slice)`. Neither consults the process default. Discord already does it right: `(get-in cfg [:defaults :crew])` before "main" (`channel-crew-id`).

## Fix

- gchat: `gate/decide` takes `:default-crew` in `opts`; the chain becomes space crew → `:gchat/crew`/`:crew` on the slice → `:default-crew` → "main". `handler` passes `(default-crew (full-config))`, normalising a keyword (`:yopp`) to its name.
- gmail: `handler/crew` adds `(get-in cfg [:defaults :crew])` (normalised) before "main".
- Sessions already created under the wrong crew: the handler dispatches with an explicit `:crew`, and the charge takes the request's crew before the session entry's, so existing gchat sessions pick up the right crew on their next message with no migration.

## Acceptance

- [ ] gchat gate spec: no space crew, no comm crew, `:default-crew "yopp"` → `:crew "yopp"`; comm crew still wins over the default; space crew wins over both; nothing given → "main".
- [ ] gchat handler spec: with `:defaults {:crew :yopp}` in the loaded config and no comm crew, the dispatched decision's `:crew` is "yopp".
- [ ] gmail spec: `crew` with `:defaults {:crew :yopp}` and no `gmail/crew` → "yopp"; `gmail/crew` still wins.
- [ ] Existing scenarios green in both repos; version bumps (gchat 0.2.12, gmail 0.1.8).

Likely repo scope: isaac-gchat (`gate.clj`, `handler.clj`), isaac-gmail (`handler.clj`).
