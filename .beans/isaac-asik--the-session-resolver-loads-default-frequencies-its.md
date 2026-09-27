---
# isaac-asik
title: The session resolver loads default frequencies itself
status: draft
type: bug
priority: high
created_at: 2026-09-27T02:25:46Z
updated_at: 2026-09-27T02:25:46Z
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

## Acceptance to write

- A blank `acp`, with `:defaults :frequencies {:crew "cordelia"}` and an existing cordelia session, resumes that session. It does not exit 1, and it does not open a second session.
- A blank `acp` with no configured frequencies and no flags exits 1 with "no session selected".
- `acp --crew ketch` resumes ketch's most recent session when one exists, and creates one when none does. The episodes policy is not asked for a default session.
- `acp --session mooring` uses mooring even when the configured default crew is someone else.
- A configured `:defaults :frequencies {:create :always}` is what a blank connect uses. The built-in `:if-missing` does not override it. An explicit `--create never` still wins.

Repo: **isaac-agent** for the resolver and `build-frequencies`. **isaac-acp** for dropping the policy `default-session` fork. The other two-argument callers get the config layer by the resolver reading the snapshot, without each of them passing a config map.
