---
# isaac-d6pw
title: 'Turn inspection: origin on every turn, turns show, turn_get tool'
status: completed
type: feature
priority: normal
created_at: 2026-09-28T01:52:08Z
updated_at: 2026-09-28T18:45:03Z
parent: isaac-q3u3
blocked_by:
    - isaac-70cr
---

Likely repo: **isaac-agent**. Split out of the Hail handoff design (2026-09-27, Micah + planner): Hail becomes stateless, so the turn is the only thing with an id and a state, and Agent must answer everything `hail_get` used to.

## Contract to plan

- **Origin on every turn request.** The durable request (isaac-70cr TurnStore) carries an opaque `:origin` map supplied by the submitter — for Hail: `:source :hail`, sender (`:from`, `:from-crew`, `:submitter-session`), `:principal`, `:thread-id`, `:reply-to`, `:params`, `:data`. Agent stores and returns it; it never interprets it.
- **`isaac turns show <id>`**: prompt/input, target, origin, state (queued / held / running / terminal), outcome and reason, timestamps.
- **`turn_get` crew tool**: the same record as data. Crews use it where they used `hail_get`.
- Idempotency keys moved to isaac-70cr (2026-09-27). Hail passes a caller-supplied `idempotency-key` (CLI/HTTP/tool) straight through as that submission key.
- **Retention:** finished turn records are kept indefinitely for now (same as Hail records today); pruning is a later decision.

## Scenario plan (to draft)

1. A submitted turn's origin round-trips through `turns show` and `turn_get`.
2. `turns show` reports queued → running → terminal with outcome and reason.
3. An unknown turn id fails clearly.


## Decisions (2026-09-28, Micah + planner) — refine the contract above

Found after isaac-70cr landed: `turns show` exists but prints only id/session/state/outcome (unknown id to stdout); `isaac.turn.submit/submit!` hardcodes `:origin {:kind :foreman}` for every caller; finished records carry no reason or timestamps. Agent tools are `:group/name` (models see `group__name`).

1. **The caller supplies `:origin`** (opaque map). `submit!` stops hardcoding `:foreman`; no origin → `{:kind :submit}`. Foreman passes `{:kind :foreman :machine … :instance …}` (one-line isaac-foreman change in this bean). Plain `prompt` records `{:kind :cli}`. Hail passes its own in isaac-ex4q.
2. **Records gain `:reason`** (error and cancelled outcomes) and `:created-at`, `:started-at`, `:finished-at`.
3. **`turns show <id>` prints the whole record**: id, session/target, input, resource pools, state, outcome, reason, timestamps, merged-into, and each origin field as `origin.<key>: <value>`. Unknown id → stderr `turn not found: <id>`, exit 1.
4. **`turn__get` crew tool** (`:turn/get`), opt-in via `tools.allow`; argument `id`; returns the record as JSON; unknown id → tool error `turn not found: <id>`.
5. **Replaces Hail's inspection surface** (Micah): `hail_get` → `turn__get`; `isaac hail` inspection commands → `turns` commands (enumerated in isaac-ex4q).

## Acceptance

Feature: `isaac-agent/features/turn/turn_inspection.feature` (new, 5 scenarios, `@wip` on main at b22826a). Remove `@wip`; all pass:

- [ ] `bb features features/turn/turn_inspection.feature` — `:18` full show, `:39` error reason, `:53` queued has no start time, `:71` turn__get tool, `:99` unknown id in CLI and tool
- [ ] Unit spec: an arbitrary origin map passed to `submit!` round-trips unchanged through the store and `turns show`.
- [ ] isaac-foreman passes its own origin to `submit!`; its specs/features stay green; repin agent.
- [ ] Existing `turn_store.feature` stays green.
- [ ] `bb verify` green in both repos; version bumps.

feature-baseline: isaac-agent b22826a771b9ac635bdbbad7c4cd44d0049e6047
feature-blob: isaac-agent features/turn/turn_inspection.feature ca26ca32320263b8620edca39566832c39c70bce

## Contract conflict (2026-09-28, scrapper@isaac-work-1)

The baselined `turn_inspection.feature` reintroduces the `stdout matches:` DSL issue resolved for isaac-70cr. `isaac.foundation.cli-steps/stdout-matches` calls `extract-patterns` then `re-find (re-pattern pattern)` on each raw table row; it does not expand `#turn-id`, capture `#"[a-z0-9]+":turn-id`, or interpret `#"\\S+"` as a regex literal. The first scenario's `| #turn-id |` cannot match the generated turn id; timestamp rows such as `created-at: #"\S+"` cannot match an ISO timestamp; error reason row `reason: #".*lamp oil spilled.*"` cannot match the message. The worker cannot change baselined feature text except removing `@wip`. This is the same case as isaac-70cr's planner correction: rewrite these raw regex rows to real regexes / literal expected values (or use the supported capture path), then re-baseline on module main before implementation. No product edits attempted; isaac-agent-d6pw worktree is at origin/main. The isaac beans clone's `bean/isaac-asik` was already ahead 6/behind 3 and its required pull --rebase conflicted on its asik bean file; rebase aborted without modification and claim recorded from clean isaac-kleb main worktree.


## Planner adjustment (2026-09-28, prowl@isaac-plan) — stdout matches is a raw regex

