---
# isaac-i0g9
title: Release auto-bump dispatch doesn't reach the tap (manual tap trigger required)
status: todo
type: bug
created_at: 2026-06-19T19:51:00Z
updated_at: 2026-06-19T19:51:00Z
---

7tle's "auto-bump" is not actually automatic. On the v0.1.4 release, foundation's
release.yml ran successfully and (line 45-46) calls:
  gh api repos/slagyr/homebrew-tap/dispatches -f event_type=foundation-release
but the tap's bump-isaac.yml (repository_dispatch: [foundation-release]) never
fired — the formula stayed at v0.1.3. Almost certainly the foundation workflow's
default GITHUB_TOKEN lacks WRITE/dispatch permission on the SEPARATE
slagyr/homebrew-tap repo (cross-repo dispatch needs a PAT with repo scope, stored
as a secret, used for the gh api call).

Workaround used: trigger the tap bump directly —
  gh workflow run bump-isaac.yml -R slagyr/homebrew-tap -f tag=vX.Y.Z

## Fix

• Add a PAT (repo scope on homebrew-tap) as a secret in isaac-foundation and use
  it for the `gh api .../dispatches` call (not the default GITHUB_TOKEN).
• Verify end-to-end: a foundation release auto-bumps the tap formula with NO
  manual tap trigger.
• Also: release.yml is workflow_dispatch-only; the push-triggered "Release"
  run fails (0s). Decide whether tag-push should auto-run release, or keep it
  manual dispatch.

## Relationships
• Follow-up to isaac-7tle (which claimed hands-off auto-bump).
