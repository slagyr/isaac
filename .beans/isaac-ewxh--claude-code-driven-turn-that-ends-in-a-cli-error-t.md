---
# isaac-ewxh
title: 'claude-code driven turn that ends in a CLI error tallies zero usage: 79 completed cycles lost; tally per cycle, keep sums on error'
status: completed
type: bug
priority: high
tags:
    - claude-code
    - tokens
    - agent
created_at: 2026-09-22T22:51:31Z
updated_at: 2026-09-22T23:45:28Z
---

## Observed (zanebot, 2026-09-22, agent 0.1.81, isaac-claude-code 34dbfa7)

The isaac-ruom turn on isaac-work-2 (hail afbfec8f, provider `:claude`, driven
mode) ran 79 tool cycles over 25 minutes, then the CLI hit the seat's session
limit (see the sibling bean). Isaac recorded:

- `:session/message-stored :tokens {:input-tokens 0, :output-tokens 0}`
- final assistant entry `:usage {:prompt-tokens 0 :output-tokens 0 :total-tokens 0}`
- session.edn tallies unchanged from the 21:38:50Z baseline (input
  1,282,869,907; cache-read 1,163,652,354; last-input 15,028)
- no per-cycle stamp events in server.log for the turn.

Compare pn98-opus-personal-2013 (20:13Z, same provider and mode, ended
cleanly): prompt 293,046 recorded. So the 79 cycles' usage existed on the CLI
stream (each assistant event carries message usage) and was dropped because
the final invocation errored.

Micah asked for this turn specifically to measure burn under reset mode + the
new hint + over-budget stamps; the measurement is lost.

## Expected

- Per-cycle usage is tallied into the session as each cycle completes
  (`:last-input-tokens`, running input/output/cache tallies), not only at turn
  end. The last successful cycle's prompt size is the gauge's stamp.
- A turn that ends in a provider error keeps the tallies of its completed
  cycles; `message-stored` (or the error entry) carries the sum of completed
  cycles, never zeros.
- `:claude/driver-exit` logs the per-turn sums (input, cache-read,
  cache-write, output, cycles) so a turn's cost is readable from the log even
  when the session file is not.

## Scenarios

- fake driven script: 3 cycles with usage, then an error result → tallies
  equal the 3 cycles' sum; stamp = cycle 3's prompt size; no zero written
  (isaac-166j rule).
- 3 clean cycles → same sums as today's end-of-turn path (no regression).

## Related

isaac-8cur (replay burst), isaac-dgod (per-request stamps), isaac-35gx (CLI
message ids + num_turns per driven turn — same log line), isaac-166j.

## Handoff (worker, 2026-09-22)

**Root cause (two halves).**

1. *Driver.* The turn's usage only ever reached the drive on the **final**
   response. When the last CLI invocation errored, the driver returned the
   error and everything the stream had already reported went in the bin.
