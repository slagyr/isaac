---
# isaac-1pi2
title: 'Hot-reload is off wherever it was never set: the watcher gate ignores its own default'
status: todo
type: bug
priority: high
created_at: 2026-09-20T23:33:49Z
updated_at: 2026-09-20T23:33:49Z
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
