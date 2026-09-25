---
# isaac-2jjb
title: 'isaac-agent: sessions set <id>.crew relocates the session — the next turn keeps its transcript instead of an empty folder'
status: in-progress
type: bug
priority: high
created_at: 2026-09-25T14:54:13Z
updated_at: 2026-09-25T15:01:56Z
---

## Symptom (yopp, 2026-09-25 14:32Z–14:47Z)

The planner ran `isaac sessions set <id>.crew yopp` on four sessions that
still carried crew `main`. The record changed, but the session's directory
stayed at `sessions/main/<id>/` while the store began reading and writing
`sessions/yopp/<id>/` — a fresh, empty folder. Every following turn on those
sessions handed the claude CLI an empty conversation: "Input must be
provided either through stdin or as a prompt argument when using --print",
reported to Micah as "Something went wrong (provider error)" — twice in the
DM, once in yopp-test-2. Worked around by merging the folders by hand.

## Design

- `isaac sessions set <id>.crew <crew>` relocates the session directory to
  the new crew's folder (transcripts, episodes, session.edn) atomically —
  or the store resolves a session's directory by id independent of crew.
  Pick whichever the store's layout supports without a migration; document
  it in the store namespace.
- After the change, the next turn sees the full prior transcript.
- A session directory that exists under two crews is reported by
  `sessions list` (warn once) rather than silently split.

## Acceptance (features/session/mutation.feature — baselined)

- [ ] Scenario "isaac sessions set <id>.crew keeps the session's transcript
  — the next turn still sees the history (isaac-2jjb)".
- [ ] Spec: relocation moves transcript + episodes + record; a failure to
  move leaves the record unchanged and errors.
- [ ] Version bump; bb spec / bb features / bb lint green.

Likely repo scope: isaac-agent (session/store, sessions CLI, mutation.feature).

feature-baseline: isaac-agent e1c375810961b75b1aaadecf5ca3e982ec2f9aaa
feature-blob: isaac-agent features/session/mutation.feature 51e4c2cd8479c78333a72cc187717ab0a64a96c5 166



## Claimed by the planner (2026-09-25 15:02Z)

Hail f64cd1c1 dropped; implemented as a planner subagent in a local worktree. Workers: do not pick this up.
