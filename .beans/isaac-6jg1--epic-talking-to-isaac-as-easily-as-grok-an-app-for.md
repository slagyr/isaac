---
# isaac-6jg1
title: 'Epic: talking to Isaac as easily as Grok — an app for phone and desktop, and voice'
status: draft
type: epic
priority: normal
tags:
    - comms
created_at: 2026-10-28T08:13:45Z
updated_at: 2026-10-28T08:13:45Z
---

Micah, 2026-10-28: "I also want improved communication tools similar to Grokbot. Grokbot is really convenient. It's got a mobile app and a desktop app. I can activate voice. It's really simple as well, at least on the surface it is."

## Aim

Talking to Isaac should be as easy as talking to Grok: open an app on the phone or the desktop, type or speak, and get an answer, with no setup in between. Today Isaac is reached through Toad (ACP in a terminal), Discord, iMessage and Google Chat. They work, but none is a first-class Isaac client, and none does voice.

## Pieces (to be shaped)

1. **Voice in.** Speech to text for audio sent on the comms we already have (Discord voice messages, iMessage audio), so speaking works before any app exists.
2. **Voice out.** Optional spoken replies (text to speech) where the client can play them.
3. **An Isaac app.** One client for phone and desktop. The cheapest path is likely a web app served by isaac-server (installable as a PWA, behind the existing auth), with sessions, crews, streaming replies, attachments and a push-to-talk button. Native apps only if the web app falls short.
4. **Live voice conversation.** A hands-free, back-and-forth voice mode like Grok's. Last, and only if 1–3 prove the demand.
5. **Simplicity.** One obvious default crew and session, no setup screens; everything else stays reachable but out of the way.

## Open questions

- Which speech-to-text and text-to-speech: a local model on zanebot (private, no quota), or a provider API?
- Is the web app a new module (isaac-app?) or part of isaac-server?
- Access from the phone: zanebot is already public through Funnel (scoped principals, isaac-gym1), so the app needs a login story, not a network one.
- Notifications: phone push when Isaac reports back (ties to the goals epic, isaac-4qxr, where Isaac follows through and reports on its own).

## Related

- isaac-7gm multi-channel chat interfaces (older, deferred epic; this one supersedes its TUI/Web UI items).
- isaac-4qxr goal-driven crews (proactive follow-through needs a way to reach Micah).
- isaac-gym1 per-principal scoped auth.

## Acceptance

The epic closes when its children do. First child to draft: voice in on Discord and iMessage (smallest piece that makes speaking to Isaac work today).
