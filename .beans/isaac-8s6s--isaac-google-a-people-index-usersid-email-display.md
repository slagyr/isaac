---
# isaac-8s6s
title: 'isaac-google: a people index — users/<id> ↔ email ↔ display name — shared by Chat and Gmail'
status: todo
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-20T00:16:34Z
updated_at: 2026-09-20T00:28:14Z
parent: isaac-bv1l
---

Micah 2026-09-19: we need to know who spoke, by a key that does not change. Chat gives users/<id> + displayName and never an email for humans; Gmail gives From: email + name and never an id; display names change. So isaac-google keeps the join.

## Design
- <root>/google/people.edn: {users/<id> {:email :display-name :domain :first-seen :last-seen}} plus an email→id map. Written through isaac-google (fs seam), read by any consumer.
- Fill: every Chat event upserts id + displayName + domainId (free); every Gmail message upserts email + name from From: (free); the People API joins them — people.get("people/<id>", personFields=emailAddresses) returns the Workspace email for an id under the directory.readonly scope (isaac-google contributes it to :isaac.google/scopes; one more consent line at login; one lookup per new person, cached). Fail soft: no scope / lookup fails ⇒ entry without email, retried on next sighting after a backoff.
- API: (people/identify! {:user "users/…" :display-name …}) → entry; (people/by-email email); (people/by-id id); (people/render entry) → "Micah Martin <micah@tonotop.com>".
- Consumers: gchat gate resolves the sender through the index so allow-from written as emails matches Chat senders (users/<id> and domain:<id> entries still work); turn input and isaac-tund's context block render (people/render); gmail renders the same; `isaac google people` lists who Isaac knows.

## Scenarios (google: people.feature; gchat/gmail inbound)
1. a Chat event from an unknown id is looked up once and the email cached; the next event does no lookup
2. allow-from ["micah@tonotop.com"] admits a Chat sender whose id resolves to that email
3. no directory scope ⇒ the entry has no email, the allow-list falls back to users/<id>/domain:, and a warn names the missing scope once
4. a Gmail From: fills name+email; a later Chat lookup joins it to the id
5. the turn input reads "Micah Martin <micah@tonotop.com>: …"



Tenants (isaac-1zkz): entries record the tenant per sighting; People API lookups use that tenant's token. Scope note for Micah: directory.readonly is an OAuth scope on Isaac's own token (permission to ask the Workspace directory for a user's email), used by module code deterministically — never by a turn.



**Re-scoped 2026-09-19 (Micah): no index.** Google is the source of truth; resolve on demand. `people/resolve` → People API `people.get("people/<id>", personFields=names,emailAddresses)` with the tenant's token under directory.readonly; an in-memory memo with a short TTL (say 1h) so a busy thread does not re-ask per message; nothing persisted. Gmail needs no lookup (From: carries email + name). The gate resolves the sender at decision time (fail soft: no scope/lookup failure ⇒ id/domain matching only, warn once). Rendering unchanged: "Micah Martin <micah@tonotop.com>: …". Scenarios 1 and 4 (caching/joins across modules) drop; 2, 3, 5 stay; add: a lookup failure does not block a message the id/domain list already admits.
