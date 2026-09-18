---
# isaac-eqkb
title: 'Epic: CLI runs inside the server process (single-writer sessions, warm CLI)'
status: todo
type: epic
priority: high
created_at: 2026-07-13T17:29:33Z
updated_at: 2026-09-17T15:55:24Z
---

## Goal (reopened 2026-09-17, Micah)

Run isaac CLI commands INSIDE the running server process. Primary reason is **single-writer**: every `prompt` / `acp` / `sessions set` / `hail` from a second process is an unguarded writer against files the server is mid-turn on (isaac-4zr3's persist lock is a JVM monitor — "a second process won't see it; do not invent flock"). In-process closes that gap with no new locking. Startup speed (no bb boot, no config resolve, live hot-reloaded config) is the second payoff.

Reverses isaac-895i's subprocess decision. Its reasons, answered: streaming duplex → bound `*in*`/`*out*`/`*err*` over the frame pipe; cwd → already off the wire; `System/exit` + friends → the CLI host library (child 1); crash containment → ACCEPTED residual risk (timeouts + cancellation; a runaway/OOM command hurts the server).

## Decisions (2026-09-17, Micah)

- **No subprocess backdoor.** No client-selectable "run as process" flag. SSH + the cold `isaac` binary is the escape hatch (and the only thing that works when the server is wedged). Subprocess spawning in cli-server is migration scaffolding and is DELETED when the last command is embedded.
- **All commands embed by default; `:local-only true` in the manifest opts out** (`server`, `service`, `modules install|upgrade`, `remote`). Over the pipe a local-only command refuses with "run this on the host".
- **Every CLI command goes through one CLI host library**; nothing in a command touches `System/exit`, stdin/stdout, env, cwd, tty, shutdown hooks, or process-global runtime installs directly.
- **PROTOCOL.md is unchanged** — execution model is a server-side detail (only the "Execution model" prose changes).
- Native-image stays ruled out; reuse the existing server, not a new daemon (unchanged from the 07-13 design).

## Survey findings (2026-09-17) — what actually endangers a long-running server

`System/exit` is the smallest hazard: ONE call, `isaac.main/-main` (foundation main.clj:180); every command already returns an exit code. The real hazards:

1. **`isaac.main/run` is a process bootstrap**: `config-api/clear-process-memo!`, `registry/clear-berth-commands!`, `lifecycle/reconcile-modules!` (would unload/reload the server's live modules), `log-output/apply-cli!` (redirects the server's logger to cli.log), `nexus/init!`, and a process-global `with-redefs` on `log/log*`. ⇒ the embedded path must NEVER call `main/run`; it is `registry/get-command` → run-fn against the live nexus.
2. **Commands re-bootstrap themselves**: `loader/load-config!` (auth, crew, sessions, prompt, embed, recall, episodes), `runtime/install!`, `builtin/register-all!`, acp's `config/set-snapshot!` + `store/register!`, and `sessions`' `config/dangerously-install-config! nil` in `finally` (session/cli.clj:277,454 — would nil the server's config).
3. **`isaac.nexus` is a global atom** with save/restore (`-with-nested-nexus`), not a binding — concurrent nested installs clobber each other. Embedded commands use the live nexus and never nest.
4. Misc: acp `System/setProperty "user.dir"` (acp/server.clj:39-47) and `with-redefs` for `--verbose` (acp/cli.clj:148); `logs --follow` and `server` block forever; worksite locks are PID-stamped (worksite/lock.clj:28); `color/tty?`, `System/getenv`, `user.dir` would reflect the server, not the caller.

Stdin readers (`acp`, `mcp-bridge`, `hail send`, `config set/validate`, `auth`) ARE hostable — `*in*` is bindable.

## Children (in order)

1. **isaac-dq4v** — CLI host library in foundation + foundation commands migrated + lint.
2. **isaac-1fwl** — Module commands migrated to the host (`ensure-runtime!`); sessions nil-out, acp hacks, worksite lock owner fixed.
3. **isaac-qvhy** — cli-server embedded dispatch (thread per stream; `:local-only` refusal; subprocess kept only for not-yet-migrated commands).
4. **isaac-gar0** — Remote-by-default CLI: default stays a separate local process; `:cli :remote {:url :token}` in `~/.config/isaac.edn` routes every non-local-only command through the server (same-machine = localhost remote). Unreachable ⇒ fail with reason, never cold fallback; `--local` bypasses; stale basis is refused SERVER-side for non-read-only commands. This, not the remote pipe, closes the major second-writer source (crew tool shell-outs + SSH'd commands).
4b. **isaac-kjzq** — Server-side stale-basis refusal + `:read-only` hints (split from gar0; blocked by qvhy only).
5. **isaac-dqy9** — Embed `prompt` + `acp`; delete subprocess spawning from cli-server.

## Accepted risks

- A runaway/OOM embedded command degrades the server (mitigation: per-command timeout, cancellation on grace expiry).
- A server restart drops embedded long-lived streams (`acp`); proxy reattach fails with unknown stream-id and the editor reconnects.
- Embedded `prompt` shares the in-flight gate — now CORRECT: it honors `:max-in-flight`.

## History

Parked 2026-07-13 as a read-only speed daemon justified by a "1.3 s bb source-load floor"; isaac-v1la (09-04) showed that floor was redundant config resolution. Supersedes/absorbs the warm-CLI part of isaac-5zfv's motivation.
