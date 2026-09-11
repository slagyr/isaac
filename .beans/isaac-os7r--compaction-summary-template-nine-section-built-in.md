---
# isaac-os7r
title: 'Compaction summary template: nine-section built-in + optional config/compaction.md'
status: todo
type: task
created_at: 2026-08-24T22:19:01Z
updated_at: 2026-08-24T22:19:01Z
---

Planned and built 2026-08-24 (Micah + planner) after the tonotop kwm5 thrash report: our summarizer asked for a concise narrative; Claude Code and Grok harnesses both use a nine-section working-ledger template. Landed isaac-agent 4d977dc; deploys with opp6 in 0.1.39.

## Why (2026-08-24)

tonotop's kwm5 worker: ~2,100 tool calls, ~80 compactions, 176 skill reloads, same files re-read 15-30x, zero edits — every compaction wiped what it had learned. Root cause (Micah pressed until the real one surfaced): NOT rubberband/telephone first — our summarizer prompt asked for "a concise summary of what happened" and got two sentences of narrative. Claude Code's compaction and Grok's detailed variant both mandate the same nine-section template (request/intent, concepts, files+code with snippets, errors+fixes, problem solving, all user messages, pending, current work, next step), thorough-not-brief, carry prior summaries forward, and exclude the instruction itself from the user messages. Ours was the outlier.

## What landed (isaac-agent 4d977dc)

- `builtin-compaction-system-prompt`: the nine-section template, adapted: memory__write first (wlha clauses kept verbatim: durable facts/preferences/discoveries; never task status; never instructions to your future self; "Work state and next steps belong in the summary, not in memory"), "never omit paths, identifiers, commands, or error text", prior-summary carry-forward, instruction exclusion (q8tr's "not part of the conversation" kept), attribution rules kept.
- `config/compaction.md` (optional, root): replaces the template verbatim; read at compaction time via nexus fs (hot-reload, no restart); absent/blank/unreadable -> built-in. Safe to replace because the load-bearing mechanics (:turnRequest, preamble, memory tools, summary-as-user-message) are structural, not prompt text. Per-crew override deferred until a crew needs it.
- Scenarios (features/session/compaction_template.feature, 2, green): built-in asks for nine sections + carry-forward + exclusion; config/compaction.md replaces verbatim (anchored regex on a ship's-log fixture).
- Regression: session features identical to pristine main (14 pre-existing, zcb9's); session+prompt specs 281/0.

## Deploy

Ships with isaac-opp6 as 0.1.39 (bump + registry pin + modules upgrade + launchctl kickstart; turns auto-resume).

## Follow-ups (next bean: "compaction keeps the turn's working context")
Loaded skills survive compaction as part of the turn frame (re-injected like :turnRequest — the 176 reloads were the harness's fault, not the model's); slinky for worker crews by config; a thrash watchdog as a registered turn-observer needing per-tool-call events (bbov interface extension). Planning guard adopted: split beans over ~10 scenarios before dispatch.
