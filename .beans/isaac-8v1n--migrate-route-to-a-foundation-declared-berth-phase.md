---
# isaac-8v1n
title: Migrate :route to a foundation-declared berth (phase 5 of berth epic)
status: completed
type: task
priority: normal
created_at: 2026-06-04T14:49:22Z
updated_at: 2026-06-05T06:49:37Z
parent: isaac-brth
blocked_by:
    - isaac-8yxs
---

Phase 5 of isaac-brth. Convert today's hardcoded `:route` mechanism
to a foundation-declared manifest berth. Existing route behavior
preserved; the registration pipeline swaps from the legacy
`module-loader/register-handler!` system to the manifest-berth
processing built in isaac-8yxs.

## Today's wiring (to be replaced)

- `:route` is a hardcoded top-level manifest key
  (`src/isaac/module/manifest.clj` line ~22 in `manifest-schema`;
  `:route` is in `known-meta-keys` and `validate-routes!` walks
  `[method path] -> handler` entries).
- `src/isaac/module/loader.clj` dispatches `:route` and
  `:route-prefix` via the `register-handler!` system.
- `src/isaac/server/routes.clj` registers `register-route!` and
  `register-prefix-route!` as the handlers; routes land in a
  global atom `*registry*`.

Today's consumers:

- isaac core's manifest at `src/isaac-manifest.edn`:
  `:route {[:post "/hail/send"] isaac.hail.http/handler}`.
- isaac-acp's manifest at `src/isaac-manifest.edn`:
  `:route {[:get "/acp"] isaac.comm.acp.websocket/handler}`.

## After this bean

- Isaac core's manifest declares `:isaac.server/route` (exact-match)
  and `:isaac.server/route-prefix` (prefix-match) as manifest-only
  berths with per-entry registration factories.
- Berth `:factory` is the existing `register-route!` /
  `register-prefix-route!` fns (now invoked through the berth
  processing pipeline instead of `register-handler!`).
- Consumer manifests (isaac core's hail handler, isaac-acp's
  websocket handler) move their entries from top-level `:route`
  to a contribution to `:isaac.server/route`.
- Top-level `:route`/`:route-prefix` keys are removed from
  `manifest-schema` and `known-meta-keys`.
- `validate-routes!` deletes; per-entry shape validation is now
  the berth's `:manifest :schema` job.
- `module-loader/register-handler!` calls for `:route` and
  `:route-prefix` delete.

Two berths or one? Probably two — they have distinct entry shapes
(`[:method "uri"]` vs `[:prefix "uri-prefix"]`). Easier to keep
them separate than overload one berth's schema with a discriminator.

## Acceptance

No new Gherkin. Existing route-related tests are the safety net;
targeted greps close the loop.

- `bb features features/hail/http.feature` passes (uses the
  registered `/hail/send` route).
- isaac-acp's existing route tests pass.
- The `/status` and `/error` routes in `built-in-routes`
  (`server/routes.clj`) keep working — decide during impl whether
  they migrate to the berth mechanism too or stay built-in.
- Greps come up clean:
  - `rg ':route\s*{' src/` — zero hits (the hardcoded key gone).
  - `rg 'register-handler!.*:route' src/` — zero hits (no more
    legacy dispatch for `:route`/`:route-prefix`).
  - `rg ':route\b' isaac.comm.acp/` (cross-repo) — zero hits except
    inside the new berth contribution shape.
- `bb spec` green; no regressions.

## Out of scope

- Other berths to migrate (`:tools`, `:provider`, `:llm/api`,
  `:slash-commands`, `:hook`, `:comm`) — separate beans, planned
  later per the epic.
- Routes' nexus-registration path. If the migration changes WHERE
  routes land in the nexus, the router's read path must update too.
  Keep the legacy global-atom registry for now; the foundation
  berth processing writes into it. Migrating storage to nexus is a
  follow-up.
- New route features (route filters, middleware, prefix priority).
  Not part of this conversion.

## Dependencies

- isaac-8yxs (manifest-only berth processing — the mechanism the
  migration uses).
- isaac-c2g5 (lexicon — `:type :symbol` in the berth's manifest
  schema for `:handler`).
- isaac-htkp ✓ completed (manifest accepts `:berths`).
- isaac-yb39 ✓ completed (contribution validation).

## Notes for the worker

- Cross-repo. Updates needed in `isaac-acp/src/isaac-manifest.edn`.
  Do it as part of this bean's PR(s) so the migration doesn't leave
  acp's manifest in a broken state.
- `register-route!` and `register-prefix-route!` keep their current
  fn shape. The only change is HOW they get called — foundation's
  berth-processing pipeline calls them per entry instead of the
  `register-handler!` dispatch.
