---
# isaac-vmlu
title: 'isaac-gmail: inbound attachments land as raw bytes — Gmail attachment data is not UTF-8 text'
status: draft
type: bug
priority: high
tags:
    - gmail
created_at: 2026-10-01T00:09:24Z
updated_at: 2026-10-01T00:09:30Z
parent: isaac-bv1l
blocked_by:
    - isaac-ut4u
---

## Symptom

Same conversion as the GChat inbound-bytes bug, different transport.
Gmail `users.messages.attachments.get` returns JSON with base64url
`data`. `isaac.comm.gmail.api/decode-raw` then does
`(String. (.decode …) "UTF-8")`, and `inbound-attachment/save-all!`
does `(.getBytes (str content) "UTF-8")` + `fs/spit`. A PNG or JPEG
attachment would land as UTF-8 replacement characters the same way
Yopp's Chat PNG did.

The HTTP body here is JSON (text is correct). The landmine is decoding
the attachment bytes as a UTF-8 String.

## Cause

Copied the e2zb shape from GChat: treat attachment content as a String.
`attachment-get!` is documented "decode it as UTF-8 text". Scenarios
only used ASCII (`%PDF-1.4 stub`).

## Design (2026-09-30, Micah)

Blocked on isaac-ut4u, which lands foundation
`write-bytes`.

- **`decode-raw` / `attachment-get!` return `byte[]`.** Base64url
  decode only — no `String.` constructor, no charset.
- **`save-all!` writes with `fs/write-bytes`.** Size is `alength`.
  Cap stays byte length.
- **Guidance unchanged.** "You cannot view images yet" stays.
- **Handbook.** Same troubleshooting note as GChat: a landed file that
  is not a valid image is this conversion, not a missing download.
- **Out of scope.** Image input to the model (deferred in isaac-e2zb).
  Chat media (the GChat bean).

## Acceptance

Draft. Scenarios after the GChat scenario plan lands and this bean's
plan is approved.

Likely home: isaac-gmail `features/comm/gmail/gmail.feature` (next to
isaac-e2zb). Existing `returns attachment … with content "…"` /
`the file … contains "…"` steps are ASCII-only.

## Likely repo scope

isaac-gmail (`api.clj`, `inbound_attachment.clj`, gmail.feature,
feature-steps, handbook). Pin foundation at the sha isaac-ut4u
landed. Do not re-implement `write-bytes` here.
