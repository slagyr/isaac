---
# isaac-c52a
title: Context modes and session observers replace SessionPolicy
status: todo
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
2. **Session-observer berth** `:isaac.agent/session-observer` (session observers). Crew config `:observers [<id> …]`, overridable per session, never per turn (observers are stateful). Events: session opened, message appended, compaction spliced, turn started, turn ended. Delivery is off the turn path: a per-session ordered queue on its own thread, so each observer sees a session's events in order and sessions run in parallel. A observer failure logs and raises an attention; it never blocks or fails a turn. Observeres observe only; writing into context belongs to the context mode.
3. **Catch-up wait**: a context mode may wait at turn prep until a named observer has drained that session's queue (generic: "observer W caught up for session S").
4. **Generic `:requires` check**: a context-mode registration may declare `:requires {:observers #{…}}`. Config validation reports a crew (or session) whose context mode requires a observer it doesn't have. Agent knows no module names; the data comes from the registry.
5. **Remove SessionPolicy**: the protocol, the chronicle policy, the `:isaac.agent/session-policy` berth, `check-session-policy`, the session-policy-mismatch refusal in bridge/prompt_cli, and the `:session-policy` stamp on sessions.

## Acceptance (scenarios TBD, gated)
- A crew with `:context-mode :reset` gets reset behavior through the berth (existing reset scenarios stay green).
- A fixture context mode contributed by a test module is selectable by crew, session and `--with-context-mode`.
- A fixture observer receives open/append/compaction/turn-start/turn-end events for a session, in order.
- A observer that throws raises an attention and the turn still completes normally.
- A slow observer does not delay the turn's reply.
- Config validation reports a context mode whose required observer the crew lacks (message names both).
- Session-level `:observers` overrides the crew's.
- One-time (bean criteria, not scenarios): no `SessionPolicy`, `session-policy` or `chronicle` policy references remain in isaac-agent src.
- Handbook (agent) documents both berths and the `:requires` check.
- `bb ci` and `bb jvm-spec` green.

Likely repo scope: isaac-agent. Lands together with its follow-up (episodes port), which must re-pin to this.

## Decision + Acceptance (Micah signed off 2026-10-04; gated)
Name: session observer (berth `:isaac.agent/session-observer`, config `:observers`), not "witness". Scenarios are config-driven: the lantern fixture module (`modules/isaac.session.lantern`) contributes the `logbook` observer (writes `lantern/logbook.edn` as `{:events [...]}`; `:lantern {:logbook {:fail true}}` makes it throw) and the `porthole` context mode (soul + last assistant reply + current message; `:requires {:observers #{:logbook}}`). Its old session-policy contribution goes away.
- The 6 @wip scenarios in isaac-agent `features/session/session_observers.feature` and the 7 in `features/session/context_mode_berth.feature` pass with @wip removed.
- Test harness drains observer queues inside its existing turn await (no new steps).
- Unit spec: an observer that blocks does not delay the turn's reply.
- One-time: no `SessionPolicy`, `session-policy`, `register-factory!` policy or chronicle-policy references remain in isaac-agent src/spec; the `logbook` policy fixture and its steps are deleted.
- Retired ahead of this bean (agent c38d19d): session_policy.feature, policy_mismatch.feature, the two lantern session-policy validate scenarios (config/cli.feature), the enum "Unknown :context-mode" scenario (context_mode.feature), and the "through the session's crew policy" scenarios in resume_repair.feature and bridge/commands.feature.
- Handbook (agent) documents both berths and the `:requires` check.
- `bb ci` and `bb jvm-spec` green.

feature-baseline: isaac-agent c526ccbfde0e742c716b862943036352abab7020
feature-blob: isaac-agent features/session/session_observers.feature 67a9d4b2dc4c5f60ce50a707327bc7f30b232405
feature-blob: isaac-agent features/session/context_mode_berth.feature 899c855d0321d316fd36dcb0df7f41386e6e53f5
