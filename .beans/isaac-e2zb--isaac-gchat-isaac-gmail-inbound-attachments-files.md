---
# isaac-e2zb
title: 'isaac-gchat + isaac-gmail: inbound attachments — files people send land in the session working directory and the turn is told'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T14:43:50Z
updated_at: 2026-09-25T14:44:27Z
---

## Why (Micah, 2026-09-25)

Micah sent Yopp an image in a Chat DM and asked what it was; Yopp saw only
the text. Neither comm reads inbound attachments: gchat only sends them
(isaac-vlxz), gmail's message parser keeps text parts only. Attachments a
person sends must reach the model.

## Design (both comms, same shape)

- **On disk, in the session's working directory.** Every attachment on a
  routed inbound message is downloaded to
  `<session cwd>/attachments/<message-id>/<filename>` before the turn is
  dispatched. gchat: Chat `message.attachment[]` (`contentName`,
  `contentType`, `attachmentDataRef.resourceName` / `downloadUri`) via the
  media download endpoint. gmail: payload parts carrying `filename` +
  `body.attachmentId` via `users.messages.attachments.get`, base64url
  decoded. Filenames are sanitised (basename only, no path parts).
- **The model is told.** The framed user input for the turn carries one
  line per attachment, after the message text:
  `[attachment: <filename> (<content-type>, <size>) at attachments/<message-id>/<filename>]`
  — a relative path under the session cwd, readable with the fs tools.
- **Guidance** (both comms' TEXT): "Files people attach are saved under
  attachments/ in your working directory and listed with the message; read
  them with the file tools. You cannot view images yet — say so if asked
  what an image shows."
- **Limits and failure.** Per-file cap 25 MB: a larger file is not
  downloaded and the line says `(too large, not saved)`. A failed download
  logs `:gchat.attachment/download-failed` / `:gmail.attachment/download-failed`
  at warn and the line says `(download failed)`; the turn still runs.
- **Not in this bean:** presenting images to the model as image content
  (needs provider-side image input in isaac-agent) — follow-up bean once
  this lands.

## Acceptance (baselined: gchat inbound.feature + gmail gmail.feature)

- [ ] gchat scenario "an attachment on the addressing message is saved under
  the session working directory and the turn is told (isaac-e2zb)". New
  steps: `the Chat API serves attachment "<resourceName>" with content
  "<text>"` (stubs the media download) and `the file "<path>" under the
  session working directory contains "<text>"`.
- [ ] gmail scenario "an email attachment is saved under the session working
  directory and the turn is told (isaac-e2zb)". New step: `the Gmail API
  returns attachment "<attachmentId>" of message "<id>" named "<filename>"
  with content "<text>"`.
- [ ] Guidance text updated in both comms with the paragraph above; specs
  assert it.
- [ ] Specs: filename sanitising; the 25 MB cap; download failure keeps the
  turn running and logs once.
- [ ] Version bumps in both repos; bb spec / bb features / bb lint green in
  both. Land both halves; either half alone is incomplete.

Likely repo scope: isaac-gchat (handler.clj, chat_api.clj, attachment.clj,
guidance.clj, inbound.feature, feature-steps) and isaac-gmail (handler.clj,
message.clj, api.clj, guidance.clj, gmail.feature, feature-steps).

feature-baseline: isaac-gchat ca25a660d986d46ae0084c44d3eed1f70fa8a41f
feature-baseline: isaac-gmail 67f01dd3cc537fcab240f048acc90a868c310ffe
feature-blob: isaac-gchat features/comm/gchat/inbound.feature fb6e2bb30b56c51ede2d78c2c448307e8071b04c 650
feature-blob: isaac-gmail features/comm/gmail/gmail.feature 8093c058869101587cfdc3ceb3cdc74fc3502584 262
