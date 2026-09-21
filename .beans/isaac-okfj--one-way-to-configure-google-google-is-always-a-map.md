---
# isaac-okfj
title: 'One way to configure Google: :google is always a map of organization id to config, with no default tenant'
status: in-progress
type: task
priority: high
tags:
    - unverified
created_at: 2026-09-21T03:51:03Z
updated_at: 2026-09-21T04:44:11Z
parent: isaac-bv1l
---

Micah, 2026-09-21: "I find that offering two ways to configure the same thing
is confusing. It's kind of ambiguous. It makes you wonder which way you should
do it, which way is the right way, and which way is the wrong way. I would be
inclined to change our Yopp config to the tenant structure and just not support
a default tenant."

isaac-1zkz shipped `:google` as two shapes: the flat map a one-organization
host writes, and a map of organization id -> config. The flat form reads as the
tenant `:default`. Both work, neither is marked as preferred, and a reader
cannot tell which is intended.

It already cost something. The dual-shape schema reports every nested field of
a flat config as an unknown key (isaac-pvfq), because validation applies the
tenant value-spec to `:oauth` and `:push` as though each were an organization.
"Unknown key" is how Isaac says a key is being ignored, so a working config
spends five lines claiming it is broken.

## Decision

One shape. `:google` is a map of organization id to that organization's
config. There is no default tenant and no flat form.

    :google {:tonotop {:project "tonotop-yopp"
                       :topic   "projects/tonotop-yopp/topics/isaac"
                       :oauth   {...}
                       :push    {...}}}

## Work

- `isaac.google.tenants`: delete `DEFAULT` and `flat?`; `tenants` returns the
  declared map. A `:google` whose values are not maps is a validation ERROR
  naming the shape, not a silent reinterpretation.
- `auth-provider` becomes `"google/<id>"` for every tenant; the `:default` ->
  `"google"` special case goes.
- Manifest schema keeps only `:key-spec`/`:value-spec`, which closes
  isaac-pvfq: with one shape there is nothing to mis-apply.
- `cli`, `door`, `health`, `registration`, `token` drop their DEFAULT branches;
  gchat and gmail feature steps write the nested shape.
- A comm may still omit `:gchat/google` / `:gmail/google` when the host has
  exactly one organization — that is a default *value*, not a second config
  shape, and it stays.

## Migrating a host (yopp is the only one)

1. rewrite `:google` as `{<id> {...}}`;
2. move the credential entry in `~/.isaac/auth.json` from `"google"` to
   `"google/<id>"` — the tokens are keyed by provider, so this preserves the
   login and avoids a re-consent;
3. restart; `isaac google status` should list the same registrations.

## Scenarios

A nested `:google` validates with no warnings and every field kept. A flat
`:google` fails validation with a message naming the shape it wants. A host
with one organization needs no `:gchat/google` on its comms. Tokens resolve
under `google/<id>`.

Dispatched: hail bb40c0c2 2026-09-21T04:04:12Z (band isaac-work)

## Implemented (2026-09-21, scrapper@2026-06-30-0021-icc9)

One shape shipped across three repos, all suites green.

**isaac-google** — branch `bean/isaac-okfj`, commit 63c081d.
`tenants`: `DEFAULT`/`flat?` gone; `organizations?` is the single predicate
(non-empty map, no organization field directly under `:google`, every value a
map) and `tenants` returns `{}` for anything else. `auth-provider` is always
`"google/<id>"`. Door registers one rule per organization, named
`:google-pubsub/<id>`, and none for a flat config. Manifest schema is
`:key-spec`/`:value-spec` only (closes isaac-pvfq). `cli`, `health`,
`registration`, `token`, `worker` lost their DEFAULT branches; `google status`
prints "No Google organization configured. Set google.<organization>.oauth.client-id."

The push door now refuses when no organization is configured: `isaac-http`
turns auth off entirely when nothing registered a rule, so a flat `:google`
would otherwise have let an unauthenticated push through with 204. The handler
answers 401 and logs `:google/no-organization` instead of persisting an event
no organization answers for.

**isaac-gchat** — branch `bean/isaac-okfj`. Feature/spec fixtures write
`google.<id>.*`; `outbound.feature` names an organization (its Google user
login belongs to one); step stub no longer falls back to `DEFAULT`.

**isaac-gmail** — branch `bean/isaac-okfj`. Same: `watch.feature` and
`gmail.feature` config, `watch_spec` fixture, step stub.

Both downstream repos currently pin isaac-google as `:local/root "../isaac-google"`
on their bean branches — **repin both `deps.edn` and `bb.edn` to the landed
isaac-google main sha before landing them.** Land order: isaac-google, then
gchat, then gmail.

Suites: isaac-google 143 specs / 28 features; isaac-gchat 84 / 27;
isaac-gmail 48 / 13 — 0 failures.

Host migration (yopp) is still the human step in "Migrating a host" above:
rewrite `:google` nested, move the `auth.json` entry from `"google"` to
`"google/<id>"`, restart.
