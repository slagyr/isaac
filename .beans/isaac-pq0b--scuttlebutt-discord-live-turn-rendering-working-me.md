---
# isaac-pq0b
title: 'Scuttlebutt: Discord live turn rendering (working message, asides, bulletins)'
status: draft
type: feature
priority: normal
created_at: 2026-08-30T22:48:18Z
updated_at: 2026-08-30T22:48:18Z
blocked_by:
    - isaac-5nxf
---

Repo: isaac-discord. Migrate to the new Comm via extend+defaults (minimal: on-turn-start typing, on-reply send, on-turn-end errors, send!). Then the UX win: post one 'working…' message at cycle 1 and EDIT it in place per cycle with the latest aside + 🧰 tool lines; replace with the reply at turn end; bulletins as a single italic line. Message-cap aware; edit-rate throttled (Discord rate limits). Decide: flag per channel (:discord/live-turn?) vs default-on. Draft until scenarios are written.
