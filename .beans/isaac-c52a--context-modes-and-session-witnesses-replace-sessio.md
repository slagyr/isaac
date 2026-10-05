---
# isaac-c52a
title: Context modes and session observers replace SessionPolicy
status: in-progress
type: feature
priority: normal
created_at: 2026-10-05T01:58:10Z
updated_at: 2026-10-05T02:56:01Z
---

Micah, 2026-10-04 design session. Replaces SessionPolicy with two narrow berths.

## Why
`SessionPolicy` is a 27-method wrapper over the session store. The chronicle policy is 100% pass-through (`prepare-turn!` returns nil). The episodes policy passes 20 of 26 methods straight through; its real behavior is a handful of hooks that split into two jobs:
- **context**: what the model sees (cold open, compaction splice)
- **observation**: watching the record (episode open, scene feeding, turn markers)

The session store is the record for every session and becomes the only session API.

## Shape
1. **Context-mode berth** `:isaac.agent/context-mode`. Agent registers `:full` and `:reset` as built-ins. Turn prep asks the registered mode for the turn's context; the hard-coded `(= :reset context-mode)` branches in `drive/turn.clj` go away. Resolution stays as today: turn (`:with-context-mode`) → session → crew → defaults.
2. **Session-observer berth** `:isaac.agent/session-observer` (session observers). Crew config `:observers [<id> …]`, overridable per session, never per turn (observers are stateful). Events: session opened, message appended, compaction spliced, turn started, turn ended. Delivery is off the turn path: a per-session ordered queue on its own thread, so each observer sees a session's events in order and sessions run in parallel. A observer failure logs and raises an attention; it never blocks or fails a turn. Observeres observe only; writing into context belongs to the context mode.
3. **Catch-up wait**: a context mode may wait at turn prep until a named observer has drained that session's queue (generic: "observer W caught up for session S").
4. **Generic `:requires` check**: a context-mode registration may declare `:requires {:observers #{…}}`. Config validation reports a crew (or session) whose context mode requires a observer it doesn't have. Agent knows no module names; the data comes from the registry.
5. **Remove SessionPolicy**: the protocol, the chronicle policy, the `:isaac.agent/session-policy` berth, `check-session-policy`, the session-policy-mismatch refusal in bridge/prompt_cli, and the `:session-policy` stamp on sessions.

## Acceptance (scenarios TBD, gated)
- A crew with `:context-mode :reset` gets reset behavior through the berth (existing reset scenarios stay green).
- A fixture context mode contributed by a test module is selectable by crew, session and `--with-context-mode`.
- A fixture observer receives open/append/compaction/turn-start/turn-end events for a session, in order.
- A observer that throws raises an attention and the turn still completes normally.
- A slow observer does not delay the turn's reply.
- Config validation reports a context mode whose required observer the crew lacks (message names both).
- Session-level `:observers` overrides the crew's.
- One-time (bean criteria, not scenarios): no `SessionPolicy`, `session-policy` or `chronicle` policy references remain in isaac-agent src.
- Handbook (agent) documents both berths and the `:requires` check.
- `bb ci` and `bb jvm-spec` green.

Likely repo scope: isaac-agent. Lands together with its follow-up (episodes port), which must re-pin to this.

## Decision + Acceptance (Micah signed off 2026-10-04; gated)
Name: session observer (berth `:isaac.agent/session-observer`, config `:observers`), not "witness". Scenarios are config-driven: the lantern fixture module (`modules/isaac.session.lantern`) contributes the `logbook` observer (writes `lantern/logbook.edn` as `{:events [...]}`; `:lantern {:logbook {:fail true}}` makes it throw) and the `porthole` context mode (soul + last assistant reply + current message; `:requires {:observers #{:logbook}}`). Its old session-policy contribution goes away.
- The 6 @wip scenarios in isaac-agent `features/session/session_observers.feature` and the 7 in `features/session/context_mode_berth.feature` pass with @wip removed.
- Test harness drains observer queues inside its existing turn await (no new steps).
- Unit spec: an observer that blocks does not delay the turn's reply.
- One-time: no `SessionPolicy`, `session-policy`, `register-factory!` policy or chronicle-policy references remain in isaac-agent src/spec; the `logbook` policy fixture and its steps are deleted.
- Retired ahead of this bean (agent c38d19d): session_policy.feature, policy_mismatch.feature, the two lantern session-policy validate scenarios (config/cli.feature), the enum "Unknown :context-mode" scenario (context_mode.feature), and the "through the session's crew policy" scenarios in resume_repair.feature and bridge/commands.feature.
- Handbook (agent) documents both berths and the `:requires` check.
- `bb ci` and `bb jvm-spec` green.

