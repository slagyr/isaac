---
# isaac-59kb
title: cli-server attach drops the second buffered stdout frame
status: draft
type: bug
priority: normal
tags:
    - cli-server
created_at: 2026-09-26T03:02:51Z
updated_at: 2026-09-26T03:02:51Z
---

## Why

Worker on isaac-jvzn (2026-09-26). Do not reopen isaac-jvzn for this, and do not land it inside that bean. The scope change does not touch stream buffering.

`bb spec spec/isaac/cli_server/dispatch_spec.clj:126` fails on isolated detached isaac-cli-server `origin/main`:

- example: `dispatch replays buffered frames after attach and renders them once`
- `spec/isaac/cli_server/dispatch_spec.clj:150`
- expected `"second\n"`, got `""`
- after attach, the trace shows an exit frame and no second stdout frame

So `bb ci` on cli-server is red on main before any jvzn change. Workers will keep hitting it.

## Change

Attach must replay every buffered stdout frame, including the second, and render it once. The existing spec is the contract. Do not weaken the assertion.

## Acceptance

- [ ] `bb spec spec/isaac/cli_server/dispatch_spec.clj:126` green on a branch cut from current origin/main, with no other spec weakened.
- [ ] `bb ci` on isaac-cli-server green, or any remaining failure named and shown to fail on origin/main too.

Likely repo: isaac-cli-server (dispatch / attach stream buffering).

## Ungated

No feature runner scenario yet. Draft for human review. Do not promote. Do not fold into isaac-jvzn.
