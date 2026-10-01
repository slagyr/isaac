---
# isaac-ut4u
title: 'isaac-gchat: inbound attachments land as raw bytes — Chat media is not UTF-8 text'
status: draft
type: bug
priority: high
tags:
    - gchat
created_at: 2026-10-01T00:09:20Z
updated_at: 2026-10-01T00:09:30Z
parent: isaac-bv1l
---

## Symptom (yopp, 2026-09-29)

Micah sent Yopp a PNG over Google Chat. The file landed (~171KB, no
`download failed` flag). Yopp opened it at the byte level: a valid PNG
starts `0x89`; this file starts `EF BF BD` (UTF-8 for U+FFFD). ~37,790 of
those replacement sequences were spread through the file. The payload
cannot be reconstructed. JPEG would fail the same way — the codec is
fine; the ingest path is treating media as text.

## Cause

Follow-up to isaac-e2zb (files land) and isaac-468y (media URL). Both kept
the body as text; every scenario used ASCII (`hello`, `meeting notes`,
`%PDF-1.4 stub`), so the binary case never failed.

1. `isaac.comm.gchat.chat-api/download-attachment!` is documented to
   "return it as text". `-http!` uses babashka.http-client's default
   string body, then `parse-body` (JSON-or-string). Chat's
   `media.download` (`?alt=media`) is raw bytes. Java's UTF-8 decoder
   replaces every invalid sequence with U+FFFD. PNG's first byte is
   `0x89`.
2. `isaac.comm.gchat.inbound-attachment/save-all!` then does
   `(.getBytes (str content) "UTF-8")` and `fs/spit`. That writes the
   replacement characters. Size still looks plausible, so there is no
   download-failure flag.

Outbound already reads with `fs/read-bytes` and uploads those bytes.
Inbound is the broken half.

`fs/spit` goes through a Writer. There is no `write-bytes` on the Fs
protocol (RealFs or MemFs). MemFs `size` / `read-bytes` currently assume
a String.

## Design (2026-09-30, Micah)

- **Bytes through Chat media.** `download-attachment!` GETs with
  `:as :bytes`, skips JSON parse, returns a `byte[]`. Other Chat JSON
  calls stay string/JSON.
- **Bytes to disk.** `save-all!` writes with `fs/write-bytes`. Size is
  `alength` of those bytes. Cap (25 MB) is still byte length.
- **Foundation primitive.** Add `write-bytes` next to existing
  `read-bytes` on `isaac.foundation.fs` (protocol + RealFs + MemFs).
  RealFs uses `Files/write`. MemFs stores a `byte[]`. `size` /
  `read-bytes` accept both String (today) and `byte[]`. `slurp` of a
  `byte[]` file UTF-8-decodes like RealFs, so existing ASCII attachment
  scenarios keep passing.
- **Guidance unchanged.** "You cannot view images yet" stays. This bean
  is file integrity. Presenting image content to the model was deferred
  in isaac-e2zb.
- **Handbook.** Inbound-attachment troubleshooting: a file that landed
  but is not a valid image is this bug (UTF-8 replacement), not a
  missing download.
- **Out of scope.** Drive-backed attachments (`source DRIVE_FILE`).
  Gmail (isaac-vmlu, blocked on this bean for `write-bytes`). Image
  input to the model.

## Acceptance

Draft. Scenarios after the scenario plan is approved.

Likely home: isaac-gchat `features/comm/gchat/inbound.feature` (next to
isaac-468y). Foundation `write-bytes` is spec-only
(`spec/isaac/foundation/fs_spec.clj`); there is no fs.feature.

Existing string steps (`serves attachment … with content "…"`,
`the file … contains "…"`) are too weak — they only exercise ASCII.

## Likely repo scope

isaac-foundation (`fs.clj` + `fs_spec.clj`), then isaac-gchat
(`chat_api.clj`, `inbound_attachment.clj`, inbound.feature,
feature-steps, handbook). GChat pins the new foundation sha at land.
Gmail waits on this bean.
