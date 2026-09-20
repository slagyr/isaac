---
# isaac-3wiu
title: Hail routing bound a work hail to a three-month-old ad-hoc session
status: todo
type: bug
priority: normal
created_at: 2026-09-20T07:34:20Z
updated_at: 2026-09-20T07:34:20Z
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
