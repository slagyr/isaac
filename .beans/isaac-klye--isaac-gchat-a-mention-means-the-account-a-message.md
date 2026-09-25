---
# isaac-klye
title: 'isaac-gchat: a mention means the account — a message that @-mentions someone else is not addressed to Yopp'
status: in-progress
type: bug
priority: high
created_at: 2026-09-25T02:35:49Z
updated_at: 2026-09-25T02:36:08Z
---

## Symptom

Marketing space (spaces/AAQASjyfLk4), 2026-09-25 01:12Z and 01:13Z: Yopp
answered two of Chris Sherrick's messages that did not mention him. Both
were `gchat/message-routed`; the policy for the space is :mentions.

## Cause

`isaac.comm.gchat.gate/mentioned?` returns true when the message carries
**any** userMention annotation — it never compares the mentioned user to the
account. A message that @-mentions a colleague reads as a mention of Yopp.

## Design

- Resolve the account's own user resource once per comm (the same identity
  the handler already uses to drop `:self` messages — sender name/email of
  yopp@tonotop.com) and expose it as `self-user` on the decision opts.
- `mentioned?` is true only when a userMention annotation names that user
  (by `users/<id>`, or by email when the annotation carries one). Mentions
  of anyone else are not mentions. `@all`/space-wide mentions: treat as a
  mention (document the choice in the docstring).
- No config change. A DM still answers everything (`:all`).

## Acceptance (isaac-gchat spec + gate feature)

- [ ] Message with a userMention of another user, policy :mentions → :log.
- [ ] Message with a userMention of the account → dispatch; same with the
  account's email in the annotation; same for both annotation shapes the
  gate already handles (map and sequence).
- [ ] Message with no annotations → :log (unchanged). DM → dispatch (unchanged).
- [ ] Version bump, bb spec / bb features / bb lint green.

Likely repo scope: isaac-gchat (gate.clj, handler.clj for self identity).
