---
# isaac-8379
title: Shared template engine in foundation; Agent, Hail and Foreman render through it
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-10-05T15:57:06Z
updated_at: 2026-10-05T17:47:27Z
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


## Implementation handoff (2026-10-05)

Shared renderer committed on isaac-foundation bean/isaac-8379 (fc05126); Agent (94f81b5), Hail (57b354f), and Foreman (92e2aac) delegate on corresponding branches. Foundation `bb ci` passes (361 feature examples); Agent features with local Foundation pass (858 examples), native Agent specs with local Foundation pass (1882 examples); Hail native specs and features with local Foundation pass (67 / 104 examples); Foreman JVM specs and JVM features with `:dev-local` pass (97 / 48 examples); Hooks `:dev-local` specs and features pass (36 / 20 examples). `rg -n "str/replace.*\\{\\{" ../isaac-{agent,hail,foreman}/src` reports no private render engine.

Verifier: land Foundation upstream first, then update all Foundation pins in Agent, Hail, Foreman deps.edn and bb.edn to its landed main SHA before their final tests/landing; Hail and Foreman also depend on Agent. No bean-branch SHA pins were written while in flight. Foreman bb spec / bb features without local overrides cannot load the new Foundation namespace until repinned. One unrelated existing JVM Agent spec `tool/comm_send_spec.clj:123` expects crew main but gets atticus on repeated isolated run; native Agent spec suite passes. Hail features with local Agent checkout (rather than its pinned Agent) report 2 queue-status failures (:held vs :queued); with pinned Agent and local Foundation, all 104 pass.


## Checkpoint (2026-10-05)

Done: Foundation engine and Agent/Hail/Foreman delegates committed and pushed on bean/isaac-8379; full local-dependency feature suites green (Foundation 361, Agent 858, Hail 104 with pinned Agent, Foreman 48 via dev-local, Hooks 20). Next: verifier lands Foundation, repins and lands Agent, Hail, Foreman; resume at isaac-agent/deps.edn:4 for first Foundation pin (then bb.edn:15). Last unoverridden Foreman native spec was red because pinned Foundation lacks isaac.foundation.template; test again after repin. bb bean-gate verify isaac-8379 exited 2 (ungated), so verify handoff is required.
