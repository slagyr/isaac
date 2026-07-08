---
# isaac-o14c
title: ACP session load replays only the active transcript (post-compaction head)
status: in-progress
type: feature
priority: normal
created_at: 2026-07-08T20:32:13Z
updated_at: 2026-07-08T20:36:18Z
parent: isaac-zt4h
tags:
  - unverified
---

## Goal

ACP session load replays only the **active transcript** (post-compaction head), not the full session history. Loading a long-lived session in Toad currently streams every entry since session birth — megabytes of `session/update` notifications for old crew sessions — because replay reads the wrong store view.

## The change (small and precise)

`isaac-acp/src/isaac/comm/acp/server.clj` — `attach-session-result!` calls `store/get-transcript` (whole session file). Switch to `store/active-transcript` (reads from `:effective-history-offset` when the session has compacted; falls back to the full file when it hasn't — see `isaac-agent/src/isaac/session/store/impl_common.clj:517-525`). This covers both `session/load` and the pre-bound `--session` attach path (isaac-d84z), which share `attach-session-result!`.

## Care points

- `tool-results-by-id` in the replay is built from the same transcript passed in; with a truncated view, entries referencing pre-offset tool ids must not blow up — replay them as toolCalls without results (or skip), never crash the load.
- The compaction summary entry at the offset boundary should replay (it's the context the user needs to make sense of the head).
- Sessions with no compaction offset must replay exactly as today.

## Scenarios (approved by Micah, 2026-07-08)

Add to `features/comm/acp/session.feature`:

```gherkin
Scenario: session/load replays only history from the effective offset
  Given the following sessions exist:
    | name        |
    | resume-test |
  And session "resume-test" has transcript:
    | type       | message.role | message.content | summary                 |
    | message    | user         | old question    |                         |
    | message    | assistant    | old answer      |                         |
    | compaction |              |                 | Earlier we discussed X. |
    | message    | user         | what next?      |                         |
  When the ACP client sends request 5:
    | key              | value        |
    | method           | session/load |
    | params.sessionId | resume-test  |
  Then the ACP agent sends notifications:
    | method         | params.update.sessionUpdate | params.update.content.text |
    | session/update | agent_message_chunk         | Earlier we discussed X.    |
    | session/update | user_message_chunk          | what next?                 |
```

```gherkin
Scenario: session/load tolerates a head beginning with an orphaned tool result
  Given the following sessions exist:
    | name        |
    | resume-test |
  And session "resume-test" has transcript:
    | type       | id   | message.role | message.content | name | arguments     | summary            |
    | toolCall   | tc-1 |              |                 | grep | {"q":"error"} |                    |
    | compaction |      |              |                 |      |               | Earlier: log hunt. |
    | toolResult | tc-1 |              | 3 matches       |      |               |                    |
    | message    |      | assistant    | found 3 errors  |      |               |                    |
  When the ACP client sends request 7:
    | key              | value        |
    | method           | session/load |
    | params.sessionId | resume-test  |
  Then the ACP agent sends notifications:
    | method         | params.update.sessionUpdate | params.update.content.text |
    | session/update | agent_message_chunk         | Earlier: log hunt.         |
    | session/update | agent_message_chunk         | found 3 errors             |
```

Full-replay regression guard: the existing scenario "session/load replays the
transcript as session/update notifications" (session.feature:42, no compaction)
must stay green — `active-transcript` falls back to the whole file when no
offset exists. Do not duplicate it.

Step amendments both scenarios depend on:

1. **The transcript fixture step's `compaction` row must ALSO set the session's
   `:effective-history-offset`** (production-faithful: a compaction entry
   without the offset is an unrepresentable state). The existing scenario
   "session/load replays the compaction summary in place of pre-compaction
   history" (session.feature:63) must stay green under this change — it then
   finally tests what its title claims. If the fixture change is impractical,
   retitle that scenario honestly instead.
2. **The "Then the ACP agent sends notifications:" step must assert the
   COMPLETE ordered notification set**, not a subset — otherwise pre-offset
   leakage and the orphan's silence cannot fail any scenario. Verify, and
   amend if it subset-matches.

## Acceptance

- [x] Scenarios above green in isaac-acp features
- [x] Existing d84z replay scenarios still green (attach path shares the fix)
- [ ] One-time: load a large compacted zanebot session in Toad — load time drops from full-history replay to head-only

## Resolution

- `attach-session-result!` now uses `store/active-transcript` (shared by `session/load` and `--session` attach).
- Gherkin: `features/comm/acp/session.feature` — compaction splice active-head replay, full replay unchanged, tool result before offset replays assistant only.
- Unit: `server_spec.clj` — memory-store offset replay cases.
- Verified: `bb features features/comm/acp/session.feature` green; `bb config-bypass-lint` ok.
- Implementation: `isaac-acp` branch `bean/isaac-o14c` commit `38bd92b`.