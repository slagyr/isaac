---
# isaac-ey73
title: ACP tool results are delivered but thin clients can still render them empty
status: draft
type: bug
priority: normal
created_at: 2026-06-27T19:35:18Z
updated_at: 2026-06-27T19:35:18Z
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

## Isaac-side improvements

Track Isaac-side compatibility hardening only. Full end-user resolution still
requires an OpenClaw/Toad change.

Potential scope:

1. Make live `tool_call_update` payloads more self-contained for thin clients.
   Include fields such as `title`, `kind`, and `rawInput` when available so an
   update can be rendered without cached state from the initial `tool_call`.

2. Audit `session/load` / replay / resume paths so stored string tool results
   always become ACP-compatible `content` blocks on the wire, with preserved
   tool identity.

3. Document the ACP contract clearly: clients must render result text from
   `update.content` or `update.rawOutput` on `tool_call_update`.

4. Add feature/spec coverage that pins the interoperability surface, not just
   the raw payload shape.

## Out of scope

- Replacing Isaac transcript storage with ACP content-block arrays
- Fixing OpenClaw/Toad rendering inside this repo
