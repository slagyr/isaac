---
# isaac-ejj3
title: 'Claude Code bridge: connect to the invoking process, run via bb, template :claude-code'
status: in-progress
type: bug
priority: high
tags:
    - claude-code
    - mcp-bridge
created_at: 2026-09-15T14:24:22Z
updated_at: 2026-09-15T15:14:30Z
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
  - isaac-claude-code `7fe99a3` ("mcp-bridge moves here with the route it talks to") adds the bridge source, spec, and feature, plus `:isaac/cli :mcp-bridge` in the manifest and a Background step that registers this module's CLI berth. **Superseded for implementation (2026-09-15, Micah): do not build on it — it is unpushed and invisible to workers. Implement from `origin/main`; Micah reconciles `7fe99a3`/`8d696f1` with the result afterwards.** Its relay logic (initialize answered locally, notifications dropped, 401 → JSON-RPC -32001) is a useful reference for `isaac.mcp-bridge.main`.
  - isaac-http (checkout dir `isaac-server`) `8d696f1` ("shed the mcp-bridge command") deletes the bridge files and the `:isaac/cli` entry, and updates the two manifest assertions. That is already the end state here, so no further HTTP-host work is needed.
  - **Push/deploy order:** `8d696f1` must not ship ahead of a bridge replacement. The deployed driver still writes `{:command "isaac" :args ["mcp-bridge" …]}`, so an HTTP host without the command and without the module's replacement leaves Claude Code turns with no bridge at all. Land it with `7fe99a3` (interim) or with this bean.
  - **Blocker:** isaac-http cannot run its suites. `deps.edn:23` and `bb.edn:21` pin isaac-agent `b6284e42ab37ccf971637d4dc791856c7fa231aa`, which is missing from upstream (verified after fetch, 2026-09-15; predates both commits). Repin before `8d696f1` can be shown green.
  - The `@wip` scenario "mcp-bridge auth failure on tools/list is a JSON-RPC error" in `features/llm/mcp_bridge.feature` (from 7fe99a3) runs in-process via `isaac is run with "mcp-bridge …"` and gets empty stdout. Under decision 2 it is rewritten to launch the bridge the way Claude Code does (a `bb` subprocess with real stdin), which should also retire the stdin problem. Unverified until drafted.
- isaac-agent: delete the built-in `:claude` provider template.
- Train: module release + agent release pinned together in `isaac/modules.edn`; zanebot `~/.isaac/config/providers/claude.edn` → `{:type :claude-code}` in the same script as the restart (old runtime rejects the new key, new runtime rejects the old). yopp `providers/claude-code.edn` likewise.

## Scenarios

Scenario plan approved 2026-09-15 (Micah), after trimming from 12 scenarios / 8 new steps to 6 scenarios / 1 new step + 1 matcher row. Loopback binding and listener shutdown on cleanup are **unit specs**, not scenarios (resource hygiene; the MCP-config URL already asserts `127.0.0.1`).

Work from isaac-claude-code `origin/main` (`dbde9bb`). The unpushed `7fe99a3`/`8d696f1` are reconciled afterwards (Micah, 2026-09-15); scenarios 5–6 therefore **create** `features/llm/mcp_bridge.feature`.

### features/llm/api/claude_driver.feature

Background: the provider file becomes `config/providers/claude-code.edn` (same rows) and `config/models/sub-sonnet.edn` names `provider | claude-code`. Name-based template inheritance (`same-name-base` in isaac-agent `llm/providers.clj`) makes every existing scenario exercise the `:claude-code` template. Update `provider` log columns that carry the provider *name* (e.g. `:turn/loop-driver`) to `claude-code`; the `:claude/*` driver events log a literal `"claude"` today — leave those unless the implementation changes them.

**Replaces** "a driven turn writes an MCP config that points Claude Code at isaac's mcp-bridge for this turn (isaac-6z4r)":

