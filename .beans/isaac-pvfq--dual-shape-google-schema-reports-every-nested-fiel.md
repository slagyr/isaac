---
# isaac-pvfq
title: Dual-shape :google schema reports every nested field as an unknown key on a single-organization host
status: scrapped
type: bug
priority: normal
created_at: 2026-09-21T03:45:09Z
updated_at: 2026-09-22T22:09:43Z
parent: isaac-bv1l
blocked_by:
    - isaac-okfj
---

On a single-organization host, `isaac config validate` reports every nested
field of `:google` as an unknown key:

    warning: :google.oauth.account         - unknown key
    warning: :google.oauth.client-id       - unknown key
    warning: :google.oauth.client-secret   - unknown key
    warning: :google.push.endpoint         - unknown key
    warning: :google.push.service-account  - unknown key
    OK - config is valid

Nothing is actually dropped — verified on yopp 2026-09-21: `isaac config get
google` returns both maps whole, `isaac google login` builds a correct consent
URL with the client id, and the push door's trust rule resolves its audience
from `[:google :push :endpoint]`. The validator is wrong, not the loader.

## Why

isaac-1zkz made `:google` dual-shape: the flat map a one-organization host
writes, or a map of tenant id -> tenant. The schema declares both an explicit
`:schema` (project, topic, oauth, push, health, renew-within-hours) and a
`:key-spec`/`:value-spec` for the tenant form. Validation applies the tenant
`:value-spec` to every key under `:google`, so `:oauth` and `:push` are checked
as if each were an organization, and their inner fields are not tenant fields.
`:project` and `:topic` escape only because they are scalars, not maps.

## Why it matters

"unknown key" is how Isaac says a key is being ignored. Five of them on a
working config trains the reader — human or agent — to scroll past exactly the
message that will one day mean a real key was dropped. It cost two people ten
minutes of doubt on the deploy that found it, mid-upgrade, on a host whose
inbound Chat depends on those very fields.

## Work

Teach the validator which shape it is looking at before it validates — the
same discrimination `isaac.google.tenants/flat?` already makes at runtime
(a slice is flat when it carries any tenant field). Options, cheapest first:

- validate flat-first: if the slice matches the flat `:schema`, use it and do
  not apply `:value-spec` at all;
- or split the declaration: `:google` flat, `:google/tenants` for several,
  so no schema is two shapes at once.

## Scenarios

A flat `:google` config validates with no warnings and keeps every field. A
tenant-map `:google` validates with no warnings and keeps every tenant's
fields. A genuinely unknown key under either shape is still reported.

Superseded in practice by isaac-okfj: with one config shape there is no
value-spec to mis-apply, so these warnings disappear rather than being fixed
in place. Keep this bean open only as the acceptance check — a nested config
validates with no warnings.

## Scrapped (2026-09-22, Micah)

Superseded by isaac-okfj: with one :google shape (organization id -> config) the dual-shape validation noise no longer exists.
