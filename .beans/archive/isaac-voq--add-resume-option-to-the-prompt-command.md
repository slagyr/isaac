---
# isaac-voq
title: "Add --resume option to the prompt command"
status: completed
type: feature
priority: low
created_at: 2026-04-15T14:01:19Z
updated_at: 2026-04-16T16:25:36Z
---

## Description

The prompt command currently creates a new session or uses --session to target a specific one. It should also support --resume to pick up the most recent session, matching the acp command's behavior.

## Design

Add -R/--resume flag to prompt command. Uses storage/most-recent-session to find the latest session, falls back to creating a new one if none exist. Feature scenarios added to features/cli/prompt.feature.

