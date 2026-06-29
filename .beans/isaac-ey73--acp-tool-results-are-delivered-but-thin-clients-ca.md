---
# isaac-ey73
title: ACP tool results are delivered but thin clients can still render them empty
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-27T19:35:18Z
updated_at: 2026-06-29T14:43:53Z
---

## Problem

Nightbird's local Toad log proves Isaac delivered ACP tool results correctly,
but the client still rendered them as empty/status-only.

Evidence:

- Local Toad log:
  `/Users/micahmartin/.local/state/toad/logs/isaac_2026-06-27T11_02_58_427061.txt`
- That log contains a live `tool_call_update` for `glimmering-cardinal` with:
  - `toolCallId`
  - `status: "completed"`
  - `rawOutput: "No skills discovered."`
  - `content[0].content.text: "No skills discovered."`
- The same log later shows the full skill list arriving over ACP as normal
  `agent_message_chunk` text after the prompt-layout fix.

Client-side findings from local OpenClaw/Toad source:

- `src/acp/client.ts` prints only `[tool update] <id>: <status>` for
  `tool_call_update`; it ignores `rawOutput` and `content`.
- `src/tui/tui-session-actions.ts` only hydrates transcript `toolResult`
  entries when `message.content` is an array, while Isaac's canonical
  transcript stores tool-result text as plain strings.

So the wire payload is already correct. The visible gap is client
interoperability.

## Decision

Isaac should **not** change its canonical session transcript schema to store
ACP-style content-block arrays for tool results.

Keep core storage transport-neutral:

- textual tool result in `:content`
- optional structured payload in `:details`

ACP should continue translating that canonical form into ACP payloads at the
transport boundary.

## Approved scenarios

- `isaac-acp/features/comm/acp/tools.feature:74`
  - `tool result updates repeat title, kind, and rawInput for thin ACP clients`
- `isaac-acp/features/comm/acp/session.feature:102`
  - `session/load replays a string tool result as ACP content and rawOutput`

## Decision (2026-06-29, Micah)

Keep `ey73` on the Isaac side of the compatibility seam:

- do not change canonical transcript storage to ACP-style content arrays
- add self-contained fields on live `tool_call_update` payloads for thin clients
- keep replay/resume/session-load responsible for translating stored string
  tool results into ACP-compatible `rawOutput` plus `content`
- no new steps

OpenClaw/Toad rendering changes are a separate concern and stay out of this
bean.

## Acceptance commands

- `cd isaac-acp && bb features features/comm/acp/tools.feature features/comm/acp/session.feature`

## Out of scope

- Replacing Isaac transcript storage with ACP content-block arrays
- Fixing OpenClaw/Toad rendering inside this repo
