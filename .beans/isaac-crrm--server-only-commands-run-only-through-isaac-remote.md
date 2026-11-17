---
# isaac-crrm
title: Server-only commands run only through isaac remote; the local CLI lists them and refuses with the hint; remove the forwarder
status: completed
type: feature
priority: normal
created_at: 2026-11-17T00:10:51Z
updated_at: 2026-11-17T00:36:14Z
---

Server-only commands stop being forwarded. They run only inside the server, through `isaac remote`, and the local CLI points the way. Micah, 2026-11-16: the forwarder "seems complicated… I'm not sure we really want that"; an alias that picks a server is too much; "there should be some local CLI indication that the module is installed and that the command is available remote only."

Today (isaac-dcxk, isaac-w0yx): foundation's `:isaac/cli-forwarder` berth lets cli-proxy catch a `:server-only` command typed in a local CLI and send it to the host's server (found in `state/cli-server.edn`), or to `--remote <url>`. With no server it runs locally. In the server process the forwarder is registered twice and logs `module/activation-failed … Only one :isaac/cli-forwarder may be registered` on every boot.

New behavior:
- Local CLI: a `:server-only` command keeps its name and summary from the manifest but never runs. `isaac --help` lists such commands under "Server-only (run on a server: isaac remote <name> -- <command>):". Typing one prints `<cmd> runs on the server: isaac remote <name> -- <cmd>` to stderr and exits 2.
- Inside the server (embedded dispatch, the `isaac remote` path) a server-only command is a plain command, as today.
- `isaac remote <loopback url> -- …` to this root's own server uses the token in `state/cli-server.edn` when no token is configured. Today only the forwarder does that; move it into `remote`.

Remove (one-time acceptance, no absence scenarios): the `:isaac/cli-forwarder` berth and `register-forwarder!`/`forwarder` in `isaac.foundation.cli.registry` and `main.clj`; the `marigold.cli.forwarder` fixture module; cli-proxy's `forwarder.clj`, its berth entry, and the `--remote` global option (`:--remote` in cli-proxy's manifest); the handbook/README text about forwarding. After deploy, zanebot's boot log has no `cli-forwarder` line.

Repos: isaac-foundation, isaac-cli-proxy (pin foundation to the fix). Release versions at deploy, not in this bean.

feature-baseline: isaac-foundation d7914d8fb7dfe4906046d5be2fc77340fdaed238
feature-baseline: isaac-cli-proxy fd7a64691226314a35f8911164ad39d2fee60c95
feature-blob: isaac-foundation features/cli/server_only.feature 1572ea904594cbdfa06f05e440ebd2212b221486 22,30
feature-blob: isaac-cli-proxy features/server_only.feature 7ceb74f12f17e01744f610c43770668beaa5e64d 34,48

## Work checkpoint (2026-11-16)

Done: foundation bean branch 5073f47 implements local refusal/help grouping and removes forwarder berth/fixture; focused feature (3 examples) and full specs (1357 examples) green. Proxy bean branch cffd221 removes forwarder/global --remote and resolves loopback token; full specs (33), features (35), slow features (11) green against the foundation worktree. Gate passed with explicit --dir for both worktrees. Both branches pushed.

Next: finish foundation `bb ci` with `ISAAC_TEST_TIMEOUT_MS=300000` (default 60s times out after ~120 feature examples; focused feature is green). Resume at `isaac-foundation-isaac-crrm/features/cli/server_only.feature:22`. Then rebase/green check both branches, land foundation on main, repin proxy deps.edn and bb.edn to landed foundation main SHA, retest proxy, land proxy, rerun gate and complete. The proxy's bb.edn :dev-local currently points at the shared foundation checkout; use a temporary override to the worktree for tests, and restore before committing.

Last test: `bb features` (foundation) timed out at 60s, exit 124, not an assertion failure. `bb features features/cli/server_only.feature` and `bb spec` are green.

## Landed on main (2026-11-16)

main-sha: isaac-foundation d641b61a83ca3fa78ad81de9239d4898c54ca3c5
main-sha: isaac-cli-proxy 3c14a26daa20f99b42662fec2efdd7fc3241eaa0

Verification: foundation `ISAAC_TEST_TIMEOUT_MS=300000 bb ci` green (1357 specs; 376 features, two pre-existing pending); proxy `bb ci` green (33 specs, 35 features, 11 slow features); bean gate PASS on both squash commits before push. The default 60s foundation feature timeout required `ISAAC_TEST_TIMEOUT_MS=300000`. A scheduler backoff spec failed once in an earlier full run, passed on rerun. All published foundation pins in proxy point to the landed main SHA.
