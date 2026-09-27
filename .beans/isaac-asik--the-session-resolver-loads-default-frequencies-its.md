---
# isaac-asik
title: The session resolver loads default frequencies itself
status: in-progress
type: bug
priority: high
created_at: 2026-09-27T02:25:46Z
updated_at: 2026-09-27T02:31:03Z
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
