---
# isaac-1zkz
title: 'isaac-google: tenants — one Isaac host serves several Google organizations, each a complete {oauth, account, project, topic, push SA, token}'
status: in-progress
type: feature
priority: high
tags:
    - google
    - comm
    - config
created_at: 2026-09-20T00:25:51Z
updated_at: 2026-09-20T19:31:12Z
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

## Progress 2026-09-20 (scrapper@isaac-work-1)

Branch `bean/isaac-1zkz` in isaac-google, base `origin/main@6c8a29f`.
Last green commit: `7597e2e` (pushed).

**Done — green, pushed.**

1. `src/isaac/google/tenants.clj` (new, 98 lines) — the seam. `tenants`,
   `ids`, `tenant-config`, `config-path`, `resolve-id`, `auth-provider`,
   `tenant-for-subscription`, `subscription-of`, and `*tenant*` (the dynamic
   binding a door or a comm sets for the thread). A flat `:google {:project …}`
   reads as `{:default <that map>}`; `auth-provider :default` stays the plain
   `"google"` key so yopp's existing login stands. Spec
   `spec/isaac/google/tenants_spec.clj` — 18 examples.
2. `src/isaac/google/config.clj` — `tenant-fields` / `tenant-schema` extracted,
   and `google-schema` now carries **both** `:schema` (one organization's
   fields, validated closed) and `:key-spec`/`:value-spec` (any other key is a
   tenant id). Spec `spec/isaac/google/config_spec.clj` — 11 examples.

   Verified empirically against isaac-foundation `b644562` before writing it:
   `isaac.schema.lexicon/conform` on a `:map` spec carrying both keys conforms
   the flat shape unchanged *and* keeps every tenant key, descending into
   `:value-spec`. `isaac.config.validation` (validation.clj:111-120) does the
   same for annotations, and `isaac.config.nav/advance-spec` (nav.clj:20-24)
   prefers `:value-spec` — so `isaac config set google.acme.project …` walks.

**Next — not started.** Resume at `src/isaac/google/token.clj:54`
(`resolve-tokens`, which hardcodes `PROVIDER` and `[:google :oauth]`):

3. token/oauth per tenant — `resolve-tokens`/`token` take an optional tenant,
   default `(tenants/resolve-id cfg nil)`; creds from
   `(tenants/config-path cfg id)` + `:oauth`; store under
   `(tenants/auth-provider id)`.
4. one door, one trust rule per tenant — the manifest's single
   `:isaac.http/identity {:google-pubsub …}` with `[:google :push …]` config
   refs cannot express N tenants (refs are static paths; `resolve-rule`
   resolves one). Register a rule per tenant at component start via
   `isaac.http.auth/register-identity-entry!`, principal
   `:google-pubsub/<tenant>`, scope `:google/push`. Keep the manifest rule as
   the `:default` case so a flat host is unchanged.
5. `http/handler` — read the tenant from the envelope's `subscription:`
   (`tenants/subscription-of` → `tenant-for-subscription`), cross-check it
   against the principal, persist the event with `:tenant`, bind `*tenant*`
   for the worker's handler call.
6. registration timer + `people/resolve` + `tools/whois` per tenant;
   `isaac google login [--tenant t]`, `isaac google status` grouped by tenant.
7. Scenarios: `features/tenants.feature` (the bean's four), and the existing
   `push_door.feature` / `login.feature` must pass unchanged — that is
   scenario 1's real assertion.

Command to resume: `cd isaac-google-1zkz && bb spec && bb ci`.


## Progress — 2026-09-20 (scrapper@isaac-work-1, update 2)

Branch `bean/isaac-1zkz` @ `0a64f6e` (pushed). All specs green: 97 examples, 0 failures.

**Done**
1. `src/isaac/google/tenants.clj` + spec — the tenants seam (`tenants`, `ids`, `tenant-config`,
   `config-path`, `resolve-id`, `auth-provider`, `tenant-for-subscription`, `subscription-of`,
   `*tenant*`, `DEFAULT`). Flat `:google` reads as `:default`.
2. `src/isaac/google/config.clj` — `tenant-fields` / `tenant-schema` / `google-schema`; the google
   table is `:schema` (flat, closed) **and** `:key-spec`/`:value-spec` (tenanted, open) in one spec.
   Verified empirically against foundation `b644562`: `lexicon/conform`, `validation/annotation-errors*`
   and `nav/advance-spec` all honour both halves.
3. `src/isaac/google/token.clj` + spec — one token per tenant. `resolve-tokens` arities
   `([]) ([config]) ([config id])`; `token ([]) ([id])`; auth-store provider `google` / `google/<tenant>`;
   login message names the tenant.
4. `resources/isaac-manifest.edn` — inline `:isaac.config/schema` regenerated *from* `config.clj`
   (manifest is pure EDN, no var refs). `module_spec` now asserts the inline copy `=` `config/google-schema`,
   so the duplicate cannot drift.

**Next — not started**
5. Door tenant: `push/unwrap` must keep the envelope's `subscription` (it drops it today —
   `src/isaac/google/push.clj:24`); `http/handler` maps it via `tenants/subscription-of` ->
   `tenant-for-subscription`, cross-checks against `(get-in request [:isaac/principal :name])`
   (`:google-pubsub/<tenant>`), persists `:tenant` on the inbox event, refuses a mismatch.
