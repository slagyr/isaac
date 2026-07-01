---
# isaac-m4bi
title: Migrate discord + imessage comm contributions to :namespace/:extra-schema + real factory registration
status: completed
type: task
priority: normal
created_at: 2026-06-14T04:50:16Z
updated_at: 2026-06-16T00:35:29Z
---

## Problem

isaac-discord's server-app integration scenarios (`bb spec-jvm`,
`spec/isaac/server/discord_app_spec.clj`) fail 4/5 — Discord gateway
connect/disconnect on startup + config hot-reload. isaac-imessage has the
same stale shape (the "same class of drift as acp" the eb1t agent flagged).
eb1t fixed the `.clj` symbol references but left the comm-contribution
manifest shape + factory registration unmigrated.

## Root cause (diagnosed 2026-06-13)

isaac's comm berth (`resources/isaac-manifest.edn` `:isaac.server/comm`)
expects each contribution to carry **`:namespace`** + **`:extra-schema`**,
and the comms config-schema gathers the slot schema from
**`:dynamic-schema {:berth :isaac.server/comm :path [:extra-schema]}`**.
Factories register via a `create` defmethod (`isaac.comm.factory/create`)
or `isaac.api/register-comm!`.

Both module manifests still use the OLD shape **`:factory X/make`** +
**`:schema {…}`**, which isaac no longer reads. Two consequences:

1. `:extra-schema` is never gathered → the comm slot schema has no
   `:discord/token` → reconcile's `validate-node!` conforms the slice to
   `{}` → discord's `on-startup!` `(when-let [token (:discord/token slice)] …)`
   guard fails → `connect!` never fires.
2. There is no `create` defmethod or `register-comm!` in `discord.clj` /
   `imessage.clj` — only the *test* registers the factory. So in **real
   usage the comm never registers at all.**

Secondary isaac gap: the legacy `register-comm!` factory host built by
`isaac.comm.factory/create!` carries only `:name`, not `:root`. discord's
`make` reads `(:root host)` for its state-dir, so even with the schema
fixed it gets nil root unless the host provides it (or the comm sources
root itself in a `create` defmethod).

## Fix (per module: discord + imessage)

- Manifest: `:factory X/make` → `:namespace X`; `:schema {…}` → `:extra-schema {…}`.
- Register the factory for real — a `create` defmethod that builds the host
  (`{:name … :root (config-root/current-root) :connect-ws! …}`), OR a
  `register-comm!` bootstrap at module load — not just in the test.
- Make the server-app spec load the module so `:extra-schema` is gathered.
- If staying on the legacy `register-comm!` path: also fix isaac's
  `comm.factory/create!` to put `:root (config.root/current-root)` in the
  legacy host (small, verified-safe — isaac specs stay green).

## Acceptance

- `bb spec-jvm` green in isaac-discord (the 4 Discord-gateway scenarios).
- imessage's equivalent server-app integration green.
- Comm registers in real usage (not only under test), verified by a spec
  that doesn't pre-register the factory.

## Related

- Companion to the acp comm drift (isaac-lyg0 family).
- Surfaced verifying isaac-eb1t (which fixed the symbol refs / bb spec only).
