---
# isaac-kleb
title: 'isaac-agent: the directory policy follows the crew the turn runs as — inject the resolved crew into tool args; stale session crews no longer lock the filesystem'
status: completed
type: bug
priority: high
created_at: 2026-09-25T14:34:20Z
updated_at: 2026-09-25T14:45:45Z
---

## Symptom (yopp, 2026-09-25 14:28Z)

Micah asked Yopp in a Chat DM for a jackalope image. Every fs tool call failed
`path outside allowed directories: /home/yopp` — `/home/yopp`, `/tmp`, the
cwd, all of it — although crew yopp allows `["/home/yopp" "/tmp"]`. The turn
ran as crew yopp (`drive/turn-accepted :crew "yopp"`, the gchat comm passes
the space's crew on dispatch) but the session record
`gchat-tonotop-dm-micah-martin` still said `crew main` from its creation
weeks ago, and no crew "main" exists on yopp. `isaac.tool.fs-bounds/
ensure-path-allowed` reads `(:crew session)` → `[:crew "main" :tools]` →
empty directory policy → everything refused. Workaround applied: the four
yopp sessions stored as `main` were set to `yopp`. The bug remains for any
session whose stored crew differs from the crew the comm dispatches with.

## Design (drive stays generic)

- The turn injects the resolved crew id into every tool call's arguments as
  `"crew"`, next to `"session_key"` and `"state_dir"` (drive/turn.clj ~1528).
- `fs-bounds/ensure-path-allowed` (and `session-workdir`'s crew use, if any)
  take the crew from `args "crew"` first, then `(:crew session)`, then
  `defaults/crew-id`. Same for exec's workdir bound if it resolves a crew.
- `isaac.tool.registry` strips `"crew"` from the arguments handed to
  non-builtin tools wherever it already strips `session_key`/`state_dir`;
  note for isaac-mcp: `isaac.mcp.runtime/mcp-arguments` strips
  `#{"session_key" "state_dir"}` — add `"crew"` there (separate small bean,
  or include if the worker can land both; read-only otherwise).
- No session record is rewritten: a hail `:with-crew` override is per
  delivery and must not become the session's crew.

## Acceptance (features/tool/directories.feature — baselined)

- [ ] Scenario "the directory policy follows the crew the turn runs as, not
  the stale crew on the session record (isaac-kleb)". The step
  `When the user sends "…" on session "…" as crew "…"` is new: it dispatches
  the turn with an explicit crew, the way a comm does (bridge/dispatch!
  request `:crew`); add it beside the existing send step.
- [ ] Existing directories/filesystem_boundaries/built_in scenarios unchanged
  and green (session crew still governs when no override is present).
- [ ] Spec: `ensure-path-allowed` prefers `"crew"` in args over the session
  record; falls back to the record, then the default crew.
- [ ] Version bump; bb spec / bb features / bb lint green; bb jvm-spec.

Likely repo scope: isaac-agent (tool/fs_bounds.clj, drive/turn.clj,
tool/registry.clj, spec, directories.feature). Follow-up: isaac-mcp
mcp-arguments strip list.

feature-baseline: isaac-agent 0d2256baeb58107bdcce8e728b3ba80f2e8f7f78
feature-blob: isaac-agent features/tool/directories.feature 0b97134a7230713bd491310672a16b657a000663 230

## Landed on main (2026-09-25)

main-sha: isaac-agent 592c87862acbb41a4929623d283b949f8dc8a71b
