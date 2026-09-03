---
# isaac-7rso
title: 'prompt_cli output redesign: stdout=reply, stderr=theater, -q/-v tiers'
status: draft
type: feature
priority: normal
created_at: 2026-08-31T15:39:29Z
updated_at: 2026-09-03T16:40:56Z
blocked_by:
    - isaac-5nxf
    - isaac-jarr
---

Repo: **isaac-agent** (`bridge/prompt_cli.clj` PromptComm + flag parsing).
Decisions final in isaac-frvu (prompt_cli review, 2026-08-31) — the tier
table there is the contract. Summary:

- stdout carries ONLY the reply (composable pipelines); errors stderr+exit.
- default: chatter streamed live to stderr (dead ends included), 🧰/← tool
  lines, compaction bulletins 🥬✨🥀🪦 — today's feel, relocated.
- --verbose adds ⋯ reckoning (dim), tool-progress, remaining bulletins.
- --quiet: reply only.
- Asides never rendered separately; old live?/stream knob removed; reply
  duplication between stderr stream and stdout is accepted.

## Scenarios (to write before todo)

- `prompt -m …` with a tool turn: stdout equals exactly the reply text;
  stderr contains the chatter + 🧰/← lines (existing stderr steps reuse).
- `-q`: stderr empty on a successful turn; errors still on stderr.
- `-v`: ⋯ reckoning lines present (grover `reasoning` fixture from 5nxf);
  tool-progress lines from the test__sounding mock.
- compaction turn: 🥬/✨ on stderr at default tier (migrate the existing
  scenarios' expectations, which already assert stderr).
- conformance: no same-destination double render (rides 5nxf's spec).

Blocked by isaac-5nxf (needs chatter/reckoning/bulletin signals + the
defaults-map protocol). Draft until scenarios are committed @wip.
