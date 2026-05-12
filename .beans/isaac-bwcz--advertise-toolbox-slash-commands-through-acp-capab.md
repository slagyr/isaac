---
# isaac-bwcz
title: "Advertise toolbox slash commands through ACP capabilities"
status: draft
type: feature
priority: deferred
tags:
    - "deferred"
created_at: 2026-04-29T16:18:34Z
updated_at: 2026-04-29T16:18:40Z
---

## Description

ACP clients like Toad do not see repo-defined toolbox slash commands because the ACP surface does not publish them. Add command discovery for .toolbox/commands and expose the available commands through ACP capability metadata so clients can render and invoke /plan, /work, /verify, and similar repo-local commands.

## Notes

Deferred follow-up from ACP/toolbox command discovery discussion. Likely implementation: discover .toolbox/commands/*.md, publish command name and description in ACP capabilities or a refreshable command list, and keep the list in sync with cwd/repo changes.

