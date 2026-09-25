---
# isaac-ogry
title: 'isaac-gchat: comm-originated turns do not offer gchat__send — the answer text is the reply'
status: todo
type: bug
priority: high
created_at: 2026-09-25T02:47:03Z
updated_at: 2026-09-25T02:47:03Z
blocked_by:
    - isaac-7mwt
---

## Why

Yopp's answer text in a gchat turn is delivered to the thread by the comm
(`on-reply*`). The model must not need — or see — `gchat__send` for that.
Removing the tool from comm-originated turns is the fix Micah asked for;
isaac-mw27's dedupe stays as a belt-and-braces guard.

## Design

- `handler/dispatch-to!` adds `:tools {:deny [:gchat/send]}` to the dispatch
  request (charge overlay from the isaac-agent bean).
- `guidance/TEXT`: replace "Reply in the addressed thread" with: "Your
  answer text is posted to the addressed thread automatically — there is
  no send step. Other spaces or threads, if ever needed, are out of scope
  for this turn."
- Hail, CLI and task turns (not comm-originated) keep `gchat__send`.

## Acceptance (isaac-gchat spec + feature)

- [ ] A gchat-dispatched turn's request carries `:tools {:deny [:gchat/send]}`.
- [ ] Feature: crew allows `gchat/*`; inbound mention → the turn's offered
  tools lack `gchat__send` (assert via the turn's `allowed-tools` /
  request-built log or transcript), reply text posted once.
- [ ] A hail-dispatched turn on the same crew still offers `gchat__send`.
- [ ] Guidance text updated; bump agent pin to the sha that carries the
  overlay; version bump; bb spec / bb features / bb lint green.

Likely repo scope: isaac-gchat (handler.clj, guidance.clj, deps.edn pin).
