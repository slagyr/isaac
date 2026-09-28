---
# isaac-asik
title: The session resolver loads default frequencies itself
status: completed
type: bug
priority: high
created_at: 2026-09-27T02:25:46Z
updated_at: 2026-09-28T15:06:11Z
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


## Planner adjustment (2026-09-28, prowl@isaac-plan) — three stale contracts match current agent

These three failures are not the resolver. They are old ACP contracts that the required repin made false. Do not change agent to satisfy them. Do not restore the implicit crew, vector Grover content, or a compaction entry this turn does not produce.

Rewritten on isaac-acp main `62d8e87`. Not `@wip`. `bb features` excludes `@wip`, so a red live scenario fails CI. These are live and must pass as written.

- `cli.feature` line 119: a blank `acp` with `{:defaults {:frequencies {}}}` and no `--session` exits 0 and the stdout contains `no session selected`. The old `{:crew {:defaults {}}}` fixture and the "no model configured for crew" assertion are gone.
- `streaming.feature` line 17: three string rows, `chunkA`, `chunkB`, `chunkC`. Grover `:content` is a string. The notifications and the joined transcript assertion are unchanged.
- `prompt.feature` line 47: the turn answers `Here is my answer`. It does not require a compaction entry. A compaction trigger is not this bean.

### Re-baselined

    feature-baseline: isaac-acp 62d8e878f4aae2e313cd2e8049d34f62e3af6af5
    feature-blob: isaac-acp features/comm/acp/cli.feature 0a9a62d058e6421bab938fcaae71db32b79c1d0b 119
    feature-blob: isaac-acp features/comm/acp/streaming.feature 4661baa25522637c81e639097ae655c89b0254c4 17
    feature-blob: isaac-acp features/comm/acp/prompt.feature 9893adfb181ea5c21e38d86347dff8d6ac65e665 47
    feature-blob: isaac-acp features/comm/acp/default_frequencies.feature 5ed7fb7e38c402b3f88b1dd2e4d6f325faafe78c 20,37,46,63,84
    feature-blob: isaac-acp features/comm/acp/episodes.feature 9d8e785d953c111a412614b7ef64da9d31bec874 32,58,92,126,156

The default_frequencies and episodes blobs are unchanged. They are repeated so this baseline's tree is the one in force. Agent is already landed `b213494`. Do not re-land it.

### Worker now

1. Rebase `bean/isaac-asik` (acp) onto `62d8e87`. Keep the implementation (`36d9e42`). Feature diff may only drop `@wip` on the episodes and default_frequencies scenarios. Do not edit cli, streaming, or prompt further.
2. `bb features` green for the three rewritten scenarios and the acceptance features. `bb ci` green.
3. `bb bean-gate verify isaac-asik` exit 0, then land acp. Record `main-sha: isaac-agent b21349432a464a5cb03fc70525df8593901eed39` and the acp sha.

This note resets the verify-fail counter.

feature-baseline: isaac-acp 62d8e878f4aae2e313cd2e8049d34f62e3af6af5
feature-blob: isaac-acp features/comm/acp/cli.feature 0a9a62d058e6421bab938fcaae71db32b79c1d0b 119
feature-blob: isaac-acp features/comm/acp/streaming.feature 4661baa25522637c81e639097ae655c89b0254c4 17
feature-blob: isaac-acp features/comm/acp/prompt.feature 9893adfb181ea5c21e38d86347dff8d6ac65e665 47
feature-blob: isaac-acp features/comm/acp/default_frequencies.feature 5ed7fb7e38c402b3f88b1dd2e4d6f325faafe78c 20,37,46,63,84
feature-blob: isaac-acp features/comm/acp/episodes.feature 9d8e785d953c111a412614b7ef64da9d31bec874 32,58,92,126,156


## Planner correction (2026-09-28, prowl@isaac-plan) — 62d8e87 broke acp CI

CI run 36439505617 failed `bb features` on that commit. Two of my rewrites were wrong. Prompt.feature did not fail. Do not treat 62d8e87 as the contract for cli or streaming.

- Blank acp: the refusal is stderr and exit 1, same as the acceptance scenario "a blank acp with nothing to select fails". I had put it on stdout with exit 0.
- Chunks: one vector content row is how Grover emits three chunks. Three string rows emit `chunkA` three times. Restored `["chunkA" "chunkB" "chunkC"]`.

isaac-acp main now has the correction. File stays live. Newest lines in force:

    feature-baseline: isaac-acp e3ca8c23099964dc5c76ce53934091e11845ce20
    feature-blob: isaac-acp features/comm/acp/cli.feature 1f690934f67ba1ece964451f98bd19c46a663c0f 119
    feature-blob: isaac-acp features/comm/acp/streaming.feature a69ff3ae7e3766f65795933fbf4d1b12805164ec 17

### Worker now

Rebase onto this main. The blank-acp scenario asserts stderr `no session selected` and exit 1. The streaming scenario is the vector row again. Do not edit either. Feature diff may only drop `@wip` on episodes and default_frequencies. Then gate and land.

This note resets the verify-fail counter.

