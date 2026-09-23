---
# isaac-sb6d
title: 'isaac-gmail: routes — match incoming mail to converse/ignore, verdict labels, unrouted default'
status: todo
type: feature
priority: high
created_at: 2026-09-23T19:29:04Z
updated_at: 2026-09-23T19:32:47Z
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
