---
# isaac-vb8m
title: "Webhook receiver: POST /hooks/<name> dispatching agent turns"
status: completed
type: feature
priority: normal
created_at: 2026-04-27T19:18:13Z
updated_at: 2026-04-28T15:58:33Z
---

## Description

Implement the webhook receiver per features/server/hooks.feature.

Inbound POST /hooks/<name> reads its config from config/hooks/<name>.edn (crew, session-key, optional model) plus the message template at config/hooks/<name>.md. JSON body fields substitute into the template via {{var}}; missing fields render as '(missing)'. The rendered text becomes a user-message on the configured session, run through process-user-input!. Returns 202 once the turn is enqueued.

Spec: features/server/hooks.feature

Implementation surfaces:
- New file: src/isaac/server/hooks.clj — handler for POST /hooks/<name>.
- src/isaac/server/routes.clj — add the route entry.
- src/isaac/config/schema.clj — :hooks config (auth.token, etc.) + per-hook entity schema (crew, session-key, model).
- src/isaac/config/loader.clj — load config/hooks/*.edn and pair with .md templates (mirror crew + soul.md pattern).
- New: template renderer for {{var}} substitution with '(missing)' fallback.

Auth/method/path/content-type ordering (locked by scenarios):
1. Bearer token check → 401 if missing/wrong (precedes path lookup).
2. Method check → 405 if not POST.
3. Path lookup → 404 if no config/hooks/<name>.edn.
4. Content-Type check → 415 if not application/json.
5. Body parse → 400 if unparseable.
6. Render template + dispatch turn → 202.

New step definitions (test infra):
- 'a POST request is made to {path}:' — table of body + header.* rows.
- Extend 'a GET request is made to {path}' to optionally take the same table (for the 405 scenario).

Out of scope for v1:
- :deliver flag (broadcast assistant message back through a paired comm). All webhook turns run silently for now.
- Default catch-all session for unmatched paths. Use 404 instead.
- Schema validation on body.
- Idempotency / dedup keys.
- Rate limiting.

Acceptance:
1. Remove @wip from every scenario in features/server/hooks.feature.
2. bb features features/server/hooks.feature passes.
3. bb features and bb spec pass.

