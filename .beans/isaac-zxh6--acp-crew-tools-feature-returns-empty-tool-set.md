---
# isaac-zxh6
title: ACP crew tools feature returns empty tool set
status: scrapped
type: bug
priority: normal
created_at: 2026-05-11T22:30:38Z
updated_at: 2026-05-13T03:00:20Z
---

## Description

Why
Unrelated bb verify failure observed while finishing isaac-efzy.

Observed behavior
Feature failure: Crew tools reach every comm path HTTP/WebSocket ACP offers the crew's configured tools expected read/exec/write but got an empty set.

Reproduction
Run bb verify in the current checkout.

Notes
bb spec and bb features are green for isaac-efzy. This appears to come from concurrent ACP-related workspace changes already present in the dirty checkout.

## Reasons for Scrapping

bb ci is fully green as of 2026-05-12. The failure no longer reproduces — fixed as a side effect of subsequent merges.
