---
# isaac-tund
title: 'isaac-gchat: a space entry''s :session-tags/:reach/:prefer/:create resolve through the agent''s session frequencies, like hail'
status: todo
type: feature
priority: normal
tags:
    - google
    - comm
created_at: 2026-09-19T23:53:42Z
updated_at: 2026-09-19T23:53:42Z
parent: isaac-bv1l
---

Micah, 2026-09-19: the gchat space schema already accepts :session, :session-tags, :crew, :reach, :prefer, :create, but handler/ensure-session! only honours :session (exact id) and :crew — it builds the session key itself and calls create-session! directly. Resolve the space's frequency fields through the same selector hail uses (isaac.session selection: tags AND, reach one/all, prefer recent/oldest, create never/if-missing/always), falling back to the gchat-<space> id when none are set. Same for DMs (one session per DM by default). Scenarios in inbound.feature: a space with :session-tags [:ops] and :create :if-missing routes to the tagged session / creates it; :reach :all fans out; :session still pins.
