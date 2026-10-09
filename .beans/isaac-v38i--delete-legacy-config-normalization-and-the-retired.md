---
# isaac-v38i
title: Delete legacy config normalization and the retired :server block
status: completed
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T02:59:04Z
---

## Ruling

Micah, 2026-09-29: clean cutover, no back-compat (isaac/AGENTS.md
"Back-compat stance"): removed keys hard-reject, no deprecated aliases, old
scenarios deleted not retained.

## Problem

`isaac-foundation/src/isaac/config/normalize.clj` carries three legacy
old-format rewriting branches that predate the current map-of-id entity-dir
shape:

- `normalize-crew-config` (lines 62-74): `old-crew-list` — a `:crew` block
  shaped as `{:list [...]}` (crew as a vector of entities with `:id`)
  instead of today's map-of-id.
- `normalize-model-config` (lines 76-90): `old-models` — models nested
  under `(:models crew-block)` (i.e. inside the OLD `:crew {:models {...}}`
  shape) instead of today's top-level `:models` map.
- `normalize-provider-config` (lines 92-104): `old-providers` — providers
  as a vector under `(get-in cfg [:models :providers])` instead of today's
  top-level `:providers` map.

`modern-crew-map?` (line 58-60) exists specifically to distinguish "modern
map-shaped crew" from "old `:list`/`:defaults`/`:models`-nested shape" so
`normalize-crew-config` can branch between them.

Additionally `src/isaac/config/schema_base.clj`'s `base-root` declares a
`:server` block (lines 35-49) — a fully retired top-level key whose only
job is to tell someone still writing `:server {...}` to use `:http`/
`:hot-reload`/`:bridge` instead, field by field, via `:retired?`
validations.

Both are back-compat surface foundation carries for configs nobody should
still be writing, and (per the cleanup's framing) the legacy-format
branches also happen to be part of why `normalize.clj` hard-codes
`:crew`/`:models`/`:providers` by name — deleting them is a prerequisite
for cleanup-bean-4's generalization of what's left.

## Wanted

1. **Verify no live config needs the legacy branches before deleting.**
   Checked this planning session:
   - **skiff** (`ssh skiff@skiff`, reachable): `~/.isaac/config/isaac.edn` +
     `crew/*.edn` + `models/*.edn` + `providers/*.edn` — modern shape
     throughout (`:crew`/`:models`/`:providers` all top-level maps of id,
     no `:list`, no `:server` key anywhere; `grep -n ':list\|:server\b'`
     across all of them returned nothing).
   - **zanebot**: NOT reachable from this planning session (no SSH key
     authorized for this sandbox — `ssh zanebot` and `ssh zane@zanebot`
     both failed with permission denied). **The worker must check zanebot
     directly** (`ssh zane@zanebot "grep -n ':list\|:server\b'
     ~/.isaac/config/isaac.edn ~/.isaac/config/crew/*.edn
     ~/.isaac/config/models/*.edn ~/.isaac/config/providers/*.edn"` or
     equivalent) before deleting — this bean's acceptance is gated on
     that check finding nothing, same as skiff.
   - Any other deployed Isaac instance Micah knows about (personal
     laptop config, other hosts) should get the same check.
2. Delete `old-crew-list`, `old-models`, `old-providers`, and
   `modern-crew-map?` from `normalize.clj`. `normalize-crew-config` /
   `normalize-model-config` / `normalize-provider-config` become
   unconditional (always map-of-id in, conform each entry, no branch).
3. Delete the `:server` block from `schema_base.clj`'s `base-root`.
   Per the same candidate's suggestion: if the retired-key hint is still
   valuable UX (someone upgrading an old isaac-http-era config), it moves
   to isaac-http's OWN manifest schema (a module can declare
   `:retired?`-validated fields same as foundation's base schema does —
   nothing foundation-specific about that mechanism) rather than living in
   foundation's base-root. Otherwise just delete — `:server` becomes an
   ordinary unknown top-level key (warns, doesn't error), same as any
   other stray key.
4. Grep `isaac-foundation/features/` for any scenario exercising the old
   `:list`-shaped crew, `:server`, or old nested `:models :providers` —
   delete those scenarios outright (no absence tests per project
   convention — this is a removal, not a new permanent scenario).

## Acceptance

- `grep -n "old-crew-list\|old-models\|old-providers\|modern-crew-map?" isaac-foundation/src/isaac/config/normalize.clj` returns nothing.
- `grep -n ":server" isaac-foundation/src/isaac/config/schema_base.clj` returns nothing (or only in isaac-http's manifest if the hint moved there — worker's call per item 3).
- Full foundation feature suite green with the deleted scenarios gone, not
  skipped.
- zanebot's live config confirmed modern (or migrated first, if it isn't —
  which would itself need a separate one-time migration, not a reason to
  keep the code path indefinitely).

