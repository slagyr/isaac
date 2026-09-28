---
# isaac-asik
title: The session resolver loads default frequencies itself
status: in-progress
type: bug
priority: high
created_at: 2026-09-27T02:25:46Z
updated_at: 2026-09-28T14:32:52Z
---

Follow-up to isaac-vp7h, which is deployed. A blank `acp` on Yopp exits 1 with "no session selected". The launcher papered over it with `--crew yopp`, and that opens a new session on every connect because the episodes policy answers no default session.

## Decision (2026-09-27, Micah)

The agent pulls the config. A consumer does not hand the config in, and a consumer does not invent frequencies it was not given.

`resolve-session-targets` stacks three layers, bottom to top:

1. Built-in defaults: `:reach :one`, `:prefer :recent`, `:create :if-missing`.
2. `:defaults :frequencies` from the config snapshot. The resolver reads that snapshot itself.
3. Only the frequencies the consumer was actually given. A blank connect contributes nothing. `--crew` and `--session` are extra frequencies and win.

`:reach` and `:create` are not part of what ACP, prompt, or any other consumer writes into the map just because the call was made. Those keys belong in layer 1. A configured reach or create in layer 2 outranks them. A flag the operator passed is layer 3 and wins.

## Where the code diverges

isaac-vp7h added the config as an optional third argument and left the two-argument call passing `nil`. `isaac prompt` was switched to the three-argument call. ACP, Discord, Google Chat, hooks, and cron still use the two-argument call, so a blank connect never sees `:defaults :frequencies {:crew :yopp}`.

`build-frequencies` also writes `:reach` and `:create` into every consumer map, so those keys sit above the configuration even when the config is passed in.

ACP then has a second fork. `resolve-attach-target` throws away the resolver result when `--crew` is set and no session id is set, and asks the crew policy for `default-session`. Episodes returns none, so every `acp --crew yopp` opens a new session. That fork goes away. The resolver is the only place a crew-only start picks or creates a session.

## Acceptance

`isaac-acp/features/comm/acp/default_frequencies.feature` and the two retargeted scenarios in `features/comm/acp/episodes.feature` (@wip):

- a blank `acp` resumes the session of the crew in `:defaults :frequencies`
- a blank `acp` with no configured crew, session, or tags exits 1 with "no session selected"
- `acp --session` wins over the configured crew
- a configured `:create :always` outranks the built-in `:if-missing`
- an explicit `--create never` outranks a configured `:create :always`
- `acp --crew` on an episodes crew resumes that crew's most recent session
- two `session/new` calls in one connect share the one session the resolver created

The resolver reads the config snapshot on the two-argument call. `build-frequencies` adds `:reach` and `:create` only when the operator passed them. ACP's `resolve-attach-target` fork and the server `session/new` call to `policy/default-session` go away.

```
cd isaac-acp && bb features features/comm/acp/default_frequencies.feature features/comm/acp/episodes.feature && bb ci
```

Repo: **isaac-agent** for the resolver and `build-frequencies`. **isaac-acp** for dropping the policy fork. Discord, Google Chat, hooks, and cron keep calling the two-argument resolver and get the config layer from it.

feature-baseline: isaac-acp b1b31b9b1110a445670988db7cc8588bcb160b55
feature-blob: isaac-acp features/comm/acp/default_frequencies.feature 5ed7fb7e38c402b3f88b1dd2e4d6f325faafe78c 20,37,46,63,84
feature-blob: isaac-acp features/comm/acp/episodes.feature 1310865e4d4caf2a71d9642983876dbe50da7d7d 123,153

## Work checkpoint (2026-09-27)

Done: `isaac-agent` branch `bean/isaac-asik` (cb41080) reads the config snapshot in the two-argument resolver and stops injecting CLI `:reach`/`:create`. Focused `bb spec spec/isaac/session/frequencies_spec.clj spec/isaac/session/frequencies_cli_spec.clj` passes (42 examples). `isaac-acp` branch `bean/isaac-asik` (5a57da5) drops the policy fork and `policy/default-session`, strips only `@wip`; `bb bean-gate verify isaac-asik --dir isaac-agent=../isaac-agent-asik --dir isaac-acp=../isaac-acp-asik` exits 0. JVM run of `default_frequencies.feature` against local agent/foundation passes (5 examples).

Next: Implement the missing second-`session/new` reuse on ACP's create path (start at `isaac-acp/src/isaac/comm/acp/cli.clj:161`; currently only attaches when `target` is existing). Fix the cross-repo test environment to run acceptance: `bb features features/comm/acp/default_frequencies.feature features/comm/acp/episodes.feature` using `bb.edn` pins timed out after 180s on the first feature; `bb jvm-features ...` with temporary `:dev-local` paths to `../isaac-agent-asik`, `../isaac-foundation-asik` and `../isaac-http-ci` ran default frequencies green but episodes fails 5 scenarios (three existing scenarios and two retargeted) due to `ClassCastException` at `isaac.config.resolve/resolve-crew:91` during feature fixture creation (config's `:defaults :crew` string from episodes.feature); cannot edit baselined feature except `@wip`. No temporary deps edits remain. Re-run `bb ci` in both repos and gate before landing; no completion yet.


