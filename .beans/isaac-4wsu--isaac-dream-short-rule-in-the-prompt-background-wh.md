---
# isaac-4wsu
title: 'isaac-dream: short rule in the prompt, background (why, evidence, history, strength) via dream show and a dream__habit tool'
status: in-progress
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T16:22:45Z
updated_at: 2026-11-16T16:23:31Z
parent: isaac-pcm3
---

Repo: **isaac-dream**. Micah, 2026-11-16: habits should be short in the prompt; the background stays for the operator, and a crew can read its own habits' background through a tool if it chooses. Spec: `features/habits/background.feature`. Follows isaac-o4di (strength), isaac-wh9y (kinds), isaac-tpa5 (retire), all landed.

## What

- **Short rule.** A habit's `lesson` is one short rule with its reason ("…, because …"), at most **300 characters**. It is the only habit text in the system prompt (already true; the scenario guards it).
- **Why.** Dream `propose` items carry `why`: the story of how the habit formed. Stored as `:why` on the habit. The dream prompt (`resources/isaac/dream/prompt.md`) asks for a one-sentence lesson with its reason and puts the story in `why`.
- **Too long.** A proposed lesson over 300 characters is dropped, noted in the diary as `dropped[].reason lesson-too-long`, and not stored.
- **`isaac dream show <id> --crew <crew>`** prints, in this order: `Rule: <lesson>`, `Why: <why>`, `Strength: <n.n>` and pin, the quoted evidence lines, then the history (proposed, approved, reinforced taught/applied, contradicted, …) each with its date and quoted evidence where it has some. Keep the existing show scenario's lines matching (run.feature).
- **`dream__habit` tool** (manifest `:isaac.agent/tools` `:dream/habit`, granted like `dream/propose`): read-only, crew-scoped to the calling session's crew.
  - no `id`: one line per habit of this crew: id, state, strength, pinned, lesson.
  - `id`: that habit's background, the same content and order as `dream show`.
  - an id that is not this crew's habit (including another crew's): "not found", and nothing from the other crew.

## Acceptance

`bb features features/habits/background.feature` green with `@wip` removed (7 scenarios), plus `bb spec` and full `bb features` green. Do not bump the module version (the deploy train does).

feature-baseline: isaac-dream bf99133ee399aca30234cada7487bb0f86b6790d
feature-blob: isaac-dream features/habits/background.feature 2c8dc0eb50bc24273ae1493925b335d05d739311
