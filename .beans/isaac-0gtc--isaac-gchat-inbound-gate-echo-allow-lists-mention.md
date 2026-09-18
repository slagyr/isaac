---
# isaac-0gtc
title: 'isaac-gchat inbound: gate (echo, allow-lists, mention, policy) → route → dispatch for one space'
status: draft
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
parent: isaac-bv1l
blocked_by:
    - isaac-1jep
---

Isaac reads one Google Chat space and answers when addressed. Discord-shaped comm: `:isaac.http/comm` contribution `{:gchat {:namespace isaac.comm.gchat …}}` + `isaac.comm.factory/create` defmethod; handler contributed to `:isaac.google/handler` for `google.workspace.chat.message.v1.*`.

## The gate (deterministic, before any turn)

1. **Echo drop**: sender == the configured Google user → drop.
2. **Allow-lists fail closed** (iMessage rule): `:gchat/spaces` map of configured spaces; `:gchat/allow-from` sender emails. Not configured = dropped.
3. **Classify**: DM vs space (space type from the pointer fetch).
4. **Mention detection**: the message's annotations mention the account.
5. **Policy** per space: `:respond :mentions` (default) | `:all` | `:never`; DMs `:all` by default.
6. **Route** with the shared vocabulary (crew, session, session-tags, reach, prefer, create) exactly as Discord `:channels` does; thread name rides along in origin so the reply lands in-thread.
7. `bridge/dispatch!` with `:origin {:kind :gchat …}`; the comm's reply path is child 4.

Pointer-only events: the handler fetches the message as the member (`spaces.messages.get`) — needed anyway for text, thread, attachments. Fetch failure = logged, record failed, no turn.

## Scenarios to draft (one space, stubbed Chat API)

1. A mention in the configured space starts a turn on the routed session; the transcript carries the user's text and sender.
2. A non-mention message under `:respond :mentions` starts no turn.
3. A DM starts a turn without a mention.
4. Isaac's own message is dropped (no turn, no log noise above debug).
5. A message from an unconfigured space is dropped.

Ambient recording of non-mention messages (context without a turn) is NOT in v1 — noted in the epic as an open question.
