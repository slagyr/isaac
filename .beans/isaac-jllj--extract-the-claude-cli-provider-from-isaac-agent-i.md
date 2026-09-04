---
# isaac-jllj
title: Extract the claude-cli provider from isaac-agent into the isaac-claude-code module (pure move)
status: in-progress
type: task
priority: high
tags:
    - claude-cli
    - module
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-04T20:48:39Z
parent: isaac-tuk1
---

PURE MOVE. Byte-identical behavior. No improvements, no renames beyond the namespace move, no scenario edits beyond path moves. If anything needs changing to make it work, stop and hail plan.

## What moves (isaac-agent → isaac-claude-code, repo https://github.com/slagyr/isaac-claude-code, empty)
- `src/isaac/llm/api/claude_cli.clj` → same namespace `isaac.llm.api.claude-cli` in the module (keeps every existing require/model config working).
- `features/llm/api/claude_cli.feature`, `spec/isaac/llm/claude_cli_spec.clj`, `spec/isaac/llm/claude_cli_real_spec.clj`, `spec/isaac/llm/claude_cli_steps.clj`.
- Manifest contributions, moved verbatim from resources/isaac-manifest.edn into the module manifest: llm-api `:claude-cli {:factory isaac.llm.api.claude-cli/make}` (line ~105); provider-template `:claude {:template {:api "claude-cli" :auth "none" :command "claude" :stream-supports-tool-calls false}}` (~169); provider schema keys `:stream-non-tool-turns`, `:command`, `:extra-args` (~277-286) — via the module's extra-schema mechanism (see isaac-imessage's :extra-schema and isaac-m4bi for how modules contribute schema).
- The `(= provider "claude") "claude-cli"` default in `src/isaac/llm/api/protocol.clj:153` and its spec in `spec/isaac/llm/providers_spec.clj:74-76`: keep the core behavior identical — either leave the mapping in core (it is a string default, harmless without the module) or move it to the module's provider-template; do NOT change the observable default. Prefer leaving it.

## Module skeleton
Copy isaac-imessage's layout (deps.edn with :dev-local + :spec aliases, bb.edn tasks spec/jvm-spec/features/jvm-features/ci/smoke, AGENTS.md, README, LICENSE). Module id `:isaac.llm.claude`, manifest at `src/isaac-manifest.edn`, version 0.1.0, `:factory isaac.module.protocol/module`. Pin isaac-agent at the SHA that removes the in-tree copy (see train rule).

## Train rule (same as scuttlebutt)
The agent release that deletes the in-tree provider and the module's first release must be pinned together in isaac/modules.edn (add `:isaac.llm.claude` to the registry) and upgraded on zanebot in one train, or every crew on :claude-opus / :claude-cli (ratchet, main, prowl, scrapper's fallback) loses its provider. Note both SHAs on this bean. Zanebot config needs no change (`providers/claude.edn` is `{:type :claude}`, resolved via the template).

## Acceptance
- isaac-claude-code: `bb features` (claude_cli.feature green, unchanged rows) and `bb spec` green; the @real spec stays gated as it is today.
- isaac-agent: `bb features` and `bb spec` green with the four files gone and the three manifest entries removed; `grep -r claude-cli src` shows only the protocol.clj default (if left).
- `isaac modules list` on a root with both pinned shows :isaac.llm.claude ok and `isaac config validate` accepts a `{:type :claude}` provider.
- Smoke after the train: `isaac prompt --crew main --session train-pong "Reply with exactly: pong"` on zanebot (main is on :claude-opus).


## Work observations (2026-09-03, scrapper)

Conflict detected before implementation handoff:

- The bean says the move must be PURE / byte-identical and says to stop if anything else must change.
- The bean also says the `:claude` provider-template manifest entry should move out of `isaac-agent`, while separately saying the existing core behavior at `src/isaac/llm/api/protocol.clj:153` / `spec/isaac/llm/providers_spec.clj:74-79` must remain observable and preferably stay in core.
- On current `isaac-agent` main, `spec/isaac/llm/providers_spec.clj` directly asserts `(sut/template "claude")` returns `{:api "claude-cli" :auth "none" :command "claude" :stream-supports-tool-calls false}` with no module index. Removing the core provider-template entry makes that spec/behavior fail unless we either (a) keep the `:claude` template in core, or (b) change the spec / behavior to require module loading.
- Option (a) violates the stated acceptance text `the three manifest entries removed`; option (b) violates the PURE MOVE / no-behavior-change rule.

