---
# isaac-u80t
title: 'isaac-gmail: pull mode — a scheduler tick walks history from the cursor where no Pub/Sub push exists'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-23T19:29:04Z
updated_at: 2026-09-23T22:50:37Z
---

Micah 2026-09-23: on hosts other than Yopp mail is pulled, not pushed (the Google Workspace CLI does the same over the same Gmail API + desktop OAuth). The triage must work for both. Push and pull differ only in the trigger: both walk history from the stored cursor and fetch the new ids.

## Design

- Config `gmail/mode` `:push` (default, today) | `:pull`, and `gmail/pull-interval-ms` (default 60000).
- In `:pull` the module schedules an interval task on the shared scheduler (see `isaac-google/src/isaac/google/component.clj` for the pattern, boot tick included) that calls the existing history walk / `resync!` with the stored cursor — the same path the watch handler takes, so gating, routes and labels are identical. No watch is registered, no Pub/Sub, no door required; login alone suffices.
- First run with no cursor: take `messages.list` newest history id as the cursor and process nothing older (no backfill flood). Log once.
- Errors: a failed tick logs at warn and the next tick retries; never fails the module.
- Two hosts on one inbox: the verdict label check from the routes bean (isaac-sb6d) is what makes this safe; document it in the module README.

## Acceptance (features/comm/gmail/pull.feature)

- [ ] `gmail/mode :pull` → no watch call at boot; a scheduler task `:gmail/pull` exists with the configured interval.
- [ ] A tick with two new INBOX messages since the cursor gates/routes them exactly as a push would (reuse the gmail.feature scenario's expectations) and advances the cursor.
- [ ] A tick with a 500 from history.list logs warn, cursor unchanged, next tick succeeds.
- [ ] No cursor on first tick → cursor set to the newest history id, zero turns.
- [ ] `gmail/mode :push` → no pull task; watch.feature unchanged.
- [ ] Manifest config keys declared, version bump, `bb spec`/`bb features`/`bb lint` green.

Likely repo scope: isaac-gmail (`module.clj`, `watch.clj`, `cursor.clj`, `handler.clj`, manifest). Read-only in isaac-google.

Related: routes bean isaac-sb6d (label idempotency this mode relies on).

feature-baseline: isaac-gmail 63f87c6c160537f1761c8b6179b08308606e68f7
feature-blob: isaac-gmail features/comm/gmail/pull.feature 07cf78c443f1da10bc8221c038f65791802951ab


## Blocked — pull.feature's Background predates isaac-sb6d's route whitelist (2026-09-23)

Implementation is done in `isaac-gmail` worktree `bean/isaac-u80t` (pushed,
commit 6e0c4e1): `isaac.comm.gmail.pull` (mode/pull-interval-ms config,
`tick!`, `start!`/`stop!`), `handler/process-message!` and `handler/resync!`
made public so pull reuses the exact push pipeline, `watch.clj` excludes
`gmail/mode :pull` comms from the registration timer, `module.clj` wires
boot/shutdown through `Module` `on-load`/`on-unload`, manifest declares
`gmail/mode` + `gmail/pull-interval-ms` and bumps to 0.2.1. `bb spec` is
green (99 examples incl. a new `pull_spec.clj`), `bb lint src/` is clean.

`bb features` on `features/comm/gmail/pull.feature` (with `@wip` removed):
3 of 5 scenarios green —
- "pull mode schedules an interval task instead of registering a watch at boot" ✅
- "a first pull tick with no stored cursor seeds it..." ✅
- "push mode (the default) schedules no pull task" ✅

2 fail, both on the same root cause, not an implementation bug:

- "a pull tick with two new INBOX messages gates and routes them like a push"
- "a pull tick that gets a server error from history.list retries on the next tick"

Both fail because the messages never converse — no session/transcript is
created — and that traces to `pull.feature`'s Background carrying **no
`gmail-routes.*` config at all**. `isaac.comm.gmail.routes/decide` (landed
by isaac-sb6d, "routes are the whitelist") returns `:action :unrouted` for
every message when zero routes are configured (confirmed by reading
`routes.clj`: `ordered-routes` is empty, the loop's `(empty? rs)` branch is
unconditional — there is no "converse everything via `gmail/crew` when no
routes exist" fallback any more; that behavior was the "pre-whitelist
default-route" isaac-sb6d's own commit message says it dropped). `:unrouted`
never calls `start-turn!`, so no session/transcript is ever created,
regardless of the pull-mode implementation underneath it.

`features/comm/gmail/gmail.feature` (push, the scenario pull.feature says to
mirror) already carries the needed `gmail-routes.team.order` /
`.match.from` / `.action` lines in its own Background — it was updated for
isaac-sb6d. `pull.feature`'s scenarios were authored in the same planning
session as isaac-sb6d/isaac-3427/isaac-betb (commits 7ba0fb3/63f87c6) but
**before** isaac-sb6d's routes implementation actually landed on `main`
(commits 3f55d06/3130621, later in the log) — its Background was never
updated to match. `features/comm/gmail/triage.feature` (isaac-betb, same
planning session) *does* carry the routes lines; `pull.feature` is the one
that was missed.

This is a worker-side "gate exit 1, contract moved" situation reached
*before* even running `bb bean-gate verify` — I did not force it, and per
the gated-worker rule I cannot edit `pull.feature`'s Background myself
(only `@wip` removal is mine to touch; `feature-baseline`/`feature-blob`
lines are append-only and re-baselining is the planner's job).

**Fix needed (planner):** add to `pull.feature`'s `Background`, matching
`gmail.feature`'s:

    | gmail-routes.team.order      | 90              |
    | gmail-routes.team.match.from | ada@tonotop.com |
    | gmail-routes.team.action     | converse        |

then re-baseline (`bb bean-gate baseline isaac-u80t isaac-gmail:features/comm/gmail/pull.feature`)
and hand back to a worker (or re-dispatch this bean) to finish the gate/land.

Left `in-progress` (not `unverified` — this is the gated flow; a gated
bean only gets `unverified` by mistake). Worktree branch `bean/isaac-u80t`
is pushed to `isaac-gmail` origin with the full implementation for whoever
picks this back up.

feature-baseline: isaac-gmail 03c675bb2ba2b5bd35a72dd31dfdafe901225fd3
feature-blob: isaac-gmail features/comm/gmail/pull.feature 03a3da0ab5a1737ea7b342e8d5aa40afaf303907

## Exceptions

Planner, 2026-09-23: pull.feature's Background gained the `gmail-routes.team` rows (`*@tonotop.com` → converse) so the two message-routing scenarios admit ada under routes-as-whitelist, matching gmail.feature. Baseline re-cut.
