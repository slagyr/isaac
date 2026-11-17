---
# isaac-mhvd
title: 'isaac-cli-proxy CI red since w0yx: integration ''a remote command runs on the server and streams back'' exits 1'
status: completed
type: bug
priority: high
created_at: 2026-11-16T23:27:48Z
updated_at: 2026-11-17T00:00:10Z
---

isaac-cli-proxy CI has been red since the w0yx commits (253ff0e, 76b97f1, 36898ac, fa9848e). Release 0.2.0 shipped on a red build. Micah, 2026-11-16: "please get it fixed".

The failure: `bb ci` → `bb features-slow` → `features/integration.feature`, scenario "a remote command runs on the server and streams back". `isaac remote ws://localhost:${server.port}/cli -- --version` exits 1, expected 0 (13 examples, 1 failure). The other 12 slow scenarios pass.

On Micah's Mac the same scenario passes (`bb gherclj features/integration.feature:<line>`, 1 example, 0 failures). It printed "isaac 0.1.29 — hello", an old foundation version, so the local run may resolve different deps than CI (dev-local siblings, a stale gitlib, or `-Sforce`). Find the difference first. Run it the way CI does (`bb -Sforce gherclj -t slow -t ~wip features/integration.feature`, `ISAAC_GIT=1`, a clean `.cpcache`), and capture the remote stderr; the step hides it today.

Likely suspects: w0yx made `--version` or `--remote` route differently, or the scenario's server lacks the cli-server module or a token setup that CI's pins now need.

Acceptance:
- `bb ci` green on GitHub for isaac-cli-proxy main.
- If the cause is product behavior, not the test setup, stop and note it on the bean. That needs a scenario before landing.
- When this scenario fails, the remote's stderr shows in the failure output.

## Implementation notes (2026-11-16)

Cause: the integration fixture registered `remote` in the process-local CLI registry, but the real `isaac is run with` CLI refreshes its registry from configured modules. On GitHub's clean runner the proxy module was not discovered; `remote` returned 1 with `Unknown command: remote` on stdout (stderr empty). On the Mac, sibling/local module discovery masked the missing fixture configuration. The failing GitHub run 43980773837 captured this output.

Fixed the test setup by explicitly configuring `:isaac.cli-proxy` at the fixture's own checkout path before invoking the CLI. Added an integration exit assertion that reports both remote stderr and stdout on mismatch (and waits for interactive client completion), with a focused diagnostic spec. No product behavior changed; no feature contract edited. `ISAAC_GIT=1 bb ci` green locally (13 slow examples, 38 assertions); GitHub CI green on branch commit `06bed71`, run 43981860860. `bb bean-gate verify isaac-mhvd` exit 2: no feature-baseline; awaiting independent verification.

## Checkpoint (2026-11-16)

Done: commits `ccc7e9a`, `43cc122`, `a3eecae`, `06bed71` pushed to `bean/isaac-mhvd`; `ISAAC_GIT=1 bb ci` green locally and GitHub branch CI run 43981860860 green. The fixture fix is at `spec/isaac/cli_proxy/integration_steps.clj:159`; failure diagnostics are at `spec/isaac/cli_proxy/integration_steps.clj:236`.
Next: ungated verification/landing of the implementation branch, then confirm GitHub CI on `main` and record the landed SHA. Resume from `spec/isaac/cli_proxy/integration_steps.clj:159` if the verifier finds a test or fixture issue.


## Landed on main (2026-11-16)

main-sha: isaac-cli-proxy d679f830e352af0e689052237345888250835171

Independent verification: `ISAAC_GIT=1 bb ci` green on landed tree (35 specs/0 failures; 13 slow examples/0 failures, 38 assertions, repeated across slow gate). GitHub main CI run 43983593941 green at the landed SHA. No feature files or production code changed; the fixture explicitly configures the proxy, and remote exit assertion includes stderr/stdout on failure.
