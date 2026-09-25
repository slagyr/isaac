---
# isaac-468y
title: 'isaac-gchat: inbound attachments download from Chat''s media endpoint — /v1/media/{resourceName}?alt=media, not /v1/{resourceName}'
status: in-progress
type: bug
priority: high
created_at: 2026-09-25T15:56:18Z
updated_at: 2026-09-25T15:57:09Z
---

## Symptom (yopp, 2026-09-25 15:52Z)

Micah sent Yopp an image in a Chat DM after isaac-e2zb shipped. The turn
input read `[attachment: optimus-prime….png (download failed)]`; the log:
`:gchat.attachment/download-failed "Chat API attachment download failed: 404"`
with the `attachmentDataRef.resourceName` (an opaque base64 string).

## Cause

`isaac.comm.gchat.chat-api/download-attachment!` GETs
`<chat-base>/<resourceName>?alt=media`, i.e. `/v1/<resource>`. Chat serves
attachment bytes from the **media** endpoint:
`GET https://chat.googleapis.com/v1/media/{resourceName}?alt=media`
(`media.download`; `resourceName` is `attachment.attachmentDataRef.resourceName`
verbatim — it may contain `+ / =`; pass it as one path segment, not
re-encoded). The e2zb scenario's stub accepted whatever URL the code used, so
the mistake was invisible to the feature.

## Design

- `download-attachment!` requests `chat-base + "/media/" + resource` with
  `alt=media`; the resource string goes through unchanged.
- Drive-backed attachments (`source DRIVE_FILE`) are out of scope: they need
  the Drive API; the saved line says `(drive file, not saved)`.

## Acceptance (features/comm/gchat/inbound.feature — baselined)

- [ ] Scenario "an attachment is downloaded from Chat's media endpoint (isaac-468y)":
  exactly one outbound GET to
  `https://chat.googleapis.com/v1/media/spaces/IA2/attachments/att-2` with
  `alt=media`, and the file lands.
- [ ] Spec on the URL builder with a resource containing `+`, `/` and `=`.
- [ ] Version bump; bb spec / bb features / bb lint green.

Likely repo scope: isaac-gchat (chat_api.clj, inbound_attachment.clj, feature-steps stub).

feature-baseline: isaac-gchat 0e77c04a19d5350ca067a6a0fb8a8dc308662880
feature-blob: isaac-gchat features/comm/gchat/inbound.feature 7124ba35044061f10352d982d8444cfacbf708cd 677
