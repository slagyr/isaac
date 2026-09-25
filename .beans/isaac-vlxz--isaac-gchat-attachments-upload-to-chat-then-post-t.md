---
# isaac-vlxz
title: 'isaac-gchat: attachments — upload to Chat, then post the message referencing them'
status: todo
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T03:40:52Z
blocked_by:
    - isaac-o9h4
---

## Why

comm__send attachments (agent bean) need each comm to upload. Google Chat
takes a media upload first, then the message references it.

## Design

- Manifest comm entry `:send-attachments? true`.
- `send!*` with `:attachments`: for each path, POST multipart to
  `https://chat.googleapis.com/upload/v1/<space>/attachments:upload`
  (filename, content type by extension), collect the returned
  `attachmentDataRef`/upload token, then post the message with
  `attachment: [{attachmentDataRef …}]` alongside `text`. One failed upload
  fails the delivery (worker retries / dead-letters as today) — no partial
  message.

## Acceptance (features/comm/gchat/outbound.feature — baselined)

- [ ] "comm__send with an attachment uploads it, then posts the message
  referencing it (isaac-vlxz)": 1 upload request per file, then 1 message request
  whose body carries the attachment ref and the text.
- [ ] Version bump; bump the isaac-agent pin to the sha carrying
  `attachments`; bb spec / features / lint green.

Likely repo scope: isaac-gchat (gchat.clj, chat_api.clj, manifest, feature).

feature-baseline: isaac-gchat e6c78a153be8a57f670666eadf162b61271e36e3
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 5b9541244161bcbbefd112f44a610170cb155f47 517
