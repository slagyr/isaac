---
# isaac-3ueo
title: Reduce claude-CLI subscription overhead so Fable is usable on zanebot
status: draft
type: feature
priority: normal
created_at: 2026-07-13T16:03:13Z
updated_at: 2026-07-13T16:27:57Z
---

## Goal

Minimize per-turn overhead on the claude subscription lane so Fable (claude-fable-5) is usable as a worker model on zanebot. Measured 2026-07-13: claude CLI turns run ~15.7s vs grok ~10.4s for the same one-sentence prompt — a ~5s fixed per-turn tax from spawning the `claude` binary cold (config load, subscription session re-establish) every turn, since Isaac runs it with --no-session-persistence.

## Scope: subscription only (Micah, 2026-07-13)

Fable MUST run on Micah's Claude subscription (WAY cheaper than metered API) — the metered anthropic-API path is OFF the table. This bean is squarely about reducing the claude-CLI per-turn cold-start (~5s: spawn `claude`, load config, re-establish subscription session, since Isaac runs --no-session-persistence).

First: confirm the subscription/claude CLI can even SELECT Fable (claude-fable-5) — the --model flag must accept it and the subscription must grant it. Probe before optimizing.

## Overhead-reduction candidates (investigate + measure each delta before committing)

1. **Warm/persistent claude process** — a long-lived `claude` the adapter feeds turns to, instead of spawn-per-turn. Biggest potential win; needs process lifecycle mgmt in claude_cli.clj. Likely shares design with a future isaac daemon (isaac-ogiu Hotspot #1 split-out).
2. **Session reuse** — --output-format stream-json with a persisted session id across turns (drop --no-session-persistence). Trades the auws 'Isaac owns everything' stance for speed; evaluate the correctness cost.
3. **Trim per-invocation load** — skip MCP/plugin/settings discovery via flags if the CLI supports it.

Target: claude-lane turn latency near grok's (~10s) rather than ~16s, so Fable is practical for real work turns.

## Note

Distinct from the isaac CLI startup tax (see classpath-cache follow-up) — that is Isaac's own boot; this is the claude subprocess. A server turn pays only the claude spawn, not Isaac's boot.

## Status

Draft per Micah 2026-07-13.
