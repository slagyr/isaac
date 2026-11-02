---
# isaac-cgz9
title: 'Mid-turn input: messages that arrive during a running turn join it at the next cycle'
status: in-progress
type: feature
priority: high
tags:
    - agent
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-02T13:54:51Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (turn queue + tool loop).

## Why

Micah, 2026-11-01: when a turn is running and new input arrives for the same session, it should join the running turn, not wait behind it. The model asks "What's your favorite color?", the person says "Red", then a second later "Wait no, blue": the reply should account for blue. The same mechanism carries subagent results into a busy parent (isaac-p5d3). New input informs the turn; it does not take it over.

## Design

- **Cycle boundary.** `llm/tool_loop.clj` already has an `after-tools` hook between a tool batch and the next model call. There, take every request waiting on this session (`queue/waiting-groups`, state `:waiting-session`) and append each as a user message.
- **Labelled by source.** Each absorbed message says where it came from: a person's message ("new message from <name>") or a subagent result (handle + stats). The framing tells the model this is new information for the current task.
- **Absorbed, not rerun.** The absorbed queue records are marked absorbed (new state) so they never run as their own turn.
- **Final-cycle arrivals.** If input arrives during the model call that would end the turn (no tool calls), run one more cycle with the input appended instead of ending. On a streaming comm the first reply may already be out; it stays, and the extra cycle corrects it.
- **Person first.** When a session is idle and several requests wait, subagent results (`origin.kind :subagent`) run after all other waiting input.
- **Two modes (Micah, 2026-11-01).** Crew key `:mid-turn-input`: `:join` (default, the above) or `:queue` (today's behavior: wait, run next, coalesce by key). Set on a crew or `defaults.crew`. Schema entry carries a `:description`. Asking the person "process now?" (Grok's style) is left to comm UIs; interrupt stays cancel + send. Subagent results always join.
- **Label.** The "arrived during this turn" label rides only in the model request (like origin framing); the transcript keeps the plain user text.

## Out of scope

- The Claude-code lane (the Claude CLI drives its own tool loop) — follow-up bean.

## Acceptance

Scenarios in `isaac-agent/features/session/waiting.feature` (all `@wip`; remove `@wip` as each passes):

- `bb features features/session/waiting.feature:23` — queue mode: waits and runs next (existing scenario, now pinned to `:queue`)
- `bb features features/session/waiting.feature:50` — queue mode: same coalesce key runs as one turn (existing, pinned to `:queue`)
- `bb features features/session/waiting.feature:98` — a message between tool cycles joins the running turn, labelled, record `absorbed`, `:turn/absorbed` logged
- `bb features features/session/waiting.feature:130` — a message during the final model call gets one more cycle (red → blue)
- `bb features features/session/waiting.feature:154` — on an idle session a subagent result runs after other waiting input
- `bb features features/session/waiting.feature:185` — a cancelled turn absorbs nothing; the waiting message runs as its own turn

Run each with `-t wip` until `@wip` is removed, or the whole file: `bb features features/session/waiting.feature`.

Also: update the isaac-agent handbook chapter (`resources/isaac/agent/handbook.md`) with `:mid-turn-input` — what the two modes do and how to set it.

## Work checkpoint (2026-11-01)

Done: joining mid-turn input at tool/final cycle, plain transcript/absorbed state, person-first idle dispatch, schema and handbook on `isaac-agent` branch `bean/isaac-cgz9` (694272c). `bb features features/session/waiting.feature` green (7/7), `bb spec -F spec/isaac/agent/llm/tool_loop_spec.clj` green (25/25), `bb bean-gate verify isaac-cgz9 --dir isaac-agent=../isaac-agent-cgz9` PASS.

## Conflict (2026-11-01)

`bb ci` RED: `features/turn/turn_store.feature:130` existing unbaselined scenario "merged waiting requests keep their own ids and share the turn's outcome" assumes *default* waiting requests with coalesce-key `t1` merge after the first turn; this directly contradicts this bean's approved default `:join` which absorbs them immediately and never runs a separate turn. Exact resume: update the old scenario at `features/turn/turn_store.feature:112` to opt into `:queue`, then rebaseline it as planner; worker cannot edit `.feature` except strip `@wip`. The other CI failure (`features/session/tool_loop.feature:24`) was repaired in acceptance helper; focused rerun green. Waiting on planner correction before landing. Gate output: `isaac-cgz9 bean-gate: PASS (isaac-agent @ HEAD 197d213 (branch bean/isaac-cgz9))`.

feature-baseline: isaac-agent 231dca7f35827639e2d5e522afe32af6999cceda
feature-blob: isaac-agent features/session/waiting.feature a09520983a9e04ace5947a30d70224a2c2f74fcd

feature-baseline: isaac-agent 0e6e17717f6097fcd3bfc1f92fba62ede4c4ddba
feature-blob: isaac-agent features/session/waiting.feature a09520983a9e04ace5947a30d70224a2c2f74fcd

## Planner correction (2026-11-01, Micah via plan-isaac-cgz9)

Decision: the legacy turn-store scenario stays, pinned to :queue. Default :join absorbs a same-key waiting request into the running turn, so the old "merged waiting requests keep their own ids" outcome no longer holds without an opt-in. On isaac-agent main (0e6e177) that scenario now sets defaults.crew.mid-turn-input to :queue and is renamed "in queue mode merged waiting requests keep their own ids and share the turn outcome (isaac-cgz9)". It is not @wip: coalescing under :queue already passes on bean/isaac-cgz9, and the gate refuses a baseline line for a scenario that is not @wip, so it is not part of this bean feature-blob. The re-baseline below re-freezes features/session/waiting.feature at the same blob, now against origin/main 0e6e177, which also carries the turn_store pin.

Resume: rebase bean/isaac-cgz9 onto origin/main (the turn_store edit is the only main commit ahead), re-run bb ci, then land. Do not edit the turn_store scenario further.


## Landed on main (2026-11-02)

main-sha: isaac-agent 3964150b7e04a1efd07a2de1aa7a4a489d841dbe

Rebased onto the planner correction (legacy turn-store scenario opts into :queue). bb ci: 1983 specs and 951 features green, one unrelated pending; bean gate PASS on the squash commit.