```gherkin
  Scenario: a driven turn's MCP config runs the bridge under bb against this turn's own listener (isaac-ejj3)
    The bridge is not an isaac command: it runs with the invoking process's
    classpath and talks to the per-turn listener that process opened.
    Given a fake Claude Code on the path scripted with:
      | cycle | kind     | payload                                            |
      | 1     | tool_use | {"name":"exec__run","input":{"command":"echo hi"}} |
      | 2     | text     | hi came back                                       |
    When the user sends "run it" on session "main"
    Then the response is "hi came back"
    And the fake Claude Code was invoked with:
      | arg                 | value       |
      | --strict-mcp-config |             |
      | --mcp-config        | #".*\.json" |
    And the MCP config handed to the fake Claude Code names server "isaac" running:
      | argv                                                                                                   |
      | #"^bb -cp .+ -m isaac\.mcp-bridge\.main --turn [0-9a-f-]{36} --url http://127\.0\.0\.1:[0-9]+$" |
```

**Replaces** "the MCP config carries the running server's own URL and auth token (isaac-o2fh)":

```gherkin
  Scenario: the turn's nonce reaches Claude Code only through its environment, never the server's port or token (isaac-ejj3)
    Given the isaac EDN file "config/isaac.edn" exists with:
      | path              | value         |
      | server.port       | 7912          |
      | server.auth.token | harbor-secret |
    And a fake Claude Code on the path scripted with:
      | cycle | kind     | payload                                            |
      | 1     | tool_use | {"name":"exec__run","input":{"command":"echo hi"}} |
      | 2     | text     | hi came back                                       |
    When the user sends "run it" on session "main"
    Then the response is "hi came back"
    And the fake Claude Code was invoked with:
      | arg                      | value |
      | (ISAAC_MCP_NONCE in env) |       |
    And the MCP config handed to the fake Claude Code names server "isaac" running:
      | argv                                                        |
      | #"^(?!.*7912)(?!.*harbor-secret)(?!.*--token)(?!.*NONCE).+$" |
```

**New:**

```gherkin
  Scenario: a provider of type claude-code under another name drives the turn (isaac-ejj3)
    Given the isaac EDN file "config/providers/harbor.edn" exists with:
      | path              | value       |
      | type              | claude-code |
      | command           | claude      |
      | drives-tool-loop? | true        |
    And the isaac EDN file "config/models/harbor-sonnet.edn" exists with:
      | path     | value  |
      | model    | sonnet |
      | provider | harbor |
    And the isaac EDN file "config/crew/deckhand.edn" exists with:
      | path  | value        |
      | model | harbor-sonnet |
      | soul  | Think hard.  |
    And the following sessions exist:
      | name   | crew     |
      | harbor | deckhand |
    And a fake Claude Code on the path scripted with:
      | cycle | kind | payload        |
      | 1     | text | harbor answers |
    When the user sends "ahoy" on session "harbor"
    Then the response is "harbor answers"
    And the fake Claude Code was invoked exactly once
```

### features/llm/mcp_turn_registry.feature

Scenarios unchanged. Only the feature description changes: the registry is served by the per-turn listener the driver opens, not an HTTP route owned by this module. The `{path}` values in the existing post step stay as written (the step uses the last segment as the turn id).

### features/llm/mcp_bridge.feature (new file)

```gherkin
Feature: The mcp bridge relays Claude Code's MCP lines to the turn's listener (isaac-ejj3)
  Claude Code spawns the bridge as a stdio MCP server: `bb -cp <classpath> -m
  isaac.mcp-bridge.main --turn <id> --url <listener>`. It answers initialize
  locally, drops notifications, and POSTs every other line to the listener,
  authenticated with the turn's nonce from ISAAC_MCP_NONCE. A refused nonce
  comes back as a JSON-RPC error so the CLI reports a failed tool call
  rather than hanging.

  Background:
    Given default Grover setup
    And the built-in tools are registered
    And the crew "main" allows tools: "exec/run,fs/read"
    And the following sessions exist:
      | name     |
      | mcp-sess |

  Scenario: the bridge answers initialize itself and relays tools/list to the turn's listener
    Given a turn "t-relay" is registered for session "mcp-sess"
    When the mcp bridge relays for turn "t-relay":
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
      {"jsonrpc":"2.0","method":"notifications/initialized"}
      {"jsonrpc":"2.0","id":2,"method":"tools/list"}
      """
    Then the MCP response matches:
      | key                  | value     |
      | id                   | 2         |
      | result.tools[0].name | exec__run |
      | result.tools[1].name | fs__read  |

  Scenario: a bridge whose nonce the listener refuses answers with a JSON-RPC error and nothing executes
    Given a turn "t-guard" is registered for session "mcp-sess"
    And environment variable "ISAAC_MCP_NONCE" is "not-the-nonce"
    When the mcp bridge relays for turn "t-guard":
      """
      {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"exec__run","arguments":{"command":"echo never"}}}
      """
    Then the MCP response matches:
      | key           | value                 |
      | id            | 3                     |
      | error.code    | -32001                |
      | error.message | #"(?i)unauthorized"   |
    And session "mcp-sess" has transcript not matching:
      | type     | name      |
      | toolCall | exec__run |
```

