---
# isaac-ks8r
title: Delete dead activate-server! and the :server? manifest key
status: in-progress
type: task
priority: low
created_at: 2026-09-30T04:57:54Z
updated_at: 2026-09-30T04:59:07Z
---

Found verifying isaac-6pqo (2026-09-30). `isaac.module.lifecycle/activate-server!` (and `loader/activate-server!`) have no callers anywhere except their own spec, and 6pqo's new `:server?` manifest key is declared by no module, so it would throw if called. Dead code.

## Wanted

Delete `activate-server!`, `loader/activate-server!`, `server-module-id`, the `:server?` manifest schema key and their specs. Clean cutover.

## Acceptance

`grep -rn "activate-server!\|:server?" isaac-foundation/src isaac-foundation/spec` returns nothing; full CI green.

## Ungated

Dead-code deletion. Hand off `tag=unverified`.
