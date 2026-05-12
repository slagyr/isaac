---
# isaac-qa65
title: "Discord-created sessions have wrong origin, chatType, and cwd"
status: completed
type: bug
priority: normal
created_at: 2026-05-01T16:44:44Z
updated_at: 2026-05-05T22:40:14Z
---

## Description

## Symptom

Sessions created by the Discord adapter (in src/isaac/comm/discord.clj
`create-session!`) have three incorrect fields:

```clojure
(storage/create-session! state-dir session-name
                         {:channel  \"discord\"
                          :chatType \"direct\"
                          :crew     crew-id
                          :cwd      state-dir})
```

## Issue 1: :origin is not set

Other adapters set :origin so downstream code can identify how the
session was created:
  - cli/prompt.clj      → `{:kind :cli}`
  - server/hooks.clj    → `{:kind :webhook :name name}`
  - acp/server.clj      → `{:kind :acp}`
  - cron/scheduler.clj  → `{:kind :cron :name job-name}`

Discord adapter sets nothing, so storage/save fills in the default
`{:kind :cli}` (see session/storage.clj:357 `(update :origin #(or % {:kind :cli}))`),
which is wrong — Discord sessions look like CLI sessions in tooling
that branches on origin.

Fix: set `:origin {:kind :discord :channel-id <id> :guild-id <id>}`
(or similar) at create time.

## Issue 2: :chatType is hardcoded to \"direct\"

The trusted-metadata builder in the same file (build-trusted-block,
discord.clj:103) correctly derives `(if guild-id \"guild\" \"direct\")`.
But create-session! always passes \"direct\", so guild messages get
filed as direct chats. Two sources of truth, one already wrong.

Fix: derive chatType from the payload the same way the trusted block
does, and pass it in.

## Issue 3: :cwd is the state-dir

The state directory (e.g. ~/.isaac) is not a working directory in any
meaningful sense — it's where Isaac stores sessions/auth/config.
Other comm channels either pass a user-provided cwd or omit. For
Discord there's no natural working directory, so this should default
to something reasonable (user home, or omitted entirely) rather than
state-dir.

Fix: omit :cwd, or pick a sane default (probably user home).

## Verification

- A new turn created by Discord MESSAGE_CREATE should yield a session
  with:
    :origin   {:kind :discord ...}
    :chatType \"guild\" when guild_id present, \"direct\" otherwise
    :cwd      not equal to state-dir (omitted or user home)
- Existing CLI / ACP / cron / webhook session creation untouched.

