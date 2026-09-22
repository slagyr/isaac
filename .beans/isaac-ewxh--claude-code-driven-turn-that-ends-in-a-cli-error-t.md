---
# isaac-ewxh
title: 'claude-code driven turn that ends in a CLI error tallies zero usage: 79 completed cycles lost; tally per cycle, keep sums on error'
status: in-progress
type: bug
priority: high
tags:
    - claude-code
    - tokens
    - agent
created_at: 2026-09-22T22:51:31Z
updated_at: 2026-09-22T23:17:02Z
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
