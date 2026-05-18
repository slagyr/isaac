---
# isaac-hliq
title: Unify server :host defaults across config and startup
status: in-progress
type: bug
priority: normal
created_at: 2026-05-18T20:35:01Z
updated_at: 2026-05-18T20:35:44Z
---

## Problem

Two layers default the server's `:host` to different values:

- `src/isaac/server/app.clj` `startup-settings`: defaults to `"0.0.0.0"` (the actual bind)
- `src/isaac/config/loader.clj` `server-config`: defaults to `"127.0.0.1"` (what other layers read)

That disagreement was the cause of the auth-bypass fixed in commit
`5bf29812` (follow-up to isaac-g69y). `wrap-auth` was reading
`server-config`'s `"127.0.0.1"`, deciding the server was on loopback,
and skipping auth — even though the actual bind was `"0.0.0.0"`.

The fix passes the resolved bind host through handler-opts as
`:bind-host` so `wrap-auth` reads truth. That patched the symptom but
left the underlying inconsistency: any *other* code that reads
`server-config`'s `:host` will still see `"127.0.0.1"` when the actual
bind is `"0.0.0.0"`. It's a latent bug waiting for the next consumer.

## Design question

What should the default be?

- **`"127.0.0.1"`** (dev-safe): new installs bind loopback only.
  Operators must opt into `0.0.0.0` AND configure `:server :auth :token`
  (the latter is already required for non-loopback). Safer-by-default.
- **`"0.0.0.0"`** (current bind behavior): matches what the server
  actually does today. Less safe but no behavior change for users.

Lean toward `"127.0.0.1"` — combined with the existing
"non-loopback without auth refuses to start" gate from isaac-g69y, this
gives a coherent story: dev defaults are local, prod requires explicit
opt-in for both host and auth.

## Implementation surfaces

- `src/isaac/server/app.clj` — `startup-settings` `:host` default.
- `src/isaac/config/loader.clj` — `server-config` `:host` default.
  These two values must agree.
- 15 feature scenarios broke when the change was naively attempted (see
  the failed-fix attempt earlier in 5041921e's session). Most of those
  scenarios assert host/port in log output or fixture state. Audit and
  update.
- `src/isaac/server/http.clj` — `wrap-auth` currently has both a
  `:bind-host` path and a config fallback. Once the unified default
  exists, decide whether to drop the fallback or keep it for safety.

## Definition of done

- One default for `:host` across `app.clj` and `config/loader.clj`.
- All existing feature and spec scenarios pass against the unified
  default.
- The auth-bypass that motivated `5bf29812` is impossible to
  reintroduce — i.e., setting `:server :auth :token` without
  `:server :host` still enforces auth on a wide-open bind.

## Related

- isaac-g69y — added `:server :auth :token` and the loopback-skip
  semantics that exposed the default mismatch.
