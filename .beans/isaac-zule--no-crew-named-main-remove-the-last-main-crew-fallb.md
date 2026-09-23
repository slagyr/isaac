---
# isaac-zule
title: 'No crew named main: remove the last "main" crew fallbacks from gchat, gmail, discord, acp and hooks'
status: in-progress
type: bug
priority: high
created_at: 2026-09-23T21:11:58Z
updated_at: 2026-09-23T21:33:08Z
---

Micah 2026-09-23: "There should be no fallback to any crew named `main`." isaac-bfwn (completed 2026-09-16) removed every production "main" crew identity from **isaac-agent** and made `:defaults :crew` required (`:present?` + `:crew-exists?`), so a config without a default crew fails validation and the charge's last resort is defaults.crew. The comm and surface modules were outside that bean and still carry the fallback; isaac-od6i covers isaac-episodes separately.

## Rule

A module that needs a crew id uses the entity's own crew, else the operator's default crew, else **nil** — and lets the drive resolve nil to defaults.crew (charge.clj already does). Never the string "main". Tests may keep "main" as a fixture *name*.

## Sites (src only)

- isaac-gchat `gate.clj` decide: `… (crew-name (:default-crew opts)) "main")` → drop the "main".
- isaac-gmail `handler.clj` crew: drop the "main".
- isaac-discord `discord.clj` channel-crew-id (~:145): drop the "main".
- isaac-acp `cli.clj` :137, :205; `server.clj` :111 (`:or {crew-id "main"}`), :180, :249, :263 → defaults.crew or nil.
- isaac-hooks `hooks.clj` :179, :210, :274 → defaults.crew or nil.

## Acceptance

- [ ] `git grep -n '"main"' -- src` in each of the five repos shows no crew-identity fallback (session-key strings and fixture comments do not count) — one-time check, not a permanent spec.
- [ ] Each repo's existing "nothing names a crew" spec now expects nil (or the defaults.crew value), not "main".
- [ ] `bb spec` + `bb features` green in each repo; version bumps; registry repin.

Repo scope: isaac-gchat, isaac-gmail, isaac-discord, isaac-acp, isaac-hooks. Planner takes gchat + gmail (fresh from isaac-rfmh); a worker takes discord, acp, hooks.

## Landed so far (planner)

- main-sha: isaac-gchat 5d130980dd773cfe405ec5d746212c2dca5e63ec (0.2.13) — gate/decide returns nil when nothing names a crew; spec 173/0, features 54/0.
- main-sha: isaac-gmail 261f65b3c4b3910e74182fa5d171557f13a05a79 (0.1.9) — handler/crew returns nil; spec 51/0, features 13/0.
- Registry repinned. discord, acp, hooks: Sonnet worker in flight.

## Scope widened — everywhere (Micah, 2026-09-23)

"Let's create a bean to get rid of the main fallback everywhere." This bean is that bean. Sweep every isaac-* repo's `src` for a "main" crew fallback; the 2026-09-23 grep found them only in gchat, gmail (done), discord, acp, hooks (worker in flight) and isaac-episodes (isaac-od6i, now a child of this bean). imessage, hail, cron, http, foreman, worksite, mcp, google, cli-server, cli-proxy, foundation, agent: clean. Re-run the sweep at landing.

**Where the default crew comes from.** Micah: "It's not even default crew anymore. It's defaults frequencies that are used to find the crew." Under isaac-ruom (`:defaults` as entity templates, NOT landed yet — branches only) the default crew key moves from `[:defaults :crew]` to `[:defaults :frequencies :crew]` behind the `isaac.config.defaults` accessor. So:
- Now (pre-ruom): a module reads `[:defaults :crew]` or passes nil and lets the charge resolve it. Never "main".
- When ruom lands: isaac-0r95 moves every downstream reader (episodes, hail, cron, hooks, http, gchat, gmail, discord, acp) to the accessor / `:frequencies` path in the same train as ruom. Prefer passing nil where the drive resolves the crew anyway — that path needs no change when the key moves.

Done when: no isaac-* repo has a "main" crew fallback in `src` (one-time sweep recorded here), and isaac-od6i is completed.


## Handoff — discord/acp/hooks (Sonnet worker)

• main-sha: isaac-discord bean/isaac-zule f9f746dc237941486ff36403116e6ff554435c26 (0.1.16) —
  channel-crew-id drops the "main" literal (channel/discord/defaults.crew, else nil); added
  `#'sut/channel-crew-id` unit specs incl. "is nil when nothing names a crew"; grep clean.
  bb spec 54/0, jvm-spec 107/0 (1 pre-existing unrelated flake: "connects Discord gateway when
  token is added via config hot-reload", reproduces on unmodified main too), bb features 68/0
  (3 pre-existing pending, unrelated).

• main-sha: isaac-acp bean/isaac-zule 38d2ceb4572d5be6ce3a4870d7906bf78fef392b (0.1.14) —
  cli.clj:137,205 and server.clj:111 (`:or {crew-id "main"}` dropped),180,249,263 all drop the
  "main" literal in favor of defaults.crew else nil; added server_spec tests "resolves crew-id
  to nil, never main" for initialize and session/prompt. grep clean. bb spec 76/0, bb features
  64/0. jvm-spec has 5 failures (cli_spec.clj:174,191,232; server_spec.clj "writes one
  session/update notification per streamed text chunk" and "no-model error when default crew
  implicit") — all 5 reproduce identically on the unmodified isaac-acp checkout (confirmed by
  running jvm-spec there first), pre-existing and unrelated to this change.

• main-sha: isaac-hooks bean/isaac-zule c4656ead03b1a0aec9123f1f825a178aad429812 (0.1.5) —
  hooks.clj:179 (build-frequencies-from-hook no longer forces :crew "main" when a hook has no
  crew/session — it now omits :crew entirely), 210 and 274 (ensure-hook-session!/handler) fall
  back to `(get-in cfg [:defaults :crew])` instead of "main". Added specs: "omits :crew when
  nothing names a crew" and "falls back to defaults.crew when the hook names no crew" (asserts
  charge/build receives the configured defaults.crew, not "main"). grep clean. bb spec 32/0,
  jvm-spec 32/0, bb features 20/0.

All three branches pushed to origin, not merged. No "main" crew-identity fallback remains in
any of the three repos' `src` (session-key strings like "discord-<id>" and fixture "main" crew
names in specs/config excluded, per rule).
