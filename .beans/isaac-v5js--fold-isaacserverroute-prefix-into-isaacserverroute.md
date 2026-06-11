---
# isaac-v5js
title: Fold :isaac.server/route-prefix into :isaac.server/route via clout-based pattern matching
status: in-progress
type: task
priority: normal
created_at: 2026-06-11T13:24:17Z
updated_at: 2026-06-11T13:26:12Z
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
