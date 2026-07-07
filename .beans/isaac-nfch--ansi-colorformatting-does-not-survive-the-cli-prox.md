---
# isaac-nfch
title: 'ANSI color/formatting does not survive the cli proxy: remote output renders plain'
status: completed
type: bug
priority: normal
created_at: 2026-07-07T18:28:46Z
updated_at: 2026-07-07T19:41:20Z
---

Observed testing isaac remote (2026-07-07): output that renders with color/zebra striping locally (e.g. sessions list) arrives plain through the /cli pipe. Likely cause: the cli-server-spawned subprocess sees a pipe, not a TTY, so the command disables color at the source — the proxy faithfully relays colorless bytes to a real local TTY. Fix direction (worker to confirm): the proxy knows its local stdout IS a TTY; forward that fact (e.g. a color/tty hint in the start frame or FORCE-color env on the spawned subprocess) so the remote command emits ANSI when the ultimate destination is a terminal — and does NOT when the local side is piped. Scope check: verify how isaac formatting decides color (TTY sniff vs flag) before choosing the seam.

## Work Notes

- Confirmed the seam in `isaac.cli.table`: ANSI auto-detect enables color only when `System/console` is present unless `NO_COLOR` disables it, so remote `/cli` subprocesses render plain when spawned behind the proxy.
- `isaac-cli-proxy` branch `isaac-nfch-color-forwarding`, commit `38f3dbd`: proxy start frames now include optional `stdout-tty: true` when local stdout is a TTY; updated protocol notes plus spec/feature coverage. Verified with `bb spec` and `bb features`.
- `isaac-foundation` branch `isaac-nfch-color-env-override`, commit `045992d`: table rendering now honors `FORCE_COLOR` during auto-detect; added corresponding spec. Verified with focused spec coverage and full `bb spec`.
- `isaac-cli-server` branch `isaac-nfch-color-hint`, commit `ff9623f`: `start` accepts the `stdout-tty` hint and passes `FORCE_COLOR=1` to spawned subprocesses when requested; updated canonical `PROTOCOL.md`, specs, and feature helpers. Verified with `bb spec`, `bb features features/cli/endpoint.feature`, and full `bb spec && bb features`.
- `isaac-cli-server` push initially failed after an amended commit due to stale lease state on the remote branch; resolved with an explicit `--force-with-lease=<expected-sha>` push so the verifier can pull the final `ff9623f` tip.
