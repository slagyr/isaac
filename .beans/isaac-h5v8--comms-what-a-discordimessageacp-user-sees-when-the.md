---
# isaac-h5v8
title: Originator is told when its turn suspends on provider weather (reason + retry-at) and gets the normal reply on completion — hail, Discord, iMessage, cron, ACP
status: in-progress
type: feature
priority: high
tags:
    - comm
created_at: 2026-09-18T14:42:12Z
updated_at: 2026-09-23T16:17:12Z
parent: isaac-ugpq
blocked_by:
    - isaac-nqeq
---

Child 4 of isaac-ugpq. Open: one-line notice on suspend or silence until the reply; per comm. Decide with Micah after child 1 lands.

## Decision (Micah, 2026-09-23): the comm shows the user what went wrong

"When something goes wrong a message should be displayed or the comm should be notified so that it can display a message to the user." The Comm protocol already carries `on-turn-end [comm session-key result]` and `on-exhausted`; gchat's `on-turn-end*` ignores the result today. First comm: gchat.

- On a turn that ends in an error, gchat posts a short in-thread notice naming the failure class (provider error, tool failure, delivery failure), never a stack trace, never a raw CLI payload.
- On provider weather (the drive parks the turn: rate-limited, auth, stall), gchat posts one notice with the reason and the retry time when known ("Out of tokens until 4:40pm; I will answer then"), and one when the turn resumes only if the reply itself is not the next message.
- The attention comm (system-scoped, yopp: Micah's DM) still gets the operator alert; the originator notice is in addition, not instead.
- Same seam for gmail later.

Scenarios in gchat outbound.feature + agent features for the on-turn-end result shape. Status → todo, high.
