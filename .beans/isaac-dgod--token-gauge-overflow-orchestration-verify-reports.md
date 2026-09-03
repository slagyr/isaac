---
# isaac-dgod
title: 'Token gauge overflow: orchestration-verify reports 12.0M / 278K (4320%) after one compaction'
status: draft
type: bug
created_at: 2026-09-03T00:00:08Z
updated_at: 2026-09-03T00:00:08Z
---

Observed 2026-09-02 on zanebot: `isaac sessions list` shows orchestration-verify (perceptor, gpt-5.4 chatgpt, 327 turns, 1 compaction) at Context 12,031,158 / 278,528 = 4320%. The session file is 1.0M on disk (~250K tokens plausible), so the gauge is not a real prompt size — last-input-tokens (or whatever feeds the PCT column) has gone cumulative or been fed a non-prompt number. Related: isaac-pqjn / isaac-x2up token accounting. Questions: (1) which provider response field seeded 12M — chatgpt usage totals across a stateful chain? (2) does compaction run against this gauge (it would plan chunks off a fictional size) or refuse? (3) is any other session drifting the same way (all other rows look sane today). Reproduce by inspecting orchestration-verify/current.ednl last-input-tokens entries on zanebot before touching the session.
