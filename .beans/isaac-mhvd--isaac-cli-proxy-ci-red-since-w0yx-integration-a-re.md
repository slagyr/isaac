---
# isaac-mhvd
title: 'isaac-cli-proxy CI red since w0yx: integration ''a remote command runs on the server and streams back'' exits 1'
status: draft
type: bug
priority: high
created_at: 2026-11-16T23:27:48Z
updated_at: 2026-11-16T23:27:48Z
---

isaac-cli-proxy CI has been red since the w0yx commits (253ff0e, 76b97f1, 36898ac, fa9848e). Release 0.2.0 shipped on a red build. Micah, 2026-11-16: "please get it fixed".

The failure: `bb ci` → `bb features-slow` → `features/integration.feature`, scenario "a remote command runs on the server and streams back". `isaac remote ws://localhost:${server.port}/cli -- --version` exits 1, expected 0 (13 examples, 1 failure). The other 12 slow scenarios pass.

On Micah's Mac the same scenario passes (`bb gherclj features/integration.feature:<line>`, 1 example, 0 failures). It printed "isaac 0.1.29 — hello", an old foundation version, so the local run may resolve different deps than CI (dev-local siblings, a stale gitlib, or `-Sforce`). Find the difference first. Run it the way CI does (`bb -Sforce gherclj -t slow -t ~wip features/integration.feature`, `ISAAC_GIT=1`, a clean `.cpcache`), and capture the remote stderr; the step hides it today.

Likely suspects: w0yx made `--version` or `--remote` route differently, or the scenario's server lacks the cli-server module or a token setup that CI's pins now need.

Acceptance:
- `bb ci` green on GitHub for isaac-cli-proxy main.
- If the cause is product behavior, not the test setup, stop and note it on the bean. That needs a scenario before landing.
- When this scenario fails, the remote's stderr shows in the failure output.
