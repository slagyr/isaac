---
# isaac-crrm
title: Server-only commands run only through isaac remote; the local CLI lists them and refuses with the hint; remove the forwarder
status: todo
type: feature
priority: normal
created_at: 2026-11-17T00:10:51Z
updated_at: 2026-11-17T00:10:51Z
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
