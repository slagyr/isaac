---
# isaac-1zkz
title: 'isaac-google: tenants — one Isaac host serves several Google organizations, each a complete {oauth, account, project, topic, push SA, token}'
status: completed
type: feature
priority: high
tags:
    - comm
    - config
    - google
created_at: 2026-09-20T00:25:51Z
updated_at: 2026-09-20T20:52:12Z
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

## Duplicate dispatch — 2026-09-20 (scrapper@2026-06-29-1749-iaqu)

Two sessions worked this bean at the same time, in the **same worktree**
(`~/agents/isaac/work-1/isaac-google-1zkz`, branch `bean/isaac-1zkz`). I was
hailed on `isaac-work` (hail af220fb2) after four earlier turns of this session
died with `empty-terminal-response`; another session was already in that
worktree and kept editing files under me (door.clj, the manifest and
door_spec.clj changed at 12:49–12:50 while I was running the suite), and it
swept my uncommitted files into its own commits.

What I contributed before standing down:

- `f29c4be` — the reconcile timer runs once per organization
  (`registration/tick!` surveys then reconciles per tenant with
  `tenants/*tenant*` bound; `renew-hours` is per tenant; `load-cfg` recognises
  a tenanted config). Specs in `registration_spec.clj`, all green.
- `cli.clj` (`login --tenant`, status grouped by tenant),
  `tenants/config-path` for an unconfigured host, `features/tenants.feature`
  (four scenarios) and its steps — written by me in the worktree and committed
  by the other session as `fbd0f45`.

Known state when I stopped: `bb spec` green at `f29c4be`;
`features/tenants.feature` scenarios 1 (flat host) and 4 (login/status per
tenant) pass; scenarios 2–3 (two tenants through the one door) answer 401
because **feature runs never start `:isaac/component`s**
(`isaac.component.runtime`: "Only isaac.runner invokes start-all!"), so
boot-time registration of per-tenant trust rules is invisible to features. The
other session's in-flight `door/verify-push` (a code verifier contributed to
`:isaac.http/identity`) is the right answer to that; boot-time registration
from the component is not.

Also fixed on the way out: `google_steps.clj` had both a `defgiven` and a
`defthen` for the same tenant auth-store phrasing, which makes gherclj throw
"ambiguous step match" — the `defgiven` is removed in the worktree.

I am not handing this bean off; the other session owns it. Escalated to the
human so the duplicate dispatch can be stopped.


## Duplicate dispatch observed 2026-09-20 (scrapper@isaac-work-1)

Two turns worked this bean at the same time, in the same worktree
(`isaac-google-1zkz`). Hail `af220fb2` was still in flight when hail `f01037ca`
dispatched the same bean-id to the same session; `ps` showed two live
`claude --print` processes, and files written by one turn were overwritten by
the other within seconds (`src/isaac/google/component.clj`,
`feature-steps/isaac/google_steps.clj`, `features/tenants.feature`).

Resolution taken by the second turn (this one): it stood down rather than keep
clobbering. Every file it had changed since `a38b37b` — `door.clj`,
`door_spec.clj`, `component_spec.clj`, `component.clj`, `isaac-manifest.edn` —
was restored to `HEAD`, leaving the first turn's in-flight edits
(`feature-steps/isaac/google_steps.clj`) untouched. `bb spec` at that point:
131 examples, 0 failures. No commit was made from this turn after `a38b37b`.

What the abandoned second line of work had found, in case it is useful:
`features/tenants.feature`'s two-organization scenarios fail with **401**
because the manifest's single `:isaac.http/identity` rule uses static config
refs (`[:google :push :endpoint]`) that do not resolve under a tenanted
config, and the feature server starts routes and berths but **not**
`:isaac/component`, so nothing registers the per-tenant rules at boot. The two
candidate fixes are (a) a feature step that starts the component (what the
first turn is doing), or (b) contributing a code verifier symbol
(`isaac.google.door/verify-push`) to `:isaac.http/identity`, which builds each
organization's rule from live config at request time and needs no component.

Orchestration bug to fix: one bean must not be dispatched to a session that is
already running a turn on it.

## Handoff to verify — 2026-09-20 (scrapper@2026-06-29-1749-iaqu)

Branch **bean/isaac-1zkz @ 596fae8** in `isaac-google` (base `origin/main@6c8a29f`),
pushed. `bb bean-gate verify isaac-1zkz` → *no feature-baseline: use the verify
path*, so this is the unverified/verify handoff, not a self-landing.

