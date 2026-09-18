---
# isaac-1jep
title: 'isaac-google: Pub/Sub push door + durable inbox + :isaac.google/handler berth'
status: todo
type: feature
priority: high
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:41:35Z
parent: isaac-bv1l
blocked_by:
    - isaac-6aw3
    - isaac-bzgw
---

The door and the inbox. After this bean a hand-published Pub/Sub message reaches a handler inside Isaac, exactly once, with the agent nowhere in the path.

## Scope (isaac-google)

- Route `POST /google/pubsub` via the HTTP route berth, declared with **`:scope :google/push`** (isaac-gym1). Authentication is an **identity source** contributed to gym1's `:isaac.http/identity` berth, not code in the handler: it verifies the Pub/Sub push OIDC bearer (signature against Google's certs, issuer, `aud` = the configured door URL, `email` = the configured push service account) and yields principal `{:name :google-pubsub :scopes #{:google/push}}`; `wrap-auth` does the rest. Anything else → 401/403 from wrap-auth, which also feeds burst control. Blocked by isaac-bzgw for the berth and route `:scope`. If bzgw's identity berth turns out header-mapping only (no request verifier), this bean extends it — say so in bzgw first.
- Unwrap the envelope (`message.data` base64, `message.attributes`, `messageId`, `publishTime`). Dedupe on `messageId` (at-least-once delivery) with a bounded seen-set persisted alongside the inbox.
- Persist the event as a durable record (pattern: `isaac.hail.store` records + lifecycle dirs) and answer **204 immediately**. Pub/Sub retries only non-2xx and its ack deadline is short; processing never happens inside the request.
- Inbox worker (`:isaac/component`): drains records in order, resolves the handler from the new berth `:isaac.google/handler` keyed on event type (Chat: CloudEvents `ce-type` attribute prefix `google.workspace.chat.`; Gmail: no ce-type, `emailAddress`/`historyId` in data → `:gmail/watch`), calls it, moves the record to done/failed. A handler exception = failed record + log, never a retry storm; a missing handler = logged and parked.
- Config additions to `:google`: `:push {:endpoint "https://…/google/pubsub" :service-account "…@…iam.gserviceaccount.com"}`.

## Scenarios (approved 2026-09-18, Micah) — committed `@wip` in isaac-google `84302db` `features/push_door.feature`

| line | scenario |
|---|---|
| :20 | a valid push is persisted and acknowledged before any handler runs |
| :35 | anything but Google's token for this door is refused and nothing is kept |
| :59 | a Google token opens only the door (403 on a route with another scope) |
| :64 | at-least-once delivery becomes exactly-once processing |
| :78 | events route by type and a failing handler does not stop the worker |

## isaac-http leg (small, lands first)

isaac-bzgw does not declare an identity berth (the epic defers identity sources). This bean adds it: manifest berth **`:isaac.http/identity`**, entries `{<id> {:verify sym}}` where `(verify request) -> {:name kw :scopes #{kw}} | nil`; `wrap-auth` consults contributed verifiers before the bearer-hash lookup, so a verifier's principal flows through the same scope check, request log (`:principal`) and burst control. Spec in isaac-http; pin bump in isaac-google.

## Step ledger

| step | status |
|---|---|
| an Isaac root at … / config: / the Isaac server is started / the client sends … / the response status is / the log has (no) entries matching: / the isaac file … exists / does not exist | reuse (foundation, isaac-http) |
| a fixture route GET /fixture requires scope … | reuse (NEW in isaac-bzgw — this bean waits for it) |
| **Google signs push tokens with a test key** | **NEW — test RSA key + JWKS stub the verifier reads instead of googleapis certs** |
| **Google pushes message {id} of type {ce-type} with data:** / **Google pushes a Gmail watch message {id} with data:** / **Google pushes to GET {path}** | **NEW — mints an OIDC token (aud = configured endpoint, email = configured SA, unless tweaked) and POSTs the Pub/Sub envelope; the Gmail variant sends no ce-type attribute** |
| **the next push token has audience {aud}** / **… is from {email}** / **… is unsigned** | **NEW — one-shot tweaks to the next minted token** |
| **the skybeam fixture module handles Google events of type {type}** / **the longwave fixture module …** / **the skybeam handler throws** | **NEW — fixture modules under test-resources/marigold contributing `:isaac.google/handler`; handlers record calls** |
| **the skybeam handler received message {id}** / **… received message {id} once** / **… received no messages** / **the longwave handler received message {id}** | **NEW** |
| **the inbox worker ticks** | **NEW — drains the inbox once on the caller thread (hail's "delivery worker ticks" shape)** |
| **the inbox holds {n} record for message {id}** | **NEW** |

Log events this bean defines: `:google/push-received` (info; `:principal :message-id :type`), `:google/push-refused` (warn, throttled), `:google/handler-failed` (error; `:message-id :type :error`), `:google/handler-missing` (warn). Inbox layout: `<root>/google/inbox/{pending,done,failed}/<messageId>.edn`, seen-set persisted at `<root>/google/inbox/seen.edn` (bounded).

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-google && bb features features/push_door.feature:20
cd isaac-google && bb features features/push_door.feature:35
cd isaac-google && bb features features/push_door.feature:59
cd isaac-google && bb features features/push_door.feature:64
cd isaac-google && bb features features/push_door.feature:78
cd isaac-google && bb ci
cd isaac-http && bb ci    # identity berth leg
```

The `:features` alias needs `isaac-http-spec` (server steps) — add alongside the existing http test-support dep.

## Smoke

`gcloud pubsub topics publish <topic> --message '{...}' --attribute ce-type=…` against a real push subscription pointed at the host (post-exposure). Recorded in the bean when done.

## Out of scope

Chat/Gmail semantics, registrations, pull.

Dispatched: hail 8c9bfd7d 2026-09-18T17:20Z (band isaac-work)
