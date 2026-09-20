---
# isaac-ddls
title: 'isaac-claude-code: every turn logs :chat/provider-contract-violated {:reasoning {:summary "is required"}} — noise at :error; fails the episodes seal on yopp'
status: completed
type: bug
priority: high
tags:
    - claude-code
    - agent
created_at: 2026-09-19T23:48:52Z
updated_at: 2026-09-20T06:53:43Z
---

Seen on yopp all day 2026-09-19 (agent fd89226, claude-code f058b2c): each turn emits :chat/provider-contract-violated :errors {:reasoning {:summary "is required"}} + :chat/stream-error :error :provider-contract, yet the turn completes and replies. The episodes seal for crew yopp fails with :provider-error, consecutive 8+, so no scene is ever sealed on yopp. Some event the claude CLI streams (a reasoning/thinking block) is missing :summary under the agent's provider contract. Find which event, make the contract accept a summary-less reasoning block (or synthesize one), and stop logging a completed turn at :error. Scenario: a claude-cli stream with a reasoning block without summary produces a clean turn and no contract error; the episodes seal succeeds on such a session.



## Implementation (2026-09-20, scrapper@isaac-work-1)

**Which event.** The claude CLI's `content_block_delta` / `thinking_delta`
events. `claude-cli/stream-json-delta-thinking` collects `[:delta :thinking]`
from every one of them, including deltas whose text is `""` or whitespace.
Both assembly paths then attached a reasoning block anyway:

- `stream-once` guarded on `(seq reasoning)` — the delta **vector** — so a
  single `""` delta produced `:reasoning {:summary ""}`.
- `parse-stream-json-response` guarded on `(seq think)` — true for `"\n"` —
  so whitespace-only thinking produced `:reasoning {:summary "\n"}`.

`c3kit.apron.schema/required` treats a blank string as absent, so
`api/validate-response` returned `{:reasoning {:summary "is required"}}`.
`dispatch/validate-provider-result` then **replaced the completed response**
with `{:error :provider-contract}`, logged `:chat/provider-contract-violated`
at `:error` plus `:chat/stream-error`, wrote an `error` entry to the
transcript, and the episodes segment recorded `:provider-error` — which is why
no scene ever sealed on yopp.

**Fix (two repos, each independently green — no pin ordering).**

1. `isaac-agent` — `isaac.llm.api.protocol/reasoning`: `:summary` is no longer
   `schema/required`. A reasoning summary is metadata, never load-bearing; a
   non-string summary is still rejected. This alone clears the whole chain:
   no contract error, no `:error` log on a completed turn, no transcript
   error entry, and the episodes seal succeeds.
2. `isaac-claude-code` — `claude_cli/stream-once` and
   `parse-stream-json-response` both guard on `(str/blank? think)` of the
   **joined** thinking text, so a blank reasoning block is never emitted in
   the first place (and no blank `reckoning` reaches the comm).

**Branches**

- `isaac-agent`: branch `bean/isaac-ddls` @ `a4f5327e1778a1e2efc46aefd64c4432fbaba2c7` (base `origin/main@20660e63`)
- `isaac-claude-code`: branch `bean/isaac-ddls` @ `be722c570109ae91835d7cea708bb64627e06ef0` (base `origin/main@4d5d4f3d`)

**Tests (all RED before the fix, GREEN after)**

- `isaac-agent` `spec/isaac/llm/api_spec.clj`: reasoning summary absent /
  `""` / `"\n"` / present all validate; a non-string summary still reports
  `must be a string`.
- `isaac-agent` `features/llm/api/response_schema.feature:93` — "a reasoning
  block with no summary rides along and the turn stays clean (isaac-ddls)".
  Verified RED on a stashed fix: transcript row was `type: error`, not
  `message`.
- `isaac-claude-code` `spec/isaac/llm/claude_cli_spec.clj`: three `it`s —
  blank thinking deltas on the stream path yield no `:reasoning`; a driven
  turn with whitespace thinking yields no `:reasoning`; thinking with text
  still yields `{:summary "weighing it"}`. Verified RED on a stashed fix:
  `got: {:summary "\n   "}` and `got: {:summary "\n  "}`.
- `isaac-claude-code` `features/llm/api/claude_driver.feature:101` — driven
  turn with an empty thinking delta stays clean, no
  `:chat/provider-contract-violated`. This one is a characterization guard on
  the driven path, not a RED→GREEN reproducer: a Gherkin table cell cannot
  express a whitespace-only payload (cells are trimmed), so the whitespace
  reproducer lives in the unit spec above.

**Suites**

- `isaac-agent` `bb ci`: 1654 examples / 0 failures / 3413 assertions, then
  832 examples / 0 failures / 1977 assertions / 1 pending (pre-existing
  `compaction_mid_turn` pending).
- `isaac-claude-code` `bb ci`: 83 examples / 0 failures / 255 assertions /
  3 pending (pre-existing), then 51 examples / 0 failures / 168 assertions.

**Design decision — "stop logging a completed turn at :error".** Satisfied by
removing the cause, not by muting `dispatch/contract-error`. After (1) and (2)
a completed claude-cli turn no longer violates the contract, so nothing is
logged at `:error`. Deliberately swallowing contract violations on completed
turns was rejected: the same gate is what catches real adapter bugs (a
`{:model "is required"}` violation from the chatgpt adapter is in the current
server log). If the planner wants that mute anyway, it should be its own bean.

## Exceptions

- `isaac-agent` `features/llm/api/response_schema.feature` — one **added**
  scenario, "a reasoning block with no summary rides along and the turn stays
  clean (isaac-ddls)", at line 93. No existing scenario was reworded,
  weakened, or removed.
- `isaac-claude-code` `features/llm/api/claude_driver.feature` — one **added**
  scenario, "thinking deltas with no text leave no reasoning block and the
  turn stays clean (isaac-ddls)", at line 101. No existing scenario was
  reworded, weakened, or removed.

The bean carried no planner-approved feature file and no `feature-baseline`
line, so `bb bean-gate verify isaac-ddls` exits 2 (not gated); both feature
edits are additive and named above.

## Landed on main (planner verify, 2026-09-20)

Verified and landed by the planner: the zanebot fleet's claude OAuth expired
mid-train, so every worker and verify turn was returning
`:empty-terminal-response`.

Reviewed both diffs, ran both suites from clean worktrees:

| repo | suite | result |
| --- | --- | --- |
| isaac-agent | `bb spec` / `bb features` | 1654 / 0, 832 / 0 (1 pre-existing pending) |
| isaac-claude-code | `bb spec` / `bb features` | 83 / 0 (3 pre-existing pending), 51 / 0 |

Fixed at both ends, as the worker proposed: the contract stops requiring
`:reasoning :summary` (a non-string summary is still rejected), and claude-cli
stops building a reasoning block out of whitespace-only thinking. The
`dispatch/contract-error` log was deliberately NOT muted — the same gate
catches real adapter bugs.

main-sha: isaac-agent 3bebe674afb8d1cceb31b4af692c40486e6a3685
main-sha: isaac-claude-code d61350eaa5ad81b2bf78232e8d1c841f05fb678b (version bump 41eb6dc5, 0.1.15)

Both `bean/isaac-ddls` branches squash-landed and deleted; trees equalled main.

Two notes for the planner: the agent scenario asserts the absence of a
`:chat/provider-contract-violated` log entry — read as a behavioural invariant
(a clean turn logs no violation) rather than a removal check. And
isaac-claude-code still pins isaac-agent at fd892263, so its suite ran against
the OLD contract; the fix is complete on either pin, but the repin belongs to
the next pin cadence (isaac-j4jr).