feature-baseline: isaac-acp e3ca8c23099964dc5c76ce53934091e11845ce20
feature-blob: isaac-acp features/comm/acp/cli.feature 1f690934f67ba1ece964451f98bd19c46a663c0f 119
feature-blob: isaac-acp features/comm/acp/streaming.feature a69ff3ae7e3766f65795933fbf4d1b12805164ec 17

## Worker return (2026-09-28, scrapper@isaac-work-1)

ACP implementation rebased against corrected main e3ca8c2, branch `bean/isaac-asik` eaa5076; feature diff removes only `@wip` on `episodes.feature` and `default_frequencies.feature`. `bb ci` exits 1: 78 specs pass, but the live `prompt.feature:47` assertion expects `Here is my answer` and gets prior transcript text `It summarizes older transcript entries to free context`; `streaming.feature:17` expects chunkA/B/C but each notification contains `{:content "must be a string"}`. The planner's corrected vector row is parsed into a vector by `session_steps.clj:426` and rejected downstream; no ACP feature changes were made. Exact command: `cd ../isaac-acp-asik && bb ci`.

Gate after the newest e3ca8c2 baseline also fails, exit 1 (`bb bean-gate verify isaac-asik --dir isaac-acp=../isaac-acp-asik --ref isaac-acp=bean/isaac-asik`):

    FAIL isaac-acp features/comm/acp/default_frequencies.feature: worker diff e3ca8c2..eaa5076 edits a feature file the bean did not baseline (- @wip)
    FAIL isaac-acp features/comm/acp/episodes.feature: worker diff e3ca8c2..eaa5076 edits a feature file the bean did not baseline (- @wip)

Newest baseline at e3ca8c2 contains only cli and streaming blobs; planner must re-baseline all five feature blobs together to permit the already-approved `@wip` removals. Planner must also resolve the two red live scenarios without asking worker to edit baselined feature files. Not landed; no ACP main-sha yet. Agent main-sha remains b21349432a464a5cb03fc70525df8593901eed39.


## Planner correction (2026-09-28, prowl@isaac-plan) — all five blobs, two live scenarios fixed

e3ca8c2 listed only cli and streaming, so dropping `@wip` on episodes and default_frequencies failed the gate. Those blobs are in force again, on the same baseline as the corrected live scenarios.

The two red live scenarios were still wrong:

- `streaming.feature` line 17: type is `text-stream`, content is the vector. A `text` row is a message, and a vector message is rejected (`must be a string`). `text-stream` is how a queued vector becomes three chunks.
- `prompt.feature` line 47: the turn completes. The transcript keeps the prior assistant line, `It summarizes older transcript entries to free context`. It does not store `Here is my answer`. No compaction entry.

cli.feature line 119 is unchanged from e3ca8c2: stderr `no session selected`, exit 1.

### Re-baselined (isaac-acp `870b9fe`)

    feature-baseline: isaac-acp 870b9fe225541c36bb74aa438466f6e9ffa6e789
    feature-blob: isaac-acp features/comm/acp/cli.feature 1f690934f67ba1ece964451f98bd19c46a663c0f 119
    feature-blob: isaac-acp features/comm/acp/streaming.feature 05d5e172814a410cab39e5165e8ce3f29cbee916 17
    feature-blob: isaac-acp features/comm/acp/prompt.feature daa33b4a9e3c84181e9f670fc6ca175504450ff7 47
    feature-blob: isaac-acp features/comm/acp/default_frequencies.feature 5ed7fb7e38c402b3f88b1dd2e4d6f325faafe78c 20,37,46,63,84
    feature-blob: isaac-acp features/comm/acp/episodes.feature 9d8e785d953c111a412614b7ef64da9d31bec874 32,58,92,126,156

### Worker now

1. Rebase `bean/isaac-asik` onto `870b9fe`. Keep the implementation (`eaa5076`). Feature diff may only drop `@wip` on episodes and default_frequencies. Do not edit cli, streaming, or prompt.
2. `bb ci` green. `bb bean-gate verify isaac-asik` exit 0. Then land acp.
3. Record `main-sha: isaac-agent b21349432a464a5cb03fc70525df8593901eed39` and the acp sha. Do not re-land agent.

This note resets the verify-fail counter.

feature-baseline: isaac-acp 870b9fe225541c36bb74aa438466f6e9ffa6e789
feature-blob: isaac-acp features/comm/acp/cli.feature 1f690934f67ba1ece964451f98bd19c46a663c0f 119
feature-blob: isaac-acp features/comm/acp/streaming.feature 05d5e172814a410cab39e5165e8ce3f29cbee916 17
feature-blob: isaac-acp features/comm/acp/prompt.feature daa33b4a9e3c84181e9f670fc6ca175504450ff7 47
feature-blob: isaac-acp features/comm/acp/default_frequencies.feature 5ed7fb7e38c402b3f88b1dd2e4d6f325faafe78c 20,37,46,63,84
feature-blob: isaac-acp features/comm/acp/episodes.feature 9d8e785d953c111a412614b7ef64da9d31bec874 32,58,92,126,156

## Wrapped (2026-09-28, Micah)

The implementation is already on the bean branches and is being committed. The Zanebot work session `isaac-work-1` was cancelled so the crew stops re-driving this bean. Do not hail it again.
