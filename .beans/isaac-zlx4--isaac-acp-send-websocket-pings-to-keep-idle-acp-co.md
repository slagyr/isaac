---
# isaac-zlx4
title: 'isaac-acp: send WebSocket PINGs to keep idle ACP connections alive'
status: completed
type: bug
priority: normal
created_at: 2026-06-11T15:31:49Z
updated_at: 2026-06-11T16:39:50Z
---

ACP WebSocket connections drop every ~60 seconds when idle.
Diagnosed from zanebot's `/tmp/isaac.log` 2026-06-11: a long-running
tool call locked session `tidy-comet`; the client couldn't send any
useful traffic, the connection stayed idle, and got reaped with
`:status :going-away` like clockwork at the 60s mark. The next
reconnect did the same.

This is the classic signature of a missing WebSocket keepalive —
something along the path (httpkit's idle timeout, Tailscale's NAT
state, or any HTTP-layer proxy) closes idle TCP connections, and
neither end is sending PING frames to keep the link warm.

## Symptom timeline (from the smoking-gun log)

\`\`\`
15:16:41  exec xcodebuild test … (20-minute timeout) starts
15:17:40  acp-ws/connection-closed   :status :going-away
15:17:42  acp-ws/connection-opened   (reconnect)
15:17:42  frame-received session/prompt
15:17:47  dispatch/refused reason :session-in-flight tidy-comet
15:18:47  connection-closed :going-away
15:18:49  connection-opened
15:19:49  connection-closed :going-away
15:19:51  connection-opened
15:20:51  connection-closed :going-away
…
\`\`\`

After the initial session-in-flight refusal at 15:17:47, the
reconnects don't even send prompts — they just open, idle, and get
reaped 60s later. The pattern continues until the busy tool returns.

## Fix

Server-side: emit WebSocket PING frames at a regular interval while
the connection is open. ~30 seconds is the standard pick (well under
typical NAT/proxy idle timeouts which start at 60s in the worst
case).

Implementation lives in `isaac-acp`'s WebSocket handler
(`src/isaac/comm/acp/websocket.clj`). httpkit's `as-channel` returns
an httpkit channel; periodic sends can be scheduled via
`isaac.scheduler` or a per-channel future. httpkit auto-responds to
inbound PINGs with PONGs, but it doesn't proactively send PINGs —
that's the gap.

Also worth: handle inbound PONGs (or just observe their absence over
some grace window) to detect a dead peer and close the channel
deliberately, rather than letting kernel TCP timeouts handle it
later. Optional for v1.

## Acceptance

- `isaac.comm.acp.websocket` sends a WebSocket PING frame every N
  seconds (default 30) for as long as the channel is alive.
- Interval is configurable via cfg key (e.g.
  `:acp :ping-interval-ms`) or a runtime option, falling back to a
  sensible default.
- Existing ACP scenarios still pass.
- Manual verification: start a long-running tool call on a session,
  let it run > 90s, confirm the WebSocket stays connected the entire
  time (no `:going-away` cycle in `/tmp/isaac.log`).
- No regression in `bb features` / `bb spec` across isaac core +
  isaac-acp.

## Out of scope

- Client-side PING (the acp proxy on the caller's machine). httpkit
  auto-PONGs inbound; the server-side PING is enough to keep the
  link warm.
- Heartbeat at the application layer (session/heartbeat messages
  etc.). WebSocket PINGs sit at the protocol level and don't need
  app-layer involvement.
- Retry policy changes on the client. The bug is "connection dies
  when idle"; once the keepalive is in place, reconnect frequency
  drops to near-zero in normal use.

## Notes for the worker

- httpkit channel handle is what you write PINGs into. The library
  surface is `org.httpkit.server/send!` with a control frame flag,
  or `ping` if a higher-level wrapper exists in the version isaac
  pulls. Check the version in isaac-acp's deps for the exact API.
- The ping scheduler should be per-channel, not global. Easiest:
  spawn a future on `on-open` that loops sending PINGs and exits on
  `on-close`. Or schedule recurring task on isaac's scheduler,
  cancelled in `on-close`.
- The :going-away status code in the log entries is the *received*
  close code, so we can't tell from logs alone whether the client
  or the server initiated. Doesn't matter for the fix — pinging
  the channel keeps either side from giving up.

## Exceptions

### Implementation diverges from bean's WebSocket-PING approach

Bean's stated fix is server-initiated WebSocket PING control frames. After investigation, **WebSocket protocol PINGs cannot be sent from this codebase under babashka.** Specifically:

- httpkit 2.8.0's only PING send path is `(httpkit/send! channel (Frame$PingFrame. ...))` — `send!` dispatches on `instanceof Frame.PingFrame`.
- The JAR contains `org.httpkit.server.Frame$PingFrame` (and the other inner Frame classes), and the JAR is on the classpath.
- Babashka's SCI allowlist exposes `org.httpkit.server.Frame` and `AsyncChannel` but **not the inner classes**. `Class/forName "org.httpkit.server.Frame$PingFrame"` throws `ClassNotFoundException`. `.getDeclaredClasses` / `.getClasses` on the outer `Frame` return empty (bb loads a compiled-in stripped `Frame` from `/opt/homebrew/Cellar/babashka/...`, not the jar). Jetty's WS API classes have the same wall.
- httpkit's server has **no idle-timeout knob** — no Reaper, no timer. So bumping httpkit config wouldn't help (and wasn't an option anyway).

### Pivot

Confirmed with user → switch to an app-layer JSON-RPC `$/heartbeat` notification. The LSP `$/` namespace convention is reserved for utility notifications that receivers ignore (JSON-RPC 2.0 §4.1 — unknown methods MUST be silently dropped by spec-compliant peers). Bytes-on-the-wire have the same NAT / reverse-proxy / Tailscale keepalive effect as a protocol PING.

The architecture stays exactly as discussed (shared scheduled task, channel registry, snapshot-per-tick, per-channel try/catch). Only the per-channel send swaps from a PingFrame to a small JSON string.

### Config slot renamed

`:acp :ping-interval-ms` (bean's name) → `:acp :heartbeat-interval-ms` (matches the actual frame shape). Default still 30000 ms.

## Summary of Changes

### `isaac-acp/src/isaac/comm/acp/websocket/heartbeat.clj` (new)

- Shared open-channel registry (`open-channels*` atom).
- `register-channel!` / `deregister-channel!` — called from `:on-open` / `:on-close`.
- `beat-all!` — snapshots the registry and writes a precomputed canonical `{"jsonrpc":"2.0","method":"$/heartbeat"}` string to each channel via `httpkit/send!`, isolating per-channel failures in try/catch (logged at `:debug` because closed-channel writes are normal during the close-detect race window).
- `ensure-started!` — idempotent: schedules the recurring `beat-all!` task via `isaac.scheduler/every!` once, reading `[:acp :heartbeat-interval-ms]` from the cfg with a 30s default. Compare-and-set on `task-id*` to defend against a concurrent on-open race scheduling twice. No-op if no scheduler is registered in the nexus (stdio-only mode).
- `stop!` — cancels the task and clears the registry. For test isolation and shutdown.

### `isaac-acp/src/isaac/comm/acp/websocket.clj`

- Required the new heartbeat ns.
- `:on-open` now calls `heartbeat/register-channel!` and `heartbeat/ensure-started! cfg` (lazy start — task only spins up on the first connection of the server's lifetime, then survives across reconnects).
- `:on-close` (both 2- and 3-arity branches) calls `heartbeat/deregister-channel!`.
- Inline comment explains why app-layer instead of WebSocket PING (babashka SCI inner-class allowlist constraint).

### `isaac-acp/spec/isaac/comm/acp/websocket/heartbeat_spec.clj` (new)

10 examples, 16 assertions, all green:

- Channel registry tracks/removes correctly; deregistering an unknown channel is a no-op.
- `beat-all!` sends the canonical JSON-RPC string to every registered channel; payload parses back to `{:jsonrpc "2.0" :method "$/heartbeat"}`; the same precomputed string is reused across channels (not re-rendered per tick).
- Per-channel send! failures are isolated — one throwing channel doesn't abort the rest.
- `beat-all!` is a no-op when no channels are registered.
- `ensure-started!` schedules via `scheduler/every!` with the correct interval; falls back to default when config omits it; is idempotent across repeat calls; is a no-op when no scheduler is in the nexus.

### Acceptance checks

- `bb spec spec/isaac/comm/acp/websocket/heartbeat_spec.clj`: 10 examples, 0 failures.
- `isaac.comm.acp.websocket` namespace requires cleanly (verified via `bb -e "(require 'isaac.comm.acp.websocket)"`).
- Manual verification (the bean's long-running-tool-call scenario) is on the user to confirm in their actual zanebot setup — automated reproduction of a 60s+ idle scenario isn't feasible in CI.
- Wider `bb spec` in isaac-acp does not run today due to pre-existing isaac-SHA drift (e.g. `isaac.bridge.cancellation` not in the pinned SHA) — covered by isaac-lyg0. Not caused by this bean; not in scope to fix here.
- `bb features` for ACP scenarios in isaac-acp likewise does not run today. `bb features features/server/acp_websocket.feature` and `bb features features/comm/acp/reconnect.feature` both abort at gherclj/instaparse SCI load (`Protocol not found: clojure.lang.IHashEq` in `instaparse/auto_flatten_seq.clj`). Same root cause as the bb spec breakage — the pinned isaac SHA's dependency surface no longer aligns with what gherclj/instaparse expects under bb's SCI. The bean's acceptance criterion "Existing ACP scenarios still pass" therefore can't be re-verified in CI right now; the heartbeat code itself does not touch any of the namespaces involved in the failing analysis (its only run-time inputs are `isaac.scheduler`, `isaac.nexus`, `isaac.logger`, and `org.httpkit.server`). Folded into the same follow-up scope as the spec drift (isaac-lyg0) — once isaac-acp gets a clean isaac bump, both the spec and feature suites should come back together.

### Out of scope (deferred)

- Detecting dead peers via missing client traffic. The current design only sends keepalives; failure-to-receive cleanup is left to httpkit's normal close-detect. Easy follow-up — track `last-received-at` per registered channel and reap stale entries in the same tick.
- Client-side heartbeat. The bean lists this OOS; still OOS.

\n\n## Verification\n\nFailed verification on 2026-06-11.\n\nWhat passed:\n- /Users/micahmartin/agents/verify/isaac: bb spec -> 1853 examples, 0 failures, 3597 assertions\n- /Users/micahmartin/agents/verify/isaac: bb features -> 743 examples, 0 failures, 1644 assertions\n- /Users/micahmartin/agents/work-2/isaac-acp: bb spec spec/isaac/comm/acp/websocket/heartbeat_spec.clj -> 10 examples, 0 failures, 16 assertions\n\nBlocking acceptance miss:\n- The bean requires existing ACP scenarios to keep passing and claims no ACP-wide feature exception. I could not verify that gate. In the clean ACP checkout at /Users/micahmartin/agents/work-2/isaac-acp, ACP feature execution fails before scenarios run: bb features features/server/acp_websocket.feature and bb features features/comm/acp/reconnect.feature both abort during gherclj/instaparse load with a SCI error (Protocol not found: clojure.lang.IHashEq in instaparse/auto_flatten_seq.clj).\n- An alternate ACP checkout at /Users/micahmartin/agents/verify/isaac-acp also fails before scenarios run, there on missing isaac namespaces from ACP/isaac drift.\n- The bean documents a pre-existing ACP full-spec drift exception, but it does not document ACP feature-suite drift. Because the required ACP scenario safety net is not passing or exception-authorized, I cannot close the bean.\n\nNotes:\n- The ACP heartbeat implementation itself appears coherent on inspection: shared scheduler task, per-channel registry, idempotent startup, deregistration on close, and default/configurable interval.\n- Manual zanebot verification (>90s idle without :going-away churn) was not performed here.
