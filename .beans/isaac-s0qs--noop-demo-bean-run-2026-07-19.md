---
# isaac-s0qs
title: noop demo bean (run-2026-07-19)
status: todo
type: task
priority: low
created_at: 2026-07-20T03:09:09Z
updated_at: 2026-07-20T03:09:09Z
---

**No-op process/demo bean** — a live demonstration of the isaac orchestration loop for a guest. Make **NO** code, config, or repo changes beyond appending the notes described below to THIS bean. There is nothing to build; the point is to exercise and show the hail → work → verify → complete flow with clean at-a-glance notifications.

## Sequence (follow exactly)

1. **Worker (scrapper):**
   - Claim the bean (status `in-progress`, tag `unverified`).
   - Post the claim notification to the notification-comm channel (the standard `🟢 claimed` format).
   - Append a `## Work note (<crew>@<session>, <date>)` line to this bean body confirming you ran the process test — one friendly sentence is enough.
   - Commit/push the bean (`.beans/`) and hand off to the **verify band** with `:bean-id` + `reply_to`.

2. **Verifier (perceptor):**
   - Confirm the `## Work note` is present.
   - Append a `## Verify note (<crew>@<session>, <date>)` line — one sentence confirming the loop is healthy.
   - Post the pass notification, remove the `unverified` tag, and set status `completed`.

## Acceptance

- [ ] Bean carries one `## Work note` and one `## Verify note`.
- [ ] The full work → verify → complete loop ran with at-a-glance notifications at each step.
- [ ] No source/config changes were made anywhere (this is a no-op).