feature-baseline: isaac-agent c526ccbfde0e742c716b862943036352abab7020
feature-blob: isaac-agent features/session/session_observers.feature 67a9d4b2dc4c5f60ce50a707327bc7f30b232405
feature-blob: isaac-agent features/session/context_mode_berth.feature 899c855d0321d316fd36dcb0df7f41386e6e53f5


## Work checkpoint (2026-10-05)

Done: context-mode and session-observer registries started; SessionStore direct-call migration in progress. `bb spec` green (1878 examples). Next: fix 12 failing acceptance scenarios (13 examples) and remove remaining policy fixtures/references. Resume at src/isaac/agent/bridge/core.clj:323 (session observer selection is lost before charge/build); inspect /tmp/c52a-features.out. Last test: `bb features features/session/context_mode_berth.feature features/session/session_observers.feature` RED — 12 failures. Implementation edits remain uncommitted while red.


## Acceptance contract conflict (2026-10-05)

Both baselined feature Backgrounds use `modules.isaac.session.lantern` as a dotted table path. The shared config step parser splits dotted paths into segments (foundation `config_steps.clj:158-162`), so this writes `:modules {:isaac {:session {:lantern {:local/root ...}}}}`, not the required `:modules {:isaac.session.lantern {:local/root ...}}`. Confirmed by running `bb features features/session/context_mode_berth.feature:37`: `C52A DEBUG {:modules {:isaac {:session {:lantern #:local{:root "modules/isaac.session.lantern"}}}} ...}`; dispatch error is `unknown context mode :porthole`. The module never discovers/registers. All 13 baselined scenarios inherit this broken Background. The contracted feature cannot be edited by worker except @wip removal; simulating a module in test helpers would violate acceptance. Planner must change both Background path cells to the supported literal JSON-pointer form `/modules/isaac.session.lantern` (or another verified literal-key syntax) and re-baseline before this bean can pass. Latest run: `bb features features/session/context_mode_berth.feature:37` RED (1 failure); `bb spec` previously green (1878 examples). Resume after planner return at features/session/context_mode_berth.feature:20 and features/session/session_observers.feature:21. Branch bean/isaac-c52a has a green checkpoint pushed (92cba1e); subsequent red implementation is uncommitted and retained in the worktree.

feature-baseline: isaac-agent ec7707226548ffd1f0de9e29187cd9e65883a0c3
feature-blob: isaac-agent features/session/session_observers.feature b0ab5a9c7b921fb5fef65f9b2c59da108be343b6
feature-blob: isaac-agent features/session/context_mode_berth.feature 483225e58dd6cdc53e479535881eef99c573d341

## Planner adjustment (2026-10-05, prowl@isaac-plan)

The module id is one key. Both backgrounds now write `/modules/isaac.session.lantern`, the JSON pointer form, so the step does not split on the dots. The local/root value is unchanged. `@wip` stays.

On isaac-agent main `ec77072`. Observers blob `b0ab5a9c`. Context-mode blob `483225e5`. Both files are line-less: every `@wip` scenario in them is this bean's.

Rebase onto `ec77072`. Drop `@wip` only. Do not edit frozen scenario text. The pointer form is what the read path already accepts; the write path must honor the same form, or the cell still nests. If the write path ignores a leading slash, say so and stop — do not invent another key syntax.


## Acceptance contract conflict (2026-10-05, planner return)

The revised `/modules/isaac.session.lantern` cell is not supported by the actual write step. `isaac-foundation/spec-support/src/isaac/foundation/fs_steps.clj:274-277` (`isaac-value-path`) maps every path through `(str/split path #"\\.")` and `keyword`, without a leading-slash branch. It therefore stores the leading slash as part of `:/modules/isaac` (and splits the remaining dots), rather than writing `:modules {:isaac.session.lantern ...}`. `isaac-foundation/spec/isaac/config/config_steps.clj:145-150` handles JSON pointer only for the *read* path. Per planner instruction, stopped without inventing another syntax or editing frozen scenarios. The previously red implementation remains uncommitted on `bean/isaac-c52a` worktree; green branch checkpoint is 92cba1e. Planner needs a corrected write-path acceptance contract or authorization to fix the foundation shared step (cross-repo change) before work can resume.

## Held (awaiting human, 2026-10-05)

