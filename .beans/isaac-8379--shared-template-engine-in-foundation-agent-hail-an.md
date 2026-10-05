---
# isaac-8379
title: Shared template engine in foundation; Agent, Hail and Foreman render through it
status: in-progress
type: task
priority: normal
created_at: 2026-10-05T15:57:06Z
updated_at: 2026-10-05T17:33:23Z
---

Likely repos: **isaac-foundation** (engine), then **isaac-agent**, **isaac-hail**, **isaac-foreman** (move onto it). Micah + planner, 2026-10-05.

## Why

Isaac has three copies of one Mustache-lite engine:
- isaac-agent `isaac.agent.prompt.template/render` — prompt catalog, and isaac-hooks
  (webhook payload → prompt); missing policy :keep / :empty / :marker; no paths.
- isaac-hail `isaac.hail.template/render` — band prompts; missing → ""; no paths.
- isaac-foreman `machine/fill-prompt` (+ its frequency templating, isaac-8r7m) —
  `{{machine}}`, `{{instance}}`, dotted `{{data.a.b}}`; missing → "".
The engine belongs in the lowest layer; the *variables* belong to each caller.
(Foundation's `${VAR}` config substitution is a different job and stays separate.)

## Design

- New `isaac.foundation.template`:
  - `(render template bindings & {:keys [on-missing]})` — `{{name}}` and dotted
    paths `{{a.b.c}}` (`[\w.-]` names) over a bindings map (keyword or string keys);
    `:on-missing` `:keep` | `:empty` | `:marker` (default `:keep`, as Agent today).
  - `(render-all value bindings opts)` — walks maps/vectors/seqs and renders every
    string; keywords, numbers, booleans untouched.
  - `(placeholders template-or-value)` — the set of variable names used (for callers'
    config validation).
- Agent's `isaac.agent.prompt.template` becomes a thin delegate (or callers switch);
  Hail and Foreman drop their copies. Behavior of every existing caller is unchanged
  (hail's missing → "", foreman's missing → "", hooks' `:marker`).
- Bump foundation pins in the three modules.

## Acceptance (ungated: library refactor, no new feature behavior)

- isaac-foundation specs for `render` (names, dotted paths, the three missing policies),
  `render-all` (nested structures, non-strings untouched), `placeholders`.
- No behavior change: isaac-agent (prompt catalog, hooks paths), isaac-hooks,
  isaac-hail and isaac-foreman feature suites stay green on the new engine.
- `git grep -n "str/replace.*{{"` in agent/hail/foreman src finds no private engine left (one-time check).
