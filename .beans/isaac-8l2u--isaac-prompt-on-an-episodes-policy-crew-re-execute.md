---
# isaac-8l2u
title: isaac prompt on an episodes-policy crew re-executes recalled prior instructions before the new message (sent an unrequested email)
status: todo
type: bug
priority: critical
tags:
    - agent
    - episodes
    - safety
created_at: 2026-09-19T23:08:46Z
updated_at: 2026-09-19T23:08:46Z
---

Observed 2026-09-19 21:30Z on yopp. `isaac prompt -c yopp --create always -m "Using the comm-send tool, send … to the gchat comm, space yopp-test …"`. The new session (crew yopp, policy episodes) FIRST composed and sent an email to micah@tonotop.com via the gws skill — subject 'Another test, another joke' — narrating it as 'round two on the email pipe', i.e. continuing an earlier session's task that recall had surfaced. Only then did it address the actual message ('Now the mid-turn ask: …'). Nobody asked for an email in this turn.

Recalled scenes/gists are being treated as live instructions rather than as memory. On a crew with real tools (gws: gmail.compose) that is an unrequested outbound side effect.

Needs: (1) reproduce with the session transcript (yopp ~/.isaac sessions, newest crew-yopp session 21:30Z); (2) the episodes policy / recall injection must frame recalled material as past context, never as pending work — and a fresh turn's first action must trace to the current message; (3) a scenario: a crew with a recorded prior episode 'send X' receives a new unrelated message; the turn does not repeat 'send X'. Until fixed: no prompt turns on yopp's crew with side-effecting tools.
