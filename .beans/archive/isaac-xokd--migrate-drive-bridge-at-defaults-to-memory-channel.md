---
# isaac-xokd
title: "Migrate drive/bridge AT defaults to memory channel + structured event assertions"
status: completed
type: task
priority: low
created_at: 2026-04-29T00:30:53Z
updated_at: 2026-04-29T17:45:03Z
---

## Description

After the domain-events Comm refactor (isaac-nyuv), drive/bridge
acceptance scenarios should drive turns through the memory channel
by default and assert on structured events rather than rendered text.

## Current state

- 101 scenarios use bare 'the user sends "X" on session "Y"' which
  defaults to cli-comm/channel — a side-effect path that prints to
  stdout/stderr.
- Only 4 scenarios opt into 'via memory channel'; only 3 assert on
  events.
- Symptom: hooks scenarios bled "Hieronymus's emergency lettuce"
  into bb features stdout because the cli-comm default kicked in
  for non-CLI dispatch (fixed via null-comm, but the bigger
  problem persisted).

## Proposed change

1. Switch the default channel for the 'the user sends' step to
   memory channel. Capture events on g/state for downstream
   assertions.
2. Audit existing scenarios that incidentally relied on cli-comm
   stdout/stderr; update those to assert on the appropriate channel.
3. Encourage new compaction/turn-lifecycle scenarios to assert on
   memory channel events instead of grepping :session/* log entries.
4. Keep 'via memory channel' as an alias that maps to the new
   default, OR retire it.

## Why

- cli-comm side-effects are unsafe for parallel test runs.
- Comm impls are the contract surface; tests should hit them, not
  bypass them.
- Domain events post-refactor (compaction-start/success/failure/
  disabled, tool-call/result) are richer than text strings —
  asserting on them catches more regressions with less brittle
  patterns.

## Depends on

- isaac-nyuv: domain events must exist for tests to assert on them.

## Definition of done

- Default channel for 'the user sends' is memory channel.
- bb features still green; updated scenarios assert on events
  where they previously asserted on rendered text inadvertently.
- A representative sample of compaction scenarios (3-5) migrated
  from log-grep to memory-event assertions as exemplars.
- 'via memory channel' phrasing either retired or kept as a
  no-op alias with a deprecation note.

## Out of scope

- ACP/Discord/CLI-specific behavior tests (those should still
  drive through their own comm impls; only the default for
  generic engine tests changes).

## Notes

Split bridge/tool_visibility into a pure memory-comm bridge suite and moved concrete-comm coverage into narrow ACP, CLI prompt, and Discord routing smoke scenarios. Added positive and zero-tools cases for each comm path, and taught the ACP request step to capture :llm-request so shared prompt-tools assertions can inspect the real built request. Verified with targeted feature selectors plus full bb spec and bb features.

