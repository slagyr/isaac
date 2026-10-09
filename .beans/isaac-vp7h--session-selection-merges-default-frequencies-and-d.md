---
# isaac-vp7h
title: Session selection merges default frequencies and drops prompt-default
status: completed
type: bug
priority: high
created_at: 2026-09-27T01:00:25Z
updated_at: 2026-09-27T01:32:25Z
---

Repo: **isaac-agent**, plus every turn consumer that builds a frequencies map (prompt, acp, discord, gchat, hooks, cron, hail).

## Where prompt-default came from

isaac-4e4b (2026-06-26) unified session selection and left one case open: what a turn targets when no filter is given. The note said keep the named session `prompt-default` as the predictable default, and settle it later. It was never settled. `resolve-session-targets` and `build-frequencies` still hardcode `:default-session-key "prompt-default"`. `isaac prompt` with no session name takes that path, so the session id is the literal string `prompt-default`.

`:defaults :frequencies` is already the config for that choice (`isaac.config.defaults/frequencies-template`). Nothing merges it in. On Skiff the map is `{:crew :skiff}`, which would have selected a skiff session. The prompt command used it only as the crew, and kept `prompt-default` as the session. `prompt_cli/resolve-target` also special-cases an explicit `--crew` into `policy/default-session` and skips the frequencies resolver.

## Decision (2026-09-27, Micah)

`prompt-default` goes away. The prompt command is not a special case. Any turn that identifies a session merges `:defaults :frequencies` underneath the frequencies the consumer supplied. Consumer keys win. One merge, used by every consumer, not a copy in each surface.

A consumer that needs a stable id of its own (a Discord channel's `discord-<id>`, a hook's `hook:<name>`, a gchat space) puts that in its frequencies, and it wins over the defaults.

If the merged map still names no session, crew, or tags, the turn fails. It does not invent a session id.

## Call sites that build frequencies today

- `isaac.session.frequencies/resolve-session-targets` — hardcoded `prompt-default`
- `isaac.session.frequencies-cli/build-frequencies` — same default
- `isaac.bridge.prompt-cli/resolve-target` — `--crew` bypasses the resolver
- `isaac.comm.acp.cli` — `build-frequencies`
- `isaac.comm.discord/channel->frequencies` — `discord-<channel-id>` when the channel names nothing
- `isaac.comm.gchat.handler` — its own `default-session-key`
- `isaac.hooks/build-frequencies-from-hook` — `hook:<name>` when the hook names nothing
- `isaac.cron.service/job->frequencies` — merges `:create :always`, not the config defaults
- `isaac.hail.router` — merges band and hail frequencies, not the config defaults

## Acceptance

The merge lives in the shared resolver, underneath the map the consumer already built. Consumer keys win, so a Discord channel id, a hook name, or a gchat space still wins. `prompt_cli`'s `--crew` bypass of the resolver goes away. `--crew` is a frequency, like any other consumer key. `prompt-default` is not produced.

`features/session/default_frequencies.feature` and the retargeted scenarios in `features/bridge/cli-prompt.feature` and `features/session/origin.feature` (@wip):

- a bare prompt resumes the default crew's existing session and does not create `prompt-default`
- a bare prompt with no existing session creates one for the default crew
- `--session` wins over the default crew
- `--crew` wins over the default crew
- no session, crew, or tags: exit 1, stderr contains `no session`, no session is created
- a created session still gets a cwd and `:origin :cli`

Compaction scenarios in `cli-prompt.feature` address `prompt-default` with `--session`. That name is only a fixture id there. `bb spec` drops the hardcoded default in `frequencies`, `frequencies-cli`, and `prompt_cli`.

```
cd isaac-agent && bb features features/session/default_frequencies.feature features/bridge/cli-prompt.feature features/session/origin.feature && bb ci
```

feature-baseline: isaac-agent ac8404b8a65808f6a3b1cfcaf14e2aafe37915a6
feature-blob: isaac-agent features/session/default_frequencies.feature 9e6e98fecadeab167e0bc16df8a2fff0b617d3ca 11,29,49,69
feature-blob: isaac-agent features/bridge/cli-prompt.feature 33cf96c545e9329f03630abcca4b5b18eb78649b 25,371
feature-blob: isaac-agent features/session/origin.feature fa7d005f452e27fc299334fa38e4dd3186119619 17

## Implementation conflict (2026-09-27)

The shared resolver now merges defaults beneath consumer frequencies; the prompt uses it without the --crew bypass, and the baselined acceptance scenarios pass (39 examples, 0 failures, 97 assertions). The bean branch is `isaac-agent` `bean/isaac-vp7h` at `55089dc`; `bb bean-gate verify isaac-vp7h --dir isaac-agent=../isaac-agent-vp7h` reports PASS. Full `bb ci` has 1800 specs green but two existing *unbaselined* feature scenarios fail in `features/session/session_policy.feature:155,184`:

