---
# isaac-kids
title: Measure classpath-cache warm gain on a module-bearing root (isaac-tki3 design pt 4)
status: todo
type: task
created_at: 2026-07-12T23:48:44Z
updated_at: 2026-07-12T23:48:44Z
---

## Goal

Measure and record cold-vs-warm CLI startup timing for isaac-tki3's classpath
cache on a **module-bearing root** (the environment the cache exists to help),
and issue the design-point-4 verdict: cache pays off (>=50% planning-phase
elimination on warm runs) OR planning is noise even with modules -> STOP/report.

## Why this is split from isaac-tki3

isaac-tki3 landed the classpath-plan cache and proved the FALSIFIABLE-IN-CI
portion hermetically at `origin/bean/isaac-tki3` @ `93f33ccf`:
- warm cache hit skips `plan-module-classpath-pairs` (recording-spy spec),
- fail-open silent replan on cached-path failure,
- `:module-coords` recorded in the cache `:basis`,
- startup phase timing instrumentation (`*timing-samples*`) exists.

The remaining acceptance item (design point 4) requires a REAL cold-vs-warm
wall-clock measurement on a real command. Three verify attempts measured it on
`/tmp/isaac-tki3-timing`, a bare `init` root with NO modules configured. With no
modules there is no module-walking / deps-resolution to skip, so cold==warm
(0.89s==0.89s) is the EXPECTED, meaningless result and `:module-coords` is
absent because there are none. That is correct empty-root behavior, not a cache
defect and not a valid STOP signal.

A meaningful measurement needs a module-bearing root, which no sandbox in the
work/verify loop has. It belongs to an operator host (the fleet's real
`~/.isaac`, ~9 modules) or a fixture whose `isaac.edn` pins the current
`:modules` set. This mirrors the l70j/l7l4, k1po, la8h precedent: a real-world
observation that cannot gate a green, verified code contract pre-merge.

## Acceptance (one-time, module-bearing environment)

- [ ] On a module-bearing root (real `~/.isaac`, or a fixture whose `isaac.edn`
      pins the current `:modules`), run a real non-fast-path command
      (e.g. `isaac config keys providers`) cold (no cache) then warm (cache
      present), with `/usr/bin/time -p`, several iterations.
- [ ] Confirm the written `cache/cli.edn` `:basis` contains `:module-coords`
      on that path.
- [ ] Record the cold/warm numbers + interpretation in this bean.
- [ ] Verdict per design point 4:
      - warm run eliminates >=50% of the planning-phase cost -> cache confirmed
        valuable; done.
      - planning is a trivial slice even WITH modules -> STOP: record that the
        cache is not worth its complexity; open a follow-up to revert/scope-down
        rather than shipping dead complexity.

## Notes

- No new production code expected here unless the STOP verdict fires.
- Depends on isaac-tki3 merging and the `:isaac.agent`/foundation pin advancing
  if measured against a deployed launcher rather than a source checkout.
