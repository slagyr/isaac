---
# isaac-4615
title: 'isaac-agent: delegated and carried-over turns keep their owner (:from and :for)'
status: in-progress
type: feature
priority: normal
created_at: 2026-10-08T22:03:41Z
updated_at: 2026-10-11T22:49:07Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

Part of the contacts epic (isaac-zt1x). Scenarios approved by Micah 2026-10-11. Its blocker, turn attribution (isaac-v403), has landed.

## What this is

A turn records `:from`, who started it, and `:for`, the outside party it is done for. Today that ownership is lost in two ways. This bean closes both in `isaac-agent`.

**1. Delegation launders ownership.** A person asks one crew member, which starts a turn for another. The second turn looks like it belongs to nobody.

**2. Carry-over drops ownership.** When a turn hits its cycle limit and continues, or is resumed after a restart, the new record is written without `:from` or `:for`. Found while planning: `isaac.agent.drive.turn` (the continuation `enqueue!`) and `isaac.agent.bridge.resume` (the resume `enqueue!`) pass neither field, so the new record becomes `{:kind :submit}` for nobody.

## Rules

**Delegation.** A turn submitted from inside a running turn gets:

- `:from` = the crew member running the current turn, `{:kind :crew :id <crew>}`;
- `:for` = the running turn's `:for`, copied unchanged. Absent stays absent.

The submitter cannot override either field from inside a turn. Whatever `:from` or `:for` it passes is replaced. A tool cannot claim to act for someone else.

**Carry-over.** A continuation and a resumed turn keep the `:from` and `:for` of the record they carry on from, unchanged. They are the same work under a new record, not a delegation: `:from` is not rewritten to the crew.

## Notes for the implementer

- Apply the delegation rule at the one place every in-process submitter passes through (`isaac.agent.turn.queue/enqueue!` or `isaac.agent.turn.submit/submit!`), so no tool has to cooperate. The running turn must make its crew and its `:for` known to that point for the duration of its tool calls. How is your call; a tool-supplied argument is not acceptable, since the rule must hold against a tool that lies.
- The continuation and resume paths run inside or beside the drive. Make sure the delegation rule does not fire on them: they copy, they do not re-attribute.
- A copied `:for` or `:from` is already identified (it may be `:kind :contact`). Passing it through the identifiers again is harmless, since identifiers are only asked about `:kind :handle`.
- Fixture module `modules/isaac.relay.pigeon`, in the style of `isaac.roster.almanac`: one tool, `pigeon__send`, taking `session`, `input`, `id` and optional `for`. It calls `isaac.agent.turn.submit/submit!`. Given `for`, it also passes `:from {:kind :cli}` and `:for {:kind :contact :id <for>}`.
- Two new steps:
  - `the user sends "{content}" on session "{key}" via memory comm as handle "{comm}/{id}"` — the existing memory-comm send, with `:from {:kind :handle :comm <comm> :id <id>}` on the charge.
  - `the latest turn on session "{key}" matches:` — a key/value table of dotted paths checked against the newest turn record for that session (by `:created-at`), any state. Compare values by name, so `handle` matches `:handle` and `1` matches `1`.
- `the stdout matches:` skips its first table row as a header; the tables lead with `| pattern |`.
- Update the agent handbook's attribution section with both rules.

## Out of scope

- `isaac-hail`: `hail__send` and the hail queue carrying `:from` and `:for`, and the HTTP and CLI entry points setting `{:kind :http}` and `{:kind :cli}`. A follow-up bean once this lands.
- Foreman `:turn` actions and cron `:for`.
- Coalesced turns: they already take `:from` and `:for` from the last record.

## Likely repo scope

`isaac-agent`.

## Acceptance

Run from `isaac-agent`, with `@wip` removed from the feature file:

- `bb features features/turn/inherited_attribution.feature` — 5 examples, 0 failures.
- `bb features features/turn/attribution.feature features/turn/continuations.feature features/turn/resume_records.feature features/module/identifier_extension.feature` still green.
- `bb verify` and `bb jvm-spec` green.
- Unit specs cover: the delegation rule replacing a submitter's own `:from` and `:for`; no `:for` staying absent; a continuation and a resume copying both fields.
- The agent handbook documents delegation and carry-over.

feature-baseline: isaac-agent 4dd2c1d1ea3097361c857bd8620b041a264f81c1
feature-blob: isaac-agent features/turn/inherited_attribution.feature 7e986c9e1b52f9193be207187ece8aeecf70d5f5

## Work checkpoint (2026-10-11)

Done: claimed bean; implemented queue delegation context and carry-over copies, added targeted specs, handbook, feature step helpers, and pigeon fixture on isolated `isaac-agent-isaac-4615` worktree. RED: `bb features features/turn/inherited_attribution.feature:41` fails because pigeon `submit!` returns "no session selected"; `bb features features/turn/inherited_attribution.feature` has 3 failures, 2 passing. No green run since latest edits, so implementation is not yet committed. Next: debug pigeon request at `modules/isaac.relay.pigeon/src/isaac/relay/pigeon.clj:5`; remove temporary DEBUG-TURNS print at `spec/isaac/agent/turn/queue_steps.clj:95`, rerun focused feature, then full acceptance and gate.

## Landed on main (2026-10-11)

main-sha: isaac-agent 49e2cfe1a2b9be934e92eff8c7e4d7ea33831122

Acceptance: inherited attribution 5/5; related features 12/12; `bb verify` 1945 specs and 922 features (one pre-existing pending), 0 failures; `bb jvm-spec` 1945/1945. Bean gate PASS on branch and squash commit.
