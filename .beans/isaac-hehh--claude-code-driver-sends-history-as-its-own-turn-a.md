---
# isaac-hehh
title: Claude Code driver sends history as its own turn and counts each request's usage twice
status: todo
type: bug
priority: high
created_at: 2026-11-17T00:21:18Z
updated_at: 2026-11-17T00:34:42Z
---

Found by Zane Lab on 2026-11-16 (experiment `claude-provider-fix`), with a
patch proven in a scratch clone. Repo: `isaac-claude-code`, file
`src/isaac/provider/claude_code/api/claude_cli.clj`.

## What is wrong

1. **History is sent as its own turn.** In driven mode `conversation->stream-json`
   writes two stdin envelopes: the prior conversation as prose, then the live
   prompt. Claude Code runs one model turn per envelope, so every turn on a
   session with history makes a hidden extra model request whose reply Isaac
   throws away. It costs a whole request, and the old instructions in the
   history read as live ones: Haiku once re-ran an earlier turn's `fs__read`
   instead of answering the live prompt.
2. **One request's usage is counted two or more times.** `stream-cycle-usages`
   counts every non-result event that carries usage. With
   `--include-partial-messages` one API request reports usage on its
   `assistant` event (once per content block) and again on the
   `stream_event` `message_delta`. A one-request turn logs `:cycles 2`; a turn
   with history logs 4–5. `--usage`, `:turn/ended :requests` and session
   token tallies all run about 2× high.

Sandbox evidence (live config copy, Haiku, 5 turns each arm): Claude Code
`result` events per turn 2 → 1; reported requests 4–5 → 1; real prompt tokens
about halved; smoke replay 4/4 in both arms.

## Fix

- **One envelope per turn.** With history, the single user message carries the
  history first, inside `<conversation_history>…</conversation_history>` with a
  one-line preamble saying it is the conversation so far and is context, then
  the live prompt. A first turn stays a plain string.
- **Usage grouped per API message.** `assistant` and `message_start` events
  name `message.id`; a `message_delta` belongs to the `message_start` before
  it. Each field takes the largest value in its group (the figures are
  cumulative). Events with no id still count on their own, so existing
  scripted cycles are unchanged.

## Reference patch

Zane's patch (+129/−39 across the source, `claude_driver_spec.clj` and
`claude_driver.feature`) is on zanebot at
`/Users/zane/agents/zane-lab/experiments/claude-provider-fix/fix.patch`, with
`BEAN.md`, `check.sh` and `evidence.edn` beside it, against sha `836ee0da`.
Use it as a starting point; you own the result. `main` has moved since
(isaac-y3wm), so rebase it by hand.

## Acceptance

- The `@wip` scenarios baselined below pass with `@wip` removed.
- Three existing scenarios in `claude_driver.feature` assert two stdin
  envelopes (the reasoning-history one near line 90, "prior turns are replayed
  as text history before the live prompt", and the stream-json envelope one
  near line 355). They encode bug 1. Update them to the one-envelope shape;
  that is a behaviour change this bean makes on purpose.
