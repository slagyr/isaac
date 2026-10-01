---
# isaac-ewua
title: Implicit remote CLI routing resolves a runner cli-proxy doesn't provide
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-10-01T05:08:09Z
blocked_by:
    - isaac-on0o
---

Found overnight 2026-09-30 (handbook chapter isaac-zk49). isaac-foundation `src/isaac/main.clj` (~line 123) routes commands remotely when the home pointer file sets `:cli :remote`, by `requiring-resolve 'isaac.cli-proxy.client/run!`. isaac-cli-proxy has no `isaac.cli-proxy.client` namespace and no `run!` (only `isaac.cli-proxy.cli`, `proxy`, `ws`, `token`, `protocol`). So with `:cli :remote` set, every command fails with "remote CLI setting requires the isaac.cli-proxy module". The explicit `isaac remote <url>/cli -- …` command works. Latent: neither this machine nor zanebot sets `:cli :remote`.

## Wanted

Implicit remote-by-default routing works (or is deliberately removed). Decide the entry point: cli-proxy provides the runner foundation resolves (ideally via a berth, not a hard-coded symbol, per "foundation never names other modules").

## Decision (Micah, 2026-09-30)

Delete it. Foundation must not own a cli-proxy setting. Remove `:cli :remote` handling and the `isaac.cli-proxy.client/run!` resolve from foundation main (and any schema/docs/handbook mention). `isaac remote <url>/cli -- …` stays. If default-remote is ever wanted again, cli-proxy introduces it. Removal checks are bean acceptance only, not permanent scenarios.

## Removed (worker, 2026-10-01)

- `isaac.foundation.main/run` (`src/isaac/foundation/main.clj`): the `remote`/`route-remote?` branch, the `requiring-resolve 'isaac.cli-proxy.client/run!`, `*remote-runner*` dynamic var, and the `env-local?`/`local-only?` bypass logic. The explicit `isaac remote <url>/cli -- …` command is untouched — it was never part of this seam; it dispatches through the normal command registry (`registry/get-command`), unaffected by the deletion.
- `--local` flag and `ISAAC_CLI_LOCAL` env bypass: both existed only to escape implicit routing, so `--local` parsing in `isaac.foundation.cli.args/extract-root-flag` is gone too (no more `:local?` in its return map).
- `features/cli/remote_routing.feature` and its step defs (`spec/isaac/foundation/remote_routing_steps.clj`) — deleted outright, not replaced with absence scenarios.
- `resources/isaac/foundation/handbook.md` — the whole "Appendix: the CLI and remote routing" section (was the last section in the file).
- No schema existed for `:cli :remote` (it was read raw off the home pointer file, never validated), so nothing to remove there. `features/cli/root_pointer.feature` doesn't mention remote routing at all — left untouched.

**isaac-cli-proxy flag (not edited this bean):** `resources/isaac/cli_proxy/handbook.md` still describes implicit remote-by-default routing at length — "The remote target" section, its `[verify]` paragraph about `isaac.cli-proxy.client/run!`, the Troubleshooting entries referencing "the implicit-routing seam", and references to foundation's now-deleted Appendix throughout ("Running a command on a remote server" section, `--local`/`ISAAC_CLI_LOCAL=1` paragraph). That chapter needs its own bean to strip those implicit-routing mentions; isaac-cli-proxy's README has no such mentions.

## Landed on main

main-sha: isaac-foundation b133e0621631af57a368b1042d8f3610d4e1a91e

CI (verify / Server boot with a module-provided config type / Slow features) all green: https://github.com/slagyr/isaac-foundation/actions/runs/36818198994

Grep confirms `cli-proxy`, `:cli :remote`, `:remote-runner`, `ISAAC_CLI_LOCAL` are gone from isaac-foundation src/resources/features/spec.