2. *Agent.* The on-cycle `:end` hook in `drive/turn.clj` **does** call
   `stamp-provider-prompt!` per cycle, so the stamps were not missing by
   accident — they are skipped twice over: the hook's `(when-not (or (:error …)
   (:unavailable? …)))` guard drops the walled cycle, and in production every
   driven tool cycle is fired from the *replay* path, which carries
   `(zero-usage)` on purpose (isaac-8cur: the same final usage was being
   stamped once per tool call). So per-cycle stamps in a driven turn have
   always been zeros; only the final cycle's response stamped anything, and
   that is precisely the response that failed on 22:05Z. Tallies
   (`:input-tokens`, `:output-tokens`, cache) are written only by
   `store-response!`, which an error result never reaches.

**Fix.**

- isaac-claude-code: `event-usage` now treats an empty usage map as silence
  rather than zero; `stream-cycle-usages` / `sum-usages` collect the stream's
  per-cycle usage once. The weather response (isaac-2sxf) carries
  `:cycle-usages` and their summed `:usage`, and `:claude/driver-exit` logs
  `:cycles :input-tokens :cache-read-tokens :cache-write-tokens :output-tokens`
  for every driven turn, walled or clean.
- isaac-agent: new public `turn/keep-cycle-usage!`, called from `run-turn!` on
  the raw loop result **before** `provider-wall/normalize` reshapes it (the
  wall classifier replaces the map and would drop the usage). It folds the
  sums into `:input-tokens` / `:output-tokens` / `:cache-read` / `:cache-write`
  / `:turn-input-tokens` and stamps `:last-input-tokens` with the last cycle
  that reported a prompt size. An error that measured nothing writes nothing,
  and a zero cycle never overwrites a trusted stamp (isaac-166j). A clean turn
  is untouched — `store-response!` still owns that tally.

**Files.**
- isaac-claude-code, branch `bean/isaac-ewxh` (from `bean/isaac-2sxf`), commit
  **af15c42**: `src/isaac/llm/api/claude_cli.clj`,
  `spec/isaac/llm/claude_driver_spec.clj` (+2 specs),
  `features/llm/api/claude_driver.feature` (+2 scenarios).
- isaac-agent, branch `bean/isaac-ewxh-agent`, commit **52f29f7**:
  `src/isaac/drive/turn.clj`, `spec/isaac/drive/turn_spec.clj` (+4 specs).

**Scenarios added.**
- claude-code feature `a turn walled on its last request keeps the usage of the
  cycles that finished` — 3 cycles (260/320/370 prompt) then a session-limit
  result: turn "suspended", session `last-input-tokens 370`,
  `turn-input-tokens 950`, and `:claude/driver-exit` with cycles 3, input 760,
  cache-read 180, cache-write 10, output 21.
- claude-code feature `three clean cycles tally the sums they always did`
  (no regression: 370 / 950, reply "all done").
- claude-code specs: the weather response's `:cycle-usages` + summed `:usage`
  and the exit-log sums; the same sums on a clean driven turn.
- agent specs (`keep-cycle-usage!`): keeps the sums and stamps cycle 3; leaves
  the tally alone when the error reports no usage; keeps sums but never stamps
  a zero; ignores a clean turn.

**Test commands + counts.**
- isaac-agent (`isaac-agent-isaac-ewxh`): `bb ci` -> `bb spec` 1715 examples,
  0 failures; `bb features` 848 examples, 0 failures, 1 pending (pre-existing).
- isaac-claude-code (`isaac-claude-code-isaac-2sxf`, branch `bean/isaac-ewxh`):
  `bb spec` -> 90 examples, 0 failures, 3 pending.
  `clojure -Sdeps '{:aliases {:agent-local {:override-deps {io.github.slagyr/isaac-agent {:local/root "../isaac-agent-isaac-ewxh"} io.github.slagyr/isaac-agent-spec {:local/root "../isaac-agent-isaac-ewxh/spec"}}}}}' -M:agent-local:features`
  -> 57 examples, **1 failure** — and that failure is not this bean's (below).

**Cross-repo notes — read before landing.**
- `bb features` / `bb ci` in isaac-claude-code still run against the **pinned**
  agent sha `fd89226`, where `keep-cycle-usage!` does not exist, so the walled
  scenario fails there ("last-input-tokens expected 370, got 0"). It is green
  the moment the agent lands and the claude-code pin moves. **The pin was not
  bumped** and `deps.edn` `:dev-local` still points at `../isaac-agent`; the
  agent branch must land first, then repin isaac-claude-code.
- Pre-existing, unrelated: run against *any* current agent main (with or
  without my change) the scenario `replayed tool cycles do not each stamp the
  turn's prompt size (isaac-8cur)` fails — it expects the prompt stamp clamped
  to 200000 and 2 `:session/stamp-implausible` entries, but isaac-dgod (agent
  `1afd3dc`) made an over-window prompt stamp as it stands. That scenario needs
  updating when the agent pin moves; I left it alone.
- Not done: live per-cycle stamping in production. It is blocked by isaac-8cur,
  which zeroes the replayed cycles' usage on purpose. Now that the driver
  parses real `:cycle-usages`, the replay path *could* stamp each cycle with
  its own usage instead of zero — that is a separate bean, not this one.

Both branches pushed; beans left `in-progress`, untagged.

## Landed on main

main-sha: isaac-agent 52f29f7
main-sha: isaac-claude-code af15c42 (main tip 94a3bd6, repin commit included)

Planner check 2026-09-22: agent `bb spec` 1715/0, `bb features` 848/0 (1 pending, pre-existing); claude-code suites green against the repinned agent. The isaac-8cur scenario in claude_driver.feature now expects the over-window stamp recorded (802832) and no implausible warning, per isaac-dgod. Follow-up not in scope: live per-cycle stamping in production is still blocked by isaac-8cur zeroing replayed cycles; with real :cycle-usages now on the response, a separate bean can stamp each cycle. Not deployed.
