---
# isaac-1jep
title: 'isaac-google: Pub/Sub push door + durable inbox + :isaac.google/handler berth'
status: draft
type: feature
priority: high
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:17:38Z
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

## Scenarios to draft

1. A push with a valid token for the configured audience is persisted and answered 204 before any handler runs.
2. Bad audience / wrong service account / bad signature → rejected by wrap-auth (401), nothing persisted; a valid Google token on any other route is 403 (scope `:google/push` only).
3. The same messageId twice → one inbox record.
4. The worker hands a `google.workspace.chat.message.v1.created` event to the handler a fixture module contributed; a `:gmail/watch` event to another.
5. A handler that throws leaves a failed record and the worker keeps going.

## Smoke

`gcloud pubsub topics publish <topic> --message '{...}' --attribute ce-type=…` against a real push subscription pointed at the host (post-exposure). Recorded in the bean when done.

## Out of scope

Chat/Gmail semantics, registrations, pull.
