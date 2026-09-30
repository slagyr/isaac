---
# isaac-0lb7
title: 'Episodes config: recall moves under :episodes; crews may override :episodes'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-29T23:54:31Z
updated_at: 2026-09-29T23:58:37Z
---

Likely repo: **isaac-episodes**. Design: Micah + planner, 2026-09-29.

## Why

Episodes settings are global only. The Mixmaster trial (isaac-cwgs) wants a
shorter recall half-life for one worker crew, and there is no way to set
it. Recall also sits in its own top-level `:recall` key although it only
ranks and injects episode scenes.

## Design (agreed)

One map. Recall moves inside episodes, and a crew may carry the same map
to override the global one:

```clojure
;; isaac.edn
{:episodes {:gist-model :grunt
            :ttl-minutes 60
            :embedding {…}
            :seal      {:idle-minutes 3 …}
            :recall    {:half-life 30 :weights {…} :inject {…} :ledger true}}}

;; crew/mixmaster.edn
{:session-policy :episodes
 :episodes {:recall {:half-life 7}}}
```

- Schema: move the `:recall` fragment under `:episodes` in the manifest's
  `:isaac.config/schema`. Contribute an optional `:episodes` field to the
  crew table (`:crew {:schema {:value-spec {:schema {:episodes …}}}}`);
  foundation's schema compose deep-merges it into Agent's crew schema.
  Bare key, not namespaced: it mirrors the root key, and compose already
  fails on a colliding definition.
- Resolution: one helper deep-merges the crew's `:episodes` over the
  root `:episodes`. Every reader that knows the crew (policy, worker tick,
  recall inject/query/ledger, tools, `recall --crew` CLI, seal/lifecycle)
  reads through it; the `get-in` paths become `[:episodes :recall …]`.
  `embed` has no crew and stays global.
- Clean cutover: top-level `:recall` is gone. zanebot has none today;
  check yopp before deploying.
- Deleted the landed scenario "leftover :recall :floor-cos does not raise
  the floor" (query.feature): it guarded a key that no longer exists.

## Acceptance

- isaac-episodes `features/recall/query.feature` — "weights resolve defaults, then :episodes :recall config, then CLI flags"
- isaac-episodes `features/recall/query.feature` — "a crew's :episodes :recall overrides the global settings for that crew only"
- isaac-episodes `features/recall/ledger.feature` — "with recall.ledger on, a turn-start recall is appended to the crew's ledger (isaac-ozh5)"
- isaac-episodes `features/recall/ledger.feature` — "with recall.ledger on, a recall__search call is appended with kind :search (isaac-ozh5)"
- isaac-episodes `features/episodes/idle_seal.feature` — "a crew's :episodes :seal :idle-minutes overrides the global idle time"
- isaac-episodes `features/recall/embedding.feature` — "config validation checks a crew's :episodes overrides"
- One-time check (not a scenario): `isaac config validate` on a config with a top-level `:recall` block reports it.

feature-baseline: isaac-episodes 53ae26ffccc6f6d46ca9f138ef30f8d3f1ba90f7
feature-blob: isaac-episodes features/recall/embedding.feature 4a9c8dde39fc65b93b9fe4dcd4678e8296ebc565 98
feature-blob: isaac-episodes features/recall/ledger.feature 715fd67a4f5f9b1df9be4d3d935266f45fb7d9b7 27
feature-blob: isaac-episodes features/recall/ledger.feature 715fd67a4f5f9b1df9be4d3d935266f45fb7d9b7 38
feature-blob: isaac-episodes features/recall/query.feature ac1e6e103e705cb12a3aa13e0e6f18c0025ea20f 86
feature-blob: isaac-episodes features/recall/query.feature ac1e6e103e705cb12a3aa13e0e6f18c0025ea20f 147
feature-blob: isaac-episodes features/episodes/idle_seal.feature e5edb587444fc400723d65e08d9e8315b199684c 51

## Implementation conflict (2026-09-29)

Implementation branch `bean/isaac-0lb7` in isaac-episodes at eac4b30. `bb spec`: 232 examples, 0 failures, 627 assertions. `bb features`: 95 examples, 1 failure, 579 assertions. All other acceptance scenarios pass. The sole failure is `features/recall/embedding.feature:98` (crew override config validation). The frozen scenario asserts stderr contains `bad value: soon`; config validate actually emits `error: crew.cordelia.episodes.recall.half-life - can't coerce "soon" to int` (exit 1). This is foundation's generic integer coercion error (not a schema validation error) and cannot honestly match the baselined contract without either changing the scenario or changing foundation's validation reporting. Planner decision needed: rebaseline expected stderr to the real error or authorize a foundation change. No edits to feature text beyond removing @wip.
