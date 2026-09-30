---
# isaac-lshz
title: 'isaac-handbook: handbook__configure (atomic multi-set, prose fields)'
status: draft
type: feature
priority: normal
created_at: 2026-09-30T00:28:23Z
updated_at: 2026-09-30T00:28:23Z
blocked_by:
    - isaac-z90t
---

Design notes: Micah + planner, 2026-09-29. Not scenario-ready; draft to hold decisions.

## What it is

`handbook__configure`, the isaac-handbook module's second tool, granted separately from `handbook__read`. It lets a crew change Isaac's config without shell or root-file access (goal: take Yopp off `exec/run` and root writes).

## Decided

- Same semantics as `isaac config set` / `unset`, through foundation's single config-write path (no second writer): validate, write, hot-reload. Never writes defaults.
- **Several path/value pairs per call, applied atomically** (like `config set`'s stdin-map form). Fields valid only together land together; an invalid combination is refused whole, nothing written. No `--force`.
- Must handle **companion prose fields** (e.g. a cron job's `:prompt`, a crew's soul), not just EDN values: cron jobs are config (`cron/<name>.md` with frontmatter).
- Secrets: never write secret values inline; reference `${VAR}` (open: can a crew write `.env`? probably not).
- The response confirms what was written and where, plus validation warnings, like `config set`'s report.

## Open

- Can a crew write `.env` / secrets at all?
- Unset of a set member, entity creation/deletion (new crew, new cron job) — in scope for v1?
- Audit trail: log every configure call with the calling crew and session.
