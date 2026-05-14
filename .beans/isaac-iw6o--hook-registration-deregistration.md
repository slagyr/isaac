---
# isaac-iw6o
title: Hook registration / deregistration
status: draft
type: feature
created_at: 2026-05-14T14:39:17Z
updated_at: 2026-05-14T14:39:17Z
---

**Status: draft — needs your refinement.**

Hooks under `config/hooks/` and `crew/*/hooks/` need a lifecycle story so adding/removing a hook doesn't require an isaac restart.

## Open questions before this is workable

- Scope: just the user-defined hooks (activity, heartrate, location, ping, …) or any extension that registers a callback?
- Trigger model: filesystem watch, explicit `/reload`, or external signal?
- Deregistration semantics: graceful drain of in-flight invocations vs. hard stop; refcount or single-owner?
- Discovery: does the registry expose currently-active hooks for `/status` and validation?

## Prior art

- `isaac-xibj` (completed): Discord plugin lifecycle — hot-load on config add, stop on remove, no-op on change. Same shape; probably worth re-using the pattern rather than inventing a parallel one.

## TODO before promoting to `todo`

- [ ] Decide trigger model
- [ ] Decide deregistration semantics
- [ ] List the surface — what files/dirs are watched, what events fire
