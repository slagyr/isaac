---
# isaac-okfj
title: 'One way to configure Google: :google is always a map of organization id to config, with no default tenant'
status: completed
type: task
priority: high
created_at: 2026-09-21T03:51:03Z
updated_at: 2026-09-21T04:50:20Z
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

## Verified + landed on main (2026-09-21, perceptor@isaac-verify)

PASS across three repos. Worktrees off each `bean/isaac-okfj`, `rm -rf target/gherclj/generated/` then `bb ci`, all exit 0:

- **isaac-google** @ `63c081d` (base `origin/main` 0abf5de): config-bypass-lint ok, **143 specs / 0 failures / 229 assertions**, **28 features / 0 failures / 114 assertions**.
- **isaac-gchat** @ `99f6055` (the verify repin, on top of `e173405`): ok, **84 specs / 0 failures / 157 assertions**, **27 features / 0 failures / 61 assertions**.
- **isaac-gmail** @ `ffd56c6` (the verify repin, on top of `32eca74`): ok, **48 specs / 0 failures / 76 assertions**, **13 features / 0 failures / 38 assertions**.

Checks:
- §1 tampering: every feature edit is the config-shape rewrite this bean decides (`google.*` → `google.<organization>.*`) plus the intentional inversion of the one flat-form scenario. Scenario counts per file are unchanged except `tenants.feature`, which gains one (4 → 5). No step reworded to weaken an assertion; `push_door.feature` gains `And the Google runtime component is started` because the manifest no longer ships a static `:isaac.http/identity` rule — the rules are registered at start, one per organization.
- §3 output: no stray `println`; the new prints are in `src/isaac/google/cli.clj` (`isaac google status`), the CLI exception.
- §4 pass A: no `Thread/sleep`, real network/fs/db, hidden clock or assertion-free `it` in the diff's spec/step changes. Pass B: `grep -rn "Thread/sleep" spec/ feature-steps/` → 0 matches in isaac-google.
- §6 pins: isaac-google's own pins unchanged and all ancestors of their `origin/main` (foundation `b644562`, agent `679aee8`, http `493416d`).
- §6a multi-repo: isaac-google squashed and landed FIRST; both downstream bean branches were still on `:local/root "../isaac-google"`, so I rewrote `deps.edn` and `bb.edn` to `:git/sha dfc7f5dd…`, committed on each bean branch, and **re-ran `bb ci` green on the repinned tree** before squashing. No downstream pin names a bean-branch sha.

Acceptance:
- One shape — `config.clj`'s `google-schema` is now `:key-spec`/`:value-spec` only, with no `:schema` of its own (`config_spec`: "declares no fields of its own under :google"), which closes isaac-pvfq: there is no declared field to mis-apply, so a nested config reports no unknown keys.
- A flat `:google` names the shape it wants. Confirmed by evaluation, not just by reading: conforming `{:project "marigold" :oauth {…} :push {…}}` against `google-schema` yields `{:project #CoerceError{:message "must be a map of one Google organization's config — :google is a map of organization id to config, e.g. google.tonotop.oauth.client-id"} …}`.
- No default organization — `DEFAULT` and `flat?` are gone; `organizations?` is the single predicate and `tenants` returns `{}` for anything else (`tenants_spec` covers flat, `{}`, non-map, and `{:google {:oauth …}}`).
- `auth-provider` is `"google/<id>"` for every organization and `nil` for none; `token_spec` stores and resolves under `google/tonotop`, and a config naming no organization answers `:auth-failed` with a message containing `google.<organization>`.
- The door registers one rule per organization, named `:google-pubsub/<id>`, and none for a flat config (`component_spec`).
- The push door refuses when no organization is configured: 401 + `:google/no-organization`, nothing persisted (`http_spec`, and `tenants.feature` end-to-end with `isaac google status` printing "No Google organization configured").
- A comm on a one-organization host still needs no `:gchat/google` / `:gmail/google` — `of-comm` falls back to the only configured organization (gchat `tenant_spec`, gmail `watch_spec`).

Noted, not blocking: a flat config written with only map-valued keys (`:oauth`, `:push`) still conforms without a schema error; `tenants/organizations?` is what rejects it, by refusing any slice carrying an organization field directly under `:google`. The behaviour is right (no organizations, push refused, status says so); only the schema message is silent in that one sub-case.

## Landed on main (2026-09-21)

main-sha: isaac-google dfc7f5dd28bef7384400447bae11e1efd02e53af
main-sha: isaac-gchat e09bf38e6cf158b89c6eefa15aaa2f1379aca9d2
main-sha: isaac-gmail 49d848553da336596d855c05d1bba6b781cb421f

Host migration (yopp) remains the human step in "Migrating a host" above: rewrite `:google` nested, move the `~/.isaac/auth.json` entry from `"google"` to `"google/<id>"`, restart.
