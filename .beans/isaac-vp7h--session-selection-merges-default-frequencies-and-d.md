---
# isaac-vp7h
title: Session selection merges default frequencies and drops prompt-default
status: in-progress
type: bug
priority: high
created_at: 2026-09-27T01:00:25Z
updated_at: 2026-09-27T01:18:45Z
---

Repo: **isaac-agent**, plus every turn consumer that builds a frequencies map (prompt, acp, discord, gchat, hooks, cron, hail).

## Where prompt-default came from

isaac-4e4b (2026-06-26) unified session selection and left one case open: what a turn targets when no filter is given. The note said keep the named session `prompt-default` as the predictable default, and settle it later. It was never settled. `resolve-session-targets` and `build-frequencies` still hardcode `:default-session-key "prompt-default"`. `isaac prompt` with no session name takes that path, so the session id is the literal string `prompt-default`.

`:defaults :frequencies` is already the config for that choice (`isaac.config.defaults/frequencies-template`). Nothing merges it in. On Yopp the map is `{:crew :yopp}`, which would have selected a yopp session. The prompt command used it only as the crew, and kept `prompt-default` as the session. `prompt_cli/resolve-target` also special-cases an explicit `--crew` into `policy/default-session` and skips the frequencies resolver.

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