Planner decision needed: either keep the built-in `:claude` provider-template (and any needed provider schema keys) in `isaac-agent` for this extraction bean, or explicitly authorize the behavior/spec change away from `(sut/template "claude")` in core.


## Planner adjustment (2026-09-03, prowl@isaac-plan) — preserve the core `:claude` contract; this bean is option A

Decision: **preserve core behavior/spec.** This bean stays a **PURE MOVE** and does **not** authorize changing `(sut/template "claude")` in core or making it depend on module loading. Therefore choose **A**:

- keep the built-in `:claude` provider-template in `isaac-agent`
- keep the related provider schema keys in `isaac-agent` for this extraction bean (`:stream-non-tool-turns`, `:command`, `:extra-args`, and any schema needed for the existing `:claude` config surface to validate exactly as it does today)
- keep the `(= provider "claude") "claude-cli"` resolve-api default in core, as already preferred above

What moves in **isaac-jllj** is the implementation and its direct tests/specs, plus the module-side registration needed so the train still provides the `claude-cli` API when the module is installed. What does **not** move in this bean is the core `:claude` template contract.

### Acceptance adjustment (supersedes the contradictory removal text above)

For `isaac-agent`, remove the in-tree implementation files named in "What moves" and remove the in-tree `llm-api` factory contribution for `:claude-cli`, **but do not remove** the built-in `:claude` provider-template or the related provider schema keys from core in this bean.

Agent acceptance is therefore:

- `bb features` and `bb spec` green with the four claude-cli files gone from `isaac-agent`
- `resources/isaac-manifest.edn` no longer contributes the in-tree `llm-api` factory for `:claude-cli`
- `resources/isaac-manifest.edn` **still** contributes the built-in `:claude` provider-template and the current provider schema keys
- `spec/isaac/llm/providers_spec.clj` continues to pass unchanged for `(sut/template "claude")`
- `grep -r claude-cli src` may still show the protocol default and any module/bootstrap references needed for the pure move train; that is not a failure

### Out of scope for this bean

Changing core so `:claude` exists only when the module is loaded is a behavioral change. If we ever want that, it is a separate follow-up bean after the extraction train is stable. Do not fold it into this bean.

## Work observations (2026-09-04, scrapper)

- Continued after the planner's option-A adjustment.
- `isaac-agent-jllj` rebased onto current `origin/main`; branch head is now `3faafb5` and was pushed to `origin/bean/isaac-jllj`.
- Agent-side change remains scoped to the pure move adjustment: the in-tree `:claude-cli` `llm-api` factory contribution was removed, while the built-in `:claude` provider-template and related provider schema keys were left intact.
- `isaac-claude-code` local branch `bean/isaac-jllj` now has root commit `597c818` with the moved implementation/tests/features plus module manifest registration for `:claude-cli`.
- Local module gates are green:
  - `bb features features/llm/api/claude_cli.feature` → `14 examples, 0 failures, 48 assertions`
  - `bb spec` → `19 examples, 0 failures, 40 assertions, 3 pending` (`@real` remains gated)
  - `bb features && bb spec` also passed locally after wiring explicit feature step namespaces/root+fs harness support in `deps.edn` and making module `bb features` invoke `clojure -M:features` directly instead of the 60s wrapper.
- Blocking issues found before verify handoff:
  - Push to `git@github.com:slagyr/isaac-claude-code.git` is denied for the current identity (`slagyr-assistant`), so the module branch cannot be published for verify/landing.
  - Full `isaac-agent-jllj` acceptance is not yet closed: `bb spec` is green (`1595 examples, 0 failures, 3275 assertions`), but full feature runs hit the repo's 180s timeout, and direct `clojure -M:features` on the rebased branch reported 5 failures in non-claude scenarios (`module/api_extension.feature`, `bridge/cancel_aborts_work.feature`). This needs a human call on whether the branch must absorb more upstream drift or whether the repo baseline is currently unstable.

## Held (awaiting human, 2026-09-04)

Escalated to human by **scrapper**@isaac-work-2. Blocking: cannot publish the `isaac-claude-code` branch with current GitHub credentials, and full `isaac-agent` feature acceptance is not yet conclusively green on the rebased branch.
Resumes only on explicit human action (re-hail the work/plan band, or re-promote). No crew re-picks this until then.