**Suites, at 596fae8:** `bb spec` 133 examples, 0 failures · `bb features` 27
scenarios, 0 failures · `bb config-bypass-lint` ok. `bb lint`'s counts are
identical with and without this branch's changes (pre-existing speclj macro
noise; `bb ci` does not run it).

### What a tenant is, in code

`isaac.google.tenants` is the seam: a flat `:google {:project …}` reads as the
single tenant `:default`, a map of tenant id → tenant reads as itself, and
`*tenant*` is the organization the current thread is acting as. Nothing on a
single-organization host changes — that is scenario 1, and it is asserted by
the whole existing suite passing unchanged, not only by the new scenario.

- **config** — `google-schema` carries both `:schema` (one organization's
  fields, flat, closed) and `:key-spec`/`:value-spec` (any other key is a
  tenant id), so `isaac config set google.acme.project …` walks. The manifest's
  inline copy is asserted equal to `config/google-schema` so it cannot drift.
- **tokens** — one provider key per tenant (`google`, then `google/<tenant>`),
  refreshed with that tenant's own OAuth client.
- **the door** — `isaac.google.door/trust-rules` builds one data-shaped OIDC
  rule per organization, each pointed at its own `[:google <id> :push …]` refs,
  granting `:google-pubsub/<tenant>` with nothing but `:google/push`.
  `component/register-door!` registers them at component start and is
  deliberately *not* on the scheduler path — a host with background services
  off still answers pushes.
- **the push** — `http/handler` decides the tenant from two independent claims
  (the project in the envelope's `subscription`, and the principal the token
  proved), stamps `:tenant` on the persisted event, and answers **403
  `:google/tenant-mismatch`** when they disagree, keeping nothing. The worker
  binds `*tenant*` around the handler call.
- **the timer** — `registration/tick!` surveys then reconciles **once per
  tenant** with `*tenant*` bound, each on its own `renew-within-hours`, with
  one health evaluation over all tenants' keys.
- **the CLI** — `isaac google login --tenant <t>`; `isaac google status` groups
  by tenant when there is more than one and is byte-identical when there is one.

### Scenarios

`features/tenants.feature` (4 scenarios, all green) covers the bean's 1, 2 and
4: a flat host's push is the `:default` tenant's; two organizations share the
door and each is accepted only under its own service account; a push crossing
one organization's SA with another's subscription is refused 403 and nothing is
kept; each organization signs in for itself and `status` lists both.

One new step, `Given the Google runtime component is started`, exists because
**feature runs never start `:isaac/component`s** (`isaac.component.runtime`:
"Only isaac.runner invokes start-all!"), so a scenario that needs a tenanted
door has to say so. Worth knowing for any module whose boot work lives in a
component.

### Not done — the bean's scenario 3 (a comm bound to a tenant)

"a comm bound to tenant :acme sends with acme's token and subscribes acme's
spaces to acme's topic" is **not** implemented. isaac-google now provides
everything the comm side needs (`*tenant*`, `token/token <id>`, per-tenant
registration ticks whose `:key`/`:remote` hooks run with `*tenant*` bound), but
the `:comms {… :google :acme}` key itself belongs to **isaac-gchat** (and the
Gmail comm), not to this repo: there is no isaac-gmail checkout in this
workspace at all, and isaac-gchat has two other beans in flight in its
checkouts (isaac-vo2q, isaac-0gtc). Recommend a follow-up bean per comm module
rather than editing a repo mid-flight from here — planner's call.



## Planner adjustment (2026-09-20, prowl@isaac-plan) — first owner keeps 1zkz; duplicate dispatch is a new bean

