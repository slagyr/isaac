---
# isaac-xoqn
title: 'isaac-agent + isaac-gchat: messages for a busy session wait instead of being refused, and prompts in one thread consolidate into one reply'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T15:56:18Z
updated_at: 2026-09-25T15:56:58Z
---

## Why (Micah, 2026-09-25)

"Are chat messages queued? So that if I type lots of messages in the DM,
Yopp will reply to them all? Each thread can be consolidated such that if
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
