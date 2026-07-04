---
# isaac-royn
title: 'Server subsystems must be supervised: a crashed worker thread must not silently wedge the pipeline'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-04T14:35:52Z
updated_at: 2026-07-04T15:18:15Z
---

## Problem

A single subsystem thread dying silently wedges the whole server. The process stays "running" but stops doing work, with no detection, no restart, no loud signal.

## Evidence (2026-07-04, zanebot overnight)

~11pm the Discord gateway reader-loop crashed (`discord.gateway/reader-loop-failed: IOException Output closed`). Simultaneously the hail delivery worker stopped processing (orphaned inflight, see the inflight-recovery bean) and comm delivery stopped (Discord silent). The server process stayed up (pid alive, serving only bot HTTP scans) for ~9 hours while doing zero real work. Both hail delivery AND Discord failed together — a subsystem crash cascaded / co-occurred with worker death and nothing recovered.

## Desired behavior

Long-lived server subsystems — the hail delivery worker, each comm gateway (discord/imessage), cron — run under supervision: if a subsystem thread/future dies, it is detected and restarted (with backoff), and the death is logged at ERROR + surfaced in health. A crash in one subsystem (e.g. the discord gateway) must not stop unrelated subsystems (hail delivery). Add a health/liveness surface so `isaac service status` reports a dead subsystem instead of showing green while wedged.

## Scope

isaac-agent / isaac-server (subsystem lifecycle + supervision), isaac-hail (delivery worker as a supervised component), isaac-discord (gateway as a supervised component; overlaps isaac-wtg8 which hardens the gateway itself). This bean is the GENERIC supervision layer; wtg8 is the gateway-specific hardening.

## Proposed acceptance

- A supervised worker that throws is restarted (with backoff) and logs the failure at ERROR; work resumes.
- Killing one comm gateway does not stop the hail delivery worker (isolation).
- `service status` (or a health endpoint) reports a subsystem as unhealthy when its thread is dead.

Priority: HIGH — this is the overnight-outage root cause (silent whole-pipeline wedge).


---

## Resolution (unverified — for verifier)

Implemented the **generic supervision layer** in isaac-server `main` commit **fb0731c**.

**What landed (isaac-server):**
- `isaac.service.protocol/Supervised` — an optional protocol (`alive?`) a
  long-lived `Service` implements to opt into supervision. Non-supervised
  services behave exactly as before.
- `isaac.service.supervisor` — polls the supervised subset of started services
  (`supervise-once!` is a pure, deterministic pass; a daemon loop drives it every
  `poll-ms`, crash-proof). On a dead probe: logs `:service/died` at ERROR,
  restarts via stop+start with **exponential backoff (1s→30s cap)** and a
  **circuit breaker** (`:service/supervision-exhausted` + status `:down` after
  `default-max-restarts` = 5). Recovery after `stable-polls` healthy checks
  resets the breaker. Per-service isolation: one service's death/crash never
  blocks another (each supervised independently; a probe that throws is caught
  and the others still run).
- Boot wiring (`server/app.clj`): supervisor starts right after `start-all!` and
  stops before `stop-all!`, reading `service-runtime/started-services`.
- Health surface (`server/status.clj` `/status`): reports `:subsystems` health;
  overall `"degraded"` while a subsystem is restarting, `"unhealthy"` + **HTTP
  503** once one is circuit-broken `:down` — no more green-while-wedged.

**How the proposed acceptance is met (via the generic mechanism, spec-proven):**
- *A supervised worker that throws is restarted (backoff) + ERROR; work resumes* →
  supervisor_spec "restarts a supervised service that dies…" + "waits out the
  backoff…" + "resets the breaker after recovery".
- *Killing one comm gateway does not stop another subsystem (isolation)* →
  supervisor_spec "restarts only the dead service, leaving healthy siblings
  untouched" + "keeps supervising the others when one service's probe throws".
- *service status reports a subsystem unhealthy when its thread is dead* →
  status_spec "degraded while restarting" + "unhealthy with 503 once down".

**Scope decision (flagged) — generic layer now, per-subsystem opt-in as follow-ups.**
The only current `:isaac.server/service` implementations are the comm gateways
(`:discord`, `:imessage`); the hail delivery worker and cron are scheduler tasks,
not Services. A *meaningful* `alive?` for the discord gateway (detecting the dead
reader-loop) is exactly isaac-wtg8's hardening — wiring it here before wtg8 would
give an `alive?` that returns true while wedged. So this bean delivers the generic
supervision mechanism (fully tested), and the real per-subsystem opt-in is
sequenced as follow-ups. **NOT YET SUPERVISED (needs beans/approval):**
1. discord gateway → implement `Supervised` once wtg8 exposes reader-loop liveness.
2. imessage gateway → same.
3. hail delivery worker + cron → promote to supervised workers with a heartbeat
   liveness signal (coordinates with the inflight-recovery bean).
Until at least one real subsystem opts in, the overnight outage class is not yet
prevented end-to-end — the mechanism is in place and ready.

**Verification:** isaac-server `bb ci` — config-bypass-lint ok; **169 spec
examples / 316 assertions, 0 failures**; **55 feature examples / 135 assertions,
0 failures**. `bb lint` src clean.
