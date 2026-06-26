---
name: hail-bean-work
description: Bootstrap and run bean work from a hail delivery when session cwd, skills catalog, or checkout layout are ambiguous. Use for isaac-work hails, orchestration/process-test beans, or when list_skills returns empty.
---

# Hail-driven bean work

Use when a hail (or band prompt) assigns bean work and you need a reliable start
path without guessing checkout locations or waiting on `load_skill`.

## Bootstrap checklist

Run in order before claiming or editing anything.

1. **Find the beans repo** — directory with `.beans/` and `.beans.yml` (always
   the `isaac` git clone). From session cwd, check `./isaac`, `../isaac`, or
   sibling role homes (`~/agents/work-1/isaac`, etc.). If hail prose names a
   path that does not exist, ignore the label and use the discovered clone.
2. **`git -C <isaac-clone> pull --rebase`** — beans and source sync together.
3. **`beans show <id>`** (or `beans list --ready`) — read full body + acceptance.
   There is no `beans list --all`; use `beans list` or `beans show`.
4. **Find the implementation repo** — bean scope / title names the repo
   (`isaac-discord`, `isaac-hail`, monolith `isaac`, …). Work in the sibling
   checkout under your role home; `git clone` on demand per `AGENTS.md`.
5. **Skills** — try `list_skills` / `load_skill` if available. If empty or
   missing, read directly:
   - `../AGENTS.md` (shared boot)
   - `isaac/AGENTS.md`
   - `isaac/.toolbox/commands/work.md`
   - this file
6. **Claim** — `beans update <id> --status=in-progress`, commit + push
   `.beans/` from the isaac clone.

All `beans` commands and bean markdown commits happen in the **isaac** clone even
when implementation edits happen in a module sibling.

## Session cwd vs worktree

| Surface | Typical path | Holds |
|---------|--------------|--------|
| Role home | `~/agents/work-N/` | Session cwd, hail landing |
| Beans + toolbox | `~/agents/work-N/isaac/` | `.beans/`, `.toolbox/` |
| Module checkout | `~/agents/work-N/isaac-discord/` etc. | Split-repo source |

Hail init text ("checkout in quarters") describes intent, not a guaranteed path.
Authoritative rule: **the clone that contains `.beans/` is the beans repo.**

## Normal implementation bean

Follow `isaac/.toolbox/commands/work.md`:

- TDD + `bb spec` / `bb features` per bean acceptance
- Hand off: `beans update <id> --tag=unverified` (stay `in-progress`)
- Push beans + code

## Process-test / no-op beans

When the bean body says **process test**, **no-op**, or **orchestration smoke**
(e.g. `isaac-orc1`):

- **No product code or tests required** unless the bean explicitly asks for them.
- TDD rules in `isaac/AGENTS.md` are **suspended** for that bean.
- Minimum deliverable:
  1. Claim the bean.
  2. Append observations under `## Process Observations` in the bean body.
  3. Create follow-up beans for gaps found (like `isaac-evws`).
  4. `beans update <id> --tag=unverified` + push.
- Do not infer that red/green tests are expected.

## Hand off to verify

- Worker: `in-progress` + `tag=unverified`, push isaac `.beans/` with any notes.
- Hail verify band/session per deployment convention, or human runs `/verify`.
- Verifier pulls isaac clone before reviewing.