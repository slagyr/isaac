---
# isaac-7ngj
title: Cron falsely records success after failed scheduled turn
status: draft
type: bug
priority: high
created_at: 2026-08-11T17:02:28Z
updated_at: 2026-08-11T18:18:40Z
---

Cron state can falsely record `:last-status :succeeded` even when the scheduled turn failed before any tool execution.

Observed on zanebot 2026-08-11:
- `health-checkin` fired at 09:00 America/Phoenix.
- Server log shows session `keen-narwhal` hit an Anthropic HTTP 400 and `:chat/provider-walled`.
- No tool calls ran, so no iMessage was sent.
- But `~/.isaac/cron.edn` recorded:
  - `:last-run "2026-08-11T09:00:00-0500"`
  - `:last-status :succeeded`
  - `:last-error nil`

That makes cron health look green when the job actually failed, which hides operational problems and breaks trust in cron state.

## Acceptance
- [ ] When a cron-fired turn fails before producing a successful assistant result, cron state records `:last-status :failed`.
- [ ] `:last-error` captures a useful failure summary for provider/tool/dispatch errors.
- [ ] Success is recorded only after a genuinely successful cron turn.
- [ ] Coverage exists for the failing path that previously wrote false success.

## Notes
- This surfaced while investigating missing morning health updates.
- Separate operational follow-ups exist for model swaps and migrating `tempest-vault-sync` from OpenClaw to Isaac; they are not part of this bug.

## Investigation note

Code inspection on current `isaac.cron` shows `fire-job!` calls `bridge/dispatch!` synchronously and records `:last-status` from the returned result (`:failed` when `:error` is present). So the bug is likely **not** that cron records success immediately upon trigger without waiting for the turn. More likely: a provider-failed turn is returning a non-error result to cron, or a later layer is swallowing the failure.

That means the bean needs root-cause confirmation before dispatching implementation.
