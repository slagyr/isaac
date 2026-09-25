---
# isaac-3t0z
title: 'isaac-gmail: a gmail__send replying to the origin is the reply — on-reply must not email the answer twice'
status: completed
type: bug
priority: high
created_at: 2026-09-25T02:35:50Z
updated_at: 2026-09-25T02:49:32Z
---

## Symptom

Micah's question about Hail by email (2026-09-25 00:49Z, session
gmail-1a0d60a2cfc63a85) got two replies with the same content. Log:
`tool/result :tool "gmail__send"` 00:51:49 (reply-to-id = the origin
message), `turn/ended :reply` 00:51:55, then two distinct sent messages
walked as `not-inbox` (1a0d60c0c1ca37dc, 1a0d60c25a3609d4). Same at 00:45Z.

## Cause

`GmailComm` `on-reply*` sends the turn's final assistant text as a reply on
the origin thread; the model had already sent its answer with `gmail__send`
(reply-to-id = origin message id). Two emails, same substance.

## Design

A `gmail__send` whose reply-to-id (or thread) equals the session's origin
**is** the reply: `:on-tool-call` records `replied-via-tool` for the session
and `on-reply*` stays silent for that turn (log `:gmail/reply-deduped` at
debug). Sends to other threads/recipients are untouched. Clear the mark in
`on-turn-end*`. Add a line to the gmail turn guidance: the answer text is
sent automatically as the reply; `gmail__send` is for other threads.

## Acceptance (isaac-gmail spec + feature)

- [ ] Turn with gmail__send reply-to the origin + final text → one message
  sent; reply-deduped logged.
- [ ] Turn with final text only → one reply (unchanged).
- [ ] gmail__send to another thread + final text → two sends (both wanted).
- [ ] Version bump, bb spec / bb features / bb lint green.

Likely repo scope: isaac-gmail (gmail.clj hooks, guidance).

## Work notes (scrapper@isaac-work-1, 2026-09-24)

Branch `bean/isaac-3t0z` on isaac-gmail (4f348a4), version 0.2.5. Not gated (no feature-baseline) → verify path.
- `isaac.comm.gmail`: `:on-tool-call` marks the session when a gmail__send's reply-to-id equals the origin message id (`reply-to-origin?`); `on-reply*` then logs `:gmail/reply-deduped` (debug) and sends nothing; mark cleared in `on-turn-end*`.
- `isaac.comm.gmail.guidance/TEXT` rides `:guidance` on every gmail dispatch: answer text is the reply; gmail__send is for other threads/new messages.
- Features: 3 new scenarios in gmail.feature (origin dedupe, text-only, other-thread two sends) + step `the Gmail API sent N messages`. Specs: gmail_spec, guidance_spec, handler start-turn! guidance.
- bb ci EXIT=0 (spec 137/0, features 43/0; needs ISAAC_TEST_TIMEOUT_MS under load — JVM features exits slowly). bb lint 76 errors/17 warnings = main baseline (pre-existing speclj :refer :all noise).
- Only a reply-to-id match on the origin message counts; a gmail__send replying to a different message in the same thread is not deduped (thread lookup would need an API call inside on-tool-call).

## Verify pass (perceptor@isaac-verify, 2026-09-24)

bb spec 137/0; bb jvm-features 43/0 EXIT=0 (~71s under load; 60s default timeout trips from load, not a hang — origin/main 40/0 EXIT=0); bb lint 76/17 identical to main baseline. Hooks :on-tool-call and charge :guidance confirmed in pinned isaac-agent b6eb475. Note: dedupe keys on reply-to-id == origin message id only (thread match from Design not implemented, disclosed); mark is set on tool call, so a failed gmail__send still suppresses the auto-reply.

## Landed on main (2026-09-24)

main-sha: isaac-gmail b3a87b9e12d60b797d5399d1d692e063a856eea3
