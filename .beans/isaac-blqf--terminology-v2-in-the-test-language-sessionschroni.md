---
# isaac-blqf
title: 'Terminology v2 in the test language: sessions→chronicles/threads in step names'
status: draft
type: task
priority: low
created_at: 2026-08-30T22:48:18Z
updated_at: 2026-08-30T23:04:02Z
---

Repo: isaac-agent (spec-support steps) + downstream feature files. Per isaac-51xy Terminology v2 the internal vocabulary is chronicle/thread/episode; the industry word 'session' stays at EXTERNAL boundaries only (CLI flags, ACP ids). The Gherkin step language is internal: 'the following sessions exist' → 'the following chronicles exist', 'session {k} has transcript' → 'chronicle {k} has transcript', etc. Keep old phrases as aliases during migration; sweep features repo-by-repo; remove aliases in a final pass. Micah 2026-08-30: worth its own bean; do NOT ride scuttlebutt. Also audit step docstrings that say 'session' for internal senses. Draft until the rename inventory (gherclj steps | grep -i session across repos) is attached and scenarios/mechanics decided.



## Scope note (2026-08-30, plan): not a find-and-replace

'Session' in the step language covers THREE terminology-v2 concepts and each
step must pick the right one:
- `the following sessions exist` seeds a container+transcript → **chronicle**
  in chronicle scenarios, but an episode's **backing session** in episode
  scenarios (episodes/live.feature) — that usage may keep a distinct phrase.
- `--session reef-chat` inside When steps is a **thread** for episode crews
  (external flag spelling stays 'session' per decision 33 — only the
  step-English around it changes).
- `session {k} has transcript matching` asserts on the **transcript** —
  policy-neutral; 'chronicle {k} has transcript' would be WRONG for episode
  backing sessions. Candidate: 'transcript of {k} matches'.

Out of scope (boundary rule + phase-2): CLI flags, ACP ids, hail
bound-session; isaac.session.* namespaces and ~/.isaac/sessions/ paths
(isaac-chronicle/isaac-episode module split, 51xy decision 34).

Plan of record: inventory first (gherclj steps | grep -i session, per repo:
agent, acp, discord, imessage, hail, foundation), per-step term decision
table in this bean, aliases during migration, alias removal as the final
pass.
