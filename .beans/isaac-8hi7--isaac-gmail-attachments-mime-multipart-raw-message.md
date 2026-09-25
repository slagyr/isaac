---
# isaac-8hi7
title: 'isaac-gmail: attachments — MIME multipart raw message from the delivery''s files'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T05:05:27Z
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

feature-baseline: isaac-gmail 3b0b4faad16cdae231139cc079ec1bcccf8d5d29
feature-blob: isaac-gmail features/comm/gmail/gmail.feature c9d74d1539c081f9528d14261f735db92b662bf3 239



## Planner note (2026-09-25)

isaac-o9h4 landed: pin `io.github.slagyr/isaac-agent` to `ff62ae76b18eb41cde12ba9f5bcb716ddd41ce9f` (0.1.83) in deps.edn — it carries comm__send `attachments` and the `:send-attachments?` manifest flag. Declare `:send-attachments? true` on this comm's manifest entry.



## Planner note (2026-09-25 05:50Z)

isaac-iwio landed: gmail main e894924 (0.2.6) carries the send-schema (gmail.to/subject/thread) and `send-new!` / `send-on-thread!` in gmail.clj — build the multipart raw on top of those. The recipient-scoped step `the sent mail to "<address>" decodes to:` exists in feature-steps; extend it with an `attachments` row.
