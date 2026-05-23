---
# isaac-aqrg
title: 'Hail HTTP route: POST /hail/send for external producers'
status: todo
type: feature
created_at: 2026-05-23T21:57:57Z
updated_at: 2026-05-23T21:57:57Z
parent: isaac-ugx7
blocked_by:
    - isaac-vduq
---

## Motivation

Slice 5c of the Hail epic. External systems (CI, GitHub webhooks,
beans CLI hooks, other Isaac installs) need to publish hails too.
The CLI (`isaac hail send`) works for shell scripts on the host;
remote / non-shell callers want HTTP. This bean adds
`POST /hail/send` to the existing isaac-server.

## Scope

### Route

```
POST /hail/send
Content-Type: application/json     ;; or application/edn
Body: { ...hail record... }
```

The body is a serialized hail with addressing (per the locked
shape: `:frequency {...}`), optional payload, optional prompt
(required for non-band addressing).

JSON body example:

```json
{
  "frequency": {"band": "bean-pickup"},
  "payload":   {"bean-id": "isaac-abc"}
}
```

EDN body example:

```clojure
{:frequency {:band "bean-pickup"}
 :payload   {:bean-id "isaac-abc"}}
```

### Response

```json
HTTP 201 Created
Location: /hail/<id>
Content-Type: application/json
Body: { "id": "hail-42", ...full record... }
```

On validation error:
```json
HTTP 400 Bad Request
{ "error": "missing addressing", "hint": "include :frequency with at least one address field" }
```

### Authentication

Reuses the existing server-wide auth token (per isaac-g69y).
Requests without the token return 401.

### `:from` identity

`:from :http` (or `:from :external` — bikeshed). Distinct from
`:cli` and `:crew/<name>`.

## Out of scope (deferred)

- **Streaming long payloads** (chunked upload).
- **Webhook signature verification** for callers like GitHub —
  follow-up.
- **Inbound rate-limiting** per source IP.

## Acceptance

- `POST /hail/send` accepts JSON and EDN content types.
- Body is parsed and validated (addressing required; matches
  locked hail schema).
- Calls `isaac.hail.queue/send!`; persists the hail.
- Returns 201 with the full record (in matching content-type) and
  `Location` header pointing at the new hail id.
- Returns 400 with a clear message on validation failure.
- Returns 401 without valid auth token.

## Feature scenarios

`features/hail/http.feature` or extend `features/server/*`, `@wip`.
To draft later:

- POST with JSON body and valid auth → 201 + hail persisted.
- POST with EDN body and valid auth → 201 + hail persisted.
- POST with missing addressing → 400 with hint.
- POST without auth token → 401.
- Returned record's `:from` is `:http`.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-vduq (substrate)** — uses
  `isaac.hail.queue/send!`.
- **Depends on existing server auth (isaac-g69y).** Same token
  protects this route.
- **Independent of fan-out / wake** — this bean only produces.
