---
# isaac-8v1n
title: Migrate :route to a foundation-declared berth (phase 5 of berth epic)
status: todo
type: task
priority: normal
created_at: 2026-06-04T14:49:22Z
updated_at: 2026-06-04T14:49:33Z
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
