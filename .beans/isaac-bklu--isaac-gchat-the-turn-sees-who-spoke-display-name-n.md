---
# isaac-bklu
title: 'isaac-gchat: the turn sees who spoke — display name, not users/<id>'
status: completed
type: bug
priority: high
tags:
    - google
    - comm
created_at: 2026-09-20T00:06:00Z
updated_at: 2026-09-20T07:09:41Z
parent: isaac-bv1l
blocked_by:
    - isaac-8s6s
---

2026-09-19: gchat dispatch! builds the turn input as "<sender> <text>" where sender is the email if Google supplied one, else the users/<id> — so Yopp's first real turn read `users/118285940969606191299 Hi Yopp. How are you?`. The message carries sender.displayName ("Micah Martin"); use it: input "Micah Martin: <text>", origin metadata carries {:user users/<id> :display-name :email}. Same rendering in isaac-tund's context block. Scenario in inbound.feature: the provider request's user message starts with the display name; origin carries the id.



Depends on isaac-8s6s (people index): render through people/render — display name + email when known; do not ship a display-name-only version.

## Landed on main (planner, 2026-09-20)

Landed by the planner during the fleet's auth outage.

The turn-input half of this bean came with isaac-8s6s (the input now reads
`Micah Martin <micah@tonotop.com>: …`). What was left was the record: a routed
decision now carries `:identity` beside the rendered `:sender`, and the
session's `:origin` keeps `{:user :display-name :email}` — the id is what a
rename cannot orphan.

New feature step `session "…" has origin:` (gchat steps) and a scenario that
drives a users/<id> sender through the People API resolve and reads the
origin back.

| suite | result |
| --- | --- |
| `bb spec` | 55 / 0 |
| `bb features` | 22 / 0 |

main-sha: isaac-gchat 22ae0f3abdee94b58f2e5d7b6d271d1c29a5ee21 (0.1.6)

Still open, as the bean says: the same rendering in isaac-tund's context block,
and isaac-gmail rendering through `people/render`.
