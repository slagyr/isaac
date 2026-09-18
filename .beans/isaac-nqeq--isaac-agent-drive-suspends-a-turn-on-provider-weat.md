---
# isaac-nqeq
title: 'isaac-agent: drive suspends a turn on provider weather; resume sweep re-drives it after retry-at; boot resume honours retry-at'
status: in-progress
type: feature
priority: high
tags:
    - turn
created_at: 2026-09-18T14:42:12Z
updated_at: 2026-09-18T16:27:28Z
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
