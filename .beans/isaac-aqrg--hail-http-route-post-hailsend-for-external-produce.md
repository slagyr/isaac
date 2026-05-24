---
# isaac-aqrg
title: 'Hail HTTP route: POST /hail/send for external producers'
status: todo
type: feature
priority: normal
created_at: 2026-05-23T21:57:57Z
updated_at: 2026-05-24T00:49:12Z
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

The body is a serialized hail with `:frequency` (per the locked
shape), optional `:payload`, optional `:prompt` (required for
non-band frequencies).

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

### Route registration

In v1 (pre-berths Isaac), the handler is registered statically at
server boot — alongside `/status`, `/hooks/*`, and other static
routes. `default Grover setup` brings the server up with the route
available; no extra config or manifest setup needed in tests.

Post-berths refactor will move this to a `:isaac.server/route`
berth declaration; not in scope for v1.

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
{ "error": "missing frequency", "hint": "include :frequency with at least one field" }
```

### Authentication

Server-wide auth (per isaac-g69y) protects this route via existing
middleware — no Hail-specific auth handling. Auth-related test
coverage lives with the server's auth feature, not duplicated here.

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
- Body is parsed and validated (`:frequency` required; matches the
  locked hail schema).
- Calls `isaac.hail.queue/send!`; persists the hail.
- Returns 201 with the full record (in matching content-type) and
  `Location` header pointing at the new hail id.
- Returns 400 with a structured `error` key on validation failure
  or unparseable body.
- Sets `:from :http` on the persisted record.

## Feature files

- `features/hail/http.feature` — 4 `@wip` scenarios:
  - POST JSON + valid auth → 201, hail persisted with `:from :http`.
  - POST EDN + valid auth → 201, hail persisted with `:from :http`.
  - POST with missing `:frequency` → 400 + structured error.
  - POST with malformed JSON body → 400 + structured error.

Auth tests (missing/wrong token) live with the server auth feature
(per isaac-g69y), not duplicated here.

Run targeted: `bb features features/hail/http.feature`.

**Definition of done:** remove `@wip` from
`features/hail/http.feature` and
`bb features features/hail/http.feature` is green.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-vduq (substrate)** — uses
  `isaac.hail.queue/send!`.
- **Depends on existing server auth (isaac-g69y).** Same token
  protects this route.
- **Independent of fan-out / wake** — this bean only produces.
