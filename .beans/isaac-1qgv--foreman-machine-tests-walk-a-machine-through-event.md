---
# isaac-1qgv
title: 'Foreman machine tests: Gherkin features with Foreman-provided steps, run by isaac foreman test'
status: completed
type: feature
priority: normal
created_at: 2026-09-29T13:59:46Z
updated_at: 2026-09-29T16:48:48Z
parent: isaac-q3u3
blocked_by:
    - isaac-50zy
    - isaac-qrl1
---

Likely repo: **isaac-foreman**. Design: Micah + planner, 2026-09-29.

## Why

Anyone who writes a Foreman machine needs to test it, and most won't want to write Clojure. Machine tests are **Gherkin** — build/operate/check is already Given/When/Then, steps are lines (repeatable, no duplicate-key problem), and it is less technical than EDN. **Foreman ships the step definitions**; users write only `.feature` files. (An EDN case format was considered and dropped for Gherkin.)

## Contract

- `isaac foreman test <file.feature>…` parses the files, runs each scenario against the machines in the current Isaac root's config (loaded as `config validate` loads them), prints one `PASS`/`FAIL` line per scenario with the first mismatch, and exits 0 only if all pass. Relative paths resolve against the root.
- **Nothing executes.** Scenarios walk Foreman's pure engine (`isaac.foreman.machine/step`); actions are recorded, never run — no `:turn` is submitted, no `:log` prints, no instance files are created.
- **No data for now** (Micah): no instance context, no signal payloads in the step suite. Prompt placeholders `{{machine}}` and `{{instance}}` are filled; `{{data.<key>}}` fills empty.
- **Parser:** reuse gherclj's parser as a library if it exposes one; otherwise a small Foreman-owned parser for the subset used (Feature, Background, Scenario, Given/When/Then/And, `key | value` tables, comments). Decide during implementation; no user step definitions either way.
- **Unhandled signals:** follows isaac-qrl1 — `Then the signal is unhandled` checks that the signal was refused and the state did not change.

## Step vocabulary (fixed; one rule: *is* = exact, *contains* = substring / membership, *matches* = regex)

- Build: `Given the "<machine>" machine` · `Given instance "<id>"` (default `test-1`) · `Given the state is "<state>"` (default: the machine's `:initial`)
- Operate: `When "<event>" is signaled`
- Check:
  - `Then the state is "<state>"` · `Then the signal is unhandled`
  - `Then the actions are "<a>, <b>"` (exact, in order) · `Then the actions contain "<a>"` · `Then there are no actions`
  - `Then the "<action>" prompt is "<text>"` · `… prompt contains "<text>"` · `… prompt matches "<regex>"`
  - `Then the "<action>" target is:` (`key | value` table over the action's `:frequencies` and `:resource-pools`)
- Checks apply to the latest signal; a scenario may signal and check repeatedly to walk a path.

## Example (agreed with Micah)

```clojure
{:initial :dark
 :actions {:light-lamp {:type :turn :frequencies {:crew "bartholomew"} :prompt "Light the lamp at {{instance}}."}
           :douse-lamp {:type :turn :frequencies {:crew "bartholomew"} :prompt "Douse the lamp at {{instance}}."}
           :log-watch  {:type :log :message "watch changed"}}
 :transitions [{:start :dark :event :dusk :end :lit  :actions [:light-lamp :log-watch]}
               {:start :lit  :event :dawn :end :dark :actions [:douse-lamp :log-watch]}]}
```

```gherkin
Feature: lighthouse-watch

  Background:
    Given the "lighthouse-watch" machine
    And instance "beacon-7"

  Scenario: dusk lights the lamp
    When "dusk" is signaled
    Then the state is "lit"
    And the actions are "light-lamp, log-watch"
    And the "light-lamp" prompt is "Light the lamp at beacon-7."
    And the "light-lamp" target is:
      | crew | bartholomew |

  Scenario: dawn before dusk is unhandled
    When "dawn" is signaled
    Then the signal is unhandled
    And the state is "dark"
    And there are no actions
```

## Scenario plan (to draft)

1. A passing machine test prints `PASS <scenario>`, exits 0, and leaves no instance and no submitted turn behind.
2. A wrong expected state prints `FAIL <scenario> — <step>: expected lit, got dark` and exits 1.
3. Prompt checks: `is`, `contains`, and `matches` each pass on the filled prompt; a failing one shows the actual prompt.
4. An expected unhandled signal passes; an unexpected one fails, naming the event and state.

Blocked by isaac-50zy (`:actions`) and isaac-qrl1 (refused signals) so the harness is built on the final semantics.


## Acceptance

Feature: `isaac-foreman/features/foreman/machine_tests.feature` (new, 4 scenarios, `@wip` on main at 1608cac). The scenarios embed test `.feature` files in docstrings — only lines `:33`, `:67`, `:83`, `:109` are scenarios of this feature. Remove `@wip`; all pass:

- [ ] `bb features features/foreman/machine_tests.feature` — `:33` PASS + nothing left behind (no instance, no turn, no `:log` output), `:67` wrong state FAIL with expectation and actual, `:83` prompt is/contains/matches + a miss shows the actual prompt, `:109` expected vs unexpected unhandled
- [ ] Unit specs for every step in the vocabulary, including `Then the "<action>" target is:` over `:frequencies` and `:resource-pools`, `Given the state is`, and the `test-1` / `:initial` defaults.
- [ ] Parser decision recorded in the bean (gherclj library vs. a Foreman-owned subset parser).
- [ ] `isaac help foreman` lists `test`.
- [ ] `bb verify` green; version bump.

feature-baseline: isaac-foreman 1608cacd69edff0632a42801c5dcfc8112beedc6
feature-blob: isaac-foreman features/foreman/machine_tests.feature 8aa9f6af29cc592409cf0ec64bf9c9307c67b602 33,67,83,109

## Parser decision

Used gherclj's own parser as a library: `gherclj.parser` (`src/gherclj/parser.clj`)
is part of gherclj's main `src` path, not a test-only namespace, and depends on
nothing beyond `clojure.java.io`/`clojure.string`. It already parses the full
subset Foreman needs — Feature/Background/Scenario, Given/When/Then/And, `|
k | v |` tables, `#`-comments (blank lines only reset pending tags, `#` isn't
special-cased but no fixture needs it), doc-strings — and is mutation-tested
upstream. Promoted `io.github.slagyr/gherclj {:git/tag "v1.3.0" :git/sha
"9c3bb1d"}` from `isaac-foreman`'s `:test`/`:spec`/`:features` aliases into the
base `:deps` (same coordinates the aliases and `bb.edn` already pinned) so
`isaac foreman test` has it at runtime. Its own deps (cheshire, c3kit/apron,
tools.cli) already overlap what `isaac-foundation` transitively pulls in, and
`isaac foreman test` uses only `gherclj.parser`, not the rest of the gherclj
test-generation framework. `isaac.foreman.test-runner` calls
`gherclj.parser/parse-feature` on file text read through `isaac.fs` (so it
still resolves relative paths against the Isaac root through the normal
fs/nexus path) and walks the returned IR with Foreman's own fixed-vocabulary
step interpreter — no Foreman-owned Gherkin grammar was written.

## Landed on main (2026-09-29)

main-sha: isaac-foreman 5b981e5e35ad29b2cb22a3df07d1d6c4aa7d3b4a
