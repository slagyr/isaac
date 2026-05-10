# AGENTS.md

Micah's AI assistant management tools.

## Toolbox

This project uses [toolbox](https://raw.githubusercontent.com/slagyr/toolbox/main/SKILL.md)
to manage agent components. If `.toolbox/` doesn't exist, fetch the toolbox
SKILL.md from the URL above and follow its instructions. Once bootstrapped:

- **Project context:** See [`ISAAC.md`](ISAAC.md) for vocabulary, working-with-Micah, project knowledge, and Isaac-specific traps.
- **Gherkin tables:** See `features/TABLES.md` for the canonical table dialect used by gherclj step definitions.
- **Skills:** Load from `.toolbox/skills/{name}/SKILL.md` when their descriptions match the task at hand.
- **Commands:** When the user invokes a command by name (e.g., "/work"), read and follow `.toolbox/commands/{name}.md`.
- **Fresh checkout setup:** Run `bb hooks:install` once so git uses the repo-tracked pre-push hook.

### Skills

- [tdd](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/tdd/SKILL.md)
- [refactor](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/refactor/SKILL.md)
- [smells](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/smells/SKILL.md)
- [architecture](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/architecture/SKILL.md)
- [logging](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/logging/SKILL.md)
- [beads-multi-worker](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/beads-multi-worker/SKILL.md)
- [crap4clj](https://raw.githubusercontent.com/unclebob/crap4clj/master/SKILL.md)
- [clj-mutate](https://raw.githubusercontent.com/slagyr/clj-mutate/master/SKILL.md)
- [scrap](https://raw.githubusercontent.com/slagyr/scrap/main/SKILL.md)
- [gherclj](https://raw.githubusercontent.com/slagyr/gherclj/refs/heads/master/SKILL.md)
- [gherkin](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/gherkin/SKILL.md)
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

Multi-worker sync (push after every bead write, pull at handoff points,
session close protocol) is documented in the
[beads-multi-worker skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/beads-multi-worker/SKILL.md).

## Testing Discipline

Every namespace in `src/` must have a corresponding spec in `spec/`.
Features test user-visible behavior; specs test implementation.
**Both are required.**

- No production code without a failing unit test first (TDD)
- Feature scenarios are NOT a substitute for unit specs
- A bead is NOT complete if new `src/` namespaces lack corresponding `spec/` files
- Run `bb spec` and `bb features` before closing any bead — both must pass

### Push Enforcement

Tests run automatically on push via the repo-tracked pre-push hook.
The hook short-circuits on doc-only changes. On `.clj`, `.cljs`,
`.cljc`, `.feature`, or `.edn` changes it runs `bb verify` and rejects
the push if anything is red.

If you bypass the hook (`--no-verify` or hook not installed), CI on
`main` runs the same suite and files a `P1` bug bead assigned to you on
failure. You'll see it next session via `bd ready`.

On a fresh checkout: `bb hooks:install`.

Implication: never push code/test changes without running `bb verify`
yourself or letting the hook run it. The work-session handoff assumes
the hook will run; bypassing it creates beads-tracked debt under your
name.

### Fast Lint Before Spec

**After editing a Clojure file, run `bb lint <file>` before `bb spec`.**
It runs clj-kondo in under 300ms and catches paren/bracket mismatches
and syntax errors before paying the cost of loading the full project
for specs.

```bash
bb lint src/isaac/foo.clj   # lint one file (~50ms)
bb lint src/isaac/foo/      # lint a directory
bb lint                     # lint all of src/ and spec/ (~1-2s)
```

`bb lint` exits 0 on success, 1 on errors. Use it as the fast pre-spec gate:
1. Edit → `bb lint <file>` → fix syntax if needed → `bb spec` → fix logic → `bb features`

### No Fixed Sleeps in Specs

Use `(isaac.spec-helper/await-condition pred)` instead of `Thread/sleep` —
polls every 1ms for up to 1 second. See the
[tdd skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/tdd/SKILL.md#polling-instead-of-sleeping)
for the general pattern. `app_spec.clj` "preserves the previous config
when reload fails validation" has a worked example of the wrap-and-count
pattern for negative assertions.

For ACP proxy specs, always set `:acp-proxy-eof-grace-ms 0` in test opts.

## Logging Discipline

See the [logging skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/logging/SKILL.md)
for principles. The registered info+ events table is in
[ISAAC.md](ISAAC.md#logging--registered-info-events).

## c3kit Schema Discipline

When working in this project with `c3kit.apron.schema`, load and follow the
[c3kit-schema skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/c3kit-schema/SKILL.md).

- For boundary and config validation, prefer `schema/conform!`
- Never ignore the return value of non-bang schema functions such as
  `schema/validate`, `schema/coerce`, `schema/conform`, or `schema/present`
- If using a non-bang schema function, explicitly check `schema/error?`
  and handle failures
- Use manual validation only for semantic or cross-field rules after
  schema conformance

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

See the [beads-multi-worker skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/beads-multi-worker/SKILL.md)
for push-after-every-write and pull-at-handoff discipline.

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
<!-- END BEADS INTEGRATION -->
