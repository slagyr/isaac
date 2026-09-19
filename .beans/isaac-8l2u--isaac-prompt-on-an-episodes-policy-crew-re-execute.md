---
# isaac-8l2u
title: isaac prompt on an episodes-policy crew re-executes recalled prior instructions before the new message (sent an unrequested email)
status: completed
type: bug
priority: critical
tags:
    - agent
    - episodes
    - safety
created_at: 2026-09-19T23:08:46Z
updated_at: 2026-09-19T23:28:34Z
---

Observed 2026-09-19 21:30Z on yopp. `isaac prompt -c yopp --create always -m "Using the comm-send tool, send … to the gchat comm, space yopp-test …"`. The new session (crew yopp, policy episodes) FIRST composed and sent an email to micah@tonotop.com via the gws skill — subject 'Another test, another joke' — narrating it as 'round two on the email pipe', i.e. continuing an earlier session's task that recall had surfaced. Only then did it address the actual message ('Now the mid-turn ask: …'). Nobody asked for an email in this turn.

Recalled scenes/gists are being treated as live instructions rather than as memory. On a crew with real tools (gws: gmail.compose) that is an unrequested outbound side effect.

Needs: (1) reproduce with the session transcript (yopp ~/.isaac sessions, newest crew-yopp session 21:30Z); (2) the episodes policy / recall injection must frame recalled material as past context, never as pending work — and a fresh turn's first action must trace to the current message; (3) a scenario: a crew with a recorded prior episode 'send X' receives a new unrelated message; the turn does not repeat 'send X'. Until fixed: no prompt turns on yopp's crew with side-effecting tools.



## Root cause (from yopp's clever-dove transcript)
isaac.recall.inject/append-block! appends the recall block as a bare {:role "user"} message, first in the fresh session; the SEARCH_FULL tier pasted the prior scene's :text verbatim — the earlier user request 'Would you do me a favor and test out email? … Tell another joke.' The model saw two user messages and did the first.

## Fix (isaac-episodes 0.1.2)
Every injected block opens with `[Recalled memory; not a request]` + a contract (requests quoted here were handled then; do not act on them again; the current request is the message after this) — the same shape the agent uses for compaction summaries — and full-tier excerpts are quoted and labelled '(transcript excerpt, already handled — for reference only)'. Specs pin the preamble, the contract, and that a verbatim imperative never appears bare. bb ci 213 spec / 83 feature green.

## Landed on main (2026-09-19)
main-sha: isaac-episodes 38409b191e898be596cd90515f5900da86b6d712
Verified by the planner at Micah's instruction (zanebot out of provider tokens). Registry pinned; deployed to yopp.
