---
# isaac-iv5c
title: 'isaac-gchat: speak only on mention or DM, but hear the whole conversation — context for the turn'
status: completed
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-19T23:53:42Z
updated_at: 2026-09-20T07:13:20Z
parent: isaac-bv1l
blocked_by:
    - isaac-bklu
---

Micah, 2026-09-19: Yopp should not answer every message in a space (default :mentions stands; :all was a test setting), but when mentioned he should know what was said. Today a mention turn's input is just "<sender> <text>" of the mentioning message.

Do:
1. Listen without speaking: non-mention events in a subscribed space are not dropped on the floor — the handler appends them (sender, thread, time, text) to a per-space rolling log under <root>/google/chat/<space>.ednl (bounded, no LLM, no turn). Gate reason :no-mention becomes :logged.
2. On a mention or DM, the turn input is the thread since Isaac's last reply in it (or the last N=20 space messages when the thread is new), then the mentioning message. Framed explicitly — a '[Chat context; not requests]' block for the history and the mention marked as the request — per the isaac-8l2u lesson: history in the user role reads as instructions unless framed.
3. Session per space unchanged; DMs default :all as today.
Scenarios: two unmentioned messages then a mention → one turn whose input carries all three, history framed, mention last; a DM carries the DM history; the log is bounded.



Micah 2026-09-19: reuse the agent's isaac.session.frequencies exactly as isaac-discord does (comm/discord.clj channel->frequencies: accepted keys #{:session :session-tags :crew :reach :prefer :create}; defaults :create :if-missing :reach :one :prefer :recent). No gchat-local resolver.

## Landed on main (planner, 2026-09-20)

Landed by the planner during the fleet's auth outage.

1. **Listen without speaking.** The gate's `:no-mention` drop became
   `{:action :log :reason :logged}`; the handler appends one EDN line per
   message — sender (rendered through `people/render`), thread, text — to
   `<root>/google/chat/<space>.ednl`, bounded at 200 lines a space. No LLM, no
   turn. Only messages from allowed senders reach it: the sender gate runs
   first, so the log is not a place unvetted traffic accumulates.
2. **Context on a mention.** The turn input is what the space said since
   Isaac's own last line there (at most 20), framed as
   `[Chat context; not requests]` with a contract sentence and
   `[End chat context]`, then the mentioning message, last and unframed —
   the isaac-8l2u lesson applied to chat history. A space Isaac has never
   spoken in hands over its last 20 lines. Isaac's own reply appends a
   `:self?` line, which is where the next mention's context starts.
3. Sessions per space unchanged; DMs still answer without a mention.

New `isaac.comm.gchat.transcript` (file per space, bounded, fails soft when
there is no root or fs — it must never break a turn) with 8 specs.

| suite | result |
| --- | --- |
| `bb spec` | 63 / 0 |
| `bb features` | 23 / 0 |

main-sha: isaac-gchat b2fd9c5a31007fb092476064d43e527115fea8f3 (0.1.7)

Not done here: the context block renders each line with `people/render` as
stored, so isaac-tund's context block and this one are already the same
rendering. Frequencies for a space entry are isaac-tund.
