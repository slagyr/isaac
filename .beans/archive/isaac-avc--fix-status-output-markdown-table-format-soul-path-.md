---
# isaac-avc
title: "Fix /status output: markdown table format, soul path only"
status: completed
type: bug
priority: normal
created_at: 2026-04-15T18:54:52Z
updated_at: 2026-04-16T06:31:29Z
---

## Description

/status outputs a flat pipe-separated mess instead of the markdown table. Also dumps the full SOUL.md content instead of just the path. Broken in both CLI and ACP paths.

features/bridge/commands.feature — CLI output assertions tightened
features/acp/slash_commands.feature — ACP notification content assertions added

Both assert: markdown table, underlined header, model as alias (provider), soul as path only, no soul content dumped.

## Acceptance Criteria

Both CLI and ACP /status scenarios pass. Toad renders clean markdown table. Soul shows path only.

