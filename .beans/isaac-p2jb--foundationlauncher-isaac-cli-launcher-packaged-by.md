---
# isaac-p2jb
title: 'foundation/launcher: isaac CLI launcher — packaged-by-default, dev-local override'
status: draft
type: feature
priority: normal
created_at: 2026-06-16T18:50:24Z
updated_at: 2026-06-16T18:50:24Z
---

The brew-installed `isaac` is a thin bb launcher (mirror slagyr/homebrew-tap's braids formula: depends_on
borkdude/brew/babashka + a bb wrapper in libexec). It composes the runtime classpath (foundation + the user's
configured :modules) via babashka.deps/add-deps and boots foundation. Runs against PUBLISHED artifacts by
default; optionally against local checkouts for development.

## Default — packaged
Config :modules holds PUBLISHED coordinates (git/url + tag, or maven). The launcher resolves them
(babashka.deps/add-deps) -> brew-cached artifacts. John Doe needs no checkouts; `brew install isaac` then
`isaac ...` just works.

## Dev-local override (the requirement)
Reuse the pattern the module repos already use (:dev-local / :override-deps), lifted to the launcher. Three
knobs, precedence FLAG > ENV > CONFIG:
- CONFIG: :module-dev-root \"<dir>\" in ~/.isaac (e.g. on zanebot, /Users/micah/agents/plan) — records WHERE
  checkouts live; on its own does NOT change behavior. (Optional :module-dev-default true to make a box default
  to local.)
- ENV: ISAAC_DEV_LOCAL=1 -> use local-roots for this process, from :module-dev-root.
- FLAG: `isaac --dev-local [agent,server] ...` -> local-roots for this invocation. Bare = all modules + foundation
  local; with a list = only those local, the rest packaged (per-module override).
When active, each selected module coord is rewritten to {:local/root \"<dev-root>/isaac-<name>\"} by convention.
Default (no flag/env/config-default) = packaged.

## Acceptance (write @wip scenarios)
- Default: `isaac <cmd>` resolves modules from published coords, no checkout present, runs.
- :module-dev-root set + `--dev-local`: same `isaac <cmd>` runs against ../isaac-<name> checkouts; an edit in a
  checkout is reflected on the next run with NO reinstall.
- Per-module: `--dev-local agent` -> agent from checkout, foundation/others from packaged (assert the source used).
- Precedence: flag overrides env overrides config-default.

## Relationships / open
- Pairs with isaac-dhzy (the `modules` command writes :modules; launcher reads it).
- Part of the brew-packaging story (the isaac.rb formula in slagyr/homebrew-tap, next to braids.rb).
- Open: confirm all modules run under bb (ISAAC.md bb-first); if a module needs JVM-only, that module gets a bb
  fix or a JVM fallback in the launcher.
