---
# isaac-iv5c
title: 'isaac-gchat: speak only on mention or DM, but hear the whole conversation — context for the turn'
status: todo
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-19T23:53:42Z
updated_at: 2026-09-20T00:06:00Z
parent: isaac-bv1l
---

Micah, 2026-09-19: Yopp should not answer every message in a space (default :mentions stands; :all was a test setting), but when mentioned he should know what was said. Today a mention turn's input is just "<sender> <text>" of the mentioning message.

Do:
1. Listen without speaking: non-mention events in a subscribed space are not dropped on the floor — the handler appends them (sender, thread, time, text) to a per-space rolling log under <root>/google/chat/<space>.ednl (bounded, no LLM, no turn). Gate reason :no-mention becomes :logged.
2. On a mention or DM, the turn input is the thread since Isaac's last reply in it (or the last N=20 space messages when the thread is new), then the mentioning message. Framed explicitly — a '[Chat context; not requests]' block for the history and the mention marked as the request — per the isaac-8l2u lesson: history in the user role reads as instructions unless framed.
3. Session per space unchanged; DMs default :all as today.
Scenarios: two unmentioned messages then a mention → one turn whose input carries all three, history framed, mention last; a DM carries the DM history; the log is bounded.



Micah 2026-09-19: reuse the agent's isaac.session.frequencies exactly as isaac-discord does (comm/discord.clj channel->frequencies: accepted keys #{:session :session-tags :crew :reach :prefer :create}; defaults :create :if-missing :reach :one :prefer :recent). No gchat-local resolver.
