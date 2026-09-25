---
# isaac-gs4a
title: config set conforms a CLI value to the field's schema type; unset takes a set member
status: in-progress
type: bug
priority: high
tags:
    - unverified
    - foundation
    - config
created_at: 2026-09-25T14:46:21Z
updated_at: 2026-09-25T15:24:57Z
---

Repo: **isaac-foundation** (src/isaac/config/cli/mutate_common.clj). Scenarios live in **isaac-agent** (features/config/set_unset.feature), because the crew schema they exercise (`tags` is a set of keywords, `soul` is a string) is agent's.

## The problem (zanebot, 2026-09-25)

Micah tried to change an HTTP principal's scopes with `config set`:

- `config set http.auth.principals.micah-zaap.scopes :hooks` crashed with `Don't know how to create ISeq from: clojure.lang.Keyword`.
- `config set …scopes hooks` was refused with "must be a set of keywords".
- `config set …scopes '#{:hooks}'` can't work either. Nothing typed on the command line becomes a set; only stdin EDN does.
- `config unset …scopes :hail/send` ignored `:hail/send` and targeted the whole `scopes` key.

The cause is `parse-set-value` (mutate_common.clj:22). It guesses the type from the text's shape: digits become a long, a leading `:` becomes a keyword, else a string. It never asks the schema what the field holds, so `soul 42` becomes a long and fails validation, and `:hooks` for a set field reaches coercion as a bare keyword and throws.

## Change

**Conform the CLI string to the field's schema type.** The composed schema knows every field's type (`target-spec-for` already resolves it), so use it:

- keyword / id: `hooks` → `:hooks`, and `:hooks` → `:hooks`
- int / long / double: parse the number; an unparseable value is a validation error (exit 1), as today
- string: keep the text verbatim, digits included (`42` stays `"42"`)
- set of X: split on commas, conform each member as X, and build the set. `hooks` → `#{:hooks}`, `hooks,hail/send` → `#{:hooks :hail/send}`. A single keyword gives a one-member set.
- boolean: `true` / `false`
- no spec (open or undeclared path): fall back to today's shape-guessing

Any exception while conforming becomes an ordinary validation error with exit 1. A raw Clojure exception never reaches the user.

**`config unset <path> <member>`** on a set-typed field removes just that member, the same as the existing `config unset <path>.<member>` form. On a path that is not a set, an extra argument is refused (exit 1, "takes no value"), never silently ignored.

## Decisions

- Decision (2026-09-25, Micah): "All the types for the config are known and we can take advantage of that to make sure that we set valid values." The schema is how a value gets parsed, not a check applied after guessing.
- Decision (2026-09-25, planner): setting a whole set-typed field replaces the set. `config set crew.joe.tags jackalope` gives `#{:jackalope}`. Adding one member is still `config set crew.joe.tags.jackalope` (the existing path form, unchanged). This matches what "set" means for every other type.
- The existing scenario "config set refuses a value a schema validator rejects" (`tags jackalope` → exit 1) was rewritten in place into the first scenario below. Clean cutover: that input is now valid.

## Scenarios (isaac-agent, @wip)

- features/config/set_unset.feature:84 — bare name conforms to the keyword set (replaces)
- features/config/set_unset.feature:101 — keyword conforms to a one-member set, no crash
- features/config/set_unset.feature:114 — comma list conforms to a set of keywords
- features/config/set_unset.feature:127 — digits stay a string for a string field
- features/config/set_unset.feature:138 — `unset <path> <member>` removes only that member
- features/config/set_unset.feature:152 — `unset` refuses a value on a path that is not a set

## Acceptance

Implement in isaac-foundation, run isaac-agent's features against it via `:dev-local`, then land foundation and repin agent's foundation pin.

```
cd isaac-foundation && bb spec && bb ci
cd isaac-agent && bb features features/config/set_unset.feature:84 features/config/set_unset.feature:101 features/config/set_unset.feature:114 features/config/set_unset.feature:127 features/config/set_unset.feature:138 features/config/set_unset.feature:152
cd isaac-agent && bb features features/config/set_unset.feature && bb ci
```

Remove `@wip` from those six scenarios; all of set_unset.feature is green.


## Verify fail (attempt 1, 2026-09-25): agent foundation pin was not repinned, so the six acceptance scenarios execute against eaea445 and fail 5/6; additionally, colon-prefixed unset members are converted to ::name rather than :name.


## Verify Repair (2026-09-25)

Repaired verifier findings across both repositories:

- `isaac-foundation` branch `bean/isaac-gs4a` at `399b679c42071cff9f0a6000b8425659792ea29a`: normalizes one leading `:` before keywordizing a raw `config unset <set-path> <member>` argument, so `:hail/send` removes `:hail/send`, not `::hail/send`; covered by a unit spec.
- `isaac-agent` branch `bean/isaac-gs4a` at `5c7ec2d`: repins every foundation dependency (product, spec, test-support, marigold.bridge, marigold.longwave) in both `deps.edn` and `bb.edn` to foundation `399b679c42071cff9f0a6000b8425659792ea29a`; rebased on `origin/main@89af4b7`.

