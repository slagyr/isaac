---
# isaac-0oqd
title: 'x2up residue: 3 scenarios still red on main after the merge'
status: completed
type: bug
priority: high
tags:
    - unverified
created_at: 2026-08-31T16:27:46Z
updated_at: 2026-08-31T17:29:13Z
---

Repo: **isaac-agent**, main @ e452bc6 (x2up merged). Full bb features:
738 examples, **3 failures** (down from 18):

1. features/bridge/cli-prompt.feature:~66 "Provider error prints a readable
   message to stderr" — expects stderr "context length exceeded"; under
   x2up/p9zy the overflow path compact-and-retries instead of surfacing the
   raw provider error. The EXPECTATION is likely stale — decide what the
   scenario's intent is now (a non-overflow provider error should still
   print; if the fixture uses an overflow error specifically, either switch
   the fixture to a different provider error or assert the new behavior).
   Do not gut the scenario's intent: provider errors must remain readable
   on stderr.
2. features/bridge/logging.feature:~78 "Compaction check and start are
   logged during chat" — :session/compaction-started never logged;
   fixture's token seeding doesn't trip the new last-input-tokens gauge.
3. Same feature, "Compaction entry precedes the triggering user message in
   transcript" — same cause.

For 2 and 3, x2up seeded `last-input-tokens` above threshold in the other
15 fixtures — apply the same treatment (or whatever the fixture's intent
needs under the context-gauge model).

## Acceptance

- [ ] bb features — 738/0 on isaac-agent main (the only reds permitted are @wip).
- [ ] The three scenarios keep their intent (no weakening).
- [ ] compaction_overflow.feature stays green.

Failing scenarios are the spec; no new Gherkin required.



## Implementation (2026-08-31, plan — Micah time-pressed, planner implemented)

- bridge/logging.feature: both fixtures seed last-input-tokens 165 (x2up's
  pattern; window 200, line at 160).
- bridge/cli-prompt.feature provider-error scenario: fixture error text
  changed to a NON-overflow error ('upstream provider mishap') — intent
  (provider errors readable on stderr) preserved; overflow errors now
  compact-and-retry by design (p9zy/x2up), so the old fixture text was
  asserting removed behavior.
- Full gate on isaac-agent main: bb features 738/0/1964, bb spec green.
- Released 0.1.40 (manifest + CHANGELOG); registry pin advanced in the same
  train.
