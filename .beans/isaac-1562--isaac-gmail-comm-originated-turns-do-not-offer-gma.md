---
# isaac-1562
title: 'isaac-gmail: comm-originated turns do not offer gmail__send — the answer text is the reply'
status: scrapped
type: bug
priority: high
created_at: 2026-09-25T02:47:03Z
updated_at: 2026-09-25T02:54:15Z
blocked_by:
    - isaac-7mwt
---

## Why

Same as the gchat bean: a gmail-originated turn's answer text is sent as the
reply by the comm; the model must not see `gmail__send` on that turn.
isaac-3t0z's dedupe stays as a guard.

## Design

- The gmail comm's dispatch adds `:tools {:deny [:gmail/send]}` to the
  request (charge overlay from the isaac-agent bean).
- Turn guidance for gmail conversations: "Your answer is sent as the reply
  on this thread automatically — there is no send step."
- Task-route hails and CLI turns keep `gmail__send` (the yopp-tasks band
  relies on it).

## Acceptance (isaac-gmail spec + feature)

- [ ] A gmail-dispatched (`:converse`) turn's request carries
  `:tools {:deny [:gmail/send]}`; offered tools lack `gmail__send`; one
  reply sent.
- [ ] A `:task` route hail on the same crew still offers `gmail__send`.
- [ ] Guidance updated; agent pin bumped to the overlay sha; version bump;
  bb spec / bb features / bb lint green.

Likely repo scope: isaac-gmail (gmail.clj / handler.clj, guidance, deps.edn).



## Scrapped (2026-09-25 02:54Z)

Micah: not the right decision. Do not work, verify, or land this bean. Any branch for it is abandoned.
