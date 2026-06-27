---
# isaac-la09
title: 'Cron: build frequencies for scheduled prompts'
status: draft
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:01:46Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

cron/service.clj creates a session per job with :crew and no real selection. Adopt frequencies: a cron job specifies a frequencies map (which session/crew/tags, :create, :prefer + :with-* override) for its scheduled prompt. e.g. run a daily prompt on the most-recent session of crew X, or always-new.

Today: uses create-with-resolved-behavior! (override seam) but ad-hoc selection. Build the frequencies map from cron config and feed the shared core (isaac.session.frequencies). Blocked-by the frequencies rename.
