---
# isaac-ddls
title: 'isaac-claude-code: every turn logs :chat/provider-contract-violated {:reasoning {:summary "is required"}} — noise at :error; fails the episodes seal on yopp'
status: in-progress
type: bug
priority: high
tags:
    - claude-code
    - agent
created_at: 2026-09-19T23:48:52Z
updated_at: 2026-09-20T04:58:06Z
---

Seen on yopp all day 2026-09-19 (agent fd89226, claude-code f058b2c): each turn emits :chat/provider-contract-violated :errors {:reasoning {:summary "is required"}} + :chat/stream-error :error :provider-contract, yet the turn completes and replies. The episodes seal for crew yopp fails with :provider-error, consecutive 8+, so no scene is ever sealed on yopp. Some event the claude CLI streams (a reasoning/thinking block) is missing :summary under the agent's provider contract. Find which event, make the contract accept a summary-less reasoning block (or synthesize one), and stop logging a completed turn at :error. Scenario: a claude-cli stream with a reasoning block without summary produces a clean turn and no contract error; the episodes seal succeeds on such a session.
