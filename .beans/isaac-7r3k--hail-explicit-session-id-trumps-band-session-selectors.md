---
# isaac-7r3k
title: 'Hail: explicit session id trumps band session selectors'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-02T17:54:35Z
updated_at: 2026-07-02T18:08:49Z
---

## Description

Today a hail band's `:session-tags` / `:crew` selectors are merged with an explicit hail `:session` selector. That makes an explicit session id behave like one more filter instead of a complete recipient coordinate.

For CI failure hails and similar "resume the exact worker session" flows, that is the wrong contract. If a hail names a session explicitly, that session id should stand on its own. Band session selectors should not be able to filter it out.

## Current behavior

In `isaac-hail`, band and hail frequencies are merged in `effective-frequencies`. If both sides provide recipient selectors:

- `:session` ids are intersected
- `:session-tags` are unioned
- `:crew` is intersected

This means a hail with `:session ["glimmering-cardinal"]` can still fail to route if the band also carries tags or crew that do not match that session.

## Desired behavior

If the hail itself includes `:frequencies {:session ...}`, that explicit session id is the complete recipient coordinate.

Band/session selection fields must not further constrain it:

- ignore band `:session`
- ignore band `:session-tags`
- ignore band `:crew`
- ignore band `:reach`
- ignore band `:prefer`
- likely ignore band `:create` as a selector policy too, unless we decide explicit named-session creation should still be supported

Band non-selection defaults should still apply:

- prompt/body template
- `:with-crew`
- `:with-model`
- `:with-effort`
- `:with-context-mode`

## Why this matters

This gives hails a clean coordinate model:

- band = reusable prompt/policy/template
- explicit session id = exact recipient

Without that separation, session-directed hails remain brittle and CI failure routing has to keep the session id as inert metadata instead of using it as an actual address.

## Acceptance (sketch)

Pending feature planning, but the core proof should cover:

1. A band with `session-tags: [:orchestration]` plus a hail with explicit `:session ["glimmering-cardinal"]`
   routes to `glimmering-cardinal` even when that session lacks the band tag.

2. A band with `with-*` overrides plus a hail with explicit `:session`
   still applies the `with-*` behavior to the delivered work.

3. A band with `reach: :all` plus a hail with explicit `:session`
   does not fan out; exact session coordinate wins.

4. Explicit session + missing target has a clear settled policy for creation:
   probably "no implicit create unless the hail itself requests it".

## Likely repo scope

- `isaac-hail`
- follow-up consumer change in `orchestration/isaac-ci` once the routing rule lands, so CI can send `frequencies.session` instead of carrying `params.session_id` as a hint
