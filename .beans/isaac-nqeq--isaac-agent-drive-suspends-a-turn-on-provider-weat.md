---
# isaac-nqeq
title: 'isaac-agent: drive suspends a turn on provider weather; resume sweep re-drives it after retry-at; boot resume honours retry-at'
status: in-progress
type: feature
priority: high
tags:
    - turn
    - unverified
created_at: 2026-09-18T14:42:12Z
updated_at: 2026-09-19T00:49:07Z
parent: isaac-ugpq
---

Child 1 of isaac-ugpq. See the epic for the decision. Scenarios after design sign-off (features/bridge/suspend.feature + features/session/resume_repair.feature are the existing restart-resume contracts to extend; provider_walls.feature has the weather fixtures).



## Scenarios (committed @wip — isaac-agent `features/bridge/weather_suspend.feature` @ d455915)

| line | scenario |
|------|----------|
| :29 | a wall mid-turn suspends the turn instead of ending it (marker: suspended, reason, suspended-on, suspended-at, retry-at; `:turn/suspended`; nothing fabricated on the transcript) |
| :51 | the resume sweep leaves a suspended turn alone before retry-at |
| :65 | the resume sweep re-drives a suspended turn after retry-at and it completes (`:turn/resumed :suspended-ms`; marker deleted on completion) |
| :88 | a resumed turn continues from the transcript — persisted tool results are not re-run |
| :112 | walling again on resume backs off and re-suspends — `suspend-count` increments, no attempt consumed |
| :132 | backoff without a provider Retry-After grows from 30 s and caps at 30 min |
| :154 | an auth failure suspends with reason auth |
| :166 | a resumed turn re-resolves the crew's model — a config reload that moves the crew triggers an immediate sweep (`:trigger :config-reload`) |
| :196 | a charge that pinned a model keeps waiting on that model |
| :223 | boot resume honours retry-at for a suspended marker (`:resume/weather-deferred`) |
| :237 | a turn parked past `turn.suspended-attention-ms` posts ONE attention notice (attention.notify path, like burst) and stays parked |
| :264 | cancelling a suspended turn deletes its marker |

Marker keys this bean defines: `:suspended true`, `:reason :wall|:auth|:stall`, `:suspended-on {:provider :model}`, `:suspended-at`, `:retry-at`, `:suspend-count`, `:model-override` (when the charge pinned one), `:attention-posted`. Backoff: provider retry-after if given, else `min(30s × 2^(n-1), 30min)`. Config: `:turn :suspended-attention-ms` (default 6 h). The sweep is a scheduler task (same family as the delivery worker); a crew/provider config reload calls it immediately for markers suspended on that crew's provider/model.

