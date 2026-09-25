---
# isaac-iwio
title: 'isaac-gmail: one send tool — gmail send-schema for comm__send, retire gmail__send, guidance, 3t0z guard out'
status: completed
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T05:05:13Z
---

## Why (Micah, 2026-09-25)

Same ruling as the gchat bean: one send tool, comm__send, all tools stay,
guidance carries the distinction, the isaac-3t0z dedupe guard comes out.
gmail today declares **no** send-schema, so through comm__send the only
email possible is a reply into the session's own thread; composing mail to
someone needs gmail__send. Give gmail a real send-schema, then retire the
module tool.

## Design

- Manifest `:isaac.agent/comm :gmail :send-schema`:
  `:gmail/to` (string, recipient email), `:gmail/subject` (string),
  `:gmail/thread` (string, optional Gmail thread id to reply in).
- `send!*` on a delivery record: with `:gmail/thread` → reply on that
  thread (Subject "Re: …", In-Reply-To/References from the thread's last
  message); with `:gmail/to` and no thread → new message with
  `:gmail/subject`; with neither → reply on the session's origin (today's
  behaviour, unchanged). `:gmail/to` without `:gmail/subject` on a new
  message is an error the tool returns before queueing (required-ness is
  per chosen comm — use `:validations [[:present?]]` only if the agent's
  comm_send treats it as conditional; otherwise validate in send!* and log).
- Retire `gmail__send` (manifest berth entry + tools.clj factory);
  `gmail__search`, `gmail__read`, `gmail__labels` stay.
- Remove the isaac-3t0z guard (`on-tool-call*` mark, `on-reply*` skip,
  `:gmail/reply-deduped`, specs) and the two 3t0z scenarios it justified.
- `guidance/TEXT` becomes exactly:

  > Your response is the text you end this turn with. It is delivered back over the channel this message came from, so never send it with comm__send. That tool is for additional messages of your own during the turn: several messages in a row, a message to another thread, space or person, or something you were asked to send. Those never replace your response, so still end the turn with it, even if it is short.

## Acceptance (features/comm/gmail/gmail.feature — baselined)

- [ ] Scenario "comm__send with gmail.to and gmail.subject sends a new
  email (isaac-iwio)".
- [ ] Scenario "comm__send replying into the origin thread, then the answer
  — both go out (isaac-iwio)".
- [ ] Existing "a turn that only answers in text sends one reply" keeps
  passing.
- [ ] One-time: `gmail__send` no longer registered; guidance verbatim;
  `:gmail/reply-deduped` gone from src. Version bump; bb spec / features /
  lint green.

Deploy note (planner, yopp): `hail/yopp-tasks.edn` prompt says "reply on
the same thread with gmail__send" — change to comm__send with
`gmail.thread` when this ships.

Likely repo scope: isaac-gmail (manifest, gmail.clj send!*, tools.clj,
guidance.clj, specs, gmail.feature).

feature-baseline: isaac-gmail 3b0b4faad16cdae231139cc079ec1bcccf8d5d29
feature-blob: isaac-gmail features/comm/gmail/gmail.feature c9d74d1539c081f9528d14261f735db92b662bf3 191,214

feature-baseline: isaac-gmail 8b502cc2f97be0d1543a8f0da07efbb8757c6724
feature-blob: isaac-gmail features/comm/gmail/gmail.feature fbf1f0f50bea3638b5f2515ae52ceb0f59934b98 191,214


## Planner note (2026-09-25 04:50Z) — re-baselined

Scenario 2 ("comm__send replying into the origin thread, then the answer — both go out") now asserts only `the Gmail API sent 2 messages`: two mails go to the same address, so a recipient-scoped decode was ambiguous. The recipient-scoped step is still needed for scenario 1 (one mail to grace@).



## Planner note (2026-09-25 05:20Z)

The two isaac-agent bugs blocking the comm__send scenarios are fixed on isaac-agent main `372b7debf6f8fe5167581d873e04c31818f5b6c3` (0.1.84): impl-keyword falls back to the slot id; the delivery-tick step layers stubs over live comms. Pin that sha, un-@wip both scenarios, re-run features.



## Landed (2026-09-25)

isaac-gmail main e894924 (0.2.6). Gate PASS; spec 132/0, features 43/0. Deploy: repin registry, upgrade yopp, and switch hail/yopp-tasks.edn prompt from gmail__send to comm__send (comm gmail, gmail.thread).
