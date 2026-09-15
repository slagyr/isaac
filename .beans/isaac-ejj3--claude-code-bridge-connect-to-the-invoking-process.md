---
# isaac-ejj3
title: 'Claude Code bridge: connect to the invoking process, run via bb, template :claude-code'
status: draft
type: bug
priority: high
tags:
    - claude-code
    - mcp-bridge
created_at: 2026-09-15T14:24:22Z
updated_at: 2026-09-15T14:24:22Z
---

## Problem

Two defects in the Claude Code provider (`isaac-claude-code`, module `:isaac.provider.claude-code`), found configuring yopp on 2026-09-14.

### 1. The MCP bridge talks to the daemon, not to the process that started the turn

`claude_cli.clj` `mcp-server-url` builds the bridge's `--server` from `:mcp-server-url`/`:server-url` config, else `http://127.0.0.1:<config [:server :port] or 6674>`; `mcp-server-token` reads `[:server :auth :token]` from a config snapshot. Nothing ties the URL to the invoking process. `register-mcp-turn!` registers the turn in the *invoking* process's `mcp-turns` atom.

- Turn inside the server (Discord, hail, cron, remote CLI): the daemon is the invoker, so it works by coincidence.
- Turn inside a CLI process (`isaac prompt` on a root with a running daemon): the bridge POSTs to the daemon, which never registered the turn → `:mcp/turn-not-active` (server.log) → `claude/mcp-status :tools 0` → `claude/driver-fallback :reason :mcp-failed` → fence fallback → `empty-terminal-response`. The crew's tools silently vanish.

Evidence (yopp, 2026-09-14T23:44, session `claude-smoke-4`): `turn/request-built :selected-tools-count 8`, then `mcp/turn-not-active` for turn `60703da5…` in server.log, `mcp-status :tools 0`, turn ended `:error :empty-terminal-response`.

### 2. The bridge runs through the full isaac CLI

`write-mcp-config!` writes `{:mcpServers {:isaac {:command "isaac" :args ["mcp-bridge" …]}}}`, so every `claude` spawn boots the isaac launcher (config resolution, module discovery, classpath) just to relay stdio JSON-RPC to HTTP POSTs.

### 3. The provider template is named `:claude`

The module contributes `:isaac.agent/provider-template {:claude …}` (`src/isaac-manifest.edn`), so a provider entity reads `{:type :claude}` — indistinguishable from the Anthropic API. isaac-agent `resources/isaac-manifest.edn` (origin/main, ~line 171) still carries its own built-in `:claude` template left over from the isaac-jllj pure move.

## Decisions

- Decision (2026-09-15, Micah): the bridge connects to the same process that invoked it — the turn's owner hands the bridge its endpoint. No config-derived server URL, no port default, no token read from a config snapshot.
- Decision (2026-09-15, Micah): the bridge is a claude-code-only program invoked directly with `bb`, not an `isaac` CLI command. The module stops contributing `:isaac/cli :mcp-bridge`. The MCP config becomes `bb … -m <bridge ns> <endpoint args>`.
- Decision (2026-09-15, Micah): the provider template is `:claude-code` (disambiguates from the Anthropic API). Clean cutover: `:claude` is removed from both the module and isaac-agent's manifest, no alias; `{:type :claude}` hard-rejects as an unknown template.

