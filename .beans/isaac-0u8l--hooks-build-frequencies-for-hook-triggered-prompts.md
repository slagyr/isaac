---
# isaac-0u8l
title: 'Hooks: build frequencies for hook-triggered prompts'
status: draft
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:01:46Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

hooks.clj is the most ad-hoc consumer: session-key = (:session-key hook) or 'hook:<name>'; crew = (:crew hook) or 'main'; manual get-or-create. Adopt frequencies: a hook specifies a frequencies map for the session its prompt runs on.

Build the frequencies map from hook config and feed the shared core (isaac.session.frequencies). Blocked-by the frequencies rename.