## Planner unblock (2026-09-28, Micah + planner) — isaac-mfc9 folded in

The 09-27 checkpoint stopped on a contract problem the worker could not fix: `episodes.feature`'s Background wrote the pre-ruom flat `:defaults` shape (`defaults.crew cordelia`, `defaults.model echo`), which the current foundation reads as a crew *template*, so `resolve-crew` threw `ClassCastException`. The root cause is isaac-mfc9 (acp never got the isaac-0r95 defaults migration and still pins pre-ruom foundation/agent/episodes). **isaac-mfc9 is merged into this bean** and scrapped.

- Planner migrated `episodes.feature` on acp main (8c76772): `defaults.crew` → `defaults.frequencies.crew`, `defaults.model` → `defaults.crew.model`; the three scenarios that share that Background (`:32`, `:58`, `:92`) are now `@wip` and belong to this bean with `:126`, `:156`. Rebase `bean/isaac-asik` (acp 5a57da5) onto acp main first.
- Added scope (from isaac-mfc9): replace every `[:defaults :crew]` read in acp (`cli.clj`, `server.clj`) with `isaac.config.defaults/crew-id`; repin foundation, agent (current main, including isaac-ey7a/70cr/i5lv), and episodes to current mains in `deps.edn` AND `bb.edn`; migrate spec fixtures that write the flat `:defaults` shape (`cli_spec.clj`, `server_spec.clj`) to the new shape. `:crew {:defaults …}` in `cli.feature` is a crew entity named defaults' template — leave it unless it fails.
- Still open from the checkpoint: a second `session/new` on the create path reuses the session (`isaac-acp/src/isaac/comm/acp/cli.clj:161`).

## Acceptance (added 2026-09-28)

- [ ] `bb features features/comm/acp/episodes.feature` (acp) — all five scenarios `:32`, `:58`, `:92`, `:126`, `:156`, `@wip` removed
- [ ] `bb features features/comm/acp/default_frequencies.feature` (acp) — unchanged contract
- [ ] One-time check: `git grep -n '\[:defaults :crew\]'` in isaac-acp src finds nothing
- [ ] Both repos `bb ci` green; acp version bump; modules.edn registry repin

feature-baseline: isaac-acp 8c76772099b96a5aed62f113d38ebb64f6880142
feature-blob: isaac-acp features/comm/acp/default_frequencies.feature 5ed7fb7e38c402b3f88b1dd2e4d6f325faafe78c 20,37,46,63,84
feature-blob: isaac-acp features/comm/acp/episodes.feature 9d8e785d953c111a412614b7ef64da9d31bec874 32,58,92,126,156

## Work checkpoint (2026-09-28, scrapper)

Done: rebased agent and acp bean branches onto their current mains; ACP episodes @wip removed, config fixtures migrated, default crew reads use defaults/crew-id, create path session/new reuses the first opened session, and acp pins updated to current main. Focused acceptance (10 examples, 43 assertions) and 78 specs green against local agent/foundation worktrees.

Next: bb ci red (3 unrelated existing features: prompt compaction, CLI no-model, streaming chunk fixture); with pinned bb.edn, focused acceptance red (3 default-frequency cases until agent branch lands). Resume at isaac-acp-asik/features/comm/acp/cli.feature:119 and spec/isaac/comm/acp/acp_steps.clj:250, then rerun bb ci with local bb.edn overrides and gate.

## Contract conflict (2026-09-28)

Gate PASS against isaac-agent b213494 (landed main) and isaac-acp bean branch 36d9e42; focused acceptance 10 examples / 43 assertions green with local agent/foundation. Agent bb ci green (1799 specs / 905 features, 1 pending). ACP bb ci still fails three existing, non-baselined feature scenarios after the required repins: `features/comm/acp/cli.feature:119` uses legacy `{:crew {:defaults {}}}` and expects an implicit crew no-model response (new resolver correctly rejects no selection); `features/comm/acp/streaming.feature:17` expects vector chunks but Grover's current schema rejects vector `:content` ("must be a string"); `features/comm/acp/prompt.feature:47` expects compaction entry when current agent does not compact. Exact command `cd ../isaac-acp-asik && bb ci` exits 1 with 3 feature failures; specs 78/78 pass. No .feature edits beyond @wip are permitted to worker. Planner must determine corrections or explicitly authorize new acceptance for these scenarios; ACP not landed or completed. Agent landed main-sha: isaac-agent b21349432a464a5cb03fc70525df8593901eed39. ACP branch remains in-progress at 36d9e42. Resume at features/comm/acp/cli.feature:119 and spec/isaac/comm/acp/acp_steps.clj:220.
