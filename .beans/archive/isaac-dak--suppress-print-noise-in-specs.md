---
# isaac-dak
title: "Suppress print noise in specs"
status: completed
type: bug
priority: high
created_at: 2026-04-07T23:30:55Z
updated_at: 2026-04-07T23:36:19Z
---

## Description

bb spec output is polluted with CLI usage text, auth command output, session messages, and compaction logs. Specs that exercise CLI commands (main, auth, chat) and session operations are printing to stdout instead of capturing output. Every spec that calls functions which print should use with-out-str or redirect *out* so that bb spec output is clean — only dots and the summary line.