> `a start the policy has no default for is named by the agent, not the policy`: Expected `default-session` (crew cordelia) before `open-session!`; got `open-session!` first.
> `a conversation start without a session id asks the policy for one`: Expected `default-session` (crew cordelia) before `record-turn-marker!`; got `open-session!` first.

Those scenarios require prompt with `--crew` and no id to ask the policy for its default, even when the resolver's merged crew selector already picks the target. The decision here explicitly removes prompt's --crew bypass and says prompt is not a special case. Reinstating that bypass would violate this bean; editing these .feature files would violate the frozen-contract worker rule. Planner needs to settle policy integration and rebaseline/adjust the conflicting scenarios. No feature wording was changed on the branch (only @wip removed). Work remains in progress; after planner decision, resume at `src/isaac/bridge/prompt_cli.clj:173`, finish consumer integration and `bb ci`, gate and land.


## Planner adjustment (2026-09-27, prowl@isaac-plan) — crew selection does not ask the policy first

Decision stands. Do not reinstate the `--crew` bypass. `prompt --crew` is a frequency. The shared resolver selects or creates the session. `policy/default-session` is not the prompt path.

The two `session_policy.feature` scenarios were the old contract: `--crew` with no id must call `default-session` before `open-session!` / `record-turn-marker!`. That call was the bypass. Rewritten on isaac-agent main `1a6eda8` and marked `@wip`:

- Line 139: "a start the policy has no default for is named by the agent, not the policy (isaac-vp7h)". The agent mints `session-1` and `open-session!` is first. No `default-session` row.
- Line 168: "a conversation start without a session id resumes the crew's existing session (isaac-vp7h)". `lantern-room` is resumed. `record-turn-marker!` is first. No `default-session` row.

Episodes still does not branch in the prompt command. isaac-6yg0's ACP scenario remains the proof that episodes mints a fresh id. Do not reintroduce a mode branch in frequencies or ACP.

### Re-baselined (newest lines in force)

    feature-baseline: isaac-agent 1a6eda8b97af541bf05e6be9dc964c175539a0a9
    feature-blob: isaac-agent features/session/session_policy.feature 9158b60debd94c8d5b52657ba2b2d95dafadb94c 139,168
    feature-blob: isaac-agent features/session/default_frequencies.feature 9e6e98fecadeab167e0bc16df8a2fff0b617d3ca 11,29,49,69
    feature-blob: isaac-agent features/bridge/cli-prompt.feature 33cf96c545e9329f03630abcca4b5b18eb78649b 25,371
    feature-blob: isaac-agent features/session/origin.feature fa7d005f452e27fc299334fa38e4dd3186119619 17

The default_frequencies, cli-prompt, and origin blobs are unchanged from `ac8404b`. They are repeated so this baseline's tree is the one in force.

### Worker now

1. Rebase `bean/isaac-vp7h` onto `1a6eda8`. Keep the implementation (`55089dc`). Feature diff against `1a6eda8` may only drop `@wip` (the two new session_policy lines, plus the already-baselined default_frequencies / cli-prompt / origin lines).
2. Do not call `policy/default-session` from `prompt --crew`. Do not restore `prompt-default`.
3. `bb bean-gate verify isaac-vp7h` exit 0, then land. `bb ci` must be green, including the rewritten session_policy scenarios.

This note resets the verify-fail counter.

feature-baseline: isaac-agent 1a6eda8b97af541bf05e6be9dc964c175539a0a9
feature-blob: isaac-agent features/session/session_policy.feature 9158b60debd94c8d5b52657ba2b2d95dafadb94c 139,168
feature-blob: isaac-agent features/session/default_frequencies.feature 9e6e98fecadeab167e0bc16df8a2fff0b617d3ca 11,29,49,69
feature-blob: isaac-agent features/bridge/cli-prompt.feature 33cf96c545e9329f03630abcca4b5b18eb78649b 25,371
feature-blob: isaac-agent features/session/origin.feature fa7d005f452e27fc299334fa38e4dd3186119619 17


## Landed (2026-09-27)

Rebased bean/isaac-vp7h onto isaac-agent main (1a6eda8, then bd1115f), removed @wip from the two rewritten session_policy scenarios, retained the shared resolver implementation. Feature diff against 1a6eda8 removes only @wip tags. Focused features: 46 examples, 0 failures; bb ci: 1800 specs and 891 features, 0 failures (1 unrelated pending). bb bean-gate verify isaac-vp7h exited 0 (PASS). Landed isaac-agent main at 180b83f; no policy/default-session prompt bypass.

main-sha: isaac-agent 180b83f8e64a50dd4d7d1fd10efecf05657e993a
