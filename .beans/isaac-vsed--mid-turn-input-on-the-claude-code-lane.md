---
# isaac-vsed
title: Mid-turn input on the Claude-code lane
status: draft
type: feature
priority: low
tags:
    - agent
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-01T18:45:06Z
parent: isaac-4qxr
blocked_by:
    - isaac-cgz9
---

Repo: **isaac-claude-code** (+ isaac-agent hook if needed). Follows the mid-turn input bean.

The Claude-code lane runs its tool loop inside the Claude CLI, so the cycle-boundary hook in Isaac's own loop never fires there. Find out whether the CLI's stream-json input accepts user messages mid-turn and, if so, feed waiting session input to it with the same labels. If it can't, the lane keeps today's behavior (input waits for the next turn) and this bean records why.

## Acceptance

- Either: a message sent during a Claude-code-lane turn shows up in that turn and the reply accounts for it; or: a written finding that the CLI can't take it, and the bean is scrapped with that reason.
