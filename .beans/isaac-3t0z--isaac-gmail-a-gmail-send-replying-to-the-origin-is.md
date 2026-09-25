---
# isaac-3t0z
title: 'isaac-gmail: a gmail__send replying to the origin is the reply — on-reply must not email the answer twice'
status: todo
type: bug
priority: high
created_at: 2026-09-25T02:35:50Z
updated_at: 2026-09-25T02:35:50Z
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
