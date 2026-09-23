---
# isaac-sb6d
title: 'isaac-gmail: routes — match incoming mail to converse/ignore, verdict labels, unrouted default'
status: in-progress
type: feature
priority: high
created_at: 2026-09-23T19:29:04Z
updated_at: 2026-09-23T22:16:28Z
---

Micah 2026-09-23: Yopp will get every kind of mail — conversations to answer on the thread, mail that should become tasks, mail to ignore. Triage must stay deterministic wherever a rule can do it. Design discussed in the planner session; this is bean 1 of 4 (routes/labels), followed by isaac-gmail pull mode, task routes via hail, and model triage fallback.

## Today

`gate/drop-reason` (allow-from + DMARC/SPF/DKIM alignment) then every allowed INBOX message starts a turn in session `gmail-<threadId>` on one crew, reply on thread. No notion of message type.

## Design

**Pure triage.** New namespace `isaac.comm.gmail.routes`: `(decide cfg message) -> {:route <name> :action :converse|:ignore|:unrouted ...}`. Takes the `message/from-api` map only (from, from-email, to, subject, labels, headers, body). No Gmail API calls, no cursor, no watch — the push handler and a future pull tick both feed it. A future IMAP module would only have to produce the same map.

**Gate signals (free, before any token).** In addition to the existing sender gate, a message is `:ignore`d when it carries `Precedence: bulk|list|junk`, `Auto-Submitted:` other than `no`, `List-Unsubscribe:`, or a Gmail category label other than CATEGORY_PERSONAL (config `gmail/ignore-categories`, default the promotions/social/updates/forums set). `message/from-api` must expose those headers.

**Routes config.** `gmail/routes` — an ordered vector, first match wins:
```edn
[{:name "ops" :match {:to "yopp+ops@*"} :action :converse :crew "ops"}
 {:name "newsletters" :match {:from "*@substack.com"} :action :ignore}
 {:name "team" :match {:from "*@tonotop.com"} :action :converse}]
```
`:match` keys: `:to`, `:from` (glob, `*` only, case-insensitive, plus-address aware so `yopp+ops@*` matches the To/Cc/Delivered-To addresses), `:subject` (`#"regex"` string), `:label` (Gmail label name), `:list-id`. Every given key must match (AND). Missing `:match` = match all (use last). `:crew` optional per route, default `gmail/crew`. A message allowed by the gate that matches no route is `:unrouted`: labelled, no turn, logged once at info with from/subject. **Nothing unexpected burns tokens.**

**Verdict labels = audit trail + idempotency.** Every gated message (including ignores and unrouted) gets Gmail label `isaac/<route-name>` (or `isaac/ignored`, `isaac/unrouted`) via a new `api/messages-modify!` (POST users.messages.modify addLabelIds) and `api/labels-create!` on first use (cache the id map per tenant; label names are config `gmail/label-prefix`, default `isaac`). The label is applied **before** the turn starts. A message that already carries any `isaac/` label is skipped by the handler — that is how two hosts (push + pull) on one inbox never double-route. Ignored mail is also marked read (removeLabelIds UNREAD) only when `gmail/ignore-marks-read` is true (default true).

**Scope.** Labels need `https://www.googleapis.com/auth/gmail.modify`; add to the manifest scope contribution (it supersedes readonly). Deploy note: scopes ride the login union — Yopp must re-login BEFORE this ships or every modify 403s. Say so loudly in the handoff.

**Log**, not comm: no in-channel notices for mail. `:unrouted` counts are visible as the label.

## Acceptance (features/comm/gmail/routes.feature, reuse gmail.feature steps)

- [ ] Route `:to "yopp+ops@*"` matches a message delivered to `yopp+ops@tonotop.com` → turn on crew `ops`, session `gmail-<threadId>`, label `isaac/ops` applied (modify call seen) before dispatch.
- [ ] First matching route wins; a later broader route does not override.
- [ ] `:ignore` route → no turn, label `isaac/newsletters`, UNREAD removed; with `gmail/ignore-marks-read false` UNREAD kept.
- [ ] Allowed sender, no matching route → no turn, label `isaac/unrouted`, one info log line with from + subject.
- [ ] `Precedence: bulk` from an allowed sender → ignored with label `isaac/ignored` even though a converse route would match.
- [ ] A message already labelled `isaac/team` is skipped: no modify, no turn, debug log `:gmail/already-routed`.
- [ ] Missing label `isaac/ops` → labels.create called once, then reused for the next message (one create across two messages).
- [ ] No `gmail/routes` configured → behaves as today (every allowed message converses on `gmail/crew`) but still labels `isaac/default`. Existing gmail.feature scenarios stay green.
- [ ] `routes/decide` unit specs: glob, plus-address, AND semantics, subject regex, list-id.
- [ ] Manifest: scope `gmail.modify`, config keys declared (`gmail/routes`, `gmail/label-prefix`, `gmail/ignore-categories`, `gmail/ignore-marks-read`); version bump; `bb spec`, `bb features`, `bb lint` green.

