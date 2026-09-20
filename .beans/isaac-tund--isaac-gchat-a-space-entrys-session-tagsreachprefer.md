---
# isaac-tund
title: 'isaac-gchat: a space entry''s :session-tags/:reach/:prefer/:create resolve through the agent''s session frequencies, like hail'
status: completed
type: feature
priority: normal
tags:
    - google
    - comm
created_at: 2026-09-19T23:53:42Z
updated_at: 2026-09-20T07:21:38Z
parent: isaac-bv1l
blocked_by:
    - isaac-iv5c
---

Micah, 2026-09-19: the gchat space schema already accepts :session, :session-tags, :crew, :reach, :prefer, :create, but handler/ensure-session! only honours :session (exact id) and :crew — it builds the session key itself and calls create-session! directly. Resolve the space's frequency fields through the same selector hail uses (isaac.session selection: tags AND, reach one/all, prefer recent/oldest, create never/if-missing/always), falling back to the gchat-<space> id when none are set. Same for DMs (one session per DM by default). Scenarios in inbound.feature: a space with :session-tags [:ops] and :create :if-missing routes to the tagged session / creates it; :reach :all fans out; :session still pins.

## Landed on main (planner, 2026-09-20)

Landed by the planner during the fleet's auth outage.

`handler/space->frequencies` maps a space entry's `:session`,
`:session-tags`, `:crew`, `:reach`, `:prefer`, `:create` onto the agent's
session frequencies with hail's defaults (`:create :if-missing`, `:reach :one`,
`:prefer :recent`) — the same shape isaac-discord's `channel->frequencies`
builds, and no gchat-local selector. `session-keys` then resolves: `:one`
through `frequencies/resolve-session-targets`, `:all` through
`frequencies/matching-sessions` (one dispatch per matching session), an entry
that selects nothing keeps `gchat-<space>`, and `:session` still pins.

Degradations are deliberate: no registered store ⇒ the canonical session (a
comm must not wait on selection); `:create :never` with no match ⇒
`:gchat.route/no-session` at `:warn` and no turn, rather than a silent drop.

| suite | result |
| --- | --- |
| `bb spec` | 69 / 0 (6 new on the frequency mapping) |
| `bb features` | 25 / 0 (tags choose the session; an explicit session still pins) |

main-sha: isaac-gchat 2dac6eded999462e8078375c40cd64a667d5f0fe (0.1.8)

Found on the way, filed as isaac-e0t7: a session whose `:tags` are a **vector**
matches no selector at all — `spi/has-tag?` does `contains?` on the stored
value, which is an index lookup on a vector. Vector tags are accepted on the
way in and silently invisible to hail, to `--tags` and now to these space
entries.
