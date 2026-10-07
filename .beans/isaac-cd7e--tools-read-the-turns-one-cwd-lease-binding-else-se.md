---
# isaac-cd7e
title: Tools read the turn's one cwd (lease binding else session), so worksite turns run tools in their leased directory
status: completed
type: bug
priority: high
created_at: 2026-10-07T14:12:58Z
updated_at: 2026-10-07T16:22:22Z
---

Likely repo: **isaac-agent**. Found by Foreman pilot 1 (isaac-8uno), 2026-10-06.

## Why

The worksite lease bound the turn to work-1, and the turn's user message records
`cwd /Users/zane/agents/isaac/work-1`; boot files, rules and skills read it
(`drive/turn.clj` uses `(:cwd charge)`). Tools never see it: a tool call carries only
`session_key`, `caller_crew`, `request_id` (`drive/turn.clj` ~1576), so
`fs_bounds/session-workdir` and the directory policy's `:cwd` token re-read the
session's stored cwd. Scrapper's `exec__run` with `workdir "."` printed
`/Users/zane/.isaac/crew/scrapper`; it then did the whole bean in work-3 (via `cd`
inside commands) while holding the lease on work-1.

## Design (Micah, 2026-10-07)

- The charge (the turn) always has exactly one cwd, settled once when the charge is
  built: the pool's `:session/cwd` binding when a lease gives one, else the session's
  cwd.
- Everything in the turn reads that one place — the drive, boot files, and every tool.
  Tools get it from the turn context; nothing re-reads the session's cwd on its own.
  `session-workdir` stops being a second source of truth (the only fallback lives in
  charge building).
- The directory policy's `:cwd` token expands to the turn's cwd.
- Out of scope: `cd` inside an `exec__run` command string (a shell escape; separate
  decision).

## Acceptance

- isaac-agent `features/turn/resource_pool_receipts.feature:104`.
- The rest of isaac-agent features stay green.

feature-baseline: isaac-agent 08174d7db3835f4711b6675983943b3ce345445e
feature-blob: isaac-agent features/turn/resource_pool_receipts.feature c6257039a00c1b5331629c3e1e419174175ae16d 104

## Work checkpoint (2026-10-07)

Done: feature :104 enabled and green; bb ci green (1890 specs, 878 features, 1 pre-existing pending); bean gate PASS on bda12ab. Implementation committed and pushed as isaac-agent bean/isaac-cd7e bda12ab.
Next: review single-source cwd design at isaac-agent/src/isaac/agent/tool/fs_bounds.clj:71 (standalone tool fallback), then land the green rebased branch on main and record main-sha.

## Landed on main (2026-10-07)

main-sha: isaac-agent 5b9a667da3593e8de6850af4226991e4e03daa56