6. One rule per tenant: the manifest's single `:isaac.http/identity {:google-pubsub ...}` uses static
   config refs `[:google :push :endpoint]`, so it cannot express N tenants. Register a rule per tenant
   at component start via `isaac.http.auth/register-identity-entry!` (principal `:google-pubsub/<tenant>`,
   scope `:google/push`); keep the manifest rule as the `:default` case so a flat host is unchanged.
7. `worker/tick!` binds `tenants/*tenant*` from the event's `:tenant` around the handler call.
8. Per tenant: registration timer, `people/resolve`, `tools/whois`, `cli` `login [--tenant t]` and
   `status` grouped by tenant.
9. Scenarios: new `features/tenants.feature` (the bean's four); `push_door.feature` and `login.feature`
   must pass unchanged.

**Resume at** `src/isaac/google/push.clj:24` (`unwrap` — add `:subscription`), starting with a RED spec
in `spec/isaac/google/push_spec.clj`.

Resume command: `cd isaac-google-1zkz && bb spec && bb ci`.


## Progress — 2026-09-20 (scrapper@isaac-work-1, update 3)

Branch `bean/isaac-1zkz` @ `1175862` (pushed). All specs green: 124 examples, 0 failures.

**Done since update 2**
5. `src/isaac/google/push.clj` — `unwrap` keeps the envelope's `:subscription` (it names the
   project, and the project is the organization).
6. `src/isaac/google/tenants.clj` — `principal-tenant` (`:google-pubsub` -> `:default`,
   `:google-pubsub/acme` -> `:acme`) and `tenant-of-push` (subscription's project and the proved
   principal must agree; disagreement is `{:refused :tenant-mismatch}`).
7. `src/isaac/google/http.clj` + new `spec/isaac/google/http_spec.clj` — the door decides the tenant,
   stamps `:tenant` on the persisted event, logs it on `:google/push-received`, and answers **403**
   with `:google/tenant-mismatch` when the subscription and the service account disagree (nothing kept).
8. `src/isaac/google/worker.clj` + new `spec/isaac/google/worker_spec.clj` — binds `tenants/*tenant*`
   from the event's `:tenant` around the handler call, so a handler asking for "the token" gets the
   right organization's without being told which.
9. NEW `src/isaac/google/door.clj` + spec — `trust-rules` builds one data-shaped OIDC rule **per
   tenant** (`:google-pubsub` flat, `:google-pubsub/<tenant>` otherwise), each with config refs at
   that tenant's own `[:google <id> :push :endpoint]` / `:service-account`, principal scoped
   `#{:google/push}`. `register-trust-rules!` takes the registrar as an argument and resolves
   `isaac.http.auth/register-identity-entry!` only when present — isaac-http is **not** a runtime
   dependency of this module (deps.edn: it is in `:spec`/`:features` only), so the call must stay soft.

**Next — not started**
10. Wire `door/register-trust-rules!` into `isaac.google.component/start!` (`src/isaac/google/component.clj:17`)
    so a tenanted host actually gets its per-tenant rules at boot; keep the manifest's single
    `:isaac.http/identity {:google-pubsub ...}` rule as the flat/default case. Feature runs must see it.
11. Per tenant: registration timer (`registration/tick!` per tenant), `people/resolve`, `tools/whois`,
    `cli` `login [--tenant t]`, `status` grouped by tenant.
12. Scenarios: new `features/tenants.feature` (the bean's four); `push_door.feature` and `login.feature`
    must pass unchanged. `bb ci` has not been run yet this bean — only `bb spec`.

**Resume at** `src/isaac/google/component.clj:17` (`start!` — register the door's trust rules),
starting with a RED spec in `spec/isaac/google/component_spec.clj`.

Resume command: `cd isaac-google-1zkz && bb spec && bb ci`.