Same ruling as isaac-70cr. Do not build a capture DSL into `Then the stdout matches:`. Each row is a regex. `#"[a-z0-9]+":turn-id` on the `queued:` line is the one capture that already works: the postflight stores `:turn-id`, and a later command interpolates `#turn-id`. A row that is only `#turn-id` matches that interpolated id as a regex. `#"\S+"` and `#".*lamp oil spilled.*"` are not regexes to this step.

Rewritten on isaac-agent main `0e23dbc`. File stays `@wip`.

- Timestamp rows: `created-at: \S+`, `started-at: \S+`, `finished-at: \S+`.
- Reason row: `reason:.*lamp oil spilled`.
- `#turn-id` stays. Do not remove the queued capture line.

### Re-baselined

    feature-baseline: isaac-agent 0e23dbc7e4887a4058cf9c9a401d77c390d3261e
    feature-blob: isaac-agent features/turn/turn_inspection.feature 14b4c33e3a6c7aa874ba43aee2a8bb3f6e2f5416

The file is `@wip`, so the blob names no lines. All five scenarios are this bean's. Drop the file `@wip` only after they pass. Do not land while it is `@wip` on main.

### Worker now

1. Rebase `bean/isaac-d6pw` onto this main. Feature diff may only drop the file `@wip`.
2. Do not put the step-table syntax back into `stdout matches` rows.
3. `bb features features/turn/turn_inspection.feature` green, then `bb bean-gate verify isaac-d6pw` exit 0, then land.

This note resets the verify-fail counter.

feature-baseline: isaac-agent 0e23dbc7e4887a4058cf9c9a401d77c390d3261e
feature-blob: isaac-agent features/turn/turn_inspection.feature 14b4c33e3a6c7aa874ba43aee2a8bb3f6e2f5416

## Implementation conflict (2026-09-28, scrapper@isaac-work-1)

Rebased on 0e23dbc; only feature change is removal of @wip. Five turn_inspection scenarios pass (18 assertions); agent specs pass (1808 examples), Foreman CI passes (42 specs / 16 feature examples). Agent full `bb ci` fails 5 scenarios in `features/tool/permissions.feature`: each exact `the prompt has tools:` table lists every built-in when `defaults.crew.tools.allow :all`, but omits the new `turn__get` tool. Actual tool set correctly includes `turn__get` under `:all` (a real built-in). This is a feature-contract conflict: cannot alter approved feature text as worker and cannot filter a correctly allowed tool to satisfy the stale list. Agent branch bean/isaac-d6pw @ 5b1ba73 (base 0e23dbc); Foreman branch bean/isaac-d6pw @ 8656f19. `bb bean-gate verify isaac-d6pw --dir isaac-agent=../isaac-agent-d6pw` returned 0, but **do not land** until agent `bb ci` green. Planner: update `features/tool/permissions.feature` exact lists to include `turn__get` in the five `:all` cases (on isaac-agent main), then return work hail. Also update baseline as appropriate. Worker will rebase, retest, repin and land after gate.


## Planner adjustment (2026-09-28, prowl@isaac-plan) — :all includes turn__get

`:all` includes every built-in. `turn__get` is one. The five exact lists in `features/tool/permissions.feature` omitted it. That is the contract, not a filter.

The tool is not on main yet, so those five scenarios are `@wip` until it lands. `bb features` excludes `@wip`, so main CI stays green. Verified: the non-wip permissions scenarios are 3/0.

### Re-baselined (isaac-agent main `16cb19c`)

    feature-baseline: isaac-agent 16cb19c12c4fcd2b4f279ed13884d079b67b67a3
    feature-blob: isaac-agent features/turn/turn_inspection.feature 14b4c33e3a6c7aa874ba43aee2a8bb3f6e2f5416
    feature-blob: isaac-agent features/tool/permissions.feature 5d435b7323961287b9a0aade5d17556453266761 18,49,83,120,190

The inspection file is still `@wip`, so that blob names no lines. The permissions blob names the five `@wip` scenario lines. Both are this bean's. Drop `@wip` only after they pass.

### Worker now

1. Rebase `bean/isaac-d6pw` onto this main. Keep the implementation (`5b1ba73`). Feature diff may only drop `@wip`: the inspection file, and the five permissions scenarios.
2. Do not remove `turn__get` from `:all`. Do not edit the lists further.
3. `bb ci` green, `bb bean-gate verify isaac-d6pw` exit 0, then land agent, then repin foreman.

This note resets the verify-fail counter.

feature-baseline: isaac-agent 16cb19c12c4fcd2b4f279ed13884d079b67b67a3
feature-blob: isaac-agent features/turn/turn_inspection.feature 14b4c33e3a6c7aa874ba43aee2a8bb3f6e2f5416
feature-blob: isaac-agent features/tool/permissions.feature 5d435b7323961287b9a0aade5d17556453266761 18,49,83,120,190

## Landed on main (2026-09-28)

Agent `bb ci`: 1808 specs, 915 feature examples, zero failures (one pre-existing pending). Foreman `bb ci`: 42 specs, 16 feature examples, zero failures. Bean gate: exit 0 on agent squash commit.

main-sha: isaac-agent 6736ca27ff3665767bddc5b00b56704f4c1270cd
main-sha: isaac-foreman 09419d62327b0183f6719d09f24a9a1c74e235fd
