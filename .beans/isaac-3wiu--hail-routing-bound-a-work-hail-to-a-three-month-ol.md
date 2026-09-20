---
# isaac-3wiu
title: Hail routing bound a work hail to a three-month-old ad-hoc session
status: todo
type: bug
priority: high
created_at: 2026-09-20T07:34:20Z
updated_at: 2026-09-20T19:38:07Z
---

2026-09-20 05:19Z the retries of hail `d4a7cd6f` (isaac-ddls, band `isaac-work`)
were bound to session **`2026-06-29-1749-iaqu`** — an ad-hoc session from June,
not one of `isaac-work-1/2/3`. It ran a turn there (and failed, since the whole
fleet's auth was down); with a working provider it would have executed the
bean-work skill against a three-month-old session's context and tool state.

The router logs `:hail/routed :candidates 3` for the first attempt, so the band
normally resolves to the three worker sessions. Something in the retry path
widens the candidate set — or that old session carries a tag or crew that makes
it a band member and it only surfaces when the bound session is busy.

Work: find why that session was a candidate; make band membership explicit and
stable across retries, so a retry lands in the same band, never in an
unrelated session. Scenario: a hail whose bound session fails is retried within
the band's sessions only.

Found while watching the Google/Chat bean train (isaac-nceb is the outage that
exposed it).

## Second sighting, with the trigger (2026-09-20 19:34Z)

Restarting the server reproduced it deliberately, and the trace is complete:

| time | event | session |
| --- | --- | --- |
| 18:41:40 | `hail/bound` | isaac-work-1 |
| 19:33:21 | `hail/turn-ended` (restart killed the turn) | isaac-work-1 |
| 19:33:21 | `hail/delivery-suspended` | isaac-work-1 |
| 19:34:29 | **`hail/delivery-recovered`** | **2026-06-29-1749-iaqu** |
| 19:34:29 | `hail/bound` | 2026-06-29-1749-iaqu |

So it is not the retry path, as the first sighting suggested — it is **delivery
recovery after a restart**. The hail was `{:band "isaac-work" :crew :scrapper}`;
recovery re-bound it by crew, and `2026-06-29-1749-iaqu` is a scrapper session,
so it qualified. It is an ad-hoc session from June with an `:episodes` policy,
134K of unrelated history, and no place in the band.

It then did the work: read its own `session.edn`, and announced on Discord
"isaac-1zkz 🔁 **scrapper**@2026-06-29-1749-iaqu resumed (google-tenants)" —
a structural refactor bean being worked in a three-month-old session that the
planner watch does not look at and `:max-in-flight` does not count.

Work: recovery must re-bind within the band's own sessions (the set the router
used originally), not merely within the crew. If no band session is free, the
delivery waits — that is what pending is for. Scenario: a delivery suspended by
a restart is recovered into a band session, and a session that is not in the
band is never a candidate however well its crew matches.
