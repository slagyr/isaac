---
# isaac-ukof
title: 'Skills: distinguish band-targeting from concrete-session targeting (never use band name as a session)'
status: todo
type: task
priority: high
created_at: 2026-07-03T20:56:20Z
updated_at: 2026-07-03T20:56:59Z
blocking:
    - isaac-8lhv
---

## Problem (evidence, 2026-07-03)

Across zanebot hail records: session [:isaac-work-1] (correct) appears 9x, but session ["isaac-work"] (the BAND NAME used as a session value) appears 11x. A session named "isaac-work" never exists — the band selects isaac-work-1/isaac-work-2 by tags — so all 11 go UNDELIVERABLE. This dead-lettered verify->work returns (contributing to isaac-wtg8 looking stuck). Spans gpt and grok turns — not model-specific.

The deployed fail-handoff line is already band-only and correct. The confusion is in the EXACT-SESSION-RETURN guidance (hail-bean-verify / -work / -plan), where — lacking a real submitter-session — the crew fills the band name into the session slot.

## Fix (orchestration prose/docs, hot-reload — no release)

1. In each skill's exact-session-return guidance, state explicitly:
   - Use a session key ONLY with a concrete session id extracted via hail_get from the thread (e.g. "isaac-work-1").
   - NEVER use a band name (isaac-work / isaac-verify) as a session value — bands are selectors, not sessions.
   - If the thread has no prior worker session (band/CLI-triggered), just hail the band; its config picks the worker.
2. Add a "Band vs session targeting" note (shared skill / REUSE.md): band-only routes via tags/crew/reach; band+session forces an exact session; multiple -work-N sessions are normal; band name is not a session name.
3. Update test/verification-guide.md + return-flow test docs: distinguish exact-continuity (real submitter-session -> direct session key expected) from normal/CLI returns (band-only is correct); add evidence check "if a return used a session key, the value was a real session id, not a band name".

## Acceptance

- [ ] All three hail-bean-* skills carry the band-vs-session rule; exact-session-return sections forbid band-name-as-session.
- [ ] REUSE.md / verification-guide document it.
- [ ] Redeployed to zanebot (scp/install; hot-reload).
- [ ] Paired with the isaac-hail defensive routing bean which enforces it deterministically.

## Scope

orchestration repo isaac-beans/prompts/skills + REUSE.md + test docs.
