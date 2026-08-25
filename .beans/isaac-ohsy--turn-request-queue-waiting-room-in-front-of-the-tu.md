---
# isaac-ohsy
title: 'Turn-request queue: waiting room in front of the turnstiles'
status: draft
type: feature
priority: high
created_at: 2026-08-25T18:39:48Z
updated_at: 2026-08-25T18:39:48Z
---

Likely repo: **isaac-agent**. Depends on isaac-opp6 (turnstile protocol) and
isaac-bbov (finalization + release tokens), both landed. Defined in the
2026-08-24 architecture session (see isaac-bbov, isaac-tdgt) as "next
session"; this bean is that session's output (2026-08-25, Micah + plan).

## Problem

opp6 ships `(admit? ctx) -> :pass | :hold | {:refuse reason}` plus release
tokens and the built-in `:tide` turnstile, but a `:hold` today is reported as a
refusal and dropped. Hail survives on its own deferral path; foreman's `:turn`
action and the CLI have nothing. "run at 22:00" (a request holding at tide) and
worksite-pool selection both need a real waiting room.

## Design

1. **submit** takes charge + turnstile stack + address spec
   `{:session | :crew | :tags, :reach}`.
2. **:hold parks the request durably** (survives restart — relates to
   isaac-wq8m). `:refuse` stays loud and unchanged.
3. **Release-token firing is the wake signal**: on wake, re-run `admit?` on
   held requests in submit order; first stack that fully passes runs.
4. **Candidate resolution in core**: from an address spec, pick one candidate
   whose whole stack passes — the same algorithm worksite pools (W2) and hail
   selection will use. Hail contracts toward naming + records over this queue.
5. CLI `--turnstile tide:22:00-06:00` becomes a real delayed prompt (the
   "wording chosen to stay true once holds become parks" in opp6 scenario 1
   now flips to a park).

Invariants (session single-writer, provider walls) remain core, never
turnstiles. No hail↔foreman arrows; both plug into this queue only.

## Scenarios (to write before todo)

- tide hold parks, wakes at window open, runs — no cron, one queued echo.
- worksite operator lock parks a submitted turn; unlock fires the token; the
  turn runs (l3ps's field check, via the queue rather than hail deferral).
- held request survives a server restart and still runs on wake.
- address spec `{:tags [...]}` with two candidates, one locked: the free one
  runs.
- unknown turnstile name still refuses loudly at submit (no park).

## Acceptance

Scenarios above committed (@wip removed) and green; existing
features/turn/turnstiles.feature, bridge/cli-prompt, session suites stay green.
