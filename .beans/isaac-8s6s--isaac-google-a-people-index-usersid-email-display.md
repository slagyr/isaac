---
# isaac-8s6s
title: 'isaac-google: a people index — users/<id> ↔ email ↔ display name — shared by Chat and Gmail'
status: in-progress
type: feature
priority: high
tags:
    - google
    - comm
    - unverified
created_at: 2026-09-20T00:16:34Z
updated_at: 2026-09-20T05:08:41Z
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

## Handoff (scrapper@isaac-work-3, 2026-09-19)

Built to the **re-scoped** design (no index, resolve on demand). Dropped with the
index, per that re-scope: `people.edn`, `identify!`, `by-email`, `by-id`, the
`isaac google people` listing, and scenarios 1 and 4.

**isaac-google** — branch `bean/isaac-8s6s` @ `0221adeefd425a0e19f8a09ae57dcc21a09b864c`
(base `origin/main@2349b111d6cd915de7a620f59e2927408e180bd8`)
- `src/isaac/google/people.clj`: `resolve` (users/<id> → {:user :display-name :email})
  through People API `people.get(personFields=names,emailAddresses)` on the
  existing `isaac.google.events/request!` seam; in-memory memo, TTL 1h, nothing
  persisted; `render` → "Micah Martin <micah@tonotop.com>"; fail soft on every
  error (entry keeps the caller's display name, no email) and warn once per
  reason — `:google.people/scope-missing` names directory.readonly,
  `:google.people/lookup-failed` otherwise.
- manifest: contributes `https://www.googleapis.com/auth/directory.readonly` to
  `:isaac.google/scopes` (version 0.1.6 → 0.1.7).
- `features/people.feature` (4 scenarios) + people steps in `google_steps.clj`.
  The Given steps take the events seam for the scenario (alter-var-root, restored
  after) so *consumer* repos reuse the same vocabulary — that is how isaac-gchat
  drives them.
- `bb ci`: 60 examples / 0 failures (spec), 23 / 0 (features).

**isaac-gchat** — branch `bean/isaac-8s6s` @ `a376d5c74510f16c76dbba9d4190b0a22e1dc447`
(base `origin/main@3e993df2dba05fe189f91df42e878e5626893598`)
- `gate/decide` takes an opts map with `:resolve-person`; `sender-identity`
  resolves the email only when Chat withheld one, never throws, and the
  allow-list still matches `users/<id>` / `domain:<id>` when the lookup fails.
  `:sender` on a route is now `people/render` of the identity.
- `handler`: passes `people/resolve` and builds the turn input as
  "<who spoke>: <text>".
- `features/comm/gchat/inbound.feature`: +4 scenarios — email allow-list admits a
  resolved id (2); turn input reads "Micah Martin <micah@tonotop.com>: …" (5);
  no directory scope ⇒ id fallback + exactly one `:google.people/scope-missing`
  warn across two messages (3); a failed lookup does not block a sender the
  domain list already admits (new).
- `bb ci`: 48 / 0 (spec), 20 / 0 (features).

**Verify, in this order:** land isaac-google first, then repin isaac-gchat's
`bb.edn`/`deps.edn` `isaac-google` sha from `0221ade…` (my branch) to google main
before landing gchat. Note: gchat's feature suite now runs ~26s; the default
60s `ISAAC_TEST_TIMEOUT_MS` tripped on a cold gitlibs fetch (first run after the
repin) — re-run, or raise the timeout, if that is what CI sees.

Not touched (no repo in scope this turn): gmail rendering and isaac-tund's
context block, which the design lists as later consumers of `people/render`;
tenants (isaac-1zkz) — `resolve` takes the process token today.
