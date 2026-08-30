---
# isaac-frvu
title: 'Scuttlebutt renderings: per-comm contract — how every Comm impl handles the new surface'
status: draft
type: task
priority: normal
created_at: 2026-08-30T23:29:05Z
updated_at: 2026-08-30T23:29:05Z
blocking:
    - isaac-i5ps
    - isaac-pq0b
---

Planning bean (2026-08-30, Micah). Output: a decision table, one row per Comm
implementation, settling how each renders the new surface (chatter /
reckoning / aside / reply / tool-call / tool-cancel / tool-result /
tool-progress / bulletin / turn-start / turn-end) — BEFORE the module
implementation beans (isaac-i5ps ACP, isaac-pq0b Discord) get scenarios, and
feeding the per-comm contract section into isaac-5nxf for the in-tree impls.

## Why

isaac-5nxf plans the interface; the implementations were only sketched. The
sketches currently living in i5ps/pq0b/5nxf are PROPOSALS, not decisions
(Micah 2026-08-30). Each comm must pick ONE altitude per voice — streams
(chatter/reckoning) or verdicts (aside/reply) — or it double-renders; that
choice, plus what to do with bulletins and tool events, is a per-surface UX
decision Micah should make deliberately.

## The table to fill (current sketch as starting point)

| comm | altitude | reckoning | tools | bulletins | notes |
|---|---|---|---|---|---|
| null | defaults everywhere | drop | drop | drop | trivially decided |
| memory | records EVERYTHING | record | record | record | reference impl; dictated by scuttlebutt.feature |
| cli (comm/cli.clj) | chatter (today's stream, byte-identical) | ⋯ dim lines behind a verbose flag? | 🧰/← lines as today | print? | UX decision |
| prompt_cli | same as cli? they differ today — inventory first | ? | ? | ? | |
| api (in-tree) | ? — inventory what it forwards today | ? | ? | ? | |
| ACP (i5ps) | streams: chatter → agent_message_chunk, reckoning → agent_thought_chunk | native | native notifications as today | session/update status? which kinds? | closest to protocol mapping, least contentious |
| Discord (pq0b) | verdicts; live-turn option: edit-in-place working message w/ latest aside + tool lines | drop? or into the working message? | into working message when live-turn on | one italic line? which kinds? | flag per channel vs default-on; rate limits |
| iMessage | reply-only? | drop? | drop? | compaction/hold notices? | pure assumption today — decide |

## Decisions the session must produce

1. Per-comm altitude + per-signal row for every impl (table above completed).
2. **No-double-render rule made testable**: conformance spec asserts a comm
   does not implement both on-chatter and on-aside/on-reply as renderers
   (or defines the sanctioned exception, e.g. memory).
3. Which bulletin kinds are user-facing per surface (compaction? recall?
   turnstile holds? episode rotation is supposed to be INVISIBLE —
   51xy decision 27 — so :episodes/* likely renders NOWHERE except memory).
4. Reckoning visibility defaults: on for ACP/CLI-verbose, off elsewhere?
   Per-crew or per-comm config knob, if any.
5. In-tree contract lands as an amendment to isaac-5nxf acceptance
   (byte-identical UX for cli/prompt_cli/api through the migration);
   module rows become the scenario seeds for i5ps / pq0b / a new imessage
   bean if its row is non-trivial.

## Inputs to gather at session start

- Inventory of what cli / prompt_cli / api actually render today (they are
  not identical; read before deciding).
- Discord rate-limit budget for edit-in-place (edits per turn at
  tool-loop-max 400 is not viable unthrottled).
- ACP protocol: which session/update kinds toad renders.

Draft until the planning session runs. Blocks isaac-i5ps and isaac-pq0b
(already linked); informs isaac-5nxf but does not block it — the in-tree
contract there is "byte-identical, one altitude", already its regression net.
