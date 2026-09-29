---
# isaac-1qgv
title: 'Foreman machine tests: Gherkin features with Foreman-provided steps, run by isaac foreman test'
status: draft
type: feature
priority: normal
created_at: 2026-09-29T13:59:46Z
updated_at: 2026-09-29T15:47:39Z
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
