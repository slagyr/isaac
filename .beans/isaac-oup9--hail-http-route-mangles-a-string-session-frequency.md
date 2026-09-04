---
# isaac-oup9
title: Hail HTTP route mangles a string :session frequency into a char vector → undeliverable (no-recipients)
status: in-progress
type: bug
priority: normal
tags:
    - hail
    - unverified
created_at: 2026-09-04T16:29:12Z
updated_at: 2026-09-04T23:01:39Z
---

Repo: **isaac-hail** (`src/isaac/hail/http.clj normalize-frequencies`).

## Problem

`POST /hail/send` with `{"frequencies":{"band":"isaac-work","session":"isaac-work-1"}}`
is routed with `:session [\i \s \a \a \c \- \w \o \r \k \- \1]` — the string
was fed through `keywordize*`, which treats it as a sequence and keywordizes
each character. The router finds no such session and drops the hail as
`:hail/undeliverable :reason :no-recipients` (hail 315538cb, 2026-09-04
14:48Z). The CLI path (`isaac hail send --session …`) works, so the shape
the router expects is a string/keyword; the HTTP normalizer disagrees.

## Fix

`normalize-frequencies`: a string `:session` becomes the session key as-is
(string, or keyword if the router expects that — match the CLI path); a
vector of strings maps element-wise. Reject other shapes with a 400 that
names the field rather than routing to nobody. Same audit for
`:session-tags` (currently `keyword-set*`) and `:crew`.

## Acceptance

`spec/isaac/hail/http_spec.clj`: `{"session":"isaac-work-1"}` normalizes to
the same value the CLI produces for `--session isaac-work-1`; a
`["a","b"]` vector normalizes element-wise; `{"session":42}` → 400 with the
field name. `features/` hail HTTP route: a scenario posting a session-
addressed hail is routed to that session (memory comm / Marigold fixtures).
`bb spec && bb features` in isaac-hail green.

## Handoff

branch: bean/isaac-oup9 @ bd109bd91f6dc1aaf60c4084a336c076e5450a09 (base origin/main@0f98f3e322673c9198d0f08a76dabdcd2fb037e2)

HTTP normalize-frequencies now wraps a string :session as a keyword vector matching CLI --session; vectors map element-wise; non-string shapes 400 naming the field. Same for :session-tags and :crew. Feature posts a session-addressed hail and asserts router delivery.
