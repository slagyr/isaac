---
# isaac-ts1d
title: Retire OpenClaw and migrate tempest-vault-sync into Isaac
status: completed
type: task
priority: high
created_at: 2026-08-11T18:14:18Z
updated_at: 2026-08-11T18:22:34Z
---

Stop any lingering OpenClaw runtime/startup paths on zanebot and migrate the active tempest vault sync into Isaac cron.\n\n## Acceptance\n- [x] No OpenClaw service/process auto-start path remains active on zanebot.\n- [x] OpenClaw-specific cron/job state is disabled or archived so it no longer drives behavior.\n- [x] Isaac owns a `tempest-vault-sync` cron job with the migrated prompt.\n- [x] The Isaac cron job is configured to run on crew `tempest` with model override `gpt`.\n- [x] Isaac config validates after the migration.\n\n## Notes\n- User explicitly wants OpenClaw permanently stopped.\n- Tempest sync currently exists only in `~/.openclaw/cron/jobs.json` and is failing on auth/credits.\n- We are not migrating every historical OpenClaw feature here; just shutting it down and moving the needed vault sync.


## Summary of Changes

- Confirmed OpenClaw is not loaded as a service on this host; `openclaw uninstall --service` reports the gateway service is not loaded.
- Found only a shell completion source in `~/.zshrc`; no launch agents, daemons, or active crontab entries auto-starting OpenClaw.
- Disabled the lingering OpenClaw jobs directly in `~/.openclaw/cron/jobs.json` (`git-backup`, `health-checkin`, `tempest-vault-sync`) so the dead config no longer drives behavior.
- Moved health-checkin onto Isaac model override `with-model: grunt`.
- Added Isaac cron job `~/.isaac/config/cron/tempest-vault-sync.md` on crew `tempest` with `with-model: gpt`, carrying over the existing migration prompt.
- Validated Isaac config successfully after the changes.
