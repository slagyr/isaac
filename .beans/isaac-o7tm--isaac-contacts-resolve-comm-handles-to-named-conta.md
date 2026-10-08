---
# isaac-o7tm
title: 'isaac-contacts: resolve comm handles to named contacts'
status: draft
type: feature
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T20:41:16Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

DRAFT. Needs scenarios before it is todo. Part of the contacts epic. Blocked by the agent attribution bean (the resolver berth).

## Idea

A module that knows who the outside parties are. It resolves the raw handle a comm supplies into one named contact, so the same person is the same contact in Chat, email and iMessage.

## Proposal

A `:contacts` config table. A contact has:

- an id and a display name;
- a kind: person or service (an agent on another Isaac instance arriving over HTTP is a service contact);
- optional title and weight, free-form, for consumers such as dreaming to rank whose word counts more;
- handles: the comm-specific ids and email addresses that belong to it.

The module registers a resolver on the agent's berth. Given a handle it matches on the comm id first, then on email, and returns the contact reference. No match returns nil and the raw handle stands: an unidentified contact.

## Constraints

- **No comm knowledge.** The module has no code for Chat, Gmail or Discord. Handles are data in its config.
- **Trust is the comm's claim.** An email match on an unauthenticated handle must not grant the contact's weight. The resolved reference keeps the handle's authenticated flag.
- **Attribution only.** No per-contact notes, permissions or recall scoping in this bean.
- **Host-specific names stay out of public repos.** Real rosters live in host config; features and docs use placeholders.

## To settle

- Whether unmatched handles are recorded somewhere an operator can see and claim them ("three unidentified contacts wrote in this week").
- `isaac contacts list|show` and a handbook chapter.

## Likely repo scope

A new `isaac-contacts` repo, plus the module registry entry in `isaac`.
