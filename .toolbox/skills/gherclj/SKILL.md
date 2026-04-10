---
name: gherclj
description: Use this skill when implementing gherclj feature steps, working on beads that reference .feature files, or writing defgiven/defwhen/defthen definitions. Ensures step definitions follow gherclj conventions — especially that defthen steps assert results.
---

# gherclj Step Implementation

## When This Skill Applies

Use this skill whenever you are implementing step definitions for `.feature` files in a gherclj project. This includes any bead that references a feature file or involves writing `defgiven`, `defwhen`, or `defthen` steps.

## Feature Contract Integrity

Approved `.feature` files are behavioral contracts.

- Do NOT semantically weaken, reinterpret, or rewrite approved scenarios without user approval.
- Clarifying wording changes are fine only when they preserve the approved behavior exactly.
- If implementation and approved feature text diverge, stop and raise the mismatch instead of changing the scenario to fit partial implementation.

## Step Definitions Must Exercise Real Behavior

Step definitions should test real product behavior through real code paths where feasible.

- Prefer calling production entry points, public APIs, application services, or other real seams the product already exposes.
- If a scenario needs a seam, prefer a real public seam or an explicitly approved test hook.
- Do not move product logic into step definitions just because the implementation is missing.

## Forbidden Acceptance-Test Shortcuts

- Do NOT inject unimplemented product behavior directly in step definitions just to make scenarios pass.
- Do NOT add acceptance-test-only shims that simulate promised behavior the product does not actually implement.
- Passing feature scenarios must reflect actual implemented behavior, not test-only shortcuts.
- A bead is not complete if the feature passes only because the steps fake missing behavior.

## Step Types and Their Responsibilities

### defgiven — Set up preconditions

Given steps mutate state to establish preconditions. No assertions needed.

```clojure
(defgiven create-user "a user \"{name}\""
  [name]
  (g/assoc! :user {:name name}))
```

### defwhen — Perform actions

When steps perform the action under test. No assertions needed.

```clojure
(defwhen user-logs-in "the user logs in"
  []
  (let [user (g/get :user)]
    (g/assoc! :response (authenticate user))))
```

### defthen — Assert results

Then steps MUST assert. A `defthen` that returns a value without asserting is a bug — it produces 0 assertions and silently passes.

If your project uses a single framework, you can use its native assertions directly (e.g., speclj's `should=`). Use `g/should=` when your steps need to be framework-agnostic — for example, when the project runs tests under both speclj and clojure.test.

```clojure
;; WRONG — no assertion, silently passes
(defthen check-status "the response status should be {status:int}"
  [status]
  (g/get-in [:response :status]))

;; RIGHT — asserts the expected value
(defthen check-status "the response status should be {status:int}"
  [status]
  (g/should= status (g/get-in [:response :status])))
```

## Step Conventions in This Project

Steps are explicit about which agent or session they operate on. No implicit state resolution.

### Given steps
- `agent {string} has sessions:` — create sessions for a specific agent
- `session {string} has transcript:` — set up transcript entries for a specific session key
- Tables support a `#comment` column for documenting intent (ignored by matchers)

### When steps
- `sessions are created for agent {string}:` — test session creation
- `entries are appended to session {string}:` — test entry appending
- `the user sends "{string}" on session {string}` — full process-user-input! flow

### Then steps
- `agent {string} has N session(s)` — count sessions for agent
- `agent {string} has sessions matching:` — match against session index
- `session {string} has N transcript entries` — count transcript entries
- `session {string} has transcript matching:` — match against transcript
- `the prompt "{string}" on session {string} matches:` — build and inspect prompt

### Transcript tables
Given and Then transcript tables share the same shape: `type`, `message.role`, `message.content`, `summary`, etc. The `type` column determines the entry kind: `message`, `compaction`, `toolCall`, `toolResult`.

## Running Features

```bash
# Run all features (excludes @slow and @wip)
bb features

# Run a specific scenario by file:line
bb features features/session/storage.feature:11

# Run slow integration tests
bb features-slow

# Run with documentation output
bb feature-docs
```

Step files are auto-discovered via glob (`-s "isaac.features.steps.*"`). New step files are picked up automatically — no bb.edn edits needed.

## Verification

After implementing steps, always run the feature specs and verify:

1. **Assertion count > 0** — If you see `0 assertions`, your `defthen` steps are not asserting
2. **No unexpected pending** — Pending scenarios mean step text isn't matching registered steps
3. **Real behavior is exercised** — Scenarios pass because the product implements the behavior, not because steps simulated it
4. **Run the specific scenario**: `bb features features/path/to/file.feature:LINE`
5. **Run the full suite**: `bb features`

```
# Good — assertions present
40 examples, 0 failures, 68 assertions

# Bad — no assertions means defthen steps aren't asserting
40 examples, 0 failures, 0 assertions
```

## Definition of Done

A feature implementation bead is NOT complete until:

1. **Step definition file exists** — `spec/isaac/features/steps/<feature>.clj`
2. **All scenarios run** — Remove the `@wip` tag from the scenario
3. **Scenario passes with file:line** — `bb features features/path/to/file.feature:LINE` shows 1 example, 0 failures
4. **No pending scenarios** — Every scenario's step text matches a registered step
5. **Assertions > 0** — Every `defthen` step asserts
6. **No fake behavior in steps** — Step definitions are not simulating missing product behavior
7. **Full suite passes** — `bb features` shows 0 failures

Do NOT close a bead if the feature file still has `@wip`, if scenarios are pending, or if the feature only passes because the steps fake missing behavior.

## State Management

Steps use `gherclj.core` for state, aliased as `g`:

```clojure
(ns myapp.features.steps.auth
  (:require [gherclj.core :as g :refer [defgiven defwhen defthen]]))
```

- `g/assoc!`, `g/assoc-in!` — set state
- `g/get`, `g/get-in` — read state
- `g/update!`, `g/update-in!` — modify state
- `g/swap!` — arbitrary state transformation
- `g/dissoc!` — remove state
- `g/reset!` — called automatically before each scenario
