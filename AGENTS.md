# AGENTS.md

Micah's AI assistant management tools.

## Toolbox

This project uses [toolbox](https://raw.githubusercontent.com/slagyr/toolbox/main/SKILL.md)
to manage agent components. If `.toolbox/` doesn't exist, fetch the toolbox
SKILL.md from the URL above and follow its instructions. Once bootstrapped:

- **Gherkin tables:** See `features/TABLES.md` for the canonical table dialect used by gherclj step definitions.
- **Skills:** Load from `.toolbox/skills/{name}/SKILL.md` when their descriptions match the task at hand.
- **Commands:** When the user invokes a command by name (e.g., "/work"), read and follow `.toolbox/commands/{name}.md`.

### Skills

- [tdd](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/tdd/SKILL.md)
- [crap4clj](https://raw.githubusercontent.com/unclebob/crap4clj/master/SKILL.md)
- [clj-mutate](https://raw.githubusercontent.com/slagyr/clj-mutate/master/SKILL.md)
- [speclj-structure-check](https://raw.githubusercontent.com/unclebob/speclj-structure-check/master/.claude/skills/speclj-structure-check/SKILL.md)
- [gherclj](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/gherclj/SKILL.md)
- [clojure](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/clojure/SKILL.md)
- [c3kit](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/c3kit/SKILL.md)
- [c3kit-schema](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/c3kit-schema/SKILL.md)

### Commands

- [plan](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/plan.md)
- [todo](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/todo.md)
- [work](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/work.md)
- [plan-with-features](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/plan-with-features.md)
- [verify](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/verify.md)

## Bead Workflow

This project uses verification. Workers mark beads `unverified`
when complete. A separate reviewer runs `/verify` to check
acceptance criteria before closing.

**Status flow:** `open` → `in_progress` → `unverified` → `closed`

If verification fails, the bead returns to `open` with notes.

## Testing Discipline

Every namespace in `src/` must have a corresponding spec in `spec/`. Features test user-visible behavior; specs test implementation. **Both are required.**

- No production code without a failing unit test first (TDD)
- Feature scenarios are NOT a substitute for unit specs
- A bead is NOT complete if new `src/` namespaces lack corresponding `spec/` files
- Run `bb spec` and `bb features` before closing any bead — both must pass

### No Fixed Sleeps in Specs

**Never use `Thread/sleep` to wait for async state to change.** Fixed sleeps are slow on fast machines and flaky on slow ones.

Instead, poll the condition directly:

```clojure
;; BAD
(trigger-something!)
(Thread/sleep 50)
(should= expected (get-state))

;; GOOD — exits as soon as the condition is true, up to 1s
(trigger-something!)
(helper/await-condition #(= expected (get-state)))
(should= expected (get-state))
```

`(helper/await-condition pred)` is available in `isaac.spec-helper`. It polls every 1ms for up to 1 second.

`Thread/sleep` is only acceptable when the spec is **explicitly testing that a function blocks for a minimum duration** (e.g. verifying a poll timeout).

**When you can't poll a positive condition** (e.g. asserting something did *not* change), instrument the triggering function instead. For example, wrap `change-source/poll!` with a counter atom and wait for the second call — that signals the first processing cycle completed — then assert the unchanged state. See `app_spec.clj` "preserves the previous config when reload fails validation" for a worked example.

**For ACP proxy specs**, always set `:acp-proxy-eof-grace-ms 0` in test opts. The default 50ms grace period fires after every request and dominates spec time.

## Logging Discipline

- New boundary code must log failures as structured maps
- Network, tool, server, and long-running operations should log key lifecycle events
- Log entries must be structured maps, never raw strings
- Include contextual identifiers such as session, provider, route, or tool when available
- Add or update specs/features when logging is part of the behavior being introduced or changed

## c3kit Schema Discipline

When working in this project with `c3kit.apron.schema`, load and follow the `c3kit-schema` skill.

- For boundary and config validation, prefer `schema/conform!`
- Never ignore the return value of non-bang schema functions such as `schema/validate`, `schema/coerce`, `schema/conform`, or `schema/present`
- If using a non-bang schema function, explicitly check `schema/error?` and handle failures
- Use manual validation only for semantic or cross-field rules after schema conformance

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Multi-machine sync

Beads' canonical store is DoltHub (slagyr/isaac-beads). Use these
commands for cross-machine sync — NOT `bd backup sync` (push-only)
or raw `dolt pull`/`dolt push` (corrupts beads' destination config).

```bash
bd dolt pull          # Pull concurrent edits from other machines
bd dolt push          # Push local edits to DoltHub
```

Before starting a work session: `bd dolt pull`.
After a batch of writes: `bd dolt push`.

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->
