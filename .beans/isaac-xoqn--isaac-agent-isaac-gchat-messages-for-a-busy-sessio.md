---
# isaac-xoqn
title: 'isaac-agent + isaac-gchat: messages for a busy session wait instead of being refused, and prompts in one thread consolidate into one reply'
status: completed
type: feature
priority: high
created_at: 2026-09-25T15:56:18Z
updated_at: 2026-09-28T00:08:46Z
---

## Why (Micah, 2026-09-25)

"Are chat messages queued? So that if I type lots of messages in the DM,
Skiff will reply to them all? Each thread can be consolidated such that if
multiple prompts appear in a single thread, they can all be addressed in one
response."

Today they are not queued. `isaac.bridge.core` refuses a dispatch whose
session is already in flight (`:dispatch/refused :session-in-flight`) and
gchat ignores the refusal: the message is appended to the space transcript
(so the NEXT mention sees it as history) but gets no reply of its own. Two
quick DMs → one answer.

## Design (drive generic; comms provide the grouping key)

- **Waiting room.** A request for a session that is in flight is not refused:
  the bridge parks it in the turn queue (`turns/held`, state
  `:waiting-session`, ordered by arrival) and returns
  `{:dispatched? false :reason :waiting-session :held-id …}`.
- **Drain on turn end.** When a session's turn ends, the drive drains that
  session's waiting requests: requests sharing the same `:coalesce-key` are
  **merged into one turn** whose `:input` is their inputs joined with a
  newline in arrival order and whose `:origin` is the last one's; different
  keys run as separate turns in arrival order. No key → never merged.
- **Comms set the key.** gchat: the thread (`spaces/…/threads/…`), so
  several prompts in one thread get one consolidated reply and prompts in
  two threads get two. gmail: the thread id. Hail/CLI/ACP: none (hail
  already defers on its own; a CLI prompt waits as today).
- gchat guidance gains: "Several messages in a thread may arrive together;
  answer them as one reply."
- Observability: `:turn/waiting` (info: session, held-id, key) when parked,
  `:turn/coalesced` (info: session, key, count) when merged.

## Acceptance (baselined: agent + gchat)

- [ ] Agent scenario "a message that arrives while the session's turn runs
  waits and runs next (isaac-xoqn)": with the LLM response delayed, a second
  message on the same session is not refused; after the first turn ends the
  second runs; both replies are in the transcript in order.
- [ ] Agent scenario "waiting messages with the same coalesce key run as one
  turn (isaac-xoqn)": two waiting requests with key "t1" become one turn whose
  user input holds both lines; `:turn/coalesced :count 2` logged.
- [ ] gchat scenario "three quick messages in one DM thread get one
  consolidated reply (isaac-xoqn)": session in flight, three events in one
  thread, the in-flight turn ends → one turn, one post, its input carries
  all three lines.
- [ ] Spec: different keys → separate turns in arrival order; no key → no
  merge. Existing suspend/cancel/turnstile scenarios green.
- [ ] Version bumps; gchat pins the agent sha; bb spec / features / lint green
  in both repos.

Likely repo scope: isaac-agent (bridge/core.clj, turn/queue.clj, drive turn
end hook, features/session/waiting.feature, steps) and isaac-gchat
(handler.clj dispatch request `:coalesce-key`, guidance.clj, inbound.feature).

feature-baseline: isaac-agent 8794de90f8493b31911318e508741c1e7ef281af
feature-baseline: isaac-gchat 0e77c04a19d5350ca067a6a0fb8a8dc308662880
feature-blob: isaac-agent features/session/waiting.feature 2982c9383a0f89ef3c1e43c19e3a6e971634a96f 11,34
feature-blob: isaac-gchat features/comm/gchat/inbound.feature 7124ba35044061f10352d982d8444cfacbf708cd 702

## Checkpoint (2026-09-25)

Implemented and pushed initial waiting-room/coalescing branches:
- `isaac-agent` `bean/isaac-xoqn` at `861c8ba`: dispatches an in-flight session to `turns/held` with `:waiting-session`, logs `:turn/waiting`, groups same non-nil keys, joins inputs, retains final origin, and drains from turn completion. Agent unit specs green (`98 examples, 0 failures, 207 assertions`).
- `isaac-gchat` `bean/isaac-xoqn` at `0431ad2`: supplies `:coalesce-key` from the Chat thread and adds thread-consolidation guidance; focused specs green (`21 examples, 0 failures, 34 assertions`).

Next: complete the feature-step integration. `bb features features/session/waiting.feature` currently fails at `features/session/waiting.feature:28,51`: the second/third harness sends do not consistently reach bridge while the first Grover-delayed turn owns in-flight state, so `:turn/waiting` / `:turn/coalesced` are absent. The gchat scenario at `features/comm/gchat/inbound.feature:702` remains pending because its cross-module step implementation is absent. Do not land or gate until both acceptance scenarios execute and pass.

## Checkpoint (2026-09-27)

Done: agent waiting.feature: both scenarios green after keeping the Grover delay through scripted queue reset, releasing first turn, and preserving coalesce-key through charge/build; fixed queue worker waiting-id flattening. Gchat in-flight-end step added, but focused scenario RED: three posts rather than one because configured `dm-queue` is normalized to `gchat-spaces-dmq` while Given marks `gchat-spaces-DMQ` in flight. Full agent `bb ci` RED: two unrelated continuation/wrap-up feature failures (`turn/continuations.feature:49`, `llm/turn_exhaustion.feature:234`); agent specs 1796 green. No claim of green checkpoint.

