---
# isaac-03wy
title: config set server.host does not rebind HTTP listener (requires full restart)
status: draft
type: bug
priority: normal
tags:
    - server
    - config
    - hot-reload
    - remote-cli
created_at: 2026-07-02T23:16:32Z
updated_at: 2026-07-02T23:16:32Z
---

## Problem
`isaac config set server.host "0.0.0.0"` (and similarly for :port) on a running server triggers a hot reload and updates the config snapshot, but the HTTP listener (httpkit) is **not** rebound.

Observed:
- Logs contain `:config.reload/begin`, `:config/set-snapshot`, `:config/reloaded`
- No new `:server/started` (or equivalent) with the updated host
- `ss -ltnp | grep 6674` (or lsof) continues to show the old bind address (typically 127.0.0.1)
- Remote clients (e.g. `isaac remote ws://...`) cannot reach the new address until a full `isaac service restart`

This was encountered while making the `/cli` endpoint reachable over Tailscale from another machine after adding `isaac.cli-server`.

## Expected behavior
Either:
- The listener is stopped and rebound to the new host/port during the reload (with appropriate logging of the change and a fresh `:server/started` or `:server/rebound` event), or
- A clear, actionable message is emitted: "Server bind address (host/port) changed. These settings only take effect on server restart. Run `isaac service restart` to apply."

The reload should not silently leave the process listening on the old address.

## Actual behavior
Config layer succeeds silently. The bind address in `app/state` and the open socket remain from the original `app/start!` call. Only a process-level restart (service restart or `isaac server` re-invocation) picks up the new `:server :host`/`:port` from `server-config/server-config`.

## Scope & constraints
- `:server :host` and `:server :port` are the affected keys (read at boot time in `app/start!` → `start-http-server`).
- Other `:server` keys (auth token, hot-reload) can be live via the per-request cfg-fn.
- Must not break hot-reload for modules, comms, or other reconfigurables.
- Live rebind must be safe (close old server, start new, update state atom, preserve other runtime state like scheduler/delivery).
- CLI `isaac server -H ...` and explicit overrides should continue to work.
- When hot-reload is disabled, behavior is unchanged.

## Root cause (from investigation)
- `app/start!` (isaac-server/src/isaac/server/app.clj) is the only place that calls `httpkit/run-server` with the resolved host/port from `server-config/server-config`.
- `config/install.clj:reload!` only does `set-snapshot!`, module reconcile, `install!`, and `install-config-berths!`. It never inspects `[:server :host]` / `[:server :port]` diffs or touches the http listener held in the `state` atom.
- `start-config-reloader!` drives `runtime/reload!` for config watcher changes (from `config set`).
- No reconfigurable entry or special case exists for the top-level HTTP server socket.
- The listener stop only happens in `stop!` (full shutdown path).

See:
- isaac-server/src/isaac/server/app.clj:105 (start-http-server), 229 (call site in start!), 272 (stop only in shutdown)
- isaac-server/src/isaac/config/install.clj:129 (reload!)
- isaac-server/src/isaac/config/server_config.clj:9 (just a reader)
- isaac-server/src/isaac/server/cli.clj:78 (the :server/started log only on initial start!)

## Acceptance criteria (runnable)
- [ ] Start a server (default 127.0.0.1:6674 or configured). Capture the listening address.
- [ ] `isaac config set server.host "0.0.0.0"` (with hot-reload true).
- [ ] The command and reload succeed.
- [ ] Within a few seconds, either:
  - A new listener appears on 0.0.0.0:6674 (old one closed), and `:server/started` (or new event) is logged with the new host, **or**
  - A warning is logged containing "bind", "restart", "host", or "port" recommending `isaac service restart`.
- [ ] From another host on the same Tailnet, a TCP connection to the new address:port succeeds (and /status or /cli responds appropriately, subject to auth).
- [ ] The running server's `app/current-config` reflects the new value, and subsequent config-driven behavior (e.g. non-loopback auth check) uses it.
- [ ] No regression: other config reloads (modules, bands, etc.) and `isaac server` CLI still work; full service restart still works.
- [ ] A test (spec or feature under features/server/) exercises the bind-change path (live rebind or explicit warning).
- [ ] Updating the bean with the chosen approach (live rebind vs. explicit restart requirement + warning).

## Notes / open questions for implementation
- Live rebind: need to stop the old http server (the fn or httpkit/server-stop!), recreate handler-opts if needed, call start-http-server again, update state atom, possibly restart the config reloader if host context changes.
- Security: going from loopback to 0.0.0.0 should still enforce the auth token gate (already present in auth-required? and wrap-auth).
- Alternative: make host/port "restart-required" settings (document + warn on change).
- Tie-in to remote CLI / Tailscale usage of non-localhost binds.
