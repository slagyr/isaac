---
# isaac-1pi2
title: Foundation should own config watching and reload, not isaac-http (and the current gate ignores its own default)
status: completed
type: bug
priority: high
created_at: 2026-09-20T23:33:49Z
updated_at: 2026-09-21T00:06:58Z
parent: isaac-3q4m
---

The config watcher never starts unless `:hot-reload` is set explicitly, but the
resolver says it defaults to on. So a host that never set the key — zanebot —
has been requiring a restart for every config change, silently, and the code
reads as though it hot-reloads.

Deployed code, isaac.http `493416d`:

    ;; server_config.clj — the resolver, with the default
    :hot-reload (let [hot-reload (:hot-reload config)]
                  (if (boolean? hot-reload) hot-reload true))

    ;; component/runtime.clj — the gate that actually starts the watcher
    (defn -start-config-source [config root opts]
      (or (:config-change-source opts)
          (when (and root (:hot-reload config))      ; <- raw config, no default
            (runtime/watch-service-source root))))

The gate reads the raw config. Absent key → nil → no watcher. The default that
says "true" lives in a function the gate never calls.

zanebot's `isaac.edn` has no `:hot-reload`, so its watcher has never run: crew
files, model files and edits to `isaac.edn` itself allrequire a restart. yopp sets
`:hot-reload true` explicitly, which is why it does reload.

Found 2026-09-20: a new crew file (`config/crew/qwen.edn`) was invisible to the
running server, and a hail addressed to that crew failed `:unknown-crew` while
the CLI — which loads config fresh per invocation — saw the crew fine.

**On current main it is worse.** Both the resolver and the gate now read
`(get-in config [:server :hot-reload])`, and `[:server :hot-reload]` is marked
RETIRED in `schema_base` in favour of the top-level `:hot-reload`. So on main
the only spelling that turns the watcher on is the one the schema tells you not
to use, and yopp's top-level `:hot-reload true` would be ignored after its next
http upgrade.

## Work

- The gate must consult the resolved server config, not the raw map, so the
  documented default applies.
- Read the current key (top-level `:hot-reload`), and keep honouring the
  retired `[:server :hot-reload]` only with the retirement warning the schema
  already defines.
- Log which way it resolved at boot — `:config/watch-started` with the source,
  or `:config/watch-disabled` with the reason. A watcher that silently does not
  exist is the whole problem here.
- Check what the watcher covers once it runs: this bean was found via a NEW
  file in `config/crew/`, so watching only `isaac.edn` would not have been
  enough either.

## Scenarios

A config with no `:hot-reload` key starts the watcher (the documented default)
and logs that it did. A config with `:hot-reload false` does not, and logs why.
A crew file created after boot is visible to the next turn without a restart.

## Re-scoped 2026-09-20 (Micah): foundation owns this, not http

Foundation owns config (`isaac/config/` — loader, paths, schema, validation),
owns the daemon (`isaac/runner.clj`, `isaac/component/{runtime,supervisor,
registry}.clj`, `main.clj`, `launcher.clj`), and owns `reconfigurable.clj`, the
protocol by which a component takes new config. The one piece it does not own
is noticing that a file changed.

That lives in isaac-http:

| piece | today |
| --- | --- |
| the fswatcher | `isaac-http/src/isaac/config/change_source_bb.clj` |
| watch / poll / reload | `isaac-http/src/isaac/config/runtime.clj` |
| reload + reconcile | `isaac-http/src/isaac/config/install.clj` |
| the default | `isaac-http/src/isaac/config/server_config.clj` |
| the gate that starts it | `isaac-http/src/isaac/http/component/runtime.clj` |

So foundation defines the config and what being reconfigured means, and **http**
decides whether anyone is ever reconfigured. A host that runs Isaac without the
http module gets no config watching at all — which is what isaac-3q4m already
calls out: "a UDP/SMTP/ping server module must not depend on HTTP to get a
lifecycle".

Second tell: isaac-agent carries a byte-identical copy of
`change_source_bb.clj` that nothing references. Two modules ship a config
watcher and neither of them owns config.

**Work:** move watching and reload into foundation's runner — the process
starts the watcher because it is the process, not because a particular module
loaded. http becomes a consumer of `reconfigurable` like anything else. Delete
the agent's orphan copy. Keep the change-source protocol so tests can swap in
the in-memory source.

Once the owner is right the original bug below cannot recur: there is no module
gate left to disagree with the default.

## Done (planner, 2026-09-20)

Config watching now belongs to foundation's runner, and it is on unless a host
turns it off.

**foundation** — `isaac.config.{change-source*,configurator,install,runtime}`
moved here from isaac-http; they were always named `isaac.config.*`. New
`isaac.config.watch` owns the decision and the reload loop, and
`isaac.runner` starts and stops it: the process watches its own config because
it is the process. `:hot-reload` defaults to **true**; the retired
`[:server :hot-reload]` still works and warns. Every outcome is logged —
`:config.watch/started`, or `:config.watch/disabled` with a reason, or
`:config.watch/unavailable` when the fswatcher has no config directory to
watch. The change source is injectable, for tests and embedders.

**isaac-http** — deleted its copies and the gate. The server runtime component
keeps installing config and reconciling it away on stop; it no longer decides
whether anyone is ever reconciled.

**isaac-agent** — deleted its byte-identical copy of the change source. This
was not merely dead code: both copies claimed the same namespaces, the agent's
won on the classpath, and a fix made in foundation's watcher silently did not
run. Three hot-reload scenarios in http failed for exactly this reason until
the copy was gone.

Any config file the layer recognises triggers a reload — `isaac.edn`, every
`<kind>/<id>.edn`, and the crew/berth/cron/hook markdown. The fswatcher is
recursive over the config root, so a file created after boot is seen like any
other; that was the original symptom (a new crew file invisible to the running
server).

| repo | version | sha | suites |
| --- | --- | --- | --- |
| isaac-foundation | — | `8903a585` | 1073 specs / 0 |
| isaac-agent | 0.1.75 | `1d49e49d` | 1658 / 0, 841 / 0 |
| isaac-http | 0.1.21 | `8d6ab8f1` | 189 / 0, 107 / 0 |

Registry updated. Not yet deployed to zanebot — that host has no `:hot-reload`
key, so it gains the watcher on the next upgrade and stops needing a restart
for config changes.

Follow-up worth its own bean: `isaac.config.watch` names the module registries
(`hail.bands`, `hooks`, `cron.service`) by symbol to reconcile them. Foundation
should not know those names — that belongs in a berth modules declare.
