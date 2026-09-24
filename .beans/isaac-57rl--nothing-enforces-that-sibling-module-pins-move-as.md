---
# isaac-57rl
title: Nothing enforces that sibling module pins move as a set
status: todo
type: bug
priority: normal
created_at: 2026-09-24T21:27:45Z
updated_at: 2026-09-24T21:27:45Z
---

Repos: **isaac-foundation** (`bb pins`) and every module repo.

## Problem

isaac-google sat pinned to isaac-foundation `b644562` while the rest of the
fleet — gmail, gchat, imessage, hail, cron, http, agent — had moved to
`9ab2527`. The fix isaac-google needed (`ba7fa5b`, isaac-1f4g) landed **one day
after** its pin. Nothing noticed.

The cost was not the stale pin itself. It was that the repo's suite exercised a
foundation build **no host runs**, so a genuine-looking failure appeared in CI
that could not be reproduced on any machine. That burned a whole bean
(isaac-a9dp) chasing a conformer bug that had already been fixed upstream,
and it forced isaac-286x to weaken two scenarios to work around it.

A second failure mode surfaced during the fix: bumping isaac-foundation *alone*
left `config validate` failing with `defaults.crew - references undefined crew`.
isaac-ruom restructured `:defaults` into entity templates and rewrote the
harness fixture in foundation's `spec-support`, but the `:defaults` schema lives
in **isaac-agent's** manifest. foundation, isaac-agent and isaac-http are a set;
moving one without the others is its own broken state.

A comment now sits at the top of isaac-google's `deps.edn` naming this. A
comment is not enforcement.

## Acceptance

- Something mechanical fails when a repo's foundation / isaac-agent /
  isaac-http pins are not a coherent set — extending `bb pins` is the obvious
  home, since it already checks pins against the registry.
- The check names what to do, not just that something is wrong: which repos are
  behind, and the set to move to.
- Being *behind the fleet* is reported even when the pins are internally
  consistent — that is the case that cost isaac-a9dp, and it is not a
  correctness error, so it should be a note rather than a hard failure unless
  the drift is large.
- A scenario covers a repo pinned to a coherent-but-stale set, and one pinned
  to an incoherent set.

## Notes

Discovered by isaac-a9dp, 2026-09-24. See `doc/token-burn-isaac-k00m.md` for an
unrelated but similar lesson: the platform's blind spots cost whole beans, and
they are cheaper to instrument than to rediscover.
