---
# isaac-8379
title: Shared template engine in foundation; Agent, Hail and Foreman render through it
status: completed
type: task
priority: normal
created_at: 2026-10-05T15:57:06Z
updated_at: 2026-10-05T22:29:37Z
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


## Summary of Changes

Verifier landed all four repos on main, in order:

- **isaac-foundation**: `edc18481808a050f8c2fd657f2e966e6040fcfc3` — new `isaac.foundation.template` (render/render-all/placeholders). `bb ci`: 1348 spec examples / 0 failures, 361 feature examples / 0 failures (2 pre-existing pending).
- **isaac-agent**: `5da7d453ffe52bc28e06863392fc7c2896258fe1` — `isaac.agent.prompt.template` delegates to foundation; foundation pin bumped to the sha above in deps.edn and bb.edn (every alias, including :test/:spec/:features). `bb spec`: 1887/0, `bb features`: 871/0 (1 pre-existing pending), `bb jvm-spec`: 1887/0.
- **isaac-hail**: `0c60c2bc30a2222fcb7ba18af03154e558dc9c87` — `isaac.hail.template` and `isaac.hail.prepare` delegate to foundation; foundation pin bumped (agent pin left at its existing sha — unchanged and still compiles/passes). `bb ci`: spec 67/0, features 104/0; `bb jvm-spec`: 67/0.
- **isaac-foreman**: `d40642db5fa9096269bd0135691acf627a7aa26f` — `isaac.foreman.machine/fill-prompt` and `fill-frequencies` delegate to foundation; foundation pin bumped (agent pin left as-is, still an ancestor of agent main). Cherry-picked cleanly past the new `isaac-wh2o` `features/foreman/template_contract.feature` already on main (no file overlap). `bb ci` (spec + features, both JVM): spec 97/0, features 49/0.

Acceptance check: `git grep -n 'str/replace.*{{'` in agent/hail/foreman `src/` finds no private template engine (all three exit non-matching).

Notes for the record:
- `isaac-agent` `spec/isaac/agent/session/session_steps_spec.clj` "a parked send that completes during admission does not await the running turn" is a pre-existing flaky race (nested `future` + `nexus/-with-nexus`); reproduces intermittently (~1/8) on unmodified `origin/main` with the old foundation pin too — unrelated to this bean, not blocking.
- `isaac-agent` JVM `tool/comm_send_spec.clj:123` (crew main vs atticus), the flake named in the handoff, did not reproduce in this verification's `bb jvm-spec` run (1887/0 clean).
- `isaac-hail` `bb jvm-features` fails on a stale step-namespace glob (`isaac.hail-steps` / `isaac.hail-hlt1-steps` no longer exist post isaac-81ua restructure) — reproduces identically on unmodified `origin/main`; pre-existing, not introduced by this bean, not blocking (native `bb features`/`bb ci` is green and is the suite CI actually runs).