Likely repo scope: isaac-gmail (`gate.clj`, new `routes.clj`, `labels.clj`, `api.clj`, `message.clj`, `handler.clj`, manifest, features). Read-only elsewhere.

## Config layout — one file per route (supersedes the `gmail/routes` vector above)

Micah wants to add a route by adding a file. The foundation loader already reads any top-level key as `config/<key>/` with one `.edn` per entry (isaac-49zp), but a namespaced key such as `gmail/routes` cannot be a directory name, so routes are a **module-declared top-level key** `:gmail-routes`, a map of route name → route, declared in the manifest schema like `:cron`/`:crew` tables. Routes are ordered by `:order` (ascending; ties by name) since a map carries no order.

```
~/.isaac/config/
  gmail-routes/
    _.edn            ; optional table defaults, e.g. {:crew "yopp"}
    ops.edn          ; {:order 10 :match {:to "yopp+ops@*"} :action :converse :crew "ops" :desc "Ops asks and incidents"}
    invoices.edn     ; {:order 20 :match {:subject "(?i)\\binvoice\\b"} :action :task :band "finance" :ack true :desc "Bills to file"}
    newsletters.edn  ; {:order 30 :match {:from "*@substack.com"} :action :ignore}
    team.edn         ; {:order 90 :match {:from "*@tonotop.com"} :action :converse :desc "Colleagues"}
```

Inline in `isaac.edn` as `{:gmail-routes {:ops {...}}}` is equivalent. `isaac config set gmail-routes.ops.crew ops` works through the normal tree, and adding/removing a file hot-reloads like any config key. Optional `:comm` on a route restricts it to one gmail comm when several tenants run. `:desc` is what the triage fallback (isaac-betb) shows the model. The `:task` action lands in isaac-3427; here only `:converse` and `:ignore` are implemented, and an unknown action is a validation error naming the file.

Extra acceptance:
- [ ] `config/gmail-routes/ops.edn` alone (no inline key) yields the route; two files order by `:order`, not filename.
- [ ] Adding `newsletters.edn` while running is picked up on the next message (config reload), no restart.
- [ ] `isaac config validate` reports an unknown `:action` with the route name.

feature-baseline: isaac-gmail 63f87c6c160537f1761c8b6179b08308606e68f7
feature-blob: isaac-gmail features/comm/gmail/routes.feature 9a5902e236f2d99df6ce83d6d3a623c71ca4da0b

## Routes are the whitelist (Micah, 2026-09-23 — supersedes the gate paragraph above)

`gmail/allow-from` goes away. A `:converse` or `:task` route must name `:from` (validation error otherwise); an `:ignore` route may omit it. A message no route claims is `isaac/unrouted` — labelled, no turn — which is the fail-closed drop the global list used to give. The authentication check moves with it: any `:from` with a wildcard domain (`*@tonotop.com`) requires `gate/authenticated?` (DMARC pass, or SPF+DKIM aligned) exactly as the old `*@domain` allow-from entries did; a spoofed sender is dropped with the existing `:unauthenticated` warn log, never routed. The baselined scenarios in `features/comm/gmail/routes.feature` carry no `gmail/allow-from`; build to them. Keep the manifest key declared-but-retired only if the schema has a retired marker; otherwise remove it and say so in the handoff.

Deploy note for the planner (not the worker): `gmail.modify` joins the scope union — Yopp must re-login BEFORE this ships.


## Worker findings — gate FAIL, held for planner review (2026-09-23)

Implemented on `bean/isaac-sb6d` (isaac-gmail @ 913811d): `isaac.comm.gmail.routes`
(pure `decide`), `isaac.comm.gmail.labels` (verdict labels, per-tenant id cache,
idempotency skip), `api/messages-modify!` + `api/labels-create!`, manifest
`:gmail-routes` table + `gmail.modify` scope + `:isaac.config/check` validation,
`gmail/allow-from` retired via `:retired?` schema marker. 9 of the 12 baselined
`routes.feature` scenarios have `@wip` removed and pass; `bb spec` (88 examples)
and `bb lint src/` are green.

`bb bean-gate verify isaac-sb6d --dir isaac-gmail=<worktree>` currently:

```
isaac-sb6d bean-gate: FAIL (3) — isaac-gmail @ HEAD 913811d (branch bean/isaac-sb6d)
  FAIL isaac-gmail features/comm/gmail/routes.feature: scenario "Scenario: a route matching the delivery address starts a turn on that route's crew" still carries @wip
  FAIL isaac-gmail features/comm/gmail/routes.feature: scenario "Scenario: a message matching no route is labelled unrouted and logged once, no turn" still carries @wip
  FAIL isaac-gmail features/comm/gmail/routes.feature: scenario "Scenario: adding a route file while running is picked up on the next message, no restart" still carries @wip
```

Left `@wip` on those 3 deliberately — each is a genuine contract issue I can't
resolve without editing the baseline, which is the planner's call:

1. **"a route matching the delivery address..." (line 24).** Its route names
   `:crew "ops"`, but nothing in the Background or scenario declares
   `crew.ops.model` (or `config/crew/ops.edn`). isaac-agent's own
   `features/bridge/crew.feature` establishes the pattern: testing a named
   crew requires declaring it first (`config/crew/ketch.edn` with a model) —
   without that, `bridge/core.clj`'s dispatch rejects the turn silently (no
   exception, just an empty transcript; verified empirically). Separately,
   even a *declared* crew (tested with `main`) never gets a `message.crew`
   tag on individual transcript entries outside the `/crew` switch-command
   flow — so the scenario's `message.crew | ops` assertion on the *user* row
   may not be satisfiable by any direct-dispatch turn at all, only by a
   crew that later `/crew`-switches. Needs a planner decision: add
   `crew.ops.model`, and confirm whether route-driven turns are expected to
   tag `message.crew`.

2. **"a message matching no route is labelled unrouted..." (line ~110) vs.
   "no routes configured behaves as before, but still labels the default
   route"** (unwip'd, passing). Both scenarios configure **zero**
   `gmail-routes` (no `Given config: gmail-routes.*` block, same Background)
   and expect opposite outcomes: the first wants `isaac/unrouted` + no turn,
   the second wants `isaac/default` + a full converse transcript. No
   deterministic `decide` can satisfy both from identical input. I
   implemented "empty table → converse on the default crew, label
   isaac/default" (matching the bean's original acceptance bullet and the
   second scenario's explicit title), which is why the first now fails.

3. **"adding a route file while running..." (line ~252).** Its first push
   has only `gmail-routes/team.edn` configured (`match.from *@tonotop.com`),
   and the pushed message is from `digest@substack.com` — which the team
   route does **not** match. Under "Routes are the whitelist" (a *non-empty*
   table with no match → `:unrouted`, no turn) this can't converse, yet the
   scenario expects a full "Noted." transcript. Same family of issue as #2,
   third variant: here a route table is non-empty but simply doesn't match,
   and the scenario still wants the default-converse fallback.

**gmail.feature reverted, not edited.** I initially rewrote `gmail.feature`
(not baselined by this bean) to stop using the retired `gmail/allow-from`, and
the gate correctly flagged that: *"edits a feature file the bean did not
baseline"*. Reverted to pristine (byte-identical to `465f793`). Direct,
unavoidable consequence: 2 of its 6 scenarios now fail —
`"sent mail, label-only changes, and unknown senders never start a turn"` and
`"a *@domain allow-list entry admits the domain only when Gmail authenticates
it (isaac-dymn)"` — both assert on `gmail/allow-from` filtering that no longer
exists. This is the same tension as #2/#3 above, one level up: "Routes are the
whitelist" mandates removing `allow-from`, but the gate forbids the worker
from updating the one feature file that still exercises it. `gmail.feature`
needs its own planner pass (rewritten onto `gmail-routes`, then re-baselined
or left to a follow-up bean) — out of scope for me to touch under this
bean's baseline.

**Also rebased.** Picked up isaac-0r95 (465f793, landed on gmail main after my
branch point) — `default-crew` now reads `isaac.config.defaults/crew-id`
(`[:defaults :frequencies :crew]`) instead of the retired flat
`[:defaults :crew]`; foundation/agent pins carried through unchanged.

**Not landed** — gate exit is non-zero, so `bean/isaac-sb6d` stays on the
remote (913811d), bean stays `in-progress`. No `main-sha:` line; nothing was
squashed into isaac-gmail `main`.

**Deploy note (unchanged from Routes-are-the-whitelist section above):**
`gmail.modify` scope joins the union — Yopp must re-login before this ships,
whenever it lands.

feature-baseline: isaac-gmail 3f55d061fc485b9059de1612a9b990dcbdf0d508
feature-blob: isaac-gmail features/comm/gmail/routes.feature be629f353c555797920c607b54bc81eca4d8fb8e
feature-blob: isaac-gmail features/comm/gmail/gmail.feature 473aed7527f77e4221b843f9dcd3414c21154821

## Exceptions

Planner, 2026-09-23, after the worker's gate FAIL(3):
- Scenario "no routes configured behaves as before, but still labels the default route" **removed** — it predates the routes-are-the-whitelist ruling; with zero routes nothing converses and every message is `isaac/unrouted` (the "matching no route" scenario covers it).
- Hot-reload scenario: the first substack message (only team.edn configured) is now `isaac/unrouted` with no turn; after newsletters.edn appears it is `isaac/newsletters`; session count 0 throughout.
- Ops-crew scenario gained `crew.ops.model grover` / `crew.ops.soul` fixture rows.
- `gmail.feature` migrated to routes (Background `gmail-routes.team` names ada; the mallory scenario expects `isaac/unrouted` + `:gmail/unrouted` info log instead of a `:sender` drop; the isaac-dymn scenario configures a `*@tonotop.com` route and expects `isaac/domain` on the authenticated message). All six tagged `@wip` and added to this bean's baseline; the worker un-tags them as they pass.
- Baseline re-cut on isaac-gmail 3f55d06.
