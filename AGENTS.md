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
- [scrap](https://raw.githubusercontent.com/slagyr/scrap/main/SKILL.md)
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

## Parallel-Worker Sync

Multiple worker checkouts (`isaac-main`, `isaac-worker-1`, …) run in parallel. Each one's view of source and beads is stale by default. The cost of skipping a sync at a handoff point is silent divergence: a verifier reviewing stale source, a worker missing reviewer notes.

**Rule:** *Before acting on another worker's output, pull. After producing your own, push.*

### Push after every bead write

Pushes are cheap. They prevent stranding state where another worker can't see it.

- After `bd create`, `bd update`, `bd close`, `bd dep add` → `bd dolt push`
- After `git commit` that signals work another worker may consume (e.g. marking a bead `unverified`, closing a dependency, finishing a refactor) → `git push`

This supersedes the "after a batch of writes" guidance in the Beads Issue Tracker section below.

### Pull at handoff points

Pulls are situational — only at moments where you're about to act on what someone else produced. Do **not** pull on every `bd show`/`bd ready`/`bd list`.

- **Verification** — running `/verify` or otherwise reviewing a bead marked `unverified`:
  `git pull --rebase` **and** `bd dolt pull` *before* reading source or bead state.
  Verification is the canonical worker→reviewer handoff. Skipping the source pull risks reopening a bead against stale code; skipping the bead pull risks overwriting concurrent reviewer notes.

- **Resuming after external change** — told "the bead was reopened", "verifier left notes", "user closed a dependency", or any signal that another actor touched your bead since you last looked:
  `bd dolt pull` before `bd show <id>`. Your in-memory model of the bead is stale.

- **Session start** — already covered in Session Completion's mirror: `git pull --rebase` and `bd dolt pull`.

## Testing Discipline

Every namespace in `src/` must have a corresponding spec in `spec/`. Features test user-visible behavior; specs test implementation. **Both are required.**

- No production code without a failing unit test first (TDD)
- Feature scenarios are NOT a substitute for unit specs
- A bead is NOT complete if new `src/` namespaces lack corresponding `spec/` files
- Run `bb spec` and `bb features` before closing any bead — both must pass

### Fast Lint Before Spec

**After editing a Clojure file, run `bb lint <file>` before `bb spec`.** It runs clj-kondo in under 300ms and catches paren/bracket mismatches and syntax errors before paying the cost of loading the full project for specs.

```bash
bb lint src/isaac/foo.clj   # lint one file (~50ms)
bb lint src/isaac/foo/      # lint a directory
bb lint                     # lint all of src/ and spec/ (~1-2s)
```

`bb lint` exits 0 on success, 1 on errors. Use it as the fast pre-spec gate:
1. Edit → `bb lint <file>` → fix syntax if needed → `bb spec` → fix logic → `bb features`

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

- All coding performed in this project must be either: (A) specified by an existing bead, or (B) explicitly requested or authorized by the user.
- Use `bd` for existing bead work and for new work only when the user explicitly asks for bead tracking or approves creating a bead. If no bead exists for requested work, ask before creating one. Do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

### Task Continuity

- Keep exactly one active task at a time.
- Do not switch to a different task unless the user explicitly says to switch.
- If the user asks a side question while work is in progress, answer it and then resume the current task unless the user explicitly redirects the work.
- If a new user message might replace the current task, ask for clarification instead of assuming.
- Before starting substantial new work after a context shift, restate the current active task and whether it has changed.
- When asked about prior requests or task history, read from the transcript or repo instructions directly instead of reconstructing from memory.

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
