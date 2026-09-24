---
# isaac-a9dp
title: 'config validate fails in the feature harness with an isaac.google tenant: scopes berth conformer casts Character to Map$Entry'
status: completed
type: bug
priority: normal
created_at: 2026-09-24T19:31:39Z
updated_at: 2026-09-24T21:27:45Z
---

Repo: **isaac-foundation** (berth-slice conformer), surfaced from **isaac-google**.

## Problem

Inside isaac-google's feature harness, `isaac config validate` with any
isaac.google tenant configured exits 1 with:

    module-index["isaac.google"].isaac.google/scopes - class java.lang.Character
    cannot be cast to class java.util.Map$Entry

`:isaac.google/scopes` is a `:seq` berth (a vector of scope strings). Something
in the berth-slice conformer is walking it as a map — seeing a string, then a
character, and trying to read that character as a map entry.

**Confirmed pre-existing.** Reproduced on clean `main` with all isaac-286x work
stashed, so it is not that bean's doing.

It does **not** reproduce on yopp — isaac-ey6q records `isaac config validate`
answering `OK` there with the Google tenant configured — so it looks like a
harness or module-index artifact rather than a production fault. That
difference is itself the interesting part and should be explained, not assumed
benign: the same validation path answering differently in the suite and on a
host means the suite is not checking what the host runs.

## Cost already paid

isaac-286x could not assert `exit code is 0` in its two positive
`pubsub_identity.feature` scenarios and had to assert
`the stderr does not contain "pubsub.credentials-file"` instead. The negative
scenarios still assert exit 1 against the real error text, so the check is
proven — but a whole class of positive assertion is unavailable to any bean
touching Google config while this stands.

## Acceptance

- The conformer handles a `:seq` berth without treating its elements as map
  entries; a foundation-level scenario covers a `:seq`-schema berth.
- The harness/host divergence is explained — either the harness is fixed to
  match the host, or the reason they legitimately differ is written down.
- isaac-286x's two positive scenarios are restored to asserting `exit code is 0`.
