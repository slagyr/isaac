---
# isaac-8hi7
title: 'isaac-gmail: attachments — MIME multipart raw message from the delivery''s files'
status: draft
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T03:37:51Z
blocked_by:
    - isaac-o9h4
---

## Why

comm__send attachments (agent bean) need each comm to build the message.
Gmail wants a MIME multipart/mixed raw message.

## Design

- Manifest comm entry `:send-attachments? true`.
- `rfc2822` gains a multipart builder: text/plain part + one part per file
  (base64, `Content-Disposition: attachment; filename=…`, content type by
  extension, `application/octet-stream` fallback). `send!*` uses it when
  `:attachments` is present, for new mail and replies alike.
- Size: refuse a delivery whose attachments exceed 25 MB total (Gmail's
  limit) with a clear error — permanent, not retried.

## Acceptance (features/comm/gmail/gmail.feature — baselined)

- [ ] "comm__send with gmail.to, gmail.subject and an attachment sends one
  multipart email carrying the file (isaac-8hi7)": the sent mail decodes to the
  To/Subject/text and lists the attachment filename (extend the
  "sent mail decodes to:" step with an `attachments` row).
- [ ] Version bump; bump the isaac-agent pin to the sha carrying
  `attachments`; bb spec / features / lint green.

Likely repo scope: isaac-gmail (rfc2822.clj, gmail.clj, manifest, feature,
feature-steps).
