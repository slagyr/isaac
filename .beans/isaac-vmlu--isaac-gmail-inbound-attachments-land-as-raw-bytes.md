---
# isaac-vmlu
title: 'isaac-gmail: inbound attachments land as raw bytes — Gmail attachment data is not UTF-8 text'
status: completed
type: bug
priority: high
tags:
    - gmail
created_at: 2026-10-01T00:09:24Z
updated_at: 2026-10-03T19:37:26Z
parent: isaac-bv1l
---

## Symptom

Same conversion as the GChat inbound-bytes bug, different transport.
Gmail `users.messages.attachments.get` returns JSON with base64url
`data`. `isaac.comm.gmail.api/decode-raw` then does
`(String. (.decode …) "UTF-8")`, and `inbound-attachment/save-all!`
does `(.getBytes (str content) "UTF-8")` + `fs/spit`. A PNG or JPEG
attachment would land as UTF-8 replacement characters the same way
Skiff's Chat PNG did.

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

## Acceptance (features/comm/gmail/gmail.feature:287)

- [ ] Scenario "a PNG email attachment is saved byte-identical and the turn is told (isaac-vmlu)":
  `bb features features/comm/gmail/gmail.feature:287`
- [ ] Spec: `decode-raw` / `attachment-get!` return a `byte[]` with no
  charset (`bb spec spec/isaac/comm/gmail/api_spec.clj` from isaac-gmail).
- [ ] Handbook inbound-attachment troubleshooting names this UTF-8
  replacement failure.
- [ ] Version bump; pin isaac-gmail to the foundation sha isaac-ut4u
  landed; `bb spec` / `bb features` / `bb lint` green.

New steps: `the Gmail API returns attachment "…" of message "…" named "…" with bytes "…"`,
`the file "…" under the session working directory has bytes "…"`.

## Likely repo scope

isaac-gmail (`api.clj`, `inbound_attachment.clj`, gmail.feature,
feature-steps, handbook). Pin foundation at the sha isaac-ut4u
landed. Do not re-implement `write-bytes` here.

feature-baseline: isaac-gmail b04884b9a2cb94a06f0d8c15e7045a61e72f4f4a
feature-blob: isaac-gmail features/comm/gmail/gmail.feature 9092838ce32e682ac4ad777e9e7861d31a499568 287

## Landed on main (2026-10-03)

main-sha: isaac-gmail 90ee552ac1acf6a95f4c7b7df33adb77e4942102

Verification: `bb ci` green (150 specs, 46 features); `bb lint src feature-steps` 0 errors (3 pre-existing warnings). `bb lint` across specs reports pre-existing clj-kondo Speclj macro errors (92 errors). Gate PASS on bean branch and landed main.
