---
# isaac-nfch
title: 'ANSI color/formatting does not survive the cli proxy: remote output renders plain'
status: todo
type: bug
created_at: 2026-07-07T18:28:46Z
updated_at: 2026-07-07T18:28:46Z
---

Observed testing isaac remote (2026-07-07): output that renders with color/zebra striping locally (e.g. sessions list) arrives plain through the /cli pipe. Likely cause: the cli-server-spawned subprocess sees a pipe, not a TTY, so the command disables color at the source — the proxy faithfully relays colorless bytes to a real local TTY. Fix direction (worker to confirm): the proxy knows its local stdout IS a TTY; forward that fact (e.g. a color/tty hint in the start frame or FORCE-color env on the spawned subprocess) so the remote command emits ANSI when the ultimate destination is a terminal — and does NOT when the local side is piped. Scope check: verify how isaac formatting decides color (TTY sniff vs flag) before choosing the seam.
