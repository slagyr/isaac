---
# isaac-l1vb
title: ACP /status content-match scenario is red on main (pre-existing)
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-07-08T23:05:24Z
updated_at: 2026-07-20T00:08:16Z
---

## Problem

`bb features features/comm/acp/slash_commands.feature` fails in scenario
`/status returns formatted markdown via ACP notification` with
`Expected truthy but was: nil`.

Confirmed **pre-existing on `origin/main`** (`isaac-acp` @ `8e71510`), not
introduced by isaac-o14c. Reproduced on both the o14c branch (`09cf9f3`) and a
clean `origin/main` checkout — identical failure, identical assertion.

## Root cause (surfaced 2026-07-08, prowl)

The `/status` scenario asserts on `the notification content matches:`, which
reads `:last-acp-notifications` and scans a `session/update` markdown chunk for
"Session Status", "Crew ... main", etc. But `emit-status-notification!`
(`src/isaac/comm/acp/server.clj:181`) emits `/status` output as a `chat/status`
notification (structured data), not as a `session/update` markdown chunk. So
`last-notification-content` (reads `:params :update :content :text`) is empty
and every pattern `re-find` returns nil.

The scenario and the server disagree about how `/status` surfaces output.
Either the server must also emit the markdown as a `session/update` chunk, or
the scenario must assert against the `chat/status` payload.

## Not isaac-o14c

o14c's method-scoped notification-set step is correct and green on the replay
proofs. This `/status` red is orthogonal: o14c only changed
`replay-transcript!`'s transcript source and the notification-set step (scoped
to listed methods; `chat/status` is unlisted in this scenario).

## Acceptance

- [ ] `bb features features/comm/acp/slash_commands.feature` green in isaac-acp.
- [ ] The `/status` scenario proves the actual status output the server emits
      (reconcile scenario vs `emit-status-notification!`).

## Decision — the SERVER is correct; fix the SCENARIO (Micah, 2026-07-19)

Session status is rendered from a **shared bridge command** (`bridge/dispatch!` → `{:command :status :data {…}}`) used by the **CLI as well as** the ACP /status slash-command. Markdown is only desirable in clients that support MD, so the server must stay **format-neutral**: it emits the structured `:data` as a `chat/status` notification (`server.clj:214`) and lets each surface render (CLI prints; MD-capable ACP clients render markdown). Pushing markdown into a `session/update` chunk would force MD on clients that can't render it — that would be the real regression.

**So the red scenario is what's wrong, not the server. Fix = rewrite the scenario, no server change:**
- Drop the `session/update` / `agent_message_chunk` expectation and the markdown `the notification content matches:` block (Session Status / Crew / ─+ / Model / etc.).
- Assert the **`chat/status`** notification and its structured `:data` fields instead, via the existing table-based `the ACP agent sends notifications:` step (same pattern as `cancel_tool_status.feature` uses for `params.update.*`, here with `chat/status` + `params.*` columns). Confirm the exact `:data` field paths from the bridge status command.

**Specs:** no new scenario; reuse the existing notifications-table step (likely **no new step**). This is a scenario correction to match the correct contract.

## Implementation notes (scrapper @ isaac-work-1)

- Per Micah decision: server stays format-neutral (chat/status with structured
  :data). Scenario was wrong expecting session/update markdown chunks.
- Rewrote /status scenario to assert chat/status notification via existing
  notifications table step: params.crew/model/provider/session-key.
- Dropped notification content matches / SOUL.md assertions (markdown path).
- Commit: isaac-acp `cee9aa5`. slash_commands.feature green (4/0). bb ci green
  (70 specs + 61 features, 0 failures; pre-existing pendings unchanged).
