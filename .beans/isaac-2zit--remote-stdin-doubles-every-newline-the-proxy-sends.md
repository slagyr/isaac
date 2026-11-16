---
# isaac-2zit
title: 'Remote stdin doubles every newline: the proxy sends line+newline and cli-server appends another'
status: draft
type: bug
priority: normal
tags:
    - cli
created_at: 2026-11-16T18:35:18Z
updated_at: 2026-11-16T18:35:18Z
---

Repos: **isaac-cli-server** (`dispatch.clj` `-send-stdin!`), maybe **isaac-cli-proxy** (`pump-stdin!`). Found while tracing isaac-2z7z.

## Observed

The proxy reads stdin line by line and sends each as `line + "\n"`; cli-server's `-send-stdin!` does `(.put queue (str data "\n"))`. So every line a remote command reads is followed by an extra blank line: `printf 'a\nb\n' | isaac remote zane -- <cmd>` gives the command `a\n\nb\n\n`. ACP rejected those blank lines (isaac-2z7z); any other stdin-reading remote command gets corrupted input.

## Fix

Remote stdin reaches the command byte-for-byte: one side owns the newline. Prefer cli-server passing frame data through unchanged (frames are bytes, not lines) and the proxy sending what it read; keep a final line without a trailing newline as-is.

Note: `features/cli/endpoint.feature:19` (fx-echo, `sends stdin "hello pipe"`) leans on the appended newline — it changes with this bean.

## Acceptance

Draft — scenarios to come: stdin `a\nb\n` through the handler reaches the command unchanged; a final line with no newline stays without one; end-to-end in `isaac-cli-proxy/features/integration.feature`.
