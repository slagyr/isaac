---
# isaac-ewua
title: Implicit remote CLI routing resolves a runner cli-proxy doesn't provide
status: in-progress
type: bug
priority: normal
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-10-01T04:56:59Z
blocked_by:
    - isaac-on0o
---

Found overnight 2026-09-30 (handbook chapter isaac-zk49). isaac-foundation `src/isaac/main.clj` (~line 123) routes commands remotely when the home pointer file sets `:cli :remote`, by `requiring-resolve 'isaac.cli-proxy.client/run!`. isaac-cli-proxy has no `isaac.cli-proxy.client` namespace and no `run!` (only `isaac.cli-proxy.cli`, `proxy`, `ws`, `token`, `protocol`). So with `:cli :remote` set, every command fails with "remote CLI setting requires the isaac.cli-proxy module". The explicit `isaac remote <url>/cli -- …` command works. Latent: neither this machine nor zanebot sets `:cli :remote`.

## Wanted

Implicit remote-by-default routing works (or is deliberately removed). Decide the entry point: cli-proxy provides the runner foundation resolves (ideally via a berth, not a hard-coded symbol, per "foundation never names other modules").

## Decision (Micah, 2026-09-30)

Delete it. Foundation must not own a cli-proxy setting. Remove `:cli :remote` handling and the `isaac.cli-proxy.client/run!` resolve from foundation main (and any schema/docs/handbook mention). `isaac remote <url>/cli -- …` stays. If default-remote is ever wanted again, cli-proxy introduces it. Removal checks are bean acceptance only, not permanent scenarios.