Next: resolve session-key case mismatch in gchat acceptance without editing baselined feature; inspect `feature-steps/isaac/gchat_steps.clj:420` and `src/isaac/comm/gchat/handler.clj:103`. Then rerun gchat focused scenario and both full suites, fix agent full-suite failures, commit/push green branches, gate and land.

## Contract conflict (2026-09-27)

The baselined gchat scenario at `features/comm/gchat/inbound.feature:733-735` demands `:turn/coalesced` with `session = gchat-spaces-DMQ`. Chat canonicalizes session names to lowercase (`isaac.comm.gchat.canon/slug`), and the actual session and log are `gchat-spaces-dmq`. The acceptance assertion fails: `Expected "gchat-spaces-DMQ", got: "gchat-spaces-dmq"`. Changing production logging to a noncanonical name would misrepresent the session and break observability. The planner must correct/rebaseline the feature (or explicitly decide a different canonical naming design); worker cannot edit baselined scenario text. Agent focused waiting scenarios pass; agent full `bb ci` still red in continuation and wrap-up scenarios noted above.


## Planner adjustment (2026-09-27, prowl@isaac-plan) — log the canonical session key

The log must name the session the store actually has. Chat slugs session keys to lowercase (`isaac.comm.gchat.canon/slug`). `gchat-spaces-DMQ` is not a session. Do not log the noncanonical key.

The scenario's session steps and the `:turn/coalesced` row now say `gchat-spaces-dmq`. The space id in the Chat API fixtures stays `spaces/DMQ` — that id is not a session key. `@wip` stays on until the scenario passes.

isaac-gchat main `b7044d4`. Scenario line is still 701.

### Re-baselined

    feature-baseline: isaac-gchat b7044d4d474507184ff044f1eb1bb64d221081a2
    feature-blob: isaac-gchat features/comm/gchat/inbound.feature 984f43696faf53a4b358202acb8bd2d047cfad22 701
    feature-baseline: isaac-agent 8794de90f8493b31911318e508741c1e7ef281af
    feature-blob: isaac-agent features/session/waiting.feature 2982c9383a0f89ef3c1e43c19e3a6e971634a96f 11,34

The agent blob is unchanged. It is repeated so this baseline's tree is the one in force.

### Worker now

1. Rebase `bean/isaac-xoqn` (gchat) onto `b7044d4`. Keep the implementation. Feature diff may only drop `@wip` on the xoqn scenario (line 701).
2. The coalesced log session is `gchat-spaces-dmq`. Do not log `gchat-spaces-DMQ`.
3. `features/session/turn/continuations.feature:49` and `features/llm/turn_exhaustion.feature:234` are not this bean. If they fail on agent main too, say so and do not absorb them. If they fail only on this branch, they are in scope.
4. `bb bean-gate verify isaac-xoqn` exit 0, then land agent, then gchat. Gchat pins the agent sha.

This note resets the verify-fail counter.

feature-baseline: isaac-gchat b7044d4d474507184ff044f1eb1bb64d221081a2
feature-blob: isaac-gchat features/comm/gchat/inbound.feature 984f43696faf53a4b358202acb8bd2d047cfad22 701
feature-baseline: isaac-agent 8794de90f8493b31911318e508741c1e7ef281af
feature-blob: isaac-agent features/session/waiting.feature 2982c9383a0f89ef3c1e43c19e3a6e971634a96f 11,34

## Checkpoint (2026-09-27, scrapper@isaac-work-3)

Done: rebased both branches, implemented guarded waiting-room drain, repinned Chat to agent's landed main SHA, adapted canonical per-space routing to the current agent frequency selector. Agent `bb ci`: 1801 specs and 886 features, 0 failures (1 pre-existing pending). Gchat `bb ci`: 196 specs and 60 features, 0 failures. `bb lint src feature-steps` in gchat: 0 errors. Gate PASS on squash commits: agent `87c3e1ec65bc1fbdd976bb9373538c23eba91d11`, gchat `b9dce73671bd98c0be50c90a89b7e603b14e230e`; both pushed to main. Earlier continuation/wrap-up failures appeared only on bean branch before guarding the drain; main's two focused scenarios were green, and the branch's full suite is now green. Gchat's initial pinned-agent run exposed seven unrelated canonical route failures caused by agent's changed frequency selector; updating the Chat selector resolved them.

Next: record `main-sha:` lines, remove bean branches and complete the bean. Resume at `.beans/isaac-xoqn--isaac-agent-isaac-gchat-messages-for-a-busy-sessio.md` below this note; rerun `bb bean-gate verify isaac-xoqn --dir isaac-agent=../isaac-agent-xoqn-landing --dir isaac-gchat=../isaac-gchat-xoqn-landing` after recording landing.

## Landed on main (2026-09-27)

main-sha: isaac-agent 87c3e1ec65bc1fbdd976bb9373538c23eba91d11
main-sha: isaac-gchat b9dce73671bd98c0be50c90a89b7e603b14e230e
