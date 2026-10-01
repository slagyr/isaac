---
# isaac-3rac
title: Prompt tools load commands, not just skills
status: draft
type: feature
priority: normal
created_at: 2026-10-01T17:23:56Z
updated_at: 2026-10-01T17:23:56Z
---

Micah, 2026-10-01. Crews can list and load skills (`skill__list`, `skill__load`) but not commands: `~/.isaac/prompts/commands/*.md` (`verify`, `work`, `plan`, `harden`, …) have no tool. Found on zanebot: perceptor (verifier) learned from its own memory notes to `fs__read ~/.isaac/prompts/commands/verify.md`, which its `:cwd`-only directory grant correctly refuses.

## Intent
Expand the prompt tools so a crew can list and load commands the same way it loads skills (and consider rules). Band/hail prompts then say "load the verify command" instead of naming a file path.

## Open questions (plan before todo)
- One tool family (`prompt__list`/`prompt__load` with a kind: skill | command | rule) or a sibling pair (`command__list`/`command__load`)? Grants: does `:skill/*` cover commands or is it a new grant?
- Where do commands come from: the same prompt roots as skills (`prompts/commands`, module-contributed)? Same precedence rules?
- Does a loaded command carry arguments ($ARGUMENTS-style) or is it plain text?
- Update the verify/work band templates and perceptor's memory note afterwards.
