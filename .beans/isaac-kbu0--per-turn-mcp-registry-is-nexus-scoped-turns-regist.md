---
# isaac-kbu0
title: 'Per-turn MCP registry is nexus-scoped: turns registered from a nested nexus (remote CLI, ACP) are invisible to the HTTP route, so Claude Code sees zero tools'
status: completed
type: bug
priority: high
created_at: 2026-09-08T19:21:08Z
updated_at: 2026-09-08T20:17:48Z
parent: isaac-tuk1
---

Repo: isaac-agent (`src/isaac/mcp/turns.clj` `registry-atom`: `(or (nexus/get :mcp-turns) (register a fresh atom in the CURRENT nexus))`) + isaac-server (`server/mcp.clj` route). Child of isaac-tuk1; follow-up to isaac-o2fh (0.1.6 + server 0.1.13, deployed 2026-09-08 ~19:20Z).

## Evidence (zanebot, 2026-09-08 19:18Z)
- Smoke via the remote CLI: `:claude/mcp-status :status "connected" :tools 0` → `:claude/driver-fallback :reason :mcp-failed` (fence path answered `mcp-loop-ok`). URL + token are now right (o2fh): the bridge connected.
- server.log: exactly ONE bridge request during the turn — `POST /mcp/turns/62839be8-…` → 200 in 3 ms. The bridge answers `initialize` locally and drops notifications, so that POST was Claude Code's `tools/list`. `isaac.mcp.turns/handle` returns JSON-RPC error -32001 (turn not active) inside a 200 when the turn is not in ITS registry — which is what Claude Code turned into zero tools.
- `registry-atom` resolves `:mcp-turns` from the current nexus and lazily registers a new atom there. Remote-CLI and ACP turns run inside a nested nexus/system (`system/with-nested-system` in ACP; the cli-server dispatch likewise), so the driver's `register!` lands in the nested nexus while the HTTP route (server root nexus) creates and reads an empty sibling. Hail- and Discord-originated turns run in the root nexus and would not hit this — which is why the isaac-zocg registry scenarios (in-process, single nexus) are green.

## Required
1. The registry is process-global: a `defonce` atom (or always resolved at the ROOT nexus), never per-nexus. Turns are keyed by uuid so there is no cross-nexus collision.
2. `handle` logs `:mcp/turn-not-active :turn <id>` at :warn when it refuses, so the next field check shows the refusal instead of a silent 200.
3. isaac-zocg's registry spec/feature gains the case: a turn registered from inside a nested nexus is served by `handle` called from the root nexus (spec, store internals — no new Gherkin steps).

## Acceptance
- isaac-agent `bb spec spec/isaac/mcp/` green incl. the nested-nexus case; `bb features features/llm/mcp_turn_registry.feature` green; `bb features && bb spec` green.
- Field check by the verifier via exec on zanebot after the train (agent bump + pin): `isaac prompt --crew scrapper --model claude-cli --session verify-mcp-smoke 'Use your exec tool to run exactly: echo mcp-loop-ok — then reply with only the command'\''s output.'` → `mcp-loop-ok`; cli.log `:claude/mcp-status :status "connected" :tools N>0`, `:claude/driver-exit :result-event true`, NO `:claude/driver-fallback`; server.log shows `POST /mcp/turns/<id>` for tools/list AND tools/call; transcript has the exec toolCall/toolResult pair executed through the bridge. The planner records the deploy and re-hails verify for it.

## Handoff

branch: bean/isaac-kbu0 @ 2bb212e (base origin/main@c54cbb0)

isaac-agent: `src/isaac/mcp/turns.clj` registry is a process-global `defonce` atom (not nexus-scoped). `handle` logs `:mcp/turn-not-active :turn <id>` at `:warn` on refuse. Nested-nexus spec in `spec/isaac/mcp/turns_spec.clj`; refuse log asserted in `features/llm/mcp_turn_registry.feature`. No isaac-server change — route already delegates to `isaac.mcp.turns/handle`.

Local: `bb spec spec/isaac/mcp/` green; `bb features features/llm/mcp_turn_registry.feature` green; `bb spec` 1679 examples, 0 failures. Do not pin modules.edn. Field check after agent bump + pin is verifier/planner.



## Landed on main (2026-09-08)
main-sha: isaac-agent 64f4ca7ea7d78fb6f8e6e0e814cd804127a8a317



## Deployed (2026-09-08 19:52Z) — agent 0.1.51 (7635a34)
Smoke via the remote CLI: still `:claude/mcp-status :status "connected" :tools 0` → `:claude/driver-fallback :reason :mcp-failed` (fence path answered `mcp-loop-ok`). One `POST /mcp/turns/<id>` → 200 (19:54:30). Planner checking whether the route now finds the turn (no :mcp/turn-not-active) and whether the registered entry carries tools.



## Finding after 0.1.51 (planner, 2026-09-08 20:0xZ): the CLI path is OUT OF PROCESS
server.log at 19:54:30: `:mcp/turn-not-active :turn ea6a46a9…` — the route still could not find the turn. Cause is one level below nexus scoping: the remote CLI server spawns every command as a separate `isaac` process (`isaac-cli-server dispatch.clj` `p/process`), so a `prompt`-originated turn's driver registers the turn in ITS process while the HTTP route lives in the server process. No in-memory registry — nexus-scoped or global — can bridge that. kbu0's fix is still correct for in-server nested nexuses (ACP), but the remote-CLI field check can never pass by design. Driven claude-cli turns are supported for server-origin turns (hail, Discord, ACP-in-server); an out-of-process turn should fall back with `:reason :out-of-process` instead of `:mcp-failed`. Field check moved to a server-origin turn via a new `smoke` hail band (session tag :smoke, session genuine-cedar) — result recorded below.

## CI note (2026-09-08, hail ef0a040d / 55c60aad) — do not reopen

GitHub Actions CI Tests failed on land SHA `64f4ca7ea7d78fb6f8e6e0e814cd804127a8a317` (run 34271321013, `bb ci` / `bb features`):
- `session/parallel_tool_batches.feature:124` — mixed concurrent batch events
- `session/compaction_logging.feature:140` — partial-compact transcript mismatch

These are ambient full-suite flakes already noted on the isaac-y802 handoff (isolated re-runs green; not introduced by the process-global registry). **isaac-kbu0 remains completed.** Subsequent main `8d9dd26` (**isaac-y802**) CI Tests run 34271982954 is success. Correlation rule: no independent repair commissioned against the kbu0 land SHA.

Flake hardening filed as draft **isaac-1d7x**. Do not retag unverified. Do not hail work or verify on this bean.



## Field check PASSED for this bean's contract (2026-09-08 20:14Z)
Server-origin turn (smoke band, session genuine-cedar pinned to claude-cli, hail 93544111): `:turn/loop-driver :driver :provider`, `POST /mcp/turns/<id>` → `:mcp/tools-listed`, `:claude/mcp-status :tools 15 :status "connected"`, `:claude/driver-exit :result-event true`, no fallback, the tool executed through the bridge and the reply reached the user. Remaining native-loop defects (double dispatch under the mcp__ name, spawn per cycle, doubled reply) → isaac-1tmw.
