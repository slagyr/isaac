---
# isaac-nceb
title: 'An empty terminal response must say why: carry the provider''s exit status and stderr, then classify'
status: todo
type: bug
priority: high
created_at: 2026-09-20T07:34:11Z
updated_at: 2026-09-20T18:41:26Z
---

**Rewritten 2026-09-20 (planner), premise corrected.** This bean was filed
saying zanebot's claude OAuth had expired and the provider swallowed the auth
error. That was wrong: the token in the launchd plist works — running the CLI
with it answers normally, on opus and on sonnet. My probe was an ssh shell
without that env var, so it got "OAuth session expired" from a shell with no
keychain, and I read the fleet's symptom into it. The real cause of the
2026-09-20 05:19Z outage was the drive's tool loop against the claude-code
provider (isaac-zz6d; claude-code 0.1.16, the fence fallback fix).

What survives is the thing that made the misdiagnosis possible and cost most of
a day:

`:empty-terminal-response` is a catch-all. It says "model returned no content
after continuation retry" and nothing else — not the provider's exit status,
not its stderr, not whether a subprocess even started. Three quite different
failures wear the same face:

- the fence-fallback bug just fixed (the model answered; the drive lost it),
- a provider that really did return an empty completion,
- a provider that failed to authenticate, spawn, or reach the network at all.

Only the last is weather under hails-never-die, and during the outage all of
them dead-lettered identically after five attempts.

Work: when a CLI-backed provider produces no content, carry *why* into the
error — exit status, the tail of stderr, whether the process started, how long
it ran — and log it at the point of failure. Then classify: a spawn or auth
failure is an auth/transport error that defers the hail; an empty completion
from a healthy call stays `:empty-terminal-response`.

Scenario: a claude-cli invocation that exits non-zero with an authentication
message on stderr ends the turn with an auth-class error, and a hail delivered
into it defers instead of counting toward the dead-letter budget; a healthy
invocation that returns empty content still reports
`:empty-terminal-response`, now carrying the exit status.

Evidence from the outage: five dead letters — `d4a7cd6f` (isaac-ddls work,
which had already handed off to verify), `f479c534` (isaac-dymn work), three
isaac-przv verify hails — plus `aacf8ca7` (isaac-e20m) at 18:04Z. Related:
isaac-zz6d (the root cause, fixed), isaac-3wiu (the retries bound to a
three-month-old session), isaac-v64q (429/401 mid-stream on the Responses path).
