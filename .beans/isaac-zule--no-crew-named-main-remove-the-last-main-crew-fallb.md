---
# isaac-zule
title: 'No crew named main: remove the last "main" crew fallbacks from gchat, gmail, discord, acp and hooks'
status: completed
type: bug
priority: high
created_at: 2026-09-23T21:11:58Z
updated_at: 2026-09-25T00:23:36Z
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

## Landed on main — discord / acp / hooks (planner-verified)

- main-sha: isaac-discord f9f746dc237941486ff36403116e6ff554435c26 (0.1.16) — spec 54/0, features 68/0 (3 pre-existing pending) on the ISAAC_GIT pinned path; dev-local features fail on unmodified main too (local sibling state), not this change.
- main-sha: isaac-acp 38d2ceb4572d5be6ce3a4870d7906bf78fef392b (0.1.14) — spec 76/0, features 64/0.
- main-sha: isaac-hooks c4656ead03b1a0aec9123f1f825a178aad429812 (0.1.5) — spec 32/0, features 20/0. A hook with neither crew nor session now emits frequencies without :crew; hail resolves the default.

Registry repinned for all three. Remaining: isaac-od6i (episodes) blocks completion; zanebot upgrade of discord/acp/hooks is Micah's call.

## Landing sweep (planner, 2026-09-24)

Swept `src` on origin/main of every isaac-* repo for `"main"`:
- **Regression, fixed:** isaac-betb (landed 2026-09-23, after zule's gmail fix) added `(or (:crew (triage-cfg cfg)) "main")` in isaac-gmail triage.clj. main-sha: isaac-gmail 7d99525dd2ec87424421b18d0974bcfa423a4e5e (0.2.4). Triage crew is now gmail/triage :crew, else `defaults/crew-id`. It can't pass nil because no-tools-config needs a concrete crew. Spec 129/0, features 40/0, CI green. Registry repinned (d6a99b7 → 7d99525; the repin also brings in completed 3427, u80t, betb and clba).
- isaac-discord discord.clj:147 is a comment ("No \"main\" …"), so it doesn't count.
- isaac-episodes: 17 sites remain in cli, layout, lifecycle and migrate. That is isaac-od6i, hailed to isaac-work 2026-09-24 (6e913eee).
- Every other repo is clean: acp, agent, claude-code, cli-proxy, cli-server, cron, foreman, foundation, gchat, google, hail, hooks, http, imessage, mcp, server, worksite.

Remaining: isaac-od6i completes → re-run the sweep → close zule. The zanebot upgrade (gchat, gmail, discord, acp, hooks) is Micah's call.

## Final sweep and handoff (planner, 2026-09-25)

- isaac-od6i is completed: isaac-episodes 9965aeb. `git grep '"main"' origin/main -- src` in isaac-episodes returns nothing.
- Re-ran the whole-fleet sweep on origin/main. The only remaining hit is a comment in isaac-discord discord.clj:147. No isaac-* repo has a "main" crew fallback in `src`.
- Registry is repinned (d95404b3) and zanebot is upgraded to it: discord 6df59f7, episodes 9965aeb, agent 5ea0e4c, imessage d7a1447. Service restarted clean.
- zanebot's `:defaults :frequencies :crew` is `:main`, and a crew named main exists there. That is operator config, not a code fallback, so it is outside this bean's rule.

Acceptance: both "done when" conditions are met. Handed off `unverified`.

## Landed on main (2026-09-24) — verified by perceptor@isaac-verify

main-sha: isaac-gchat 5d130980dd773cfe405ec5d746212c2dca5e63ec
main-sha: isaac-gmail 261f65b3c4b3910e74182fa5d171557f13a05a79
main-sha: isaac-gmail 7d99525dd2ec87424421b18d0974bcfa423a4e5e
main-sha: isaac-discord f9f746dc237941486ff36403116e6ff554435c26
main-sha: isaac-acp 38d2ceb4572d5be6ce3a4870d7906bf78fef392b
main-sha: isaac-hooks c4656ead03b1a0aec9123f1f825a178aad429812
main-sha: isaac-episodes 9965aeb (isaac-od6i, completed)

Evidence: every SHA is an ancestor of origin/main. Ran `git grep '"main"' origin/main -- src` across 20 isaac-* repos; the only hit is the comment at discord.clj:147. A `:main` keyword grep is also clean. `bb ci` on origin/main (fresh worktrees): gchat df22945 173/0 + 54/0; gmail 7d99525 129/0 + 40/0; discord 6df59f7 55/0 + jvm 108/0 + features 68/0 (3 pending); acp 6030a97 78/0 + 65/0; hooks d9044bb 32/0 + 20/0; episodes 9965aeb 223/0 + 85/0. All exit 0. The "nothing names a crew → nil / defaults.crew" specs exist in each of the five repos.
