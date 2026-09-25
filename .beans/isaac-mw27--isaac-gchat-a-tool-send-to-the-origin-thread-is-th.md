---
# isaac-mw27
title: 'isaac-gchat: a tool send to the origin thread is the reply — on-reply must not post the answer twice'
status: completed
type: bug
priority: high
created_at: 2026-09-25T02:35:49Z
updated_at: 2026-09-25T02:41:21Z
---

## Symptom

Every answer Yopp gave in the marketing space and in Micah's DM on
2026-09-25 arrived twice. Log for one turn (00:37Z): `tool/result :tool
"gchat__send"` at 00:37:24, `turn/ended :reply` at 00:37:27, then two
`gchat/message-dropped :reason :self` pushes at 00:37:28 — two posts.

## Cause

The yopp crew allows `:gchat/*`, so the model composes its answer with
`gchat__send` into the addressed thread — and then `GchatComm`'s `:on-reply`
(`on-reply*`) posts the turn's final assistant text into the same thread.
The guidance text ("Reply in the addressed thread") reads as an instruction
to use the tool.

## Design

A tool send to the originating space+thread **is** the reply. `:on-tool-call`
already sees every tool call: when the tool is `gchat__send` and its
space/thread equal the session's origin, record `replied-via-tool` for the
session; `on-reply*` then posts nothing for that turn (log
`:gchat/reply-deduped` at debug). Sends to other threads/spaces are untouched.
Clear the mark in `on-turn-end*`. Guidance text: say the answer text is
delivered automatically and `gchat__send` is for other threads/spaces.

## Acceptance (isaac-gchat spec + feature)

- [ ] Turn whose model calls gchat__send to the origin thread then ends with
  text → exactly one message posted (the tool's); reply-deduped logged.
- [ ] Turn that ends with text and no tool send → one post (on-reply, unchanged).
- [ ] Tool send to a different thread + final text → two posts (both wanted).
- [ ] Mark cleared per turn: a following turn with text only posts once.
- [ ] Version bump, bb spec / bb features / bb lint green.

Likely repo scope: isaac-gchat (gchat.clj hooks, guidance.clj).

## Work (scrapper@isaac-work-3, 2026-09-25)

isaac-gchat branch `bean/isaac-mw27` @ ed8fcd4 (not gated — gate exit 2).

- `on-tool-call*` marks the session replied-via-tool when the call is
  `gchat__send` and its space+thread equal the origin's (map or JSON-string
  args); `on-reply*` then posts nothing, logs `:gchat/reply-deduped` at debug,
  still sets ✅. `on-turn-end*` clears the mark. Invited-DM divert unchanged.
- Guidance: answer text is delivered to the addressed thread automatically;
  gchat__send is for other threads or spaces.
- Specs: 6 new in gchat_spec (dedupe, JSON args, no-tool, other thread, other
  space, mark cleared), 1 in guidance_spec. Features: 2 new outbound scenarios
  (DD1 origin-thread → 1 post, DD2 other thread → 2 posts); DD1 fails with the
  src change stashed.
- Version 0.2.13 → 0.2.14. bb ci green (180 specs, 56 features).
- bb lint: 74 errors / 15 warnings, same as main before this change — all
  speclj `:refer :all` unresolved-symbol noise in specs; src is clean. Lint
  was already red on main before this bean.
