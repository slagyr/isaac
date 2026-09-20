---
# isaac-1zkz
title: 'isaac-google: tenants — one Isaac host serves several Google organizations, each a complete {oauth, account, project, topic, push SA, token}'
status: todo
type: feature
priority: high
tags:
    - google
    - comm
    - config
created_at: 2026-09-20T00:25:51Z
updated_at: 2026-09-20T07:33:06Z
parent: isaac-bv1l
blocked_by:
    - isaac-8s6s
---

Decided 2026-09-19 (Micah). Today `google` config is one org; a second org's pushes are refused (:claims — the trust rule pins one push SA), which is safe but not supported. Everything Google is per org: the project, topic, push service account, OAuth client AND the Google user Isaac is (yopp@tonotop.com cannot read another org's spaces). So a tenant is a complete set, not a namespace over one login.

## Shape
```
:google {:tonotop {:project … :topic … :oauth {…:account "yopp@tonotop.com"} :push {:service-account …}}
         :acme    {…}}
:comms  {:gchat      {:type :gchat :google :tonotop …}
         :gchat-acme {:type :gchat :google :acme …}}
```
- **Single-tenant convenience:** a flat `:google {:project …}` (yopp today) reads as tenant :default; a comm with no :google key uses :default. Nothing on yopp changes until a second org exists. Schema: :google is a map of tenant → tenant-schema, with the flat form coerced.
- **One door.** /google/pubsub stays. The identity berth contributes ONE trust rule PER TENANT (audience = the shared endpoint config ref, claims email = that tenant's push SA, principal :google-pubsub/<tenant> with scope :google/push). The door persists the event with :tenant (from the subscription's project in the Pub/Sub envelope, cross-checked against the principal) and handlers receive it.
- Tokens per tenant in the auth store (google/<tenant>); `isaac google login [--tenant t]` (default when one); registration timer and Gmail watch run per tenant; `isaac google status` groups by tenant.
- People index (isaac-8s6s) keyed globally by users/<id> (Google ids are global), tenant recorded per sighting; People API lookups use the tenant's token.
- What a push carries: `subscription: projects/<project>/subscriptions/<name>` (tenant), the push SA email in the OIDC token (tenant), space name + domainId (Chat) / mailbox (Gmail).

## Scenarios (google: tenants.feature; gchat/gmail one each)
1. flat config reads as :default; existing push_door/registration scenarios pass unchanged
2. two tenants: each push is accepted only under its own SA and persisted with its :tenant; a push signed by tenant A's SA carrying tenant B's subscription is refused
3. a comm bound to tenant :acme sends with acme's token and subscribes acme's spaces to acme's topic
4. login per tenant stores separate tokens; status lists both

Constrains isaac-8s6s and isaac-dymn (they must carry :tenant); does not block them.

## Not started 2026-09-20 (planner)

Everything else in this train (8s6s, bklu, dymn, iv5c, tund, jqk2, 7rce, ddls)
is landed and deployed to yopp; this one is not. Two reasons, both worth
stating plainly rather than half-doing it:

1. It is the only structural change in the set — config shape
   (`google.tenants.<id>`), token resolution per tenant, registrations per
   tenant, push routed by tenant, comms naming a tenant, people/resolve and
   every isaac-jqk2 tool taking the tenant's token. That deserves a worker and
   a verify pass, not a planner landing a refactor by hand at the end of an
   outage.
2. Yopp has exactly one Google organization today, so nothing observable
   changes for it. The cost of waiting is low; the cost of a half-migrated
   config on a live host is not.

Hail it to isaac-work as the first bean when zanebot's claude login is back.
Note for whoever takes it: isaac-google 0.1.8's `people/resolve` and the new
tools all take the process token today — those are the call sites that become
per-tenant, along with `isaac.google.token/token`.