## Likely repo scope

`isaac-foundation` only: `src/isaac/config/normalize.clj`,
`src/isaac/config/schema_base.clj`, `features/` (delete any legacy-shape
scenario). `isaac-http` only if the `:server` retired-hint moves there
(item 3).

## Notes

- **Sequence after isaac-dnib lands on main.** isaac-dnib
  (`bean/isaac-dnib`, in progress as of this planning session, already
  84 changed lines in `normalize.clj` and 72 in `schema_base.clj` beyond
  main) is actively rewriting both files this bean touches — applying the
  conform-overlay treatment to `normalize-crew`/`normalize-model`/
  `normalize-defaults`, and deepening `:modules`' schema in
  `schema_base.clj`. **Do not draft this bean's diff against current
  main's normalize.clj/schema_base.clj — rebase onto isaac-dnib's landed
  sha first**, since the line numbers and even some function shapes cited
  above will have shifted.
- This bean should land before cleanup-bean-4 (entity-table derivation),
  which generalizes what's LEFT in normalize.clj after this deletion.

## Open questions

- None blocking — the only real unknown is zanebot's actual config
  shape, which the worker must check directly (see Wanted item 1).

## Planner note (2026-09-30)

Checked zanebot live config (zane@zanebot ~/.isaac/config): no `:list`, no `:server`, models/providers in their own dirs — fully modern, like skiff. Safe to delete.

## Ungated

Refactor with no new user-visible behavior, so no new scenarios: acceptance is both repos' full CI green (existing scenarios are the regression net), the grep checks named in this bean, and planner verification. Worker hands off with `tag=unverified`.

## Landed on main

Zanebot was down for this session; ran locally in its place, on a worktree
(`isaac-foundation-v38i`, branch `bean/isaac-v38i`), rebased onto latest
`origin/main` (which had picked up isaac-yo8d) right before landing.

Deleted from `isaac-foundation/src/isaac/config/normalize.clj`:
`modern-crew-map?`, and the `old-crew-list`/`old-models`/`old-providers`
branches inside `normalize-crew-config`/`normalize-model-config`/
`normalize-provider-config` — all three are now unconditional (map-of-id
in, conform each entry via the existing `normalize-crew`/`normalize-model`
overlay helpers from isaac-dnib, no legacy-shape branch). Also dropped the
now-dead `crew-block` parameter from `normalize-model-config` and the
unused `clojure.set` require.

Deleted the `:server` block from `schema_base.clj`'s `base-root` outright
(no hint moved to isaac-http) — per the bean's "otherwise just delete"
option and the project's clean-cutover stance; `:server` is now an
ordinary unknown top-level key (warns, doesn't error). isaac-http's own
`schema/root.clj` `server` var already read from manifest contributions
(none declare `:server`), so it was already effectively dead — left
untouched, out of this bean's scope.

Left `isaac.config.watch/hot-reload?`'s `[:server :hot-reload]` fallback
(a separate, functional back-compat path from isaac-1pi2, not part of
`schema_base.clj`'s declarative retirement) untouched — not named in this
bean's Wanted/Acceptance, and both `watch_spec.clj` tests for it still
pass unchanged.

Tests removed (legacy-shape-only, no absence tests added):
- `isaac-foundation/spec/isaac/config/normalize_spec.clj`: `"normalizes
  legacy crew lists nested models and provider vectors"` — the only spec
  in the repo exercising `:list`-shaped crew, crew-nested `:models`, or
  vector `:providers`.
- `isaac-foundation/spec/isaac/config/schema_base_spec.clj`: narrowed
  `"contains process-owned config and retired server settings"` to
  `"contains process-owned config"`, dropping the `:server`-keys/
  `:retired?`-message assertions.

Grepped `isaac-foundation/features/` and `isaac-agent` (specs + features):
no scenario anywhere exercises the legacy crew/models/providers shapes or
the retired `:server` block, so no `.feature` deletions were needed and
isaac-agent was not touched.

Results: `bb ci` green (270 examples, 0 failures, 741 assertions, 2
pre-existing unrelated `@wip` pendings) and `bb features-slow` green (6
scenarios, 0 failures) on isaac-foundation, both before and after the
rebase onto origin/main. GitHub Actions CI on the landed main commit:
success (all jobs, including the cross-repo isaac-http boot scenario).

main-sha: isaac-foundation 7f5b519282d1731c88a6da5b787aca5a455bcb27

Branch `bean/isaac-v38i` and its worktree deleted after landing (local +
remote).