### Step ledger (approved 2026-09-15)

| Step | Status | Notes |
|---|---|---|
| `a turn {turn-id:string} is registered for session {session-key:string}` | existing, **extended** | also opens the turn's listener and keeps its URL + nonce, as production registration does; `after-scenario` closes listeners |
| `the turn {turn-id:string} is cleared` | existing, **extended** | also stops the listener |
| **`the mcp bridge relays for turn {turn-id:string}:`** | **NEW** | runs `bb -cp <classpath> -m isaac.mcp-bridge.main --turn <id> --url <listener>` as a real subprocess with the docstring on stdin and `ISAAC_MCP_NONCE` = the turn's nonce unless an `environment variable "ISAAC_MCP_NONCE"` override is set; stores the **last** stdout line, parsed, as `:mcp-response` |
| `the fake Claude Code was invoked with:` row `(ISAAC_MCP_NONCE in env)` | **NEW row** in existing matcher | beside `(no ANTHROPIC_API_KEY in env)` |
| `the MCP response matches:`, `environment variable {name} is {value}`, `session {key} has transcript not matching:`, fake Claude Code steps, EDN-file steps | existing | unchanged |

Delete the two replaced scenarios; no alias scenario for the old `isaac mcp-bridge` argv or `--token`.

## Acceptance

Feature and spec gates (run with `ISAAC_GIT=1`; trust `examples, 0 failures` + unwrapped exit per deploy lessons):

```
bb features features/llm/api/claude_driver.feature
bb features features/llm/mcp_turn_registry.feature
bb features features/llm/mcp_bridge.feature
bb ci
```

Unit specs (new): the per-turn listener binds `127.0.0.1` only; it is stopped when the turn is cleaned up (a POST afterwards is refused at the socket); a request without the turn's nonce gets 401 and never reaches the registry.

One-time checks (not scenarios, per the no-absence-tests rule):
- isaac-claude-code `src/isaac-manifest.edn` has no `:isaac/cli` and no `:isaac.http/route`; template key is `:claude-code`.
- isaac-agent `resources/isaac-manifest.edn` has no `:claude` provider template; `isaac config validate` rejects `{:type :claude}` on a root with both released.
- `grep -rn "mcp-server-url\|mcp-server-token\|running-server\|ISAAC_SERVER_TOKEN" src/` in isaac-claude-code is empty.
- Real-binary smoke after the train, on yopp: `isaac prompt --crew claude -m "Use a tool to list /home/yopp/.isaac/config/models …"` performs a real tool call; cli.log shows `:claude/mcp-status` with `:tools` = the crew's allowed tool count; no `:mcp/turn-not-active` in either log. Repeat a hail-driven tool call on zanebot.

## Worker checkpoint (2026-09-15)

Done: authenticated per-turn loopback listener, direct Babashka stdio bridge, driver wiring for process classpath + `ISAAC_MCP_NONCE`, cleanup specs, and initial provider/feature cutover. Green commits pushed through claude-code `5f6d264`. Feature harness now resolves `:claude-code`; the focused driver feature improved from 20 failures to 1.

Next: fix the remaining pre-existing thinking/reckoning scenario harness mismatch, then commit provider feature cutover; complete listener-backed registry/bridge feature steps and remove Agent's built-in `:claude` template. Resume at `features/llm/api/claude_driver.feature:87`; current RED is `ISAAC_GIT=1 bb features features/llm/api/claude_driver.feature` with 21 examples, 1 failure (expected reckoning/chatter ordering).
