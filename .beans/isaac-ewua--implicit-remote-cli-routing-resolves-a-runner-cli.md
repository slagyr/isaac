---
# isaac-ewua
title: Implicit remote CLI routing resolves a runner cli-proxy doesn't provide
status: draft
type: bug
priority: normal
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-09-30T06:37:18Z
---

Found overnight 2026-09-30 (handbook chapter isaac-zk49). isaac-foundation `src/isaac/main.clj` (~line 123) routes commands remotely when the home pointer file sets `:cli :remote`, by `requiring-resolve 'isaac.cli-proxy.client/run!`. isaac-cli-proxy has no `isaac.cli-proxy.client` namespace and no `run!` (only `isaac.cli-proxy.cli`, `proxy`, `ws`, `token`, `protocol`). So with `:cli :remote` set, every command fails with "remote CLI setting requires the isaac.cli-proxy module". The explicit `isaac remote <url>/cli -- …` command works. Latent: neither this machine nor zanebot sets `:cli :remote`.

## Wanted

Implicit remote-by-default routing works (or is deliberately removed). Decide the entry point: cli-proxy provides the runner foundation resolves (ideally via a berth, not a hard-coded symbol, per "foundation never names other modules").
