---
# isaac-ceeq
title: Discord gateway double-IDENTIFY on reconnect after opcode 7
status: in-progress
type: bug
priority: normal
tags:
    - discord
created_at: 2026-07-02T16:59:59Z
updated_at: 2026-07-02T17:58:21Z
---

## Symptom
Discord gateway heartbeats stop being emitted (no :discord.gateway/liveness or :heartbeat) even though the overall isaac service is up. The client gets into a dead state after certain disconnects/reconnects. Seen on zanebot 2026-07-01 ~22:01Z and similar cycles on 06-30.

In logs:
- opcode 7 (reconnect-requested)
- disconnects (4000 + 1000)
- reconnect attempt (identify)
- hello + TWO identifies
- ready
- heartbeat-cancelled "Already authenticated."
- reader-loop-failed "Output closed"

Result: no more heartbeats until full restart of the service/client.

## Root cause (in isaac-discord)
In src/isaac/comm/discord/gateway.clj:

- do-reconnect! (called for opcode 7, close codes, etc.) does:
  start-reader-loop!
  send-resume! or send-identify!   <--- eager send

- handle-hello! (op 10, which Discord always sends immediately on connect) always does:
  schedule-heartbeats!
  send-identify!   <--- unconditional

Normal initial connect! path works (no pre-send, hello triggers the single identify).

Reconnect path (after opcode 7 or non-fatal closes) does the double send. Discord rejects the second auth.

See:
- handle-reconnect-op! + on-close! + attempt-reconnect! + do-reconnect!
- handle-hello! lines ~138-149
- send-identify! / send-resume!

## Scope
isaac-discord gateway reconnect logic. Affects resilience of Discord comm (heartbeats, liveness, session continuity).

## Acceptance criteria (runnable)
- [ ] Reproduce the failing sequence in gateway_spec or feature (opcode 7 or 1000 close while "connected" → reconnect) and observe exactly one auth payload (op 2 or 6) sent, not two.
- [ ] After the reconnect, the client reaches :ready, schedules heartbeats, and emits :discord.gateway/liveness (no "Already authenticated" cancel or reader-loop-failed).
- [ ] Initial connect path (HELLO → single IDENTIFY) is unchanged.
- [ ] All existing reconnect specs in gateway_spec.clj still pass (re-identify, RESUME for resumable codes, etc.).
- [ ] No duplicate auth messages in the sent payloads for reconnect cases.
- [ ] (stretch) Add a dedicated spec or feature step exercising the opcode-7-while-ready path.

## 2026-07-02 update
Scenario added @wip to feature file (approved "good").
Ledger: reuses existing HELLO/READY/IDENTIFY/RESUME/connected/HEARTBEAT + log checks; new: "Discord sends opcode 7", "sends exactly one RESUME or IDENTIFY" (for count), log absence assertions.
This makes the key AC runnable once supporting step(s) added.

## References
- Prod logs: server-20260701.log around 22:01:40 (reconnect-requested → double identify → Output closed)
- Similar pattern in earlier 06-30 cycles
- Code: isaac-discord/src/isaac/comm/discord/gateway.clj (do-reconnect!, handle-hello!, on-close!)
- Specs: isaac-discord/spec/isaac/comm/discord/gateway_spec.clj (reconnect tests)
- Feature: isaac-discord/features/comm/discord/gateway.feature

## Decision (2026-07-02)
Approved the following @wip scenario for the opcode 7 reconnect case (one at a time per playbook). 

Added to gateway.feature as first new scenario.

Step ledger:
- Reuses: faked gateway, config table, connects, sends HELLO:, sends READY:, sends IDENTIFY:, sends RESUME:, is connected, sends HEARTBEAT, log checks.
- New steps needed: "Discord sends opcode 7", precise "sends exactly one RESUME or IDENTIFY" (count + kind), log absence for the error strings.
- This covers the exact prod failure path and proves clean single-auth reconnect + resumed heartbeats.

Scenario:
  @wip
  Scenario: reconnects cleanly after opcode 7 without duplicate auth
    Given the Discord Gateway is faked in-memory
    And config:
      | comms.discord.discord/token | test-token |
    When the Discord client connects
    And Discord sends HELLO:
      | heartbeat_interval | 45000 |
    And Discord sends READY:
      | session_id | fake-session |
    And Discord sends opcode 7
    And the reconnect delay passes
    Then the Discord client sends exactly one RESUME or IDENTIFY
    And the Discord client is connected
    And the Discord client continues sending HEARTBEATs
    And no "Already authenticated" or reader-loop failure is logged


---

## Resolution (unverified — for verifier)

Fixed in isaac-discord `main` commit **8fdcfd4** (base 5743552).

**Root cause confirmed exactly as the bean describes.** On reconnect,
`do-reconnect!` eagerly sends IDENTIFY/RESUME on the new socket, and then the
reconnected socket's HELLO (op 10, which Discord always sends) hit
`handle-hello!`'s *unconditional* `send-identify!` — a second auth. Discord
rejects it, the reader loop dies, heartbeats stop until restart.

**Fix (minimal, `src/isaac/comm/discord/gateway.clj`):** track `:auth-sent?`
per connection — `send-identify!`/`send-resume!` set it; `handle-hello!` only
sends IDENTIFY `when-not` it is already set. A fresh `connect!` starts with the
flag unset, so the initial HELLO still drives the single IDENTIFY; every
reconnect has already eagerly authed, so its HELLO no longer re-auths.

**Why this shape (not the protocol-clean alternative):** the "correct" Discord
design is auth-on-HELLO only (drop the eager send in `do-reconnect!`). That
would rewrite the 7 existing reconnect specs that assert the eager send, which
AC #4 ("all existing reconnect specs still pass") forbids. This fix keeps the
eager send and all 7 specs green.

**Tests:**
- New `gateway_spec` example "reconnects after opcode 7 without a duplicate auth
  when the new HELLO arrives" — reproduces the double (red before fix: got 2
  auths; green after: 1), and asserts heartbeats resume.
- The approved `@wip` scenario is landed **un-@wip** in
  `features/comm/discord/gateway.feature` with one necessary correction: it now
  delivers the **post-reconnect HELLO** (`And Discord sends HELLO:` after "the
  reconnect delay passes"). Without it the bug cannot reproduce and heartbeats
  cannot resume (they are only rescheduled in `handle-hello!`) — this realizes
  AC #1's stated intent. The `Then` is phrased "...one RESUME or IDENTIFY **on
  reconnect**" (scoped past the initial IDENTIFY); dropped the "is connected"
  line (a resume never re-emits READY, so `connected?`/`:ready` wouldn't hold —
  running + heartbeat resumption is the right liveness check here).
- New steps: "Discord sends opcode 7", "the reconnect delay passes", "...sends
  exactly one RESUME or IDENTIFY on reconnect", "...continues sending
  HEARTBEATs", `no "…" reconnect failure is logged`.

**Verification:** `bb ci` in isaac-discord — 79 spec examples / 172 assertions,
50 feature examples / 108 assertions, 0 failures.

**Note:** the bean's scenario text above (still `@wip`) is the original draft;
the landed scenario reflects the corrections described here.
