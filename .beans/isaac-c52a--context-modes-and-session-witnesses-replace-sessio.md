---
# isaac-c52a
title: Context modes and session witnesses replace SessionPolicy
status: draft
type: feature
priority: normal
created_at: 2026-10-05T01:58:10Z
updated_at: 2026-10-05T01:58:10Z
---

Micah, 2026-10-04 design session. Replaces SessionPolicy with two narrow berths.

## Why
`SessionPolicy` is a 27-method wrapper over the session store. The chronicle policy is 100% pass-through (`prepare-turn!` returns nil). The episodes policy passes 20 of 26 methods straight through; its real behavior is a handful of hooks that split into two jobs:
- **context**: what the model sees (cold open, compaction splice)
- **observation**: watching the record (episode open, scene feeding, turn markers)

The session store is the record for every session and becomes the only session API.

## Shape
1. **Context-mode berth** `:isaac.agent/context-mode`. Agent registers `:full` and `:reset` as built-ins. Turn prep asks the registered mode for the turn's context; the hard-coded `(= :reset context-mode)` branches in `drive/turn.clj` go away. Resolution stays as today: turn (`:with-context-mode`) → session → crew → defaults.
2. **Witness berth** `:isaac.agent/witness` (session observers). Crew config `:witnesses [<id> …]`, overridable per session, never per turn (witnesses are stateful). Events: session opened, message appended, compaction spliced, turn started, turn ended. Delivery is off the turn path: a per-session ordered queue on its own thread, so each witness sees a session's events in order and sessions run in parallel. A witness failure logs and raises an attention; it never blocks or fails a turn. Witnesses observe only; writing into context belongs to the context mode.
3. **Catch-up wait**: a context mode may wait at turn prep until a named witness has drained that session's queue (generic: "witness W caught up for session S").
4. **Generic `:requires` check**: a context-mode registration may declare `:requires {:witnesses #{…}}`. Config validation reports a crew (or session) whose context mode requires a witness it doesn't have. Agent knows no module names; the data comes from the registry.
5. **Remove SessionPolicy**: the protocol, the chronicle policy, the `:isaac.agent/session-policy` berth, `check-session-policy`, the session-policy-mismatch refusal in bridge/prompt_cli, and the `:session-policy` stamp on sessions.

## Acceptance (scenarios TBD, gated)
- A crew with `:context-mode :reset` gets reset behavior through the berth (existing reset scenarios stay green).
- A fixture context mode contributed by a test module is selectable by crew, session and `--with-context-mode`.
- A fixture witness receives open/append/compaction/turn-start/turn-end events for a session, in order.
- A witness that throws raises an attention and the turn still completes normally.
- A slow witness does not delay the turn's reply.
- Config validation reports a context mode whose required witness the crew lacks (message names both).
- Session-level `:witnesses` overrides the crew's.
- One-time (bean criteria, not scenarios): no `SessionPolicy`, `session-policy` or `chronicle` policy references remain in isaac-agent src.
- Handbook (agent) documents both berths and the `:requires` check.
- `bb ci` and `bb jvm-spec` green.

Likely repo scope: isaac-agent. Lands together with its follow-up (episodes port), which must re-pin to this.
