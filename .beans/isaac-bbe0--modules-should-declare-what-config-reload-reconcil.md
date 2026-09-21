---
# isaac-bbe0
title: Modules should declare what config reload reconciles; foundation should not name hooks, cron and hail
status: todo
type: task
priority: normal
created_at: 2026-09-21T00:51:34Z
updated_at: 2026-09-21T00:51:39Z
parent: isaac-3q4m
---

Foundation hardcodes the names of three modules in order to reload them.

`isaac.config.watch/registries` resolves this list by symbol, skipping whatever
is not installed:

    '[isaac.hail.bands/registry
      isaac.hooks/registry
      isaac.cron.service/registry]

`reload!` hands the resolved maps to `configurator/reconcile!`, which diffs each
module's config slice old-vs-new and starts, stops, or notifies the live
instance. The list came from isaac-http; isaac-1pi2 moved it to foundation
without fixing it, so foundation now knows the names of hooks, cron and hail.

**Reconciling is not the problem.** These three hold live resources, not lazily
read config: hooks own HTTP routes, cron owns scheduled jobs, hail bands own
running instances. They have to be *told* when their slice changes so they can
register and deregister. That is exactly what `on-config-change!` is for.

**The problem is the direction.** Everything else announces itself. Comms
declare config-shaped nodes through berths and reconcile through
`isaac.config.berths/reconcile!` — foundation walks what is declared, names
nobody. These three are pushed instead of pulled, and pushing needs a list of
names in the one place that should not have one.

Each module already has the whole answer in a plain map:

    (def registry {:kind :component :path [:hooks] :impl "hooks" :factory make})

Nothing declares it.

## Work

Give the registry a berth so modules declare it, the way they already declare
lifecycle through `:isaac/component` (hail does: `{:hail-runtime {:namespace
isaac.hail.component}}`). Either extend that berth with the config path and
factory, or add a sibling — whichever reads better next to
`isaac.config.berths`. Then:

- `reload!` asks the module index for declared reconcilables instead of
  resolving symbols;
- `isaac.config.watch/registries` and its symbol list are deleted;
- hooks, cron and hail declare in their manifests;
- a module that is not installed simply is not declared — the `requiring-resolve`
  and its silent `catch Throwable` go away with it.

## Scenarios

A module that declares a reconcilable registry has its slice reconciled on
reload — started when the slice appears, notified when it changes, stopped when
it vanishes — without foundation naming it. A module that declares none is left
alone. Removing a module from the index removes it from reconciliation without
any edit to foundation.

Found while landing isaac-1pi2 (config watching moves to foundation). Micah,
2026-09-20: "Foundation definitely shouldn't know those names."