## Planner ruling (2026-09-04 20:30Z, plan@micah) — hold released
1. **Module branch published.** `bean/isaac-jllj` @ `597c818` (your ~/Projects/isaac-claude-code checkout) is now on git@github.com:slagyr/isaac-claude-code.git — pushed by the planner. The slagyr-assistant invitation is pending Micah; until it is accepted, hand further module commits to plan (note the checkout path) rather than blocking.
2. **The agent-side reds are a message-threshold artifact, not a regression.** `isaac.schema.registered-in` (foundation registered_in.clj:114-117) says `must be one of [...]` when a berth has ≤5 contributions and `must be a registered contribution to <berth>` when more. Core llm-api drops from 6 to 5 with claude-cli gone, so `features/config/cli.feature:179`, `features/module/api_extension.feature:56` and `:70` flip form. Verified: green on main 668f157, red on the rebased branch, and `config validate` shows the two messages side by side. (provider_extension.feature:99 was the branch being 8 commits behind main — it passes after rebase.)

## Exceptions (authorized)
- Rebase onto current `origin/main` (≥ 668f157; the branch is 8 behind — 0.1.44 and later).
- In those three scenarios, change the expected `value` cell from the literal berth message to a regex that matches either form, e.g. `#"must be (a registered contribution to :isaac\.agent/llm-api|one of)"` (and the cli.feature stderr pattern row likewise). Nothing else in acceptance files changes. Foundation follow-up to make the message stable is filed separately.
- Full agent gate: `clojure -M:features` unwrapped (the 180s wrapper lies) must exit 0; `bb spec` green.
Then re-tag unverified with: agent branch SHA, module branch SHA (597c818 or later), and the line that the train pins both together.


## Work observations (2026-09-04, scrapper@isaac-work-2) — hold released, re-handoff

Planner ruling applied:

- Rebased `isaac-agent` `bean/isaac-jllj` onto current `origin/main` (`ee990a9`).
- Authorized threshold-message cells updated in the three named scenarios, plus the same llm-api unknown-api cell in `features/module/provider_extension.feature:99` (identical `:isaac.agent/llm-api` registered-in flip; same regex shape). Gherkin tables cannot carry a `|` alternation, so the cell is `must be (a registered contribution to :isaac\.agent/llm-api)?(one of)?`.
- Did not change any other acceptance rows.

Evidence:
- isaac-agent `clojure -M:features` → `749 examples, 0 failures, 1987 assertions`
- isaac-agent `bb spec` → `1609 examples, 0 failures, 3306 assertions`
- isaac-claude-code `bb spec` → `19 examples, 0 failures, 40 assertions, 3 pending` (`@real` remains gated)
- isaac-claude-code `bb features features/llm/api/claude_cli.feature` → `14 examples, 0 failures, 48 assertions`

SHAs for verify / train pin:
- agent branch: `bean/isaac-jllj` @ `0004c1871f9a98c742da35597026a178e1ceb762` (base `origin/main@ee990a9c3c0e14cd0884196084613993c69d1624`)
- module branch: `bean/isaac-jllj` @ `597c81807097467c4cb2eeb7b7623bbb48653f6a`
- Train must pin both together in `isaac/modules.edn` (`:isaac.llm.claude` / isaac-claude-code) with the agent SHA that deletes the in-tree `:claude-cli` factory.

## Verify fail (attempt 1, 2026-09-04): train-pin / landing acceptance is still incomplete for the new module

Acceptance is not fully satisfied for `isaac-jllj`.

Evidence:
- `isaac-agent` `origin/bean/isaac-jllj` `0004c1871f9a98c742da35597026a178e1ceb762`: `clojure -M:features` → `749 examples, 0 failures, 1987 assertions`; `bb spec` → `1609 examples, 0 failures, 3306 assertions`.
- `isaac-claude-code` `origin/bean/isaac-jllj` `597c81807097467c4cb2eeb7b7623bbb48653f6a`: `bb features features/llm/api/claude_cli.feature` → `14 examples, 0 failures, 48 assertions`; `bb spec` → `19 examples, 0 failures, 40 assertions, 3 pending`.
- The moved implementation, feature file, and two spec files are byte-identical to `isaac-agent` `origin/main@ee990a9c3c0e14cd0884196084613993c69d1624`; `spec/isaac/llm/claude_cli_steps.clj` is the only moved file that changed, adding module declaration so the moved feature/spec can run from the module repo.
- Remaining acceptance gap: top-level `isaac/modules.edn` still has no `:isaac.llm.claude` / `isaac-claude-code` pin, so the bean's required `isaac modules list` / `isaac config validate` integration acceptance on a root with both pinned cannot yet be verified.
- Updated verify requires a passing bean to be landed on `main` first. `isaac-claude-code` currently exposes only `origin/bean/isaac-jllj` and no `origin/main`, so there is no main branch to land and record with `main-sha:`.
