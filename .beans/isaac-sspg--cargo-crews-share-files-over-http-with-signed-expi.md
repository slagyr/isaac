---
# isaac-sspg
title: 'Cargo: crews share files over HTTP with signed, expiring links'
status: draft
type: feature
priority: normal
created_at: 2026-09-28T14:53:26Z
updated_at: 2026-09-28T15:26:47Z
---

Likely repo: **isaac-cargo** (new module). Depends on isaac-http (routes, auth) and Agent's tools berth. Idea: Micah + planner, 2026-09-28.

## Why

Micah asks a crew (e.g. Zane) for an image or a document, but is on another computer with no SSH to the host. The crew needs a way to hand the file over through Isaac's public HTTP door — securely, not by obscurity. A random id in a URL is not enough: it never expires, cannot be revoked, and lives forever in chat logs.

## Design sketch (to refine before scenarios)

- **Store.** Shared files live under the Isaac root (`cargo/<id>/`: the bytes + metadata — owner crew/session, original name, content type, size, created, expires, visibility). Copying in, not linking: the file stays available even if the crew's working copy changes.
- **Tools** (contributed via `:isaac.agent/tools`, opt-in per crew with `tools.allow`):
  - `cargo_share` — path (+ optional expiry, optional label) → link. Only paths the crew may already read (filesystem boundaries apply).
  - `cargo_list`, `cargo_revoke` — optional.
  The tool description tells the model when to use it (the user can't reach the host; share and send the link). A guidance line or skill only if crews under-use it.
- **Route.** `GET /cargo/<id>` contributed through the `:isaac.http/route` berth; serves the bytes with the stored content type.
- **Signed, expiring links.** The link carries the id, an expiry, and an HMAC signature made with a server key only Isaac holds (S3-presigned style). Unforgeable, expires on its own (default e.g. 24h), dies early on revoke or key rotation. Still a bearer link: whoever holds it can use it until it expires — acceptable for everyday sharing, and chat previews work.
- **Private assets (later).** Visibility `:private` requires the viewer to be signed in — depends on browser sign-in (OIDC login + session cookies in isaac-http, on top of isaac-pqqm's auth-strategy chain; not yet beaned). A bearer principal token (`Authorization: Bearer`) also works for curl/scripts.
- **CLI.** `isaac cargo list`, `isaac cargo revoke <id>`, `isaac cargo share <path>` (operators can share too).

## Open questions

- Default and maximum expiry; size limit per file and total.
- Retention: delete bytes at expiry, or keep until revoked/pruned?
- Signing-key location and rotation (config secret vs generated under the root).
- Whether a crew may share files it created outside its working directories.
- Name: "cargo" is the working name (ship vocabulary).

Draft until the design questions are settled and scenarios exist.


(2026-09-28: private visibility depends on isaac-lie6 — OIDC sign-in in isaac-http.)