- New fake capabilities the scenarios need: script kind `delta_usage` (the
  same request's usage repeated on a `stream_event` `message_delta`; the
  cycle's `usage` event and its `delta_usage` share one message id), and the
  step `the fake Claude Code received {n} stdin lines`.
- Capture one raw stream from the real CLI (`ISAAC_CLAUDE_REAL=1`) and confirm
  in the bean notes that `message_delta` usage is cumulative, field by field.
  If it is not, stop and say so.
- `bb ci` and `bb jvm-spec` (if the repo has it) pass. Do not bump the module
  version; that happens at deploy.

feature-baseline: isaac-claude-code 4de0cd84fe7d53717ed209a652d0872a6bdcd1e7
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 71230245efb580b8e7df36162ca232614933fca9 750,769,780

## Worker conflict (2026-11-16, scrapper@bean-isaac-hehh)

Implementation on `isaac-claude-code` branch `bean/isaac-hehh` at `eaded99`:
one stdin envelope with history block, per-message cumulative usage maxima, fake
`delta_usage` and stdin-line assertion. `bb ci` passes (88 specs, 70 features),
`bb jvm-spec` passes (67 examples). The baselined three @wip scenarios run.

Gate exit 1: acceptance explicitly requires updating three older scenarios, but
those same scenarios are frozen by the feature baseline. Planner must update
those scenarios on module main and append a new feature baseline before landing.
Gate output:

```
isaac-hehh bean-gate: FAIL (4) — isaac-claude-code @ HEAD eaded99 (branch bean/isaac-hehh)
  FAIL isaac-claude-code features/llm/api/claude_driver.feature: baselined block "Scenario: text and thinking deltas reach the comm in order; thinking persists as reckoning and stays out of the next prompt" (baseline line 79) was changed
      - | type | message.role | message.content                   |
      - | user | user         | #"(?s).*think then answer.*here is my answer.*" |
      - | user | user         | and again                         |
      + | type | message.role | message.content |
      + | user | user         | #"(?s).*think then answer.*here is my answer.*</conversation_history>.*and again" |
  FAIL isaac-claude-code features/llm/api/claude_driver.feature: baselined block "Scenario: prior turns are replayed as text history before the live prompt" (baseline line 130) is missing
  FAIL isaac-claude-code features/llm/api/claude_driver.feature: baselined block "Scenario: stdin carries stream-json user envelopes, never bare role/content lines (isaac-6z4r)" (baseline line 354) was changed
      - | type | message.role | message.content                   |
      - | user | user         | #"(?s).*one.*first.*"             |
      - | user | user         | two                               |
      + | type | message.role | message.content |
      + | user | user         | #"(?s).*one.*first.*two.*" |
  FAIL isaac-claude-code features/llm/api/claude_driver.feature: worker diff 4de0cd8..eaded99 changes the feature beyond @wip removal
      -       | type | message.role | message.content                   |
      -       | user | user         | #"(?s).*think then answer.*here is my answer.*" |
      -       | user | user         | and again                         |
      +       | type | message.role | message.content |
      +       | user | user         | #"(?s).*think then answer.*here is my answer.*</conversation_history>.*and again" |
```

Real CLI raw capture attempted with `--include-partial-messages` and
`--input-format stream-json` (Claude Code 2.1.316); CLI returned exit 1,
`authentication_failed: Failed to authenticate: OAuth session expired and could
not be refreshed`; `claude auth status` reports `loggedIn: false`. Captured
stream only has zero usage on the auth failure. Cannot confirm cumulative
`message_delta` usage field by field until real CLI login is restored; do not
land without this acceptance evidence. Reference sandbox reports cumulative
usage but did not capture raw stream field by field.

feature-baseline: isaac-claude-code 09ce4914a4808f17d4daf6934db06f144760692d
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature bc5d84804f600f0333eb6ba02e2f969847618a77 80,131,355,750,769,780

## Planner decision (2026-11-16, for isaac-hehh)

1. **The three older scenarios.** You were right: they were frozen. I put your
   exact edits to them on module main (`isaac-claude-code` plan commit after
   `4de0cd8`), tagged `@wip`, and re-baselined. The gate now covers six
   scenarios: lines 80, 131, 355, 750, 769, 780. Rebase `bean/isaac-hehh` on
   main; your feature diff should then be the six `@wip` removals and nothing
   else.
2. **The real-CLI capture is done; do not wait for a login.** I captured a raw
   stream myself (Claude Code, Haiku, `--output-format stream-json
   --include-partial-messages --verbose`, one request):

   | event | message id | input | cache write | cache read | output |
   |---|---|---|---|---|---|
   | `stream_event` `message_start` | …9WFa | 2 | 40811 | 0 | 8 |
   | `assistant` (text) | …9WFa | 2 | 40811 | 0 | 8 |
   | `stream_event` `message_delta` | (none) | 2 | 40811 | 0 | 61 |
   | `result` | | 2 | 40811 | 0 | 61 |

   Usage is cumulative within a request: every event repeats the input
   figures, and `message_delta` carries the final output total, which equals
   the result. Taking the largest value of each field per request is correct.
   That acceptance item is satisfied by this note. Land when the gate passes.