Passing checks:

- foundation: `bb lint src/isaac/config/cli/mutate_common.clj`; `bb spec` — 1285 examples, 0 failures; focused mutation spec — 17 examples, 0 failures.
- agent: focused six scenarios against `:dev-local` — 6 examples, 0 failures; `bb features features/config/set_unset.feature` — 24 examples, 0 failures.
- agent: `bb spec` — 1794 examples, 0 failures.

Full `bb ci` was attempted in both repositories after the focused green runs. It is red only in unrelated existing integration scenarios: foundation's `cli/modules_pins.feature` resolves an obsolete `verify-2/isaac-foundation-rxun/fixture-agent` git fixture (4 failures); agent's `session/parallel_tool_batches.feature:79` cancellation assertion flaked (1 failure). Neither failure overlaps the config CLI implementation or acceptance feature.

Branches: foundation `bean/isaac-gs4a @ 399b679c42071cff9f0a6000b8425659792ea29a` (base `origin/main@eaea445b268545311fb5fc9292b3872a000dc83c`); agent `bean/isaac-gs4a @ 5c7ec2d` (base `origin/main@89af4b7`).



## Verify fail (attempt 2, 2026-09-25): foundation landed as squash commit b7f1d00fc6ed4748468d849befdae86911b0f914, but the agent branch still pins pre-landing branch commit 399b679c42071cff9f0a6000b8425659792ea29a in deps.edn and bb.edn. Repin all foundation dependencies to the landed main SHA, re-run the complete acceptance gate, then return for verification.


## Planner adjustment (2026-09-25, prowl@isaac-plan) — pin the landed foundation sha

Verify fail 2 is a pin, not a behavior change. Foundation landed as squash `b7f1d00fc6ed4748468d849befdae86911b0f914`. The agent branch still pins the pre-squash branch commit `399b679c42071cff9f0a6000b8425659792ea29a`. That sha is not on foundation main, so the acceptance cannot be verified or landed.

### Worker now

1. Confirm `b7f1d00fc6ed4748468d849befdae86911b0f914` is on isaac-foundation `origin/main` and contains the unset-member colon fix (one leading `:` keywordizes to `:hail/send`, not `::hail/send`).
2. On `bean/isaac-gs4a`, repin every foundation dependency in both `deps.edn` and `bb.edn` from `399b679` to `b7f1d00fc6ed4748468d849befdae86911b0f914`. No other pin moves.
3. Re-run the acceptance gate against that pin, not `:dev-local`:
   ```
   bb features features/config/set_unset.feature:84 features/config/set_unset.feature:101 features/config/set_unset.feature:114 features/config/set_unset.feature:127 features/config/set_unset.feature:138 features/config/set_unset.feature:152
   bb features features/config/set_unset.feature
   ```
4. Land agent only after that pin is the one the gate ran against. Record `main-sha` for both repos.
5. `cli/modules_pins.feature` (foundation, obsolete fixture path) and `session/parallel_tool_batches.feature:79` are not this bean. If they fail on main too, say so and do not absorb them.

Acceptance is otherwise unchanged. Do not re-cut the scenarios.

This note resets the verify-fail counter.


## Landed on main (2026-09-25)

main-sha: isaac-foundation b7f1d00fc6ed4748468d849befdae86911b0f914
main-sha: isaac-agent 07f0f3b2acc6db05c98d7e69c6ff33ed316842df

## Verification repair evidence (2026-09-25)

Confirmed foundation `origin/main` is `b7f1d00fc6ed4748468d849befdae86911b0f914` and its `member-keyword` strips exactly one leading colon before keywordizing (`:hail/send` becomes `:hail/send`, not `::hail/send`). Agent `main` is `07f0f3b2acc6db05c98d7e69c6ff33ed316842df`; all 13 `deps.edn` and 5 `bb.edn` foundation pins name that landed SHA, with no `399b679` pin remaining. The post-land runs used the SHA-pinned foundation (not `:dev-local`): focused six acceptance scenarios passed (6 examples, 0 failures, 24 assertions) and full `features/config/set_unset.feature` passed (24 examples, 0 failures, 74 assertions).

Independent-main check: foundation `features/cli/modules_pins.feature` still fails 4/6 due to obsolete unavailable fixture path `/Users/zane/agents/isaac/verify-2/isaac-foundation-rxun/fixture-agent`; this is outside this bean. Agent `features/session/parallel_tool_batches.feature:79` passed on agent main (1 example, 0 failures, 4 assertions), so no persistent main failure was reproduced.