- Decision (2026-09-15, Micah): every driven turn — CLI process or server — opens its own per-turn listener in the process that owns the turn: http-kit on `127.0.0.1`, port 0, serving that turn's MCP messages, stopped when the turn is cleaned up. The tool function, session, and ACL live in that process, so the listener must too (a separate driver process could not run them). One path everywhere: the module drops its `:isaac.http/route` contribution and no longer depends on isaac-http or the server-wide bearer token. Sized at ~15–20 lines added, ~20 deleted in `claude_cli.clj` (`running-server`, `mcp-server-url`, `mcp-server-token` go). `bb -e` confirmed http-kit `run-server` on port 0 + `server-port` + `server-stop!` works (2026-09-15).
- Decision (2026-09-15, Micah): the bridge's classpath is the invoking process's own. isaac runs under bb (`libexec/isaac` → `bb … isaac.bb`) and foundation adds module deps with `babashka.deps/add-deps`, so the driver passes its running classpath to `bb -cp <classpath> -m isaac.mcp-bridge.main`. No path discovery.
- Decision (2026-09-15, Micah): each turn mints a random nonce held only in memory; the listener accepts only requests carrying it. Localhost alone is not enough on a multi-user host (zanebot: `micahmartin` and `zane`), and argv is visible to every user via `ps`. The nonce reaches the bridge only through the `claude` subprocess environment (`ISAAC_MCP_NONCE`) — never argv, never the MCP config file. Verified 2026-09-15: a stdio MCP server spawned by `claude -p --strict-mcp-config --mcp-config …` inherits the parent's environment (`ISAAC_NONCE_PROBE` appeared in the probe's env dump). The bridge sends it as `Authorization: Bearer <nonce>`. The server-wide token and `--token` go away.

## Scope / ripple

- isaac-claude-code: `claude_cli.clj` (`mcp-server-url`, `mcp-server-token`, `write-mcp-config!`), bridge namespace, manifest (`:isaac/cli` entry, template key), `features/llm/api/claude_driver.feature`, specs.
- **Starting point: two unpushed commits from another agent (verified 2026-09-15).** They finish isaac-q9j6, which moved `POST /claude/turns/:id` into this module but left the bridge command in the HTTP host:
  - isaac-claude-code `7fe99a3` ("mcp-bridge moves here with the route it talks to") adds the bridge source, spec, and feature, plus `:isaac/cli :mcp-bridge` in the manifest and a Background step that registers this module's CLI berth. Build on it: keep the relay code and its unit spec (JSON-RPC error shape); drop the `:isaac/cli` entry and the registration step (decision 2).
  - isaac-http (checkout dir `isaac-server`) `8d696f1` ("shed the mcp-bridge command") deletes the bridge files and the `:isaac/cli` entry, and updates the two manifest assertions. That is already the end state here, so no further HTTP-host work is needed.
  - **Push/deploy order:** `8d696f1` must not ship ahead of a bridge replacement. The deployed driver still writes `{:command "isaac" :args ["mcp-bridge" …]}`, so an HTTP host without the command and without the module's replacement leaves Claude Code turns with no bridge at all. Land it with `7fe99a3` (interim) or with this bean.
  - **Blocker:** isaac-http cannot run its suites. `deps.edn:23` and `bb.edn:21` pin isaac-agent `b6284e42ab37ccf971637d4dc791856c7fa231aa`, which is missing from upstream (verified after fetch, 2026-09-15; predates both commits). Repin before `8d696f1` can be shown green.
  - The `@wip` scenario "mcp-bridge auth failure on tools/list is a JSON-RPC error" in `features/llm/mcp_bridge.feature` (from 7fe99a3) runs in-process via `isaac is run with "mcp-bridge …"` and gets empty stdout. Under decision 2 it is rewritten to launch the bridge the way Claude Code does (a `bb` subprocess with real stdin), which should also retire the stdin problem. Unverified until drafted.
- isaac-agent: delete the built-in `:claude` provider template.
- Train: module release + agent release pinned together in `isaac/modules.edn`; zanebot `~/.isaac/config/providers/claude.edn` → `{:type :claude-code}` in the same script as the restart (old runtime rejects the new key, new runtime rejects the old). yopp `providers/claude-code.edn` likewise.

## Acceptance (draft — scenarios TBD)

- A CLI-process turn (`isaac prompt`) on a root whose daemon is running makes a real tool call through Claude Code: `claude/mcp-status :tools N` with N = the crew's allowed tool count, and no `mcp/turn-not-active` in either log.
- The generated MCP config's command is `bb`, not `isaac`; `isaac help` lists no `mcp-bridge`.
- `{:type :claude-code}` resolves; `{:type :claude}` fails `isaac config validate`.
- One-time grep: no `:claude` template key in isaac-agent or isaac-claude-code manifests.
- Real-binary smoke on yopp and zanebot after the train (see isaac deploy lessons: exercise the real `claude`, not the fake CLI).
