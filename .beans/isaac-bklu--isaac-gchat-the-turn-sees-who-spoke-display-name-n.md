---
# isaac-bklu
title: 'isaac-gchat: the turn sees who spoke — display name, not users/<id>'
status: todo
type: bug
priority: high
tags:
    - google
    - comm
created_at: 2026-09-20T00:06:00Z
updated_at: 2026-09-20T00:16:34Z
parent: isaac-bv1l
---

2026-09-19: gchat dispatch! builds the turn input as "<sender> <text>" where sender is the email if Google supplied one, else the users/<id> — so Yopp's first real turn read `users/118285940969606191299 Hi Yopp. How are you?`. The message carries sender.displayName ("Micah Martin"); use it: input "Micah Martin: <text>", origin metadata carries {:user users/<id> :display-name :email}. Same rendering in isaac-tund's context block. Scenario in inbound.feature: the provider request's user message starts with the display name; origin carries the id.



**Re-scoped 2026-09-19 (Micah):** the AGENT owns session naming. Episode ids stay timestamps; the backing session is named by the agent's naming strategy before any policy's open-session! is called, and a policy takes the identifier it is given. Today isaac-episodes/lifecycle.clj falls back to the episode id when no :session-id is supplied — that fallback goes, and the agent's api/open-session! (and every path that opens through a policy: comms, CLI, hail) mints a name when the caller has none. Work is mostly in isaac-agent; episodes loses its fallback.
