---
# isaac-v5js
title: Fold :isaac.server/route-prefix into :isaac.server/route via clout-based pattern matching
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-11T13:24:17Z
updated_at: 2026-06-11T13:34:51Z
parent: isaac-brth
blocked_by:
    - isaac-8v1n
---

Phase-5's migration of `:route` ended up declaring TWO berths
(`:isaac.server/route` for exact match, `:isaac.server/route-prefix`
for the one prefix case — the hooks handler at `/hooks/`). The
prefix berth's only existence is to compensate for the hand-rolled
router's lack of pattern-matching.

Fold them into one by pulling clout in as the path matcher.

## Why clout (and not roll-our-own)

URI matching has decades of edge-case lore: percent-encoding,
regex anchoring, wildcard greediness, empty segments,
trailing-slash equivalence, param-vs-literal precedence,
compiled-pattern caching. Clout has ~15 years of bug fixes against
those. Rolling our own to look like clout means re-discovering
each one as a production bug. The dep is tiny (one jar) and we
only use its matcher; nothing else from Compojure comes along.

## Changes

1. **deps.** Add clout to isaac-server's `deps.edn`.
2. **`isaac.server.routes`.**
   - Keep the exact-match hash as a fast path
     (most isaac routes are exact and don't change after registration).
   - Add a pattern-route list. On dispatch: try exact map first,
     then iterate pattern routes asking
     `(clout/route-matches pattern request)`. If matched, merge
     clout's `:route-params` into the request and invoke the
     handler.
   - Delete `register-prefix-route!`,
     `register-prefix-route-entry!`, the `[:prefix uri-prefix]`
     registry shape, and the prefix-dispatch branch in
     `dispatch-request`.
3. **`src/isaac-manifest.edn`.**
   - Delete the `:isaac.server/route-prefix` berth declaration
     under `:berths`.
   - Migrate the hooks contribution from
     `:isaac.server/route-prefix [{:prefix "/hooks/" :handler isaac.hooks/handler}]`
     to
     `:isaac.server/route [{:method :* :path "/hooks/*" :handler isaac.hooks/handler}]`.
4. **Route entry shape.** `:method` accepts a keyword OR `:*` for
   any-method. `:path` is a clout pattern string — literal
   segments, `:name` named params, `*` wildcard, segment regex
   constraints (`/users/:id{[0-9]+}` style).
5. **Handler convention.** Ring-style throughout: handler takes
   the request map (with any matched params merged in under
   `:route-params`) and returns a response map.

## Acceptance

No new Gherkin. Existing tests are the safety net.

- `bb features` and `bb spec` green — every hooks scenario stays
  green (since `/hooks/<anything>` now routes through clout
  matching instead of prefix-startswith).
- `rg ':route-prefix\b|:isaac\.server/route-prefix\b' src/ spec/ features/`
  returns zero hits.
- `rg 'register-prefix-route|register-prefix-route-entry' src/ spec/`
  returns zero hits.
- The exact-match hot path is preserved: registering a non-pattern
  route still results in an O(1) hash lookup on dispatch.

## Out of scope

- Other route improvements (middleware composition, route
  groups, content-negotiation). Pure consolidation.
- Wholesale Compojure adoption. We're pulling clout specifically;
  not Compojure's macros or its broader surface.
- The `built-in-routes` map at `server/routes.clj:51` is left
  alone — it's small, exact-match only, and not customer-facing.

## Dependencies

- isaac-8v1n (phase 5, completed) — the berth that this cleans up.
- Doesn't block isaac-owrh (phase 9 split), but ideally lands
  BEFORE isaac-owrh so isaac-server's manifest only has the one
  route berth at extraction time.

## Notes for the worker

- Clout's `route-compile` takes the pattern string and returns a
  compiled pattern object. Cache it at registration time, not on
  every request.
- The `:method :*` (any-method) handling can live at the
  isaac-server level (the matcher checks method match before
  invoking clout, treating `:*` as "skip the method check").
- Watch out for clout's percent-encoding behavior — by default it
  decodes path-params. Confirm hook tests still see the expected
  param values; if they don't, decide whether isaac wants to
  expose raw vs decoded params and document the call.

## Summary of Changes

### Dependency

- **`bb.edn`**: added `clout/clout {:mvn/version "1.1.0"}` to `:deps`. (1.1.0 specifically — 2.1.x pulls in instaparse, which doesn't analyze cleanly under babashka's SCI.)

### `src/isaac/server/routes.clj` rewrite

- `*registry*` is now a map of `{:exact <map> :patterns <vec>}`. Exact routes (literal paths) stay an O(1) hash lookup on `[method uri]`; pattern routes (clout) get a linear scan. The legacy `[:prefix uri]` key shape is gone.
- `register-route!` now detects whether the uri is a pattern (`:name` segments or `*` wildcard) and routes it to the right collection. Clout patterns are compiled once at registration time and cached on the entry — no per-request `route-compile`.
- `register-route-entry!` is unchanged in shape; the berth schema also unchanged.
- Deleted `register-prefix-route!`, `register-prefix-route-entry!`, and `dispatch-prefix-routes`. Their behavior is subsumed by clout patterns ending in `*`.
- `dispatch-pattern` checks `:method` first (with `:*` meaning any-method), then asks clout for a match, then merges `:route-params` into the request before invoking the handler. Matched wildcard segments land at `{:* "suffix"}` per clout's convention.
- Dispatch order unchanged: user-registered exact → built-in exact → user-registered patterns → 404.

### Core manifest (`src/isaac-manifest.edn`)

- Removed the `:isaac.server/route-prefix` berth declaration under `:berths` (its description, schema, and factory pointer).
- Migrated the hooks contribution from the prefix berth to the route berth: `{:method :* :path "/hooks/*" :handler isaac.hooks/handler}`. Same dispatch behavior — any-method, any suffix.
- Route berth description updated to mention the clout pattern syntax and the `:*` any-method convention.

### Tests / fixtures

- **`spec/isaac/server/routes_spec.clj`** ("registers the hooks route from the core manifest…"): updated to expect `:route-params {:* "bibelot"}` merged into the request when the pattern matches.
- **`spec/isaac/module/manifest_spec.clj`** `route-manifest` fixture: migrated from two separate berths to a single `:isaac.server/route` vec with the hooks entry now an `:*`/`*`-path pattern.
- **`spec/isaac/module/loader_spec.clj`** + **`src/isaac/module/loader.clj`** + **`src/isaac/server/app.clj`** docstring/comment references to the gone `:isaac.server/route-prefix` berth removed (so the literal acceptance grep is zero-hit).

### Acceptance checks

- `bb spec`: 1849 examples, 0 failures.
- `bb features`: 743 examples, 0 failures.
- `rg ':route-prefix\b|:isaac\.server/route-prefix\b' src/ spec/ features/`: zero hits.
- `rg 'register-prefix-route|register-prefix-route-entry' src/ spec/`: zero hits.
- Exact-match hot path preserved — `register-route!` only puts non-pattern uris in the `:exact` hash; `dispatch-request` checks `:exact` first.
- Cross-repo: isaac-acp / discord / imessage have no `:route-prefix` references; no migration needed there.