Escalated to human by **prowl**@isaac-plan. Blocking: a module id with dots cannot be written by the shared EDN step. `isaac-value-path` splits every path on dots and has no JSON-pointer branch, so `/modules/isaac.session.lantern` still nests. The read path already honors a leading slash. Worker stopped as instructed (note 77fc0d0a). No further key syntax invented. Resumes only on explicit human action: authorize a foundation writer change, or change the fixture so the id has no dots.

## CI note (2026-10-05, prowl@isaac-plan)

isaac-agent CI `37255935709` on planner commit `ec77072` failed `features/session/waiting.feature:20` (isaac-xoqn): the step that sends "two" on session "dm" without waiting timed out after 30s. Specs 1882/0. Features 847/1. That commit only rewrote the two c52a background cells. The waiting failure is not this bean's contract and not this bean's code. No repair commissioned. The bean stays held for the dotted module-id writer.

feature-baseline: isaac-agent 6e4cac938cb3d734e0abed0d37fb02c61a579b86
feature-blob: isaac-agent features/session/session_observers.feature 8c954a8731ae6e5855ffc0e3e32922b9cb5b98fc
feature-blob: isaac-agent features/session/context_mode_berth.feature 07353ff67a9e186a3a0e1fd1dfbd15e6c6034157

## Planner note (2026-10-04)
Module-id write fixed in agent 6e4cac9: Background writes `modules` as one EDN map value (`{:isaac.session.lantern {:local/root …}}`) — no dotted path, no pointer. Supersedes ec77072. Re-baselined; resume on the existing `bean/isaac-c52a` branch. The waiting.feature:20 CI timeout is unrelated to this bean.

## Acceptance contract conflict (2026-10-05, work-2 after 6e4cac9)

Rebased existing branch onto agent 6e4cac9; removed only @wip. Ran `bb features features/session/context_mode_berth.feature features/session/session_observers.feature`: 13 examples, 6 failures. Two current assertions contradict the fixture/shape, independent of the module-id fix:

- `features/session/context_mode_berth.feature:54`: porthole explicitly contains soul + last assistant reply + current message. The fixture `isaac.session.lantern/porthole` returns `[previous current]`; prompt builder places soul at `messages[0]`, reply at `messages[1]`, current at `messages[2]` when the prior assistant exists. However the scenario 'a session's context mode overrides the crew's' runs with a session-level :context-mode, and got `messages[2].content: Expected "Check the oil", got: nil`. This requires diagnosis whether the session override is lost or the prompt factory only produced one message.
- `features/session/context_mode_berth.feature:75`: scenario 'a turn fails when its session lacks the observer its context mode requires' asserts exactly **2 transcript entries** after a failed turn. Background already appends two messages to an opened session, and the store counts its required session header as an entry: **3 entries before any turn**. The actual is 3, consistent with refusal before appending. No implementation can preserve both the required header+two background messages and the assertion 2. Planner must change this count (likely 3) and re-baseline; worker cannot edit frozen scenario.

Other current failures (implementation still incomplete): `session_observers.feature:34` missing session-opened, `:62` session override prompt exits 1, `:89` missing turn-started, `:107` failing observer turn result nil. A partially wired observer factory and logbook fixture remain uncommitted on bean/isaac-c52a; no green run has occurred after resumed edits. Resume at `src/isaac/agent/bridge/core.clj:324` and `src/isaac/agent/session/session_observer.clj:23`. The green checkpoint on the rebased branch remains; do not land until planner adjusts the count.

feature-baseline: isaac-agent 49aaf24058a4df201bed36ba53d32b120b010fc1
feature-blob: isaac-agent features/session/session_observers.feature 8c954a8731ae6e5855ffc0e3e32922b9cb5b98fc
feature-blob: isaac-agent features/session/context_mode_berth.feature 0495c4c096fc71b115f8bb818e6888ff13036423

## Planner adjustment (2026-10-05, prowl@isaac-plan, count)

The refused turn keeps what the background seeded. `context_mode_berth.feature` line 79 now expects 3 transcript entries, not 2: the session header plus the two background messages. A refusal appends nothing. `@wip` stays.

On isaac-agent main `49aaf24`. Context-mode blob `0495c4c0`. Observers blob `8c954a87`, unchanged. Both files stay line-less.

The other six failures stay implementation work. Rebase onto `49aaf24`. Drop `@wip` only. Do not edit frozen scenario text.

## Planner note (2026-10-05, Micah decision)
`sessions list`: the POLICY column becomes CONTEXT, showing the session's resolved context mode (session override, else crew). When you remove the `:session-policy` read in `session/cli.clj`, render context mode there. The gated scenario for it lives in isaac-ka10 (agent `features/session/cli.feature`); doing it here is welcome but not required for this bean.
