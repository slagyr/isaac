---
# isaac-6doh
title: 'The drive knows nothing about hail: hail markers resume like any other source'
status: completed
type: task
priority: high
created_at: 2026-09-21T17:07:03Z
updated_at: 2026-09-21T17:35:35Z
blocking:
    - isaac-9azm
---

**Ruling (Micah, 2026-09-21, isaac-9azm): the driver should know nothing
about hail.** Today `isaac-agent` carries hail-shaped code on the resume and
marker paths because hail used to own the turn's outcome. Once hail's
responsibility ends at turn start, a hail marker is a work order like cron's:
never stale, resumed in its own session through the turn queue, exactly like
`#{:comm :cron :cli}` (isaac-yxch).

## What goes

- `src/isaac/bridge/resume.clj`: `marker->delivery`, `requeue-hail!`,
  `write-delivery!` / `deliveries-path`, `archive-cancelled-hail!` /
  `cancelled-dir`, `crash-orphan?`, `resume-attempts`, and the `(= :hail
  source)` branches in `resume-marker!`. `:hail` takes the
  `enqueue-resume-turn!` path with the other sources. A cancelled marker of
  any source is simply cleared.
- `src/isaac/bridge/core.clj` `turn-marker`: no `:delivery-id`, no
  `:attempts`, no embedded `:delivery` payload; the marker is source +
  started-at (plus whatever suspend/weather stamps later). `marker-source`
  needs no `:hail` case — the origin kind is just carried.
- Anything else `grep -rn hail src/` turns up in isaac-agent (the
  `:hail-delivery` charge key is set by hail, not read here; confirm and
  drop any reader).

## Interim behaviour (between this landing and isaac-9azm)

Hail still embeds the delivery on the charge and still waits on the turn's
future; the bridge simply stops copying the payload into the marker. A
restart with an in-flight hail turn resumes it in its own session (right)
and hail's stale-delivery guard finds no marker references (inert, harmless).
Hail's own `turn-resume.feature` / `turn-marker-claim.feature` are already
`@wip` on hail main for isaac-9azm, so this bean must **not** repin
isaac-hail — isaac-9azm bumps the pin and recuts hail's specs.

Known trade-off (recorded, not solved here): the hard-crash "attempts+1"
crash-loop breaker for hail markers goes with `crash-orphan?`. Comm, cron
and CLI markers never had one; hail joins the same policy.

## Scenarios

`isaac-agent/features/session/resume_repair.feature`: "a legacy hail marker
is requeued and removed from its original path" is recut (`@wip`) to "a hail
marker resumes in its own session through the turn queue".

## Acceptance

```
cd isaac-agent
bb features features/session/resume_repair.feature
bb features features/session/resume_queue.feature
bb features features/session/turn_markers.feature
bb ci
grep -rn "hail" src/    # one-time check: no hail-shaped code remains
```

- A `:hail` marker (suspended or crash orphan, any age) is handed to the
  turn queue and completes in its own session; no file is written under
  `hail/`.
- `grep -rn hail isaac-agent/src/` is clean (one-time acceptance, not a
  permanent scenario).
- Do **not** repin isaac-hail in this bean.

feature-baseline: isaac-agent cabfdf29e81b87307a158b0fccd8056d0c03135d
feature-blob: isaac-agent features/session/resume_repair.feature 19b913c0fd8d157a2f251c81adcc2f0341b9f3d6 50



Dispatched: hail 209439d6 2026-09-21T17:15:30Z (band isaac-work)

## Landed on main (2026-09-21)

main-sha: isaac-agent 984f59ab9b1b138d0c64140e98572430205c1381

### What went

`bridge/resume.clj`: `marker->delivery`, `requeue-hail!`, `write-delivery!`,
`deliveries-path`, `archive-cancelled-hail!`, `cancelled-dir`,
`crash-orphan?`, `resume-attempts`, `normalize-id`, `write-edn` and the
`clojure.pprint` require. `resume-marker!` no longer reads `:source` at all:
a cancelled marker of any source is cleared, and every other live marker
takes `enqueue-resume-turn!` (the old `#{:comm :cron :cli}` set is gone, so
`:hail` and any future source resume the same way). `comm-stale?` now says
in its docstring why only `:comm` ages out.

`bridge/core.clj`: `turn-marker` is `{:source … :started-at …}` — no
`:delivery-id`, no `:attempts`, no `:delivery`. `marker-source` lost its
`:hail` case; an autonomous origin's kind is carried unread. The
`:hail-delivery` charge key had exactly one reader (that marker) and now has
none.

`config/schema/root.clj`: `hail` and `hail-band` were unread in this repo
(isaac-hail composes its band schema from its own manifest; isaac-server
keeps its own copy) — deleted as dead readers.

### Residual `grep -rn hail src/` (deliberate, not drive code)

- `bridge/core.clj:76` `autonomous-origin?` `#{:hail :cron}` — a routing set
  of dispatcher *kinds*, read identically for both; nothing hail-shaped is
  read from the charge. Replacing it with a flag the dispatcher stamps needs
  isaac-hail to set that flag, and this bean must not repin isaac-hail.
  Natural companion to isaac-9azm.
- `tool/builtin.clj` `hail__send` — the tool's wire name plus the
  "hail module is not loaded" stub that delegates to `isaac.tool.hail`.
- `config/schema/root.clj:77` `entity-collections #{:crew :hail :models
  :providers}` — a hardcoded path-normalization set. Deriving it from the
  tables that carry a `:value-spec` would also change `:comms`/`:cron`/
  `:hooks` path handling, which no scenario covers. Left alone.
- `attention.clj:68` — a docstring reference.

### Specs recut

`spec/isaac/bridge/resume_spec.clj`: the three hail-shaped examples became
"enqueues a suspended hail marker like any other source, writing nothing
under hail/", "resumes an hour-old hail marker: a work order never goes
stale", "clears the legacy marker path after enqueueing its turn", and
"drops a cancelled hail marker the same as any other source, archiving
nothing". Two assert `hail/` is never created at all.

Verified: `bb features` on resume_repair / resume_queue / turn_markers, then
`bb ci` (1683 unit + 839 feature examples, 0 failures). isaac-hail was not
repinned.
