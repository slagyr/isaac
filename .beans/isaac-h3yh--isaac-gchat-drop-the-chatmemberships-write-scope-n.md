---
# isaac-h3yh
title: 'isaac-gchat: drop the chat.memberships write scope — no API accepts a DM request, so it buys nothing'
status: in-progress
type: task
priority: normal
tags:
    - gchat
created_at: 2026-09-23T15:51:29Z
updated_at: 2026-09-23T15:51:29Z
---

Added in gchat 0.2.5 (isaac-qry7 probe). members.create on a DM answers 400 for human DMs; the scope is dead weight on the consent screen. Remove it from the :isaac.google/scopes contribution and the module spec; bump 0.2.6. Note in doc that a re-login is not required (extra granted scopes are harmless).
