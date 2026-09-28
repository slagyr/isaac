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
- [beans-multi-worker](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/beans-multi-worker/SKILL.md)
- [crap4clj](https://raw.githubusercontent.com/unclebob/crap4clj/master/SKILL.md)
- [dry4clj](https://raw.githubusercontent.com/slagyr/dry4clj/refs/heads/master/SKILL.md)
- [clj-mutate](https://raw.githubusercontent.com/slagyr/clj-mutate/master/SKILL.md)
- [scrap](https://raw.githubusercontent.com/slagyr/scrap/main/SKILL.md)
- [gherclj](https://raw.githubusercontent.com/slagyr/gherclj/refs/heads/master/SKILL.md)
- [gherkin](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/gherkin/SKILL.md)
- [clojure](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/clojure/SKILL.md)
- [c3kit](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/c3kit/SKILL.md)
- [c3kit-schema](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/c3kit-schema/SKILL.md)
- [planning](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/planning/SKILL.md) — co-authoring beans + Gherkin with the user; the craft layer (Isaac specifics in `## Planning` below)
- [hail-bean-work](.toolbox/skills/hail-bean-work/SKILL.md) — hail-driven worker bootstrap; repo discovery; `list_skills` fallback; process-test beans (ungated beans / other projects)
- [hail-bean-work-gate](.toolbox/skills/hail-bean-work-gate/SKILL.md) — gated worker bootstrap: implement, `bb bean-gate verify`, land on main, `completed` (the `isaac-work` band loads this one)

### Commands

- [plan](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/plan.md)
- [todo](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/todo.md)
- [work](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/work.md)
- [plan-with-features](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/plan-with-features.md)
- [verify](https://raw.githubusercontent.com/slagyr/agent-lib/main/commands/verify.md)
- [work-bean-gate](.toolbox/commands/work-bean-gate.md) — the gated close: gate, land, complete (short path; mechanics in the `hail-bean-work-gate` skill)

## Bean Workflow

Two flows, decided by one thing: whether the bean carries a `feature-baseline:`
line (see [Baseline the bean](#baseline-the-bean-bean-gate)). The gated flow is
the **default**: a planned, scenario-backed bean is baselined by the planner and
its worker lands and completes it. The `unverified` + `isaac-verify` flow is the
**exception** — what happens to a bean that carries no `feature-baseline:`
(beans that predate the gate, and work with no feature scenarios behind it).

**Gated beans (`feature-baseline:` present) — the worker lands its own bean.**
The worker implements on `bean/<id>`, runs `bb bean-gate verify <id>` from the
isaac clone, and on **exit 0** lands it: rebase, squash-merge to `main`, repin
and re-`bb ci` any downstream sibling, append `## Landed on main` with one
`main-sha: <repo> <sha>` line per repo, delete the bean branch, then
`beans update <id> --status=completed`. No `unverified` tag and no hail to
`isaac-verify`. On **exit 1** the contract moved: the worker reverts the
`.feature` to its baselined text or hails the plan band — a worker never adds
`## Exceptions` and never re-baselines. On **exit 2** the bean is not gated and
takes the flow below. CI re-runs the gate on every completed baselined bean that
reaches main, so the contract is still checked by something other than the
worker. Mechanics: [hail-bean-work-gate](.toolbox/skills/hail-bean-work-gate/SKILL.md)
and [work-bean-gate](.toolbox/commands/work-bean-gate.md).

**Status flow (gated):** `draft` → `todo` (planner, via `bb bean-gate baseline`) → `in-progress` → `completed` (worker)

A bean is todo only when bb bean-gate baseline has frozen its scenarios; the baseline command sets the status.
Nobody runs `beans update <id> --status=todo` by hand. Before hailing
`isaac-work`, dispatch runs `bb bean-gate ready <id>` (exit 0 = todo and
baselined); a non-zero exit stops the dispatch. The one exception: a bean whose
module has no feature runner (the `isaac` repo itself) is dispatched ungated,
with a `## Ungated` note in its body saying why.

**Ungated beans (no `feature-baseline:`) — the exception; verification by a
reviewer.** Workers leave the bean `in-progress` and add `tag=unverified` when
implementation is finished; they do **not** mark it `completed`. A reviewer runs
`/verify`, then either marks the bean `completed` or returns it to normal work,
removing the tag in either case. If verification fails, the bean returns to
`in-progress` with notes appended to the body.

**Status flow (ungated):** `todo` → `in-progress` → `in-progress + tag=unverified` → `completed`

**Worker rule (ungated only):** handoff is `beans update <id> --tag=unverified`
while the bean stays `status=in-progress`. `completed` is verifier-only. A bean
without a `main-sha:` line is not `completed` on either flow.

**Pin rule:** a bean may only pin a sibling repo at a sha reachable from that
repo's `main` (`git merge-base --is-ancestor <sha> origin/main`). Never a
bean-branch sha — verify squashes and deletes it. Verify rejects a handoff
whose pins fail this check.

**Cross-repo beans:** while the bean is in flight, the downstream repo runs
against the sibling checkout via `:dev-local` (`:override-deps` / `:local/root`),
not a sha pin at the upstream bean branch. The sha pin changes only at
handoff; verify rewrites it to the squashed main sha before landing the
downstream repo (verify.md §6a). `bb lint-pins` in `bb ci` fails fast if a
published pin cannot be fetched.

**Planner watch — a gated bean needs no verify hail.** The watch dispatches the
work hail and then waits for `status=completed` with a `main-sha:` line: there
is no `unverified` step to look for and nothing to hand to `isaac-verify`. The
stall rule is unchanged — a worker that goes quiet (no bean note, no branch
push, no completion) is chased the same way it always was. A *gated* bean that
turns up tagged `unverified` is a worker that took the wrong close: treat it as
a stall and send it back, not as verify's queue. Only an ungated bean is
handed to `isaac-verify`, and the watch then waits for the verifier to complete
it.

## Planning

Co-authoring beans + Gherkin scenarios with the user is governed by the
[planning](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/planning/SKILL.md)
skill — the craft layer (investigate before asserting, settle design before
drafting, one scenario at a time, record decisions in the bean). **This
replaces the former `PLANNING-PARTNER.md`.** The skill is generic; the
Isaac-specific extensions it defers to the project are below.

### Mechanics to read first

- `.toolbox/commands/plan-with-features.md` — the feature-first workflow, `@wip`, bean lifecycle.
- `.toolbox/skills/gherclj/SKILL.md` — step/helper structure, contract integrity.
- `isaac-foundation/features/TABLES.md` — the gherclj table dialect and matchers (`#*` any-non-nil, `#"regex"`, `#index`).
- [`ISAAC.md`](ISAAC.md) — vocabulary, working-with-the-user, Isaac-specific traps.

### Repo layout

Beans live in **this** repo (`isaac/.beans/`); it is the planning/coordination
repo. Feature files live in the **module** repos they test
(`isaac-agent/features/…`, `isaac-hail/features/…`, `isaac-foundation/features/…`,
etc.). A planning session commits the bean here and the `@wip` feature file on
the **`main`** branch of the relevant module repo — never on a `bean/<id>`
branch (see [Baseline the bean](#baseline-the-bean-bean-gate) below).

### Baseline the bean (bean gate)

After the scenarios are settled, the planner records what the bean is allowed to
change. In the planner's own order of work:

1. **Commit the scenarios `@wip` to the module's `main`.** Not to a
   `bean/<id>` branch. Module CI excludes `@wip`, so main stays green and the
   worker's branch starts from a tree that already holds the contract.
2. **Baseline, from the isaac clone:**

   ```sh
   bb bean-gate baseline <bean-id> <repo>:<path>[:<line>…] … [--dir <repo>=<path>]
   ```

   Without line numbers, every `@wip` scenario in the file belongs to the bean.
   With them, name each scenario by the line of its `Scenario:` keyword (the
   keyword line, not the tag line above it). `--dir <repo>=<path>` points at a
   checkout that is not `../<repo>`. Baseline fetches `origin` and **appends**
   `feature-baseline:` / `feature-blob:` lines to the bean body, and **promotes
   the bean to `todo`** — it does not commit. A bean is todo only when bb bean-gate baseline has frozen its scenarios; the baseline command sets the status.
   Baseline runs from `draft` or `todo` only; an `in-progress` / `completed` /
   `scrapped` bean, or no `<repo>:<path>` refs, exits 2 and changes nothing.
3. **Commit the bean** with the appended `feature-baseline:` / `feature-blob:`
   lines. The baseline commit must come from the **planner**: the gate fails a
   baseline introduced by a commit carrying an `Isaac-Session: isaac-work…` or
   `Isaac-Session: isaac-verify…` trailer.
4. **A feature edit after baselining is made on module `main`** and then
   re-baselined (baseline appends new lines; the newest lines are in force).
   Never rewrite or delete an existing `feature-*` line — the gate walks the
   bean's git history and treats those lines, plus everything under
   `## Acceptance…` and `## Exceptions`, as **append-only**.

5. **Dispatch checks readiness:** `bb bean-gate ready <bean-id>` exits 0 when
   the bean is `todo` and baselined, else 1 with the reason (`not baselined`,
   `status draft`, …). Do not hail `isaac-work` on a non-zero exit.

`bb bean-gate --help` documents the subcommands and the exit codes.

### Fixture theme — Marigold

All scenario content uses the fictional **Marigold** cast. Names live in
per-module `marigold*` source/spec files — read them in the modules you touch
and reuse the established names (`longwave`, `skybeam`, `logbook`, Cordelia, …).
Inventing parallel fixtures is a smell; no real PII or real use-cases in specs.

### Abstraction level

Isaac is layered; a scenario must test the seam that **owns** the behavior. An
`isaac-agent` feature describing `comm_send` must **not** assert Discord/iMessage
specifics — agent doesn't know those comms. Test the generic seam with Marigold
fixtures; push concrete-comm assertions down to the comm module that owns them.

### Back-compat stance

The user strongly prefers **clean cutover — no legacy, no back-compat**. When a
redesign removes or renames something, take the breaking-clean path and make it
explicit in the bean (removed keys hard-reject, no deprecated aliases, old
scenarios deleted not retained).

## Parallel-Worker Sync

Multiple worker checkouts (`isaac-main`, `isaac-worker-1`, ...) run in
parallel. Each one's view of source and beans is stale by default. The
cost of skipping a sync at a handoff point is silent divergence: a
verifier reviewing stale source, a worker missing reviewer notes, an
agent claiming "I don't see that bean" or "I don't have that code."

Beans live as plain markdown under `.beans/`, so `git pull` brings both
source and bean state. There is no separate sync command.

**Rule:** *Before acting on another worker's output, pull. After
producing your own, push.*

### Session start — always pull

First action in any new session, before any other work:

```bash
git pull --rebase
```

Without this, you'll reason about stale code and stale beans. Common
symptoms: "I don't see that bean," "I don't have that code," or
recommending a fix that already shipped.

### Push after every bean write

Pushes are cheap. They prevent stranding state where another worker
can't see it.

- After `beans create`, `beans update`, `beans archive`, `beans delete`
  → `git add .beans/ && git commit -m "..." && git push`.
- The bean change usually rides with the related code commit. Claiming a
  bean (`status=in-progress`) is a fine standalone commit.

### Pull at handoff points

Beyond session start, pulls are situational — only when you're about
to act on what someone else produced. Do **not** pull on every
`beans show` / `beans list`.

- **Verification** — before `/verify` or otherwise reviewing a bean
  tagged `unverified`: `git pull --rebase` before reading source or
  bean state. One pull covers both.

- **Resuming after external change** — told "the bean was reopened",
  "verifier left notes", "user closed a dependency", or any signal
  another actor touched your bean since you last looked: `git pull
  --rebase` before `beans show <id>`.

See the [beans-multi-worker skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/beans-multi-worker/SKILL.md)
for the full canonical reference.

## Testing Discipline

Every namespace in `src/` must have a corresponding spec in `spec/`.
Features test user-visible behavior; specs test implementation.
**Both are required.**

- No production code without a failing unit test first (TDD)
- Feature scenarios are NOT a substitute for unit specs
- A bean is NOT complete if new `src/` namespaces lack corresponding `spec/` files
- Run `bb spec` and `bb features` before closing any bean — both must pass

### Push Enforcement

Tests run automatically on push via the repo-tracked pre-push hook.
The hook short-circuits on doc-only changes. On `.clj`, `.cljs`,
`.cljc`, `.feature`, or `.edn` changes it runs `bb verify` and rejects
the push if anything is red.

If you bypass the hook (`--no-verify` or hook not installed), CI on
`main` runs the same suite and fails the run. Check `gh run list` (or
the project's notification channel) after a push to see CI status.

On a fresh checkout: `bb hooks:install`.

Implication: never push code/test changes without running `bb verify`
yourself or letting the hook run it. The work-session handoff assumes
the hook will run; bypassing it creates breakage your teammates have
to chase down.

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

## Config Read Discipline

Never read Isaac config **content** with raw `slurp` / `edn/read-string` on
`config/isaac.edn`, entity files under `config/`, or `.env`. Those paths skip
`${VAR}` resolution, schema validation, and entity merge.

**Always** use `isaac.config.api` (`resolved-config`, `resolved-slice`,
`load-resolved`) or `isaac.config.loader/load-config-result` for live reads.

Sanctioned exceptions (do not route product reads through these):

- `isaac.config.*` — the loader, mutate, and validation stack itself
- `isaac.cli.registry` — `isaac init` scaffolding that **writes** config
- `isaac.config.root` — pointer files (`~/.config/isaac.edn`) that locate the
  root, not config values

Config bypass lint lives in `isaac-foundation/spec-support`
(`isaac.foundation.config-bypass-lint`), exposed as the
`io.github.slagyr/isaac-foundation-test-support` coordinate (`:deps/root
"spec-support"`). Every module's `bb.edn` depends on that coord and runs
`bb config-bypass-lint` (wired into `bb ci`) on its own `src/`. Bump the
test-support `:git/sha` when foundation changes the lint.

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

## Beans Issue Tracker

This project uses **beans** for issue tracking. Beans live as plain markdown under `.beans/`, version-controlled with the code. Run `beans prime` for the canonical agent-priming output.

### Quick Reference

```bash
beans list --ready                     # Find available work
beans show <id>                        # View bean details
beans update <id> --status=in-progress # Claim work
beans update <id> --tag=unverified     # Hand off to /verify; keep status=in-progress
```

### Multi-machine sync

`git pull` brings both source and bean state. `git push` sends both. No separate sync command. See the [beans-multi-worker skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/beans-multi-worker/SKILL.md) for session-close discipline and git-conflict resolution on bean files.

### Rules

- All coding performed in this project must be either: (A) specified by an existing bean, or (B) explicitly requested or authorized by the user.
- Use `beans` for existing bean work and for new work only when the user explicitly asks for bean tracking or approves creating one. If no bean exists for requested work, ask before creating one. Do NOT use TodoWrite, TaskCreate, or markdown TODO lists.
- Persistent knowledge lives in `.memories/<slug>.md` — plain markdown files outside the bean tracker. Do NOT use MEMORY.md files.

### Task Continuity

- Keep exactly one active task at a time.
- Do not switch to a different task unless the user explicitly says to switch.
- If the user asks a side question while work is in progress, answer it and then resume the current task unless the user explicitly redirects the work.
- If a new user message might replace the current task, ask for clarification instead of assuming.
- Before starting substantial new work after a context shift, restate the current active task and whether it has changed.
- When asked about prior requests or task history, read from the transcript or repo instructions directly instead of reconstructing from memory.
