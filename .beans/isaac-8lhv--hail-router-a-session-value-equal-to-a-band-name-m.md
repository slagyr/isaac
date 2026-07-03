---
# isaac-8lhv
title: 'Hail router: a session value equal to a band name must not dead-letter (coerce to band routing + warn)'
status: draft
type: bug
priority: high
created_at: 2026-07-03T20:56:45Z
updated_at: 2026-07-03T20:56:45Z
---

## Problem (evidence, 2026-07-03)

Hail records show 11 hails with a session value equal to a BAND NAME (e.g. session ["isaac-work"]) — always undeliverable, because bands select real sessions (isaac-work-1/2) by tags and no session literally named "isaac-work" exists. These silently dead-letter (contributed to isaac-wtg8 appearing stuck). A band name as a session value is ALWAYS an error and is deterministically detectable — the router has the band config (isaac-ukof fixes the prose; this makes it robust regardless of model, which matters given grok-build's demonstrated prose-following wobble).

## Design

In the hail router addressing/merge path (src/isaac/hail/router.clj, `merge-session-ids` / frequency resolution — the router already holds cfg band names): when a resolved :session id equals a known band name, DO NOT treat it as a session. Coerce: drop that session id and fall through to the band's own selectors (session-tags/crew/reach), and log a WARN (:hail/session-is-band-name) so the upstream prose mistake stays visible. Net effect: today's 11 dead-letters become successful band deliveries, with a warning trail.

Chosen behavior: coerce+warn (not hard error) — the operator intent is clearly "reach the worker", and the band delivers it correctly; the warn preserves diagnosability. (Alternative — reject to undeliverable with a clear reason — was considered; it keeps the dead-letter, just louder, so coerce wins.)

## Acceptance scenarios (to be committed @wip after review)

isaac-hail features/explicit-session-routing.feature:
- A hail whose :session value equals a configured band name is NOT delivered to a nonexistent session; it routes via that band's session-tags to a real session, and a :hail/session-is-band-name WARN is logged.
- A hail with a genuine (non-band) explicit :session still routes directly (existing behavior unchanged — regression guard).

## Scope

isaac-hail (router.clj addressing merge + a log warn; features/explicit-session-routing.feature).
