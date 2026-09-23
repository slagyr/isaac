---
# isaac-zule
title: 'No crew named main: remove the last "main" crew fallbacks from gchat, gmail, discord, acp and hooks'
status: in-progress
type: bug
priority: high
created_at: 2026-09-23T21:11:58Z
updated_at: 2026-09-23T21:11:58Z
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
