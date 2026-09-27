---
# isaac-rcbe
title: A turn fails when the session policy stamp disagrees with the crew
status: todo
type: bug
priority: high
created_at: 2026-09-27T00:45:22Z
updated_at: 2026-09-27T00:45:22Z
---

Repo: **isaac-agent**. The turn chooses the session policy from the crew (`policy/for-request` reads `:crew <id> :session-policy`). The session record stores the policy it was created under, and that field is immutable, so the sessions index can list it. Those two are allowed to diverge, and when they do the turn still runs the crew policy. An episodes crew therefore opens episode containers and injects recall on a session whose record still says chronicle. Seen on Yopp `prompt-default` (created 2026-09-14, stamped chronicle, crew set to episodes on 2026-09-17): the 2026-09-26 turn opened episode `20260926233010390` on it.

## Decision (2026-09-27, Micah)

Keep the stamp. A turn whose session record already has a `:session-policy` that is not the crew's policy fails before it appends, recalls, seals, or opens a container. The failure names the session id and both policies. A session that does not exist yet is not a conflict: opening it stamps the crew policy. Absent crew policy means chronicle, same as today.

Both directions fail: episodes crew on a chronicle session, and a chronicle crew on an episodes session.

## Not in this bean

Migrating the sessions that already diverged. That is data repair on the host, and it has to be done before this check is deployed or those sessions start failing.

`prompt-default` is not configuration. It is the hardcoded `:default-session-key` inside `isaac prompt` when the command names no session. `:defaults :frequencies` is not consulted for that choice. A bare prompt uses the frequencies crew as the crew, and the literal session id `prompt-default` as the session.

## Acceptance

`isaac-agent/features/session/policy_mismatch.feature` (@wip). Logbook is the
stand-in for any non-chronicle policy, including episodes. The check compares
the two policy names.

- a session stamped chronicle refuses a turn from a logbook crew: exit 1, stderr names the session and both policies, logbook records no calls, no user message is appended
- a session stamped logbook refuses the next turn after the crew policy is removed: exit 1, the first turn's transcript remains
- a session that does not exist yet is created under the crew policy and the turn runs
- a chronicle session runs when the crew is chronicle

```
cd isaac-agent && bb features features/session/policy_mismatch.feature
```

Remove `@wip` as each scenario passes. `bb ci` in isaac-agent.

feature-baseline: isaac-agent d8d71f85612fff178438e1ffdd1df485780a2104
feature-blob: isaac-agent features/session/policy_mismatch.feature 69c993f154504234f66e68c7c62bedca4743f77b 14,36,66,82
