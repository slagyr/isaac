---
# isaac-wn2u
title: 'gmail: labels.create 409 (label exists) breaks gmail/watch after every restart'
status: draft
type: bug
priority: normal
created_at: 2026-10-02T04:41:34Z
updated_at: 2026-10-02T04:41:34Z
---

Found 2026-10-02 on skiff: `:google/handler-failed :type "gmail/watch" :error "Gmail labels.create failed: 409"` on every push since the 03:51Z deploy.

isaac-gmail api.clj `labels-create!` (169-179) throws on any non-2xx incl. 409 (already exists); labels.clj `label-id!`/`create-and-cache!` (38-46) creates whenever its in-process cache (`defonce`, empty after each start) misses, so the first watch after a restart 409s, the id is never cached, and it fails on every push.

## Wanted
On 409, list labels and match by name to recover the existing id; cache it.
## Acceptance (scenarios TBD)
- `label-id!` for a name that already exists (409 on create) returns and caches the real id; `gmail/watch` succeeds after a restart.
