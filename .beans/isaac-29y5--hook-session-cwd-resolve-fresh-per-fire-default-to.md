---
# isaac-29y5
title: 'Hook session cwd: resolve fresh per fire, default to crew workspace'
status: todo
type: feature
priority: high
created_at: 2026-05-14T15:30:01Z
updated_at: 2026-05-14T15:30:01Z
---

Hook sessions today persist whatever cwd was current at first session creation. The symptom: `hook:location` session on zanebot still carries `:cwd "/Users/zane/Projects/isaac/isaac-live"` from a worktree the server hasn't run in for hours. Workers writing hook-driven tools end up rooted in stale paths that may not even exist.

## Desired behavior

**Resolve cwd fresh on every hook fire** (do not persist in the session edn). Resolution order:

1. **Hook frontmatter `:cwd`** wins — explicit per-hook escape hatch. Useful for hooks that target a project workspace (e.g. a CI hook that should operate in a repo).
2. **Crew workspace dir** — `(config/resolve-workspace crew-id)` from `loader.clj:968`. Returns `~/.isaac/crew/<id>/` when populated, falls back to legacy `.openclaw/workspace-<id>` and `.isaac/workspace-<id>` dirs. This is the natural home for crew-scoped state (AGENDA.md, memory/, hooks/).
3. **State-dir parent** — `(fs/parent state-dir)` = user home. Safe fallback when no crew workspace exists.

What it must NOT be: the server process cwd. That's an accident of where `bb` was launched, not semantic intent.

## Acceptance Criteria

- Hook session edn no longer contains `:cwd`. (Or if persisted, it's purely informational/cached and overridden on dispatch — preferred: drop it entirely.)
- `server/hooks.clj` resolves cwd at dispatch time from the order above, passes it to the turn via `turn-opts`.
- `build-turn-ctx` reads the dispatch-time cwd, not session `:cwd`, for webhook-origin sessions. (For interactive sessions — `:origin :acp` / `:cli` — keep current cwd-from-session behavior; the user has been steering it.)
- Existing hook session files on disk with stale `:cwd` are tolerated — the persisted value is ignored or stripped on next dispatch. No migration required.
- Restarting isaac from a different worktree does not change hook cwd resolution.

## Scenarios (Gherkin shape)

```
Scenario: Hook with no frontmatter cwd uses crew workspace
  Given crew "main" has a workspace at "~/.isaac/crew/main"
  And the hook "location" has no :cwd in its frontmatter
  When the location hook fires
  Then the turn cwd is "~/.isaac/crew/main"

Scenario: Hook frontmatter :cwd overrides the default
  Given the hook "build-report" has :cwd "/Users/zane/repos/widgets" in its frontmatter
  When the build-report hook fires
  Then the turn cwd is "/Users/zane/repos/widgets"

Scenario: Hook for crew without a workspace falls back to home
  Given crew "ephemeral" has no workspace dir
  And the hook "ping" targets crew "ephemeral"
  When the ping hook fires
  Then the turn cwd is the user home (state-dir parent)

Scenario: Stale persisted :cwd in session edn is ignored
  Given the session "hook:location" has a stale :cwd "/old/path/that/no/longer/exists"
  When the location hook fires
  Then the turn cwd is resolved fresh from hook + crew + fallback
  And the stale :cwd is not used

Scenario: Server restart from a different worktree does not affect hook cwd
  Given isaac is restarted from /Users/zane/Projects/isaac/isaac-live
  And then restarted from /Users/zane/Projects/isaac/isaac-main
  When any hook fires after each restart
  Then the resolved hook cwd is the same in both cases
```

## TODO

- [ ] Add cwd-resolution helper to `server/hooks.clj` (or factor into `config/loader.clj` next to `resolve-workspace`)
- [ ] Update hook handler to pass cwd in turn-opts and not from `(:cwd session)`
- [ ] Update `build-turn-ctx` to prefer dispatch-time cwd for webhook-origin sessions
- [ ] Specs for all five scenarios above
- [ ] Decide: strip `:cwd` from new hook session edns entirely, or write it for diagnostics only

## Related

- The misleading `cwd "/Users/zane/Projects/isaac/isaac-live"` was the secondary observation in this morning's hook-debugging session. The primary (allowed-tools=0) was traced to `server/hooks.clj` using `system/with-system` instead of `with-nested-system`, which clobbered the config snapshot for hook turns. Separate bug, separate fix.
