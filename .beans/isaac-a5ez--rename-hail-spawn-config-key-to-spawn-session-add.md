---
# isaac-a5ez
title: Rename hail :spawn config key to :spawn-session (+ add to schema)
status: in-progress
type: task
priority: normal
created_at: 2026-06-23T17:06:34Z
updated_at: 2026-06-23T17:08:57Z
---

The hail `:spawn` config key is opaque — it actually controls whether a hail may START A NEW SESSION when no matching session is available (`:reach :one` + spawn -> if no session is handling the band, create one with the host crew; see delivery_worker/spawn-target, spawn-session!). Rename to `:spawn-session` for clarity. Clean cutover, NO deprecated alias (Micah, 2026-06-23).

Also a schema gap: `:spawn` is read but NOT declared in the band schema — fix that while renaming.

## Rename (config key only)
`isaac.hail.router/effective-spawn` reads the key at three levels (router.clj:115-117):
- `(:spawn hail)`                    -> `(:spawn-session hail)`
- `(get-in hail [:frequency :spawn])` -> `[:frequency :spawn-session]`
- `(:spawn band)`                    -> `(:spawn-session band)`

DO NOT touch the internal action keyword `:action :spawn` in delivery_worker.clj (lines 173, 180) — that's the dispatch verb for the spawn action (`:bind`/`:wait`/`:spawn`), not the config key.

## Schema (currently missing)
Add `:spawn-session` to the hail band schema in `isaac-hail/resources/isaac-manifest.edn` (alongside `:reach`), e.g.:
```clojure
:spawn-session {:type :boolean
                :description "When :reach is :one and no matching session is available, start a new session (with a host crew) to handle the hail."}
```
If the frequency level has its own schema, declare it there too.

## Tests / features to update (swap :spawn -> :spawn-session)
- `isaac-hail/features/spawn.feature` (the spawn scenarios; consider renaming file to spawn-session.feature)
- `isaac-hail/features/delivery.feature`
- `isaac-hail/spec/isaac/hail/router_spec.clj` (effective-spawn cases)
- `isaac-hail/spec/isaac/hail/delivery_worker_spec.clj`

## Acceptance
- Band/frequency/hail config uses `:spawn-session`; `:spawn` is no longer read anywhere (clean cutover).
- Band schema declares `:spawn-session` with a description; config validation recognizes it.
- features + specs updated and green.
- Release notes mention the breaking config-key rename.

## Notes
No CLI/http/tool currently SETS the key (grepped clean) — it's set in band config (and test fixtures) — so the surface is the router reads + schema + tests. Surfaced 2026-06-23 (Micah).
