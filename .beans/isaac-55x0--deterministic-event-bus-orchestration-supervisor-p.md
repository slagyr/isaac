---
# isaac-55x0
title: Deterministic event bus + orchestration supervisor (PARKED — agent pub/sub vs deterministic pub/sub)
status: draft
type: feature
priority: low
created_at: 2026-07-13T20:44:51Z
updated_at: 2026-07-13T20:44:51Z
---

## Goal (PARKED — design captured 2026-07-13, deliberately NOT embarked upon)

A deterministic event/pub-sub system for Isaac, with a deterministic bean-orchestration SUPERVISOR as its first subscriber. Parked because it is a much higher barrier to entry than prompts alone — do NOT build until prompt-only orchestration has been honestly tried and shown insufficient.

## The two-pub-sub framing (Micah, 2026-07-13)

Isaac already has ONE pub/sub: **hails = an AGENT pub/sub.** Agents publish messages to bands/sessions; agents (LLMs) subscribe and react. Non-deterministic, barrier-to-entry = writing a prompt. This is Isaac's low-barrier orchestration substrate and should stay that way.

This bean would add a SECOND, different pub/sub: a **DETERMINISTIC pub/sub.** Code publishes neutral events; code (not LLMs) subscribes and reacts deterministically. Barrier-to-entry = writing a code plugin. Higher barrier, but reliable in ways prompts can't be (a code subscriber can't get stuck the way the verify agent did while trying to escalate 7l5m).

## What it would enable

1. **Neutral event bus**: post-hail-cleanup, hail (pure transport) emits neutral facts — `turn-ended {delivery outcome executed-tools}`, `delivered`, `dead-lettered` — knowing nothing about who listens.
2. **Deterministic supervisor** (first subscriber): watches BEAN-LEVEL convergence (not turn-level — turn-level stuck is handled by the tool-loop rethink). Maintains per-bean counters currently smeared into prompts: verify-fail count (qpp4), work<->verify cycle count, dead-letter count, time-since-status-change. On non-convergence -> HALT the bean (held state transport honors: no bind, no re-hail) + ONE human escalation. NEVER re-queues.
   - Key asymmetry: a CONTINUATION layer listening to the bus is wrong (it creates work). A SUPERVISOR listening is fine (it only observes + halts). Test: does the subscriber create new work? Continuation=yes(bad), supervisor=no(ok).
   - The supervisor IS bean-aware and MAY shell beans/git — that is legitimate for the orchestration layer; the point of the hail cleanup is that TRANSPORT must not, not that nothing may.
   - Would subsume/replace the prompt-based qpp4 escalation counting and absorb fi41's halt-on-escalation.

## Design decisions deferred to if/when we build it

- Event-driven (supervisor subscribes to bus) vs polling watchdog.
- Halt thresholds (N verify-fails / K cycles / M dead-letters / T minutes no-progress, OR-ed).
- Whether it lives as a supervised service (like discord watchdog) or a new orchestration module.

## Status

PARKED per Micah 2026-07-13: "I don't think we should embark on that journey... get the hail system cleaned up and try to achieve successful orchestration without a deterministic plugin, because that is such a higher barrier to entry than prompts alone." Revisit ONLY if prompt-only orchestration (post hail-cleanup + tool-loop rethink) proves insufficient.
