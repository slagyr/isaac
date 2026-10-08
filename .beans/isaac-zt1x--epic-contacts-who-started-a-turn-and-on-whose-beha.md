---
# isaac-zt1x
title: 'Epic: contacts — who started a turn and on whose behalf'
status: draft
type: epic
created_at: 2026-10-08T20:41:15Z
updated_at: 2026-10-08T20:41:15Z
---

DESIGN, from a planning conversation with Micah on 2026-10-08. Nothing here is dispatchable yet; each child is a draft awaiting scenarios.

## Motivation

Yopp is the first Isaac instance that several people talk to. The agent core has no idea who is speaking. Each comm handles it alone: Google Chat writes "Name <email>" into the message text, Discord injects a sender id, iMessage a phone handle, Gmail a from address. A session's origin records where it came from, not who spoke, and one Chat space is a single session with many speakers.

Who is interacting is orthogonal to everything built so far. It wants one small seam in the agent and a module on top.

## Rulings (Micah, 2026-10-08)

- Built one piece at a time. **Attribution is the first step.** Privacy and access control are later and not designed here.
- The module is `isaac-contacts`; the people (and outside services) are **contacts**. "Users" was rejected: the transcript role, the Google Chat origin field and the unix account already use the word. "Passengers" was considered.
- Crew are NOT contacts. Crew are aboard; contacts are outside the hull.
- Dreaming must work with or without contacts (see isaac-b1ir).

## Shape

Ownership is per turn, not per session. A turn carries two references:

- `:from` — who started the turn: an outside handle, a crew member, a schedule, the CLI. Hail already uses `:from` this way (`:crew/<id>`, `:cli`, `:http`).
- `:for` — on whose behalf, if anyone. Always an outside party or nothing.

A crew-started turn inherits `:for` from the turn that spawned it, so delegation does not launder ownership onto agents.

The agent stores and copies these and knows nothing else. With no module installed, `:from` is the raw handle the comm supplied. `isaac-contacts` resolves a handle to a named contact before the turn is stored.

Comms and contacts do not depend on each other; both depend only on `isaac-agent`.

## Children

1. isaac-v403 — `isaac-agent`: `:from` and `:for` on the turn record. The fields only.
2. isaac-s715 — `isaac-agent`: the resolver berth.
3. isaac-4615 — crew-started turns inherit `:for` (`isaac-agent`, then `isaac-hail`).
4. isaac-o7tm — `isaac-contacts`: the module. Needs 1 and 2.
5. isaac-dlw5 — `isaac-gchat`: supply the handle. Needs 1. Gmail, Discord and iMessage follow as their own beans.

Order: 1 first. 2, 3 and 5 are independent of each other.

## Later, not beaned

- Per-contact context on a turn (notes, preferences). Dreaming's per-person habits are the first consumer.
- Recall scoped by contact. Today recall is crew-wide, so something said in one person's DM can surface in another's conversation.
- Per-contact permissions and cost accounting.
