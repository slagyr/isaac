---
# isaac-gmc5
title: Derive state-dir from config home in loader; drop nexus :state-dir slot
status: scrapped
type: task
priority: normal
created_at: 2026-05-24T23:10:10Z
updated_at: 2026-05-24T23:34:40Z
---

state-dir is currently passed in via opts, registered as its own nexus slot (app.clj:211), and loader/state-dir falls back to that slot. But loading config from `home` already determines state-dir = <home>/.isaac (parent of paths/config-root). Derive it in the loader, hang it on the config map (not user-settable), and drop the separate nexus slot + fallback.

Part of a larger effort to unify config loading + Nexus population behind a single coordinator function (plan to follow).

## Reasons for Scrapping

Accidental duplicate of isaac-2w4d (the first create call's JSON parse failed but the bean was created). Work tracked on isaac-2w4d.
