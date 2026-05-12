---
# isaac-lidv
title: "Thread state-dir from server boundary; delete all re-derivations"
status: completed
type: bug
priority: normal
created_at: 2026-05-01T00:27:52Z
updated_at: 2026-05-01T16:18:48Z
---

## Description

## Symptom

Every Discord-driven turn fails with :auth-missing
\"Missing OpenAI ChatGPT login.\" The auth.json is on disk,
unexpired, and the same provider works fine from \`isaac prompt\`.

## Root cause

Two competing notions of \"where is Isaac's state\" are mixed:

- **home**     = $HOME (e.g. /Users/zane). Used by
  paths/config-root which appends /.isaac/config.
- **state-dir** = home + /.isaac (e.g. /Users/zane/.isaac).
  Used by auth-store/load-tokens, sessions, queues, etc.

cli/server.clj defaults home to (System/getProperty \"user.home\")
and server/app.clj:88 then passes that home directly as
:state-dir to plugin/build-all. The Discord plugin captures it,
turn.clj/augment-provider injects it as :state-dir into provider
config, and openai_compat/resolve-oauth-tokens hands it to
auth-store/load-tokens — which appends /auth.json and looks for
/Users/zane/auth.json. Doesn't exist → nil → :auth-missing.

CLI prompt (cli/prompt.clj:113) gets it right by deriving
state-dir = home + /.isaac at the entry point and threading the
correct value through. The server entry never does that
derivation, and every downstream consumer that needs the real
state-dir patches around it with its own fallback (openai_compat
line 37: \`(or :auth-dir :state-dir (str user.home \"/.isaac\"))\`).

The fallbacks paper over the inconsistency until something —
like Discord-driven oauth lookup — happens to hit a path where
the fallback doesn't fire (because :state-dir is set, just to
the wrong value).

## Fix

Establish state-dir ONCE at each entry point and thread it
through. Delete all re-derivations and all fallbacks.

1. cli/server.clj: derive state-dir = home + \"/.isaac\" once.
   Pass only :state-dir to app/start!.
2. server/app.clj: drop \`home (or (:home opts) (:state-dir opts))\`.
   Take :state-dir as the sole input. Pass it through to
   plugin/build-all, worker/start!, scheduler/start!, http
   handler opts, etc.
3. openai_compat/resolve-oauth-tokens: require :state-dir to be
   present in provider config. Delete the user.home fallback.
4. Audit every other \`(System/getProperty \"user.home\")\` and
   \`(str ... \"/.isaac\")\` site for the same pattern. Likely
   suspects: cron/scheduler, delivery/worker, session/storage,
   config/loader (resolve-workspace already takes :home — leave
   alone if it's truly the user-home concept; rename if confused).
5. Rename CLI flag if helpful: \`--home\` is currently overloaded.
   Either rename to \`--state-dir\` (with home + /.isaac as the
   default) or keep \`--home\` as the user-home concept and add
   \`--state-dir\` separately. Whichever, the *internal* name
   used past the entry point should be :state-dir.

## Definition of done

- Discord message → turn → openai-codex provider → loads tokens
  from auth.json successfully. No :auth-missing for an
  unexpired token.
- Grep for \`(System/getProperty \"user.home\")\` returns hits
  only inside CLI entry points (cli/server.clj, cli/prompt.clj,
  cli/chat.clj, etc.) — never in src/isaac/{auth,llm,comm,
  session,delivery,cron,server}/.
- Grep for \`/.isaac\` returns hits only at CLI entry points and
  in paths/config-root.
- Existing CLI prompt + Discord + cron paths all work.

