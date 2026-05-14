---
# isaac-k1m4
title: Declarative :route extension kind in the manifest
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-14T19:27:10Z
updated_at: 2026-05-14T20:37:37Z
blocked_by:
    - isaac-zl32
---

Once manifest v2 (`[[isaac-zl32]]`) lands, add `:route` as a top-level manifest kind so routes are **data**, not imperative `register-routes!` calls. Each module that has routes declares them in its manifest; the manifest loader does the registration.

## Shape (post-v2)

```edn
{:id        :isaac.core
 :bootstrap isaac.hooks/start-reconciler!   ; whatever each module actually needs at activation

 :route {[:get  "/acp"]      isaac.comm.acp.websocket/handler
         [:post "/hooks/*"]  isaac.hooks/handler}

 :comm  {...}
 :tool  {...}
 ...}
```

Singular `:route` matches zl32's convention (`:provider`, `:comm`, `:tool`, `:slash-command`). If the in-flight pluralization bean changes that across the board, this kind follows.

## Path syntax

- `/foo` — exact match. Loader calls `register-route!`.
- `/foo/*` — prefix match. Loader calls `register-prefix-route!`. Glob suffix is the standard web idiom (Sinatra/Compojure/Ring/Express); chosen over the trailing-slash-means-prefix alternative because the path syntax explicitly carries the intent and leaves room to grow into segment captures (`/foo/:id`, `/foo/*/replay`) later.

## Scope

- Add `:route` to v2's per-kind manifest schema. Each entry's key is `[method-keyword path-string]`; value is a fully-qualified symbol resolving to a handler.
- Manifest loader iterates `:route` entries; dispatches to `register-route!` or `register-prefix-route!` based on whether the path ends in `/*`.
- Existing imperative `register-routes!` functions in `isaac.hooks` and `isaac.comm.acp` get deleted. Their routes move into their respective manifest entries.
- `register-prefix-route!` becomes private to `isaac.server.routes` (the manifest loader is the only caller).

## Acceptance

- [ ] `:route` listed in v2's per-kind manifest schema with the expected entry shape.
- [ ] Manifest loader registers routes declared under `:route`. Path suffix `/*` → prefix route; otherwise → exact route.
- [ ] `isaac.hooks/register-routes!` deleted. Its route moves into the hooks module's manifest entry.
- [ ] `isaac.comm.acp/register-routes!` deleted. Its route moves into ACP's manifest entry.
- [ ] `register-prefix-route!` is private (or removed if `register-route!` can subsume it via path-shape).
- [ ] All existing route-driven scenarios pass unchanged.

## No new feature scenarios

Same as `[[isaac-ft8g]]` — refactor, contract preserved.

## Unit tests

- Manifest loader: declarative `:route` entries register with the correct method, path, and prefix-ness. Cover both exact and `/*` forms.
- Schema rejects: missing handler symbol, unresolvable handler symbol, malformed `[method path]` key.

## Open question to settle at implementation time

Whether `:bootstrap` itself shrinks toward zero once routes are declarative — i.e., should Reconfigurable lifecycle hooks also become a manifest extension kind? Out of scope for this bean; flag and revisit when more modules need it.

## Related

- `[[isaac-zl32]]` — **blocker.** v2 manifest shape is the substrate for the `:route` kind.
- `[[isaac-ft8g]]` — sibling. Decouples and renames hooks; lands first. By the time this bean activates, the imperative `register-routes!` functions to delete already live in `isaac.hooks` (not `isaac.server.hooks`).
- `[[isaac-iw6o]]` — predecessor; introduced the current imperative-bootstrap shape.
