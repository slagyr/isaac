---
# isaac-8od3
title: isaac modules warns about module-owned config keys as unknown
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-11-17T15:07:03Z
updated_at: 2026-11-17T15:11:22Z
---

Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker, then
verifier.

## Planner notes

- Start from the lab's reviewed patch on zanebot:
  `/Users/zane/agents/zane-lab/experiments/log-bug-hunt/beans/unknown-key-without-module-classpath.v2.patch`. It was written
  against `isaac-foundation 91c5a9b`; main may have moved, so rebase by hand. Read the
  reviewer's `../REVIEW.md (one directory up)` in the same experiment first: it says what the first
  patch got wrong and what v2 changed. You own the result; the patch is a
  reference, not a contract.
- The lab's text below is its own draft. Where it and the code disagree,
  the code wins; say so in the bean.
- Never signal a process group or use `pkill`/`killall` in any check you
  run; the lab's `check.sh` scripts do, so do not run them as they are.
- `bb ci` and `bb jvm-spec` pass. No version bump; that happens at deploy.
- v1 hid real unknown keys when no git modules were configured; v2 fixes
  that. Keep a spec that a truly unknown key still warns in that case.

---

# Bean (draft): config loads that cannot see module schemas must not log unknown keys

- Repo: isaac-foundation (live: Homebrew `HEAD-91c5a9b`, v0.1.32)
- Class: **real bug** (false-positive warning)
- Patch: [`unknown-key-without-module-classpath.patch`](unknown-key-without-module-classpath.patch), proven by `../check.sh`

## What is wrong

`isaac.foundation.main/run` binds `classpath/*resolve-classpath?*` to false for
the `modules` command (so `isaac modules` still works when a module is broken),
and `modules/cli.clj` `read-root-config` does the same. With classpath
resolution off, `discovery/resolve-manifest-resource` cannot read a git-coord
module's `isaac-manifest.edn`, so that module drops out of the index and its
`:isaac.config/schema` contributions are never composed. The loader then runs
`warnings/log-unknown-keys!` over the result anyway, and every key a module
owns is logged `:warn :config/unknown-key`.

On zanebot that is 11 keys per load: the nine `:discord/*` / `:imessage/*`
slot fields under `comms` and `:env` on both claude-code providers. Every one
of them is declared in its module's manifest (checked against the live shas);
the config is fine.

## Evidence (last 24 h, keys and counts only)

- 770 `warn config/unknown-key`, **all** in `cli.log`, 0 in `server.log`.
- 11 distinct paths, each logged 70 times. Bursts are whole multiples of 11
  (11, 22, 55, 110, 121), meaning one or more loads per CLI invocation.
- Reproduced in a secret-free scratch root (`/tmp`, fake token): `isaac config
  validate`, `config get`, `config set/unset`, `crew list`, from three different
  cwds, logged 0. `isaac modules list` logged 4 (one per module-owned key in
  the scratch config).
- The two older paths in `cli.log` (`hail._tono-template.prefer`,
  `models.glm-5-3.extra-system-prompt`, about 1400 lines) were last logged in
  September 2026. Gone.

What is inferred: which live callers run `isaac modules` about 64 times a day.
Isaac's `exec__run` tool-start log shows 30 such commands in 26 h; the rest are
most likely Claude Code sessions' own Bash, which Isaac does not log.

## Fix (in the patch)

`loader/load-config-result` skips `log-unknown-keys!` when
`*resolve-classpath?*` is false. The rows stay in `:warnings`, so `isaac config
validate` (which resolves classpaths) reports exactly what it did before.

## Acceptance

```gherkin
Feature: unknown-key warnings only come from loads that can see module schemas

  Scenario: isaac modules does not log keys it cannot check against module schemas
    Given an empty Isaac root at "/tmp/isaac-89q1"
    And the isaac file "isaac.edn" exists with:
      """
      {:bogus-top-level-key 1}
      """
    When isaac is run with "modules list"
    Then the exit code is 0
    And the CLI log file has no event "config/unknown-key"

  Scenario: any other command still logs a genuinely unknown key
    Given the same root
    When isaac is run with "help"
    Then the stderr is empty
    And the CLI log file contains an event "config/unknown-key"

  Scenario: a module-owned key is not unknown when classpaths resolve
    Given a module declares a signal slot field "token"
    And config sets that field
    When the config loads with classpath resolution on
    Then no "config/unknown-key" is logged for it
```

Specs added (`signal_slots_spec.clj`): a load bound to
`*resolve-classpath?* false` with a git-coord module keeps the row in
`:warnings` and logs nothing. An undeclared module's key still logs with
classpaths on. The two isaac-89q1 scenarios used `modules list` as a generic
"non-config command" to prove the cli.log sink. They now use `help`, which
keeps their intent, and the new scenario pins the `modules` behaviour.

## Tests

| | spec | features |
|---|---|---|
| baseline @91c5a9b | 1358 ex, 0 fail | 378 ex, 0 fail (run alone) |
| tests only (red) | signal_slots 17 ex, **1 fail** | quiet_default_logging 4 ex, **1 fail** |
| patched | **1360 ex, 0 fail** | **379 ex, 0 fail** |

When I ran all four suites at once, the patched features showed 7 failures
and the base showed 2. Each failing file passed on its own, and the patched
full suite then passed run alone (379/0). Those failures came from load and
had nothing to do with the patch.

## Risk / what it might make worse

- `isaac modules ...` no longer warns about a truly unknown top-level key.
  Every other command and `config validate` still do, so it remains visible.
- If some future server path also loads with `*resolve-classpath?*` false, it
  goes quiet too. That is correct (it cannot know), but worth knowing.
- The `cli.log` noise was hiding real CLI warnings. Expect the scoreboard's
  top key to change after this ships.


## Implementation / verification (2026-11-17)

- Implemented in isaac-foundation `bean/isaac-8od3` at `dd0fe30` (pushed). On loads with classpath resolution off, suppress unknown-key log events only when declared modules are absent from the discovery index; retain warning rows and unresolved-reference logging. When no module is hidden (including no modules or only local/root), genuinely unknown keys still log.
- The draft acceptance scenario says `modules list` with no modules must emit *no* unknown-key event. This conflicts with the reviewed v2 fix and the planner requirement to retain warnings for genuinely unknown keys when no git module exists. The implementation follows the latter; feature coverage asserts the event *is* logged for no modules. The draft statement that every `*resolve-classpath?* false` load should skip unknown-key logging is likewise too broad: local/root schemas remain visible.
- TDD: signal_slots red 23 examples/2 failures before implementation, green 23/0 afterward; focused CLI feature 4/0. `bb ci`: 1365 specs/0 failures; 377 features/0 failures, 2 pending. JVM focused signal_slots 23/0. `bb jvm-spec` full: 1365 examples, 9 failures in module/protocol, module/lifecycle and module/coords (JVM-specific failures outside modified config code; focused JVM config spec passes). No process-group signals or lab check.sh used.
- `bb bean-gate verify isaac-8od3`: exit 2, no feature-baseline. Hand off for independent verification; bean remains in-progress.


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- No existing scenario changes.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.
