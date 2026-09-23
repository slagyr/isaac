---
# isaac-8zl8
title: 'isaac google smoke: the default door URL reads http.port, which most hosts leave unset — probe the server''s effective port instead'
status: in-progress
type: bug
priority: normal
tags:
    - google
created_at: 2026-09-23T02:15:07Z
updated_at: 2026-09-23T15:51:29Z
---

## Observed (yopp, 2026-09-23 02:15Z, isaac-google 0.1.10)

`isaac google smoke` reported `FAIL door — door unreachable: java.net.ConnectException` while `curl -X POST http://127.0.0.1:6674/google/pubsub` answered 401 and the Funnel URL answered 401 too. yopp's isaac.edn sets no `:http :port`; `cli/default-door-url` builds the probe URL from that key, so the port was missing or wrong. `--url http://127.0.0.1:6674/google/pubsub` passes.

## Change

Default the probe to the port the server actually listens on: isaac-http's resolved config (its own default when the key is absent), not the raw key. Spec: config with no :http :port → probe URL carries isaac-http's default port; config with a port → that port; --url still wins.

## Acceptance

bb spec / bb ci green in isaac-google; one-time on a host with no :http :port set, `isaac google smoke` passes the door check without --url.

## Handoff (worker, 2026-09-23)

Branch: `bean/isaac-pl8x` in isaac-google (worktree
`isaac-google-isaac-pl8x`), pushed to `origin/bean/isaac-pl8x`, commit
48625f2 — same branch/commit as isaac-pl8x (both beans were worked together
per the dispatching instructions). Manifest bumped to 0.1.13.

`src/isaac/google/cli.clj`:
- `http-server-config-fn` — `(requiring-resolve
  'isaac.config.server-config/server-config)`, the same seam
  `isaac.google.door/registrar` already uses to reach isaac-http without a
  compile-time dependency (isaac-http is only a `:spec`/`:features`/
  `:dev-local` dependency in `deps.edn`, not a main one).
  `isaac.config.server-config/server-config` (in isaac-http,
  `src/isaac/config/server_config.clj`) is isaac-http's own resolved bind
  config — it defaults `:port` to 6674 exactly as the running server does
  when a host sets no `:http :port`. isaac-http exposes this as a function,
  not just a bare constant, so that's what gets called, per the bean's
  "find how isaac-http exposes its default port" instruction.
- `resolved-http-port` calls it when resolvable; falls back to
  `FALLBACK-HTTP-PORT` (6674, a local copy of isaac-http's own default,
  documented as such) only for a host where isaac-http itself is not
  loaded.
- `default-door-url` is now a thin wrapper: `"http://127.0.0.1:" +
  (resolved-http-port config) + smoke/DOOR-PATH`.
- Bug confirmed: the old fallback was `8080`, not isaac-http's real default
  of `6674` — a second latent defect beyond just reading the wrong config
  key.
- `--url` still wins — unchanged, `run-smoke` still does `(or (:url opts)
  (default-door-url config))`.
- Also deduped the probe ce-type string: `publish-test-message!` now uses
  `smoke/PROBE-TYPE` instead of a second literal `"isaac.google.smoke/probe"`.

Scenarios: new `spec/isaac/google/cli_spec.clj` (isaac-google had no
`cli_spec.clj` before this — `default-door-url` was previously untested).
Tests the private fn via `#'isaac.google.cli/default-door-url`:
- "carries isaac-http's own resolved default port when the host sets no
  :http :port" → `{}` → `http://127.0.0.1:6674/google/pubsub`
- "carries the configured port when the host sets one" →
  `{:http {:port 9999}}` → `http://127.0.0.1:9999/google/pubsub`

Needed `(around [example] (nexus/-with-nexus {:fs (fs/real-fs)}
(example)))` — isaac-http's `server-config` composes its schema from the
classpath's module manifests (`isaac.config.schema-compose/
cached-root-schema` → `discovery/builtin-index`), which needs a real
filesystem to discover; the project's usual mem-fs `around` blocks aren't
enough and the spec throws `isaac.fs/instance: no filesystem available` in
isolation without it (it happened to pass inside the full suite only
because an earlier spec had already warmed the schema cache — fixed so it's
correct standalone too).

Test commands and counts: same run as isaac-pl8x (worked in the same
commit) — `bb spec` 260/0, `bb features` 36/0, `bb ci` green,
`bb lint` clean on touched `src/` files.

Not done (needs a live host, per bean acceptance): one-time check on a host
with no `:http :port` set that `isaac google smoke` passes the door check
without `--url`. No real Google calls or live-host access from the
worktree.

Left `in-progress`, no tags, per the dispatching instructions.
