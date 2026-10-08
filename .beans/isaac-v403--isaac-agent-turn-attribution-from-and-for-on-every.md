---
# isaac-v403
title: 'isaac-agent: turn attribution — :from and :for on every turn, with a resolver berth'
status: draft
type: feature
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T20:41:16Z
parent: isaac-zt1x
---

DRAFT. Needs scenarios before it is todo. First step of the contacts epic.

## Problem

A turn does not record who started it. `charge-schema` (`isaac.agent.charge`) has `:origin`, an untyped map each comm fills its own way, and the turn record (`<root>/turns/<id>.edn`) copies it. Nothing downstream can ask "who said this" without parsing comm-specific shapes or the message text.

## Proposal

Two fields on the charge and the turn record:

- `:from` — who started the turn.
- `:for` — on whose behalf; absent when nobody.

Each is a small opaque reference with a kind and an id. Working shapes:

- outside handle: the comm type, the comm's own id for the sender, and optional `:email` and `:name`;
- crew: the crew id;
- schedule: the cron job name;
- CLI / HTTP principal.

The submitter sets `:from`. For an outside handle, `:for` defaults to the same party.

**Trust rides on the handle.** The comm states whether the sender identity is authenticated (Google Chat: yes; an email From line: no). The agent stores the flag and does not interpret it.

**Inheritance.** When a crew member starts a turn from inside a turn (`hail__send`, `comm__send` to another crew, foreman `:turn` actions), the new turn's `:from` is that crew member and its `:for` is copied from the current turn. Cron turns have no `:for` unless the job names one.

**Resolver berth.** A module may register a resolver that is handed an outside handle and returns a replacement reference (a named contact) or nil. Applied once, before the turn record is written. A resolver that throws or is slow is skipped and logged; the raw handle is stored. No resolver installed means raw handles, and everything still works.

## Constraints

- The agent never learns what a contact is. It stores, displays and copies references.
- `:origin` stays for now; comms keep whatever else they put there. Migrating readers off `:origin` is not this bean.
- Hail's existing `:from` values (`:crew/<id>`, `:cli`, `:http`) should land in the new field without a second vocabulary.
- Visible to operators: `turn__get`, `isaac sessions` output and the turn record show `:from` and `:for`.

## To settle

- Whether the transcript entry for the user message also carries `:from`, so a reader of one session's transcript can attribute each message without joining to turn records. Dreaming and scene distillation would both use it.
- How much plumbing inheritance needs in `isaac-hail` and the delivery queue. Not yet measured.

## Likely repo scope

`isaac-agent`; a small follow-on in `isaac-hail` for inheritance.
