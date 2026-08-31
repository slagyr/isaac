---
# isaac-0oqd
title: 'x2up residue: 3 scenarios still red on main after the merge'
status: todo
type: bug
priority: high
created_at: 2026-08-31T16:27:46Z
updated_at: 2026-08-31T16:27:46Z
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
