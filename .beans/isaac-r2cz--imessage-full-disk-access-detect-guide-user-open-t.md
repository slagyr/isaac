---
# isaac-r2cz
title: 'imessage Full Disk Access: detect + guide user (open the System Settings page)'
status: todo
type: feature
created_at: 2026-06-20T15:26:29Z
updated_at: 2026-06-20T15:26:29Z
---

imessage receiving needs macOS Full Disk Access — the launchd server can't read
~/Library/Messages/chat.db without it. Post-u129 the runtime error is clear
(:imsg.client/db-path-unavailable "... Full Disk Access"). Make it ACTIONABLE.

## Behavior

• Detect: a preflight that tries to read chat.db; permission failure => FDA needed.
• Guide: print the exact steps AND offer to open the macOS FDA page directly:
    open "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles"
  Name the binary to add (the bb/isaac launcher the launchd service runs).
• Embed the remediation in the runtime :imsg.client/db-path-unavailable error so
  `isaac logs` shows the fix inline.

## When (Micah leaned "on install"; refined)

NOT at module install (config-only; FDA not yet relevant). Trigger at:
1. `isaac service install` — deploy time; the background service will need FDA.
   Run the check; if missing, instruct + interactively offer to open the page.
2. On-demand `isaac doctor` (or `isaac comm imessage check`).
Plus the embedded error hint (always).

## Relationships
• Follows isaac-u129 (which surfaced the FDA cause). macOS-only path.
