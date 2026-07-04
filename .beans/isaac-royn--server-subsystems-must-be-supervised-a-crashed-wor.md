---
# isaac-royn
title: 'Server subsystems must be supervised: a crashed worker thread must not silently wedge the pipeline'
status: in-progress
type: bug
priority: high
created_at: 2026-07-04T14:35:52Z
updated_at: 2026-07-04T15:05:58Z
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
