---
# isaac-r2cz
title: 'imessage Full Disk Access: detect + guide user (open the System Settings page)'
status: draft
type: feature
priority: normal
created_at: 2026-06-20T15:26:29Z
updated_at: 2026-06-20T15:39:53Z
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


## Reframe (2026-06-20, with Micah) — declarative preconditions, not install-hooks/doctor

Status -> DRAFT; explore later. Micah likes the "preconditions" direction.

Problem with the original "trigger at service install + doctor": service install
lives in isaac-server, FDA is imessage-specific -> a backwards module hook (edge
case); and a doctor command is reactive (user must know to run it = a crutch).

Better shape — generalize to MODULE-DECLARED PRECONDITIONS:

1. Modules DECLARE preconditions in their manifest (not code hooks). e.g.:
     :preconditions [{:id     :full-disk-access
                      :check  isaac.comm.imessage/chat-db-readable?
                      :detail "iMessage needs Full Disk Access to read chat.db"
                      :remedy {:open "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles"
                               :hint "Add the isaac launcher under Full Disk Access"}}]
   Generic foundation capability — any module (comm needing a binary, provider
   needing an API key) uses the same mechanism.

2. Foundation CHECKS declared preconditions at boot and records a failure as a
   PERSISTENT "needs attention" state (not just a log line).

3. SURFACE proactively, no doctor required:
   • the comm's runtime error already carries the remedy (isaac-u129),
   • the NEXT interactive `isaac` command prints a one-line banner
     ("⚠ iMessage needs Full Disk Access — run `isaac fix full-disk-access` or
     open Settings"), like brew/git nudges.
   `isaac fix <id>` / `isaac doctor` become optional "do it now", not the path.

Flip: DECLARE once -> CHECK at boot -> SURFACE on next human touch. Kills the
install-hook coupling and the doctor-crutch.

## To explore later
• precondition schema in the manifest berth; check fn signature; severity levels.
• where the "needs attention" state lives (server health endpoint / a state file
  the CLI reads) so any CLI invocation can surface it.
• `isaac fix <id>` UX (interactive open + verify).
• imessage Full Disk Access = the first consumer.