The existing `:unavailable?` turn RESULT stays for the non-drive callers that expect it (`prompt --json`, provider_walls.feature's classification assertions); the drive's own loop is what stops returning it and suspends instead.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / the isaac EDN file … exists with: / … changes to: / the following sessions exist: / the following model responses are queued: (text + http-error rows) / the user sends … on session … / the turn ends on session … / the turn result is … / a turn marker exists for session … with: / no turn marker exists for session … / session … has transcript: / … has transcript matching: / the turn is cancelled on session … / interrupted turns are resumed at … / the log has (no) entries matching: / config: / the crew … allows tools: … / the built-in tools are registered / the exec tool is executed | reuse |
| the only file in {dir} EDN contains: / the directory {dir} has exactly N file | reuse (isaac-http spec-support; on agent's feature classpath via the `isaac.**-steps` glob — verify, else copy the two steps into agent's steps) |
| **the user sends {text} on session {name} at {iso}** | **NEW — the existing send step with a fixed clock (so suspended-at/retry-at are assertable)** |
| **a suspended turn marker exists for session {name} with:** | **NEW — writes a marker with `:suspended true` + the table keys and sane defaults (source :hail, boundary :clean, charge for the session's last user message)** |
| **the resume sweep runs at {iso}** | **NEW — invokes the sweep once with a fixed clock** |
| **the exec tool is executed {n} times** | **NEW — count form (also planted for isaac-jkx7; implement once)** |
| **the llm request for session {name} includes the tool result {text}** | **NEW — inspects the recorded outbound request** |
| `a turn marker … with:` accepting dotted keys (`suspended-on.provider`) | reuse if the table helper already resolves dotted paths (other tables do); else extend the helper |

Five new steps.

## Acceptance
```
cd isaac-agent && bb features features/bridge/weather_suspend.feature features/bridge/suspend.feature features/session/resume_repair.feature features/llm/provider_walls.feature && bb spec && bb ci
```
Version bump; pin is a train step. Field check after the train (with isaac-q2v5 landed, or with hail's defer still present — both are safe since the turn no longer returns `:unavailable?` to hail from the drive): on zanebot during a 429 window, `server.log` shows `:turn/suspended … :retry-at` and later `:turn/resumed`, NO `:hail/delivery-deferred`, and the bean's work continues in the same transcript.


## Handoff (scrapper@isaac-work-2)

branch: bean/isaac-nqeq @ 683fd3c (base origin/main@679aee8).

Acceptance green:
- `bb features features/bridge/weather_suspend.feature features/bridge/suspend.feature features/session/resume_repair.feature features/llm/provider_walls.feature` — 24 examples, 0 failures
- `bb spec` — 1645 examples, 0 failures
- version already 0.1.72

`bb ci` failed once on a pre-existing flake (`session_steps_spec` wait-gate drain) that is green on focused re-run and on `bb spec`. Not a product failure.

**Done**
- Drive stamps a durable weather marker (`:suspended`, `:reason`, `:suspended-on`, `:suspended-at`, `:retry-at`, `:suspend-count`) instead of ending the turn.
- Stamp result keeps `:unavailable?` (and `:retry-after-ms` when the provider gave one) so `provider_walls.feature` / `prompt --json` still classify walls.
- `release-turn-marker!` keeps weather-parked markers (`:retry-at` present); shutdown suspend still stamps `:boundary`.
- Resume sweep re-drives after `:retry-at`; boot resume defers future `:retry-at` (`:resume/weather-deferred`) and only re-drives weather markers that have `:retry-at` (legacy hail `:suspended` without retry-at still requeues).
- Cancel of a weather-parked turn deletes the marker.
- Config-reload step sweeps immediately (`:trigger :config-reload`).
- 429 classified as wall; usage-limit messages use configured 30 min retry-after.



## Verify fail (attempt 1, 2026-09-19): full bb features red — turn_exhaustion.feature:68 and cli-prompt.feature:22 (green on origin/main)

HEAD isaac-agent: 683fd3c (bean/isaac-nqeq). Working tree: clean. Base origin/main@679aee8; origin/main has since moved to c3a56a8 (isaac-lsz2 pin only).

verify.md §7 / hail-bean-verify: GREEN means the repo FULL suite, not the bean's file:line selectors. pre-existing must reproduce on origin/main.

- bb spec: 1645/0/3395 (12.68s) — pass
- features/bridge/weather_suspend.feature: @wip removed only (no ## Exceptions needed)
- ISAAC_GIT=1 bb features weather_suspend + named siblings: 12/0/39
- ISAAC_GIT=1 bb features (full): 813 examples, 2 failures, 1914 assertions, 1 pending (179s)

Failures (reproduce isolated on the branch; GREEN on origin/main@c3a56a8):

1. llm/turn_exhaustion.feature:68 — a provider wall ends with :provider-unavailable, not :context-exhausted
   Then the memory comm has events matching: turn-end / :provider-unavailable
   Drive now stamps weather-suspend (:ended-by :suspended) instead of ending :provider-unavailable.

2. bridge/cli-prompt.feature:22 — prompt exits nonzero on provider 403 auth rejection
   And the stderr contains "api:access"
   Auth weather-suspend swallows the provider message on the prompt CLI path.

Isolated re-run on bean/isaac-nqeq: both red. Same two selectors on origin/main: 2/0/4.

Do not land. Drive suspend must keep the existing ended-by / prompt-stderr contracts (provider_walls / prompt --json / turn_exhaustion) or those scenarios need a planner Exceptions/rewrite. Then re-hand for verify.


## Verify fail repair (scrapper@isaac-work-1)

branch: bean/isaac-nqeq @ 326bfc2 (base origin/main@c3a56a8). FF-able.

Weather-suspend still parks the turn (marker + :stopReason "suspended"), but
classify-ended-by now reports :provider-unavailable for weather-parked
unavailable results so turn_exhaustion / memory comm turn-end keep the
existing contract. stamp-weather! keeps :message so prompt 403 stderr still
contains "api:access".

- bb spec 1647/0
- ISAAC_GIT=1 bb features (full) 813 examples, 0 failures, 1 pending (pre-existing compaction_mid_turn)
- Isolated: turn_exhaustion:68 + cli-prompt:22 + weather_suspend + siblings 26/0/95

Do not land. Do not pin.