Conflict: hail `af220fb2` was still executing when hail `f01037ca` dispatched the same bean-id to the same session; two turns clobbered `isaac-google-1zkz`. Second turn stood down (restored its files since `a38b37b`, left first turn's edits, `bb spec` 131/0). First turn owns the bean; handoff to verify already sent (`4bf95c5a`, reply-to `af220fb2`). Bean is `in-progress` + `unverified`.

**Decision: do not re-dispatch isaac-1zkz to work. Do not start a third turn.** The first owner / verifier keeps it. Product work (tenants) is not rescoped here. Scenario 3 (comm bound to a tenant) stays deferred to a follow-up per-comm bean — not this conflict.

### Orchestration (not this bean)

- **isaac-3wiu** (todo) — recovery rebound work hails into ad-hoc `2026-06-29-1749-iaqu` by crew, not by band.
- **isaac-dr1k** (draft) — in-band duplicate bind: do not start a second turn on a session already running the same bean-id.

### Worker / verifier now

Verifier (`4bf95c5a`) continues. If still red, return to **one** work session. Do **not** hail a second work turn while one is live. Do **not** land from this planner hail.

This note resets nothing on the product contract.

## Verify fail (attempt 1, 2026-09-20)

**perceptor**@isaac-verify — FAIL: the bean's scenario 3 (a comm bound to a tenant) is not implemented, and the bean's own scenario plan ("google: tenants.feature; **gchat/gmail one each**") has no gchat or gmail scenario. The worker says so itself in its handoff.

### What IS green (isaac-google, branch bean/isaac-1zkz @ 596fae8, base origin/main@6c8a29f)

- `bb ci`: `bb spec` 133 examples / 0 failures / 202 assertions; `bb features` 27 scenarios / 0 failures / 110 assertions; `config-bypass-lint: ok`. CI_EXIT=0.
- `features/tenants.feature` — 4 scenarios, no `@wip` anywhere in `features/`.
- Covers bean scenarios **1** (flat host reads as `:default`, existing push_door/login scenarios pass unchanged), **2** (two organizations through the one door; crossed SA/subscription refused 403 with nothing persisted) and **4** (login per tenant, separate tokens, status lists both).
- Diff scope is isaac-google only; no `deps.edn` / `bb.edn` pin changes, so no repin needed on this branch.
- New `println` calls are all in `src/isaac/google/cli.clj` (CLI stdout — verify.md §3's legitimate exception). Log noise in the spec run is pre-existing module logging, not stray prints.
- `bb bean-gate verify isaac-1zkz` → exit 2 (ungated; verify path correct).

### Why this is a FAIL, not a pass

Bean scenario 3 — "a comm bound to tenant `:acme` sends with acme's token and subscribes acme's spaces to acme's topic" — has **no implementation and no scenario anywhere**. `isaac.google.tenants` gives the comm side everything it needs, but nothing consumes it:

- `isaac-gchat` still takes the process token with no tenant: `src/isaac/comm/gchat.clj:21` and `src/isaac/comm/gchat/chat_api.clj:54` both call `((requiring-resolve 'isaac.google.token/token))` (0-arity). Its comm schema (`resources/isaac-manifest.edn` `:extra-schema`) has no `:google` key, so `:comms {:gchat-acme {:type :gchat :google :acme}}` from the bean's Shape section cannot even be written.
- `isaac-gmail` likewise (`src/isaac/comm/gmail/*`), and its registration contribution is per-mailbox with no tenant.

A verifier cannot pass a bean whose acceptance scenarios do not exist.

### The worker's stated blocker is stale

The handoff defers scenario 3 because "isaac-gchat has two other beans in flight in its checkouts (isaac-vo2q, isaac-0gtc)" and "there is no isaac-gmail checkout in this workspace". Checked today:

- `isaac-vo2q` → **completed**. `isaac-0gtc` → **completed**. Nothing is in flight in isaac-gchat; `origin/main` is `5bcaa35`.
- `isaac-gmail` is a real repo and clones fine (`origin/main` `476e4d2`); it just was not in that worker's workspace. `git clone git@github.com:slagyr/isaac-gmail.git` next to the others.

So the reason to descope is gone. If the planner still wants scenario 3 split into follow-up beans per comm module, that is the planner's call to make on a re-hail — not a verifier's, and not a worker's.

### To finish

1. Keep branch `bean/isaac-1zkz` in isaac-google; do not re-do the green work.
2. `isaac-gchat`: add the comm-level `:google <tenant>` key to its comm schema; resolve the tenant at send/subscribe time and call `isaac.google.token/token` with it (the 1-arity now exists); registration for that comm's spaces goes to that tenant's topic. One scenario, per the bean.
3. `isaac-gmail`: the same shape, one scenario.
4. Cross-repo landing order (verify.md §6a): isaac-google squashes to main first, then each downstream repo's `isaac-google` pin is rewritten to that squash sha and that repo's `bb ci` re-run before its own squash. One `main-sha:` line per repo.

## Handoff to verify — attempt 2, 2026-09-20 (scrapper@isaac-work-1)

Resumed from the verify fail (hail 15a254d6). The isaac-google half was not
touched except to add one seam the comms needed; the missing half — the bean's
scenario 3, a comm bound to an organization — is implemented in **isaac-gchat**
and **isaac-gmail**, one scenario each, as the bean's scenario plan asks.

`bb bean-gate verify isaac-1zkz` → *no feature-baseline: use the verify path*,
so this stays the unverified/verify handoff, not a self-landing.

### Branches (all pushed)

| repo | branch @ head | base | suites |
|------|---------------|------|--------|
| isaac-google | bean/isaac-1zkz @ 71fd373 | origin/main@6c8a29f | bb spec 138/0 · bb features 27/0 · lint ok |
| isaac-gchat | bean/isaac-1zkz @ 17bfa22 | origin/main@5bcaa35 | bb spec 84/0 · bb features 27/0 · lint ok |
| isaac-gmail | bean/isaac-1zkz @ 86900ac | origin/main@71adcee | bb spec 48/0 · bb features 13/0 · lint ok |

`bb ci` is green in all three.

### What a comm naming an organization means, in code

`isaac.google.tenants` gained the question itself, so both comm modules answer
it the same way: `comms` (every configured comm of one kind, by `:type` or by
the conventional name), `of-comm` (the organization a slice names with
`:google`, else the only one configured, else `:default`) and `comms-for`.
Specs in `spec/isaac/google/tenants_spec.clj` (+5 examples). Nothing else in
isaac-google changed — the 27 scenarios the last verify pass read are the same
scenarios, still green.

**isaac-gchat** (`:comms {:gchat-acme {:type :gchat :google :acme …}}`)

- `resources/isaac-manifest.edn` — the comm's `:extra-schema` gained `:google`
  (`:type :keyword`), so the bean's Shape section can now be written at all.
- `src/isaac/comm/gchat.clj` — `access-token` takes the comm's slice and asks
  `isaac.google.token/token` for *that organization's* token; `send!` and
  `on-reply` pass it. The 0-arity stays for the agent tools, which run with
  `*tenant*` already bound.
- `src/isaac/comm/gchat/tenant.clj` (new) — the Chat half: which spaces belong
  to an organization, and the live-config lookup for a running comm.
- `src/isaac/comm/gchat/registration.clj` — a reconcile pass runs once per
  organization with `tenants/*tenant*` bound, so `space-keys` answers with that
  organization's comms' spaces only and `create!` subscribes them to that
  organization's `:topic` in its own project.
- `features/comm/gchat/tenants.feature` — 2 scenarios: a comm bound to acme
  posts with `Bearer at-acme` while the tonotop comm posts with `at-tonotop`;
  one timer tick subscribes `spaces/ACME` to acme's topic with acme's token and
  `spaces/ENG` to tonotop's with tonotop's.
- The feature steps no longer stub `gchat/access-token` with a constant: they
  stub `isaac.google.token/token` per organization, so *which* organization the
  comm asked for is what the assertion reads.

**isaac-gmail** (`:comms {:gmail-acme {:type :gmail :google :acme …}}`)

- `resources/isaac-manifest.edn` — same `:google` key on the Gmail comm.
- `src/isaac/comm/gmail.clj` — every send runs as the comm's organization
  (`tenants/*tenant*` bound around the send), so the token, and anything else
  that asks, answer for it.
- `src/isaac/comm/gmail/tenant.clj` (new) — the Gmail half: which mailboxes
  belong to an organization.
- `src/isaac/comm/gmail/watch.clj` — `keys*` is that organization's mailboxes
  and the watch names that organization's topic.
- `features/comm/gmail/tenants.feature` — 2 scenarios, the same shape as Chat's:
  a comm bound to acme sends with `Bearer at-acme`; each mailbox is watched
  against its own organization's topic.

### Pins — read before landing

While the bean is in flight both comm repos pin isaac-google at
`{:local/root "../isaac-google-1zkz"}` in `deps.edn` **and** `bb.edn` (a
bean-branch sha pin would dangle once the branch is squashed and deleted).
Landing order, per verify.md §6a:

1. isaac-google: rebase on main, `bb ci`, squash-merge → that sha is its
   `main-sha:`.
2. isaac-gchat and isaac-gmail: rewrite both `:local/root` pins to that sha
   (`:git/url "https://github.com/slagyr/isaac-google.git" :git/sha "<main-sha>"`),
   re-run `bb ci`, then squash-merge each.
3. One `main-sha:` line per repo — three lines.

### Not done, deliberately

Inbound routing still reads the conventional `:comms :gchat` / `:comms :gmail`
slice: a push already arrives with `*tenant*` bound by the door, so the token is
the right organization's, but *which comm* handles a message for a second
organization's space is a routing question the bean does not ask. Worth a
follow-up bean if a second organization's inbound traffic is wanted.

## Verified + landed on main (attempt 2, 2026-09-20)

**perceptor**@isaac-verify — PASS. The half that failed attempt 1 (bean scenario 3: a comm bound to an organization) now exists in both comm modules, one scenario each, exactly as the bean's scenario plan asks.

### Suites — green in all three repos

| repo | branch @ head | base | `bb ci` |
|------|---------------|------|---------|
| isaac-google | bean/isaac-1zkz @ 71fd373 | origin/main@6c8a29f | spec 138/0/213 · features 27/0/110 · config-bypass-lint ok · CI_EXIT=0 |
| isaac-gchat | bean/isaac-1zkz @ 17bfa22 → 55b43ba (repin) | origin/main@5bcaa35 | spec 84/0/157 · features 27/0/61 · lint ok · CI_EXIT=0 |
| isaac-gmail | bean/isaac-1zkz @ 86900ac → a8edf6b (repin) | origin/main@71adcee | spec 48/0/76 · features 13/0/38 · lint ok · CI_EXIT=0 |

Each `bb ci` was run twice in gchat/gmail: once on the worker's `:local/root` pin, and again after the verify repin to isaac-google's main squash sha (§6a step 3) — green both times.

### Acceptance, scenario by scenario

1. **flat config reads as `:default`; existing scenarios pass unchanged** — isaac-google's 27 feature scenarios (incl. push_door, login, registration) are unmodified and green; `git diff origin/main...bean` touches no existing `.feature` file in any of the three repos, only adds new ones.
2. **two organizations through the one door** — `features/tenants.feature` (105 lines, 4 scenarios): each push accepted only under its own SA and persisted with its `:tenant`; crossed SA/subscription refused 403 `:google/tenant-mismatch` with nothing kept.
3. **a comm bound to `:acme` sends with acme's token and subscribes acme's spaces to acme's topic** — now covered twice over:
   - `isaac-gchat features/comm/gchat/tenants.feature` — comm `gchat-acme` posts to `spaces/ACME` with `Bearer at-acme` while `gchat` posts to `spaces/ENG` with `Bearer at-tonotop`; one registration tick subscribes `spaces/ACME` → `projects/acme-prod/topics/isaac` with acme's token and `spaces/ENG` → `projects/marigold/topics/isaac` with tonotop's.
   - `isaac-gmail features/comm/gmail/tenants.feature` — same shape for send and for `users/me/watch` topicName per mailbox.
   - The gchat steps no longer stub `gchat/access-token` with a constant; they stub `isaac.google.token/token` per organization, so *which* organization the comm asked for is what the assertion reads. That is a strengthening, not a weakening.
   - Both manifests gained the comm-level `:google` key (`:type :keyword`), so `:comms {:gchat-acme {:type :gchat :google :acme}}` from the bean's Shape section is now writable.
4. **login per tenant, separate tokens, status lists both** — covered in `tenants.feature` and `spec/isaac/google/{token,cli}` specs.

### Other checks

- No `@wip` in any of the three `features/` trees.
- Feature-file tampering: none — the diffs add `features/tenants.feature`, `features/comm/gchat/tenants.feature`, `features/comm/gmail/tenants.feature` and touch no existing feature file.
- Stray output: all added `println`s are in `src/isaac/google/cli.clj` (CLI stdout — verify.md §3's stated exception). None in gchat/gmail.
- Smell pass A (diff scope): no `Thread/sleep`, `currentTimeMillis`, `Date.`/`Instant/now` added in specs or feature-steps.
- `bb bean-gate verify isaac-1zkz` → *no feature-baseline: use the verify path* (ungated; verify path correct).
- Sibling pins (§6): after the repin, both downstream repos pin isaac-google at `68e29aa`, an ancestor of isaac-google `origin/main`. No dangling bean-branch shas.

### Landing (verify.md §6a order)

1. isaac-google squashed to main first → `68e29aa`; tree identical to branch tip `71fd373` (`git diff` empty).
2. gchat and gmail `deps.edn` + `bb.edn` repinned from `{:local/root "../isaac-google-1zkz"}` to `{:git/url … :git/sha "68e29aa…"}`, committed on each bean branch, `bb ci` re-run green, then squashed.
3. Squash trees verified identical to their branch tips.

### Noted, not blocking

The worker's "not done, deliberately": inbound routing still reads the conventional `:comms :gchat` / `:comms :gmail` slice — a push arrives with `*tenant*` already bound by the door, so the token is right, but *which comm* handles a second organization's inbound space is a routing question this bean does not ask. Worth a follow-up bean if a second organization's inbound traffic is wanted.

## Landed on main (2026-09-20)

main-sha: isaac-google 68e29aabb1d11067daa09938e8f1f2166c190c7d
main-sha: isaac-gchat c4f18042c77f15960c06e9db2a4cb9d53b8673ca
main-sha: isaac-gmail 1a26c67e14eb586653bff517e14fb8952ae267a3
