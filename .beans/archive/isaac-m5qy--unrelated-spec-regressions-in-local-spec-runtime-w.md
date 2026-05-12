---
# isaac-m5qy
title: "Unrelated spec regressions in local spec-runtime work"
status: scrapped
type: bug
priority: normal
created_at: 2026-05-10T21:55:00Z
updated_at: 2026-05-11T18:09:14Z
---

## Description

A separate local spec-runtime optimization worktree change set currently causes unrelated bb spec failures outside bead isaac-xo9p scope. Failures observed in spec/isaac/bridge/chat_cli_spec.clj, spec/isaac/session/store/file_impl_spec.clj, spec/isaac/cron/scheduler_spec.clj, and spec/isaac/session/cli_spec.clj. This follow-up tracks restoring full bb spec green for that work without blocking the cancellation-hook bead.

