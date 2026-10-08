---
# isaac-s715
title: 'isaac-agent: resolver berth — a module replaces a turn''s handle with a named reference'
status: draft
type: feature
created_at: 2026-10-08T22:03:41Z
updated_at: 2026-10-08T22:03:41Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

DRAFT. Needs scenarios before it is todo. Part of the contacts epic (isaac-zt1x). Blocked by the attribution fields bean (isaac-v403).

## Idea

A plug point where a module replaces a raw handle with a named reference before the turn record is stored. `isaac-contacts` is the first user: `{:kind :handle :comm :gchat :id "users/123" …}` becomes `{:kind :contact :id "chris" :authenticated true}`.

## Proposal

A new berth, working name `:isaac.agent/from-resolvers`. A resolver is handed a `:kind :handle` map and returns a replacement map or nil.

- Applied once, at submit, to `:from` and to `:for` when they are handles. Other kinds are never passed to a resolver.
- No resolver installed: the raw handle is stored. Everything works without one.
- Nil from the resolver: the raw handle is stored.
- A resolver that throws or is slow is skipped and logged; the raw handle is stored. A turn never fails or waits on a resolver.
- The handle's `:authenticated` claim survives resolution. A resolver cannot upgrade it.

## Scenarios to draft

- A fixture resolver renames a known handle; the turn record shows the replacement.
- An unknown handle passes through unchanged.
- A throwing resolver is skipped, logged, and the turn is stored with the raw handle.
- An unauthenticated handle stays unauthenticated after resolution.

Needs a small fixture module, in the style of the existing berth fixtures (`isaac.slash.echo`).

## Likely repo scope

`isaac-agent`.
