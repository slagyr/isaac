---
# isaac-dlw5
title: 'isaac-gchat: supply the sender as the turn''s :from handle'
status: draft
type: feature
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T20:41:16Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

DRAFT. Needs scenarios before it is todo. Part of the contacts epic. Blocked by the agent attribution bean.

## Problem

Google Chat knows exactly who sent each message and tells the model only through text: the user message is rendered `[thread:xxxx] Name <email>: text` (`isaac.comm.gchat.canon`), and the origin map carries `:user users/<id>`, `:display-name` and `:email` in a shape only gchat understands.

## Proposal

When gchat submits a turn it sets `:from` to an outside handle: comm `gchat`, the Google `users/<id>`, the email and display name it already resolves through `isaac.google.people`, and authenticated true (Google vouches for the sender).

The rendered message text and the origin map do not change in this bean.

## Notes

- Including the email lets `isaac-contacts` match the same person across Chat and Gmail without calling Google.
- gchat does not depend on `isaac-contacts`.
- Gmail, Discord and iMessage get their own adoption beans. Gmail marks its handle unauthenticated.

## Likely repo scope

`isaac-gchat`.