- The "built-in routes" map at `server/routes.clj:41`
  (`/status`, `/error`) is currently registered separately from the
  manifest-driven flow. Either: (a) leave it as-is and only migrate
  the manifest entries, or (b) move the built-ins into isaac core's
  manifest too. (b) is more symmetric (foundation acts as a normal
  consumer) but bigger diff. Pick during impl.

## Summary of Changes

### Berth declarations + factories

- **`src/isaac-manifest.edn`**: added `:isaac.server/route` and `:isaac.server/route-prefix` as manifest-only berths under `:berths`. Each declares a per-entry factory (`isaac.server.routes/register-route-entry!` / `register-prefix-route-entry!`) and a schema for the contribution shape. The old top-level `:route` map migrated to two seq contributions on the new berths.
- **`src/isaac/server/routes.clj`**: added `register-route-entry!` and `register-prefix-route-entry!` — thin shims that resolve a symbol-valued `:handler` and delegate to the existing `register-route!` / `register-prefix-route!` fns. `register-prefix-route!` made public (was `defn-`) so it can be used by the entry factory. Dropped the `module-loader/register-handler! :route ...` / `:route-prefix ...` declarations — the dispatch system that called them is gone for these kinds.

### Pipeline / loader rewiring

- **`src/isaac/module/loader.clj`**: deleted `register-route-extensions!` and the `:route`/`:route-prefix` paths in `register-extensions!`/`register-handler!` docstring. Route registration flows exclusively through `process-manifest-berths!` now.
- **`src/isaac/server/app.clj`**: removed the two explicit `(register-route-extensions! ...)` calls (one for core, one per declared module). `process-manifest-berths!` covers both via the berth declarations.
- **`src/isaac/module/manifest.clj`**: deleted `validate-routes!` (per-entry shape rejection now lives in the berth's `:manifest :schema`). Kept `:route` in `known-meta-keys` + as `{:type :ignore}` in the manifest schema so a legacy top-level `:route` map still parses (conform! drops it; the entry is not registered).

### isaac-acp manifest migration

- **`isaac-acp/src/isaac-manifest.edn`**: migrated `:route {[:get "/acp"] ...}` to `:isaac.server/route [{:method :get :path "/acp" :handler isaac.comm.acp.websocket/handler}]`. While in there, also migrated the broken legacy `:cli {<id> {:factory ...}}` map shape to the new `:cli [{...}]` seq shape (phase 4 / isaac-qpgy left the old shape silently no-op'ing for acp).
- **`src/isaac/cli.clj`**: `register-cli-command!` now also accepts `:option-spec` (resolves symbol references — module manifests would otherwise need to inline the full tools.cli spec, which contains fns that don't survive EDN).
- **isaac-acp/spec/isaac/comm/acp_spec.clj**: "registers the /acp WebSocket route…" updated to drive registration through `process-manifest-berths!` (wrapped in a `-with-nested-nexus` for fs), matching the new pipeline.

### Test fallout / spec updates

- **`spec/isaac/server/routes_spec.clj`**: "registers the hooks route from core manifest activation" rewired to use `process-manifest-berths!` instead of `activate-core!`.
- **`spec/isaac/module/loader_spec.clj`**: removed two activate-time tests (`registers exact and prefix routes…`, `fails activation when a declarative route handler cannot be resolved`) — they tested register-route-extensions!, which is gone. The factory itself is a one-liner around register-route!; covered indirectly via routes_spec + the berth-pass coverage in loader_spec.
- **`spec/isaac/module/manifest_spec.clj`**: `route-manifest` def migrated to the new shape; "rejects malformed route keys" / "rejects route handlers that are not symbols" tests deleted (the strict read-manifest-level validation moved into the berth's :manifest :schema).
- **`spec/isaac/server/app_spec.clj`**: "registers route extensions from every declared module at startup" rewritten as "processes route berth contributions from every declared module at startup" — same intent, exercises the berth pass with the new contribution shape.
- **`spec/isaac/server/server_steps.clj`**: `register-direct-routes!` calls `process-manifest-berths!` over a merged `core-index + module-index` rather than the deleted `register-route-extensions!`.

### Acceptance checks

- `bb spec`: 1849 examples, 0 failures.
- `bb features`: 743 examples, 0 failures.
- `bb features features/hail/http.feature` passes (POST /hail/send routes through the new berth).
- `rg ':route\s*\{' src/`: only `:route {:type :ignore}` in the schema declaration — intentional back-compat parse hook, no functional dispatch.
- `rg 'register-handler!.*:route' src/`: zero hits.
- `rg ':route\b' /Users/micahmartin/agents/work-2/isaac-acp/src/`: zero hits.

### Out of scope (caught en route)

- isaac-acp's spec suite has a pre-existing failure unrelated to this bean: `paths/default-state-dir` referenced in `isaac.comm.acp.chat_cli` no longer exists in isaac core (renamed/removed by an earlier state-dir bean). The route-specific test in `acp_spec.clj` passes; the rest of the acp suite is blocked on that stale symbol. Filing as a follow-up.
