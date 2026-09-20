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
updated_at: 2026-09-20T00:06:00Z
parent: isaac-bv1l
---

2026-09-19: gchat dispatch! builds the turn input as "<sender> <text>" where sender is the email if Google supplied one, else the users/<id> — so Yopp's first real turn read `users/118285940969606191299 Hi Yopp. How are you?`. The message carries sender.displayName ("Micah Martin"); use it: input "Micah Martin: <text>", origin metadata carries {:user users/<id> :display-name :email}. Same rendering in isaac-tund's context block. Scenario in inbound.feature: the provider request's user message starts with the display name; origin carries the id.
