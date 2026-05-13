# ISAAC.md

Project-specific context for working on Isaac. General-purpose
discipline (refactoring, smells, gherkin, beans multi-worker, logging,
architecture, etc.) lives in [agent-lib](https://github.com/slagyr/agent-lib)
skills referenced from `AGENTS.md`. This file captures what's specific
to Isaac.

## Vocabulary

Isaac is modeled as a spaceship. Top-level structure is named for what
the system *does* (bridge, comm, crew), not for the frameworks it uses.
The metaphor is the shared vocabulary — when you add a new top-level
domain, prefer a metaphor-coherent name.

### Metaphor terms

| Term     | Meaning                                          | In code               |
|----------|--------------------------------------------------|-----------------------|
| Ship     | The system itself                                | `isaac`               |
| Bridge   | Input triage — slash commands vs prompts         | `isaac.bridge.*`      |
| Comm     | Channel of communication (CLI, ACP, memory, …)   | `isaac.comm.*`        |
| Crew     | Agents, each with provider, model, and soul      | `:crew` in config     |
| Quarters | A crew member's writable filesystem area         | `~/.isaac/crew/<id>/` |
| Modules  | Pluggable capabilities                           | `:modules` in config  |

### Domain terms (no metaphor)

| Term          | Meaning                                                       |
|---------------|---------------------------------------------------------------|
| Soul          | A crew member's standing orders (`.md` companion file)        |
| Provider      | LLM service (xAI, Anthropic) — base-url + auth + models       |
| Api           | Wire-format adapter (anthropic-messages, openai-completions)  |
| Model         | Specific LLM identifier (claude-sonnet-4-6, grok-2)           |
| Effort        | Universal thinking knob (integer 0-10), translated per API    |
| Tool          | Capability the LLM can invoke (web_search, web_fetch, …)      |
| Slash command | User input starting with `/` — handled at the bridge          |
| Session       | One ongoing conversation with history                         |
| Transcript    | Append-only JSONL record of session events                    |
| Compaction    | Compressing transcript history when token budget tightens     |

## Working with Micah

### Terse

Don't explain what you're about to do. Do it. Don't summarize what you
did. He reads the diff.

### Flag concerns before implementing

When you see a trade-off or potential issue, say it before writing
code. "My recommendation: X. Concern: Y." He'll tell you how to weigh
it.

### Ask when you're genuinely unsure

Don't guess at preferences. "Do you want A or B?" is welcome. Guessing
and being wrong wastes time.

### He refines, you implement

He iterates on ideas through conversation. Don't write files on the
first draft — present in-message text until he says "good." Then
write.

### He'll correct you fast

"No sed." "opencode is on PATH." Take corrections seriously, apply
immediately, don't reopen the debate. He knows his environment.

### Parallel workers may land changes

Other agents work on beans in parallel. Files you read earlier may
have been modified. If you see a `<system-reminder>` about a file
being modified, he already knows. Don't revert.

### Push back when you disagree

The best moments in a planning session are when he asks "What do you
think of X?" and you answer "Actually, Y because Z." He values that
friction. A planner who just says "yes" to everything is missing the
point. His gut is usually right but he wants someone to stress-test it.

### Don't pad

"Terse" and "code over English" are the same instinct — don't pad,
don't explain the obvious, trust the reader. Internalize this on
day one.

## Project Knowledge

### Architecture anchors

- Compaction with `firstKeptEntryId` — transcript is append-only, the
  prompt is a view over it
- `Comm` protocol for multi-UI support (CLI, ACP, Memory, future
  web/Discord/Slack) — chat flow is UI-agnostic. Named "comm" (ship
  communications) to avoid overloading the word "channel" with
  Discord's own "channel" concept.
- Config-driven: `{:dev true}` toggles development features; config
  can pull from env via `${VAR}` substitution

### Conventions

- Clojure idioms: protocols for DI points, not maps of fns
- c3kit-apron and c3kit-wire for infrastructure (babashka-compatible
  as of 2.5.1)
- bb-first but not bb-only — if something breaks in bb, fix it
  upstream (we fixed `c3kit.apron.refresh` for bb)
- File:line targeting: `bb features features/foo.feature:42` runs one
  scenario
- Auto-discovered step files via glob — new step files in
  `spec/isaac/features/steps/*.clj` are picked up automatically

### Test infrastructure

- `isaac.spec-helper/with-captured-logs` — wraps each spec/`it` in
  log capture; assertions read from `@log/captured-logs`
- `isaac.spec-helper/await-condition` — polls every 1ms for up to 1
  second; replaces `Thread/sleep` for waiting on async state
- `MemoryComm` (see `isaac.comm.memory`) — primary test vehicle
  for chat flows; records events in an atom
- Grover is the in-memory LLM test double; queue responses with
  `the following model responses are queued:` and EDN vectors for
  chunked streaming
- Speclj 3.12.3 gives line-numbered failures
- `#comment` column in matcher tables is ignored by assertions
- For ACP proxy specs, always set `:acp-proxy-eof-grace-ms 0` in test
  opts. The default 50ms grace period fires after every request and
  dominates spec time.

### Key files to know

- `src/isaac/cli/chat.clj` — `process-user-input!` is the core chat
  flow; coupling point for many beans
- `src/isaac/session/storage.clj` — JSONL + JSON index persistence
- `src/isaac/context/manager.clj` — compaction logic
- `src/isaac/comm/*.clj` — Comm protocol implementations
- `src/isaac/acp/*.clj` — ACP agent mode (JSON-RPC over stdio)
- `spec/isaac/features/steps/session.clj` — the big step definition
  file; domain-scoped
- `features/` — Gherkin feature files, grouped by subsystem
- `bb.edn` — all the `bb` task definitions; step files auto-discovered

### Security posture

Three principles cross every part of the config and tool surface:

- **Secrets are redacted by default.** `${VAR}` substitutions appear
  as `<VAR:redacted>` or `<VAR:UNRESOLVED>` in readout — the
  UNRESOLVED variant doubles as a diagnostic signal ("you forgot to
  set this env var"). Full values require `--reveal` with typed
  confirmation: the command refuses to proceed unless the literal
  word `REVEAL` appears on stdin. This blocks accidental
  `isaac config --reveal | clipboard`, shell-history leaks, and CI
  scripts that shouldn't do it.
- **Config lives outside crew filesystem boundaries.** Crew members
  can read/write inside their quarters (`~/.isaac/crew/<id>/`) and
  explicitly whitelisted directories, but never `~/.isaac/config/`.
  That prevents a crew from introspecting or escalating its own
  privileges — no prompt injection can rewrite the soul or expand
  the tool allowlist because the crew literally can't see the file.
- **Self-modification is explicit opt-in.** Crew can't update their
  own soul by default. A future "self-refining crew" would be a
  discrete feature with an explicit flag (`:allow-self-modify-soul
  true`), not the default behavior.

### Effort

Effort is Isaac's universal cross-provider knob for controlling how hard a
model "thinks". It is an integer 0–10; the built-in default is 7.

**Resolution chain** (first non-nil wins):
session → crew → model → provider → `defaults.effort` → 7

Each config tier accepts an `:effort` integer. Models that should not receive
an effort value (non-reasoning models) declare `:allows-effort false` in their
model config; the universal layer then omits `:effort` from the request entirely
and each API adapter sees no effort to translate.

**Per-provider wire translation** happens in the API adapter, not in the
universal layer:

| API adapter          | Wire field                              | Mapping (int → string) |
|----------------------|-----------------------------------------|------------------------|
| openai-completions   | top-level `reasoning_effort`            | 1–3→low, 4–6→medium, 7–10→high |
| openai-responses     | nested `reasoning.effort` + `summary:"auto"` | same bucketing   |
| anthropic-messages   | `thinking.budget_tokens` (future bean)  | separate mapping       |
| ollama, other        | ignored                                 | —                      |

**Session-level override**: `/effort N` (0–10) sets session effort; `/effort`
shows the current effective value; `/effort clear` removes the override.

**Effort 0** passes through to the request map (so steps can assert on it)
but each API adapter treats 0 as "omit the field" — same effect as
`allows-effort false` but user-controlled per session.

### Config vs state discipline

Isaac splits these cleanly and every new feature should honor it:

- **Config** (`~/.isaac/config/*.edn` + companion `.md` files):
  declarative intent, hand-editable, version-controllable. Never
  mutated at runtime.
- **State** (`<state-dir>/*`): mutable runtime data — sessions,
  transcripts, last-run timestamps, queued deliveries.

When scoping a new feature, identify which parts are intent
(config) vs observation (state) and place them accordingly. Avoid
files that mix both — a cron job's `:expr` / `:crew` / `:prompt`
belong in config; its `:last-run` / `:last-status` belong in state.
Config files that mutate at runtime are a smell.

The `.md` companion pattern (crew soul, cron prompts, future
fields) is a shared helper — don't re-implement it per field.

## Project-Specific Traps

### Worker premature-close

A worker marks a bean `completed` or tags it `unverified` before `bb verify` is green.
The bean looks done; the verifier reviews stale source; the failure ships.

Detection layer: the pre-push hook runs `bb verify` automatically on `.clj`,
`.feature`, and `.edn` changes and rejects the push if anything is red.
If the hook is bypassed (`--no-verify` or hook not installed), CI on `main`
runs the same suite and **fails the run**. Check `gh run list` after a push.

Prevention: never bypass the hook. One-time setup per checkout: `bb hooks:install`.

### Deceptive default fallback (Isaac example)

Early `isaac config` on a fresh install printed a crafted-looking config
the user never wrote — a new operator couldn't tell what was real vs
fabricated. Prefer fail-fast with guidance ("no config found, create
`~/.isaac/config/isaac.edn`") or an explicit `init` bootstrap, not
silent materialization.

## Logging — Registered Info+ Events

Isaac uses structured logging via `isaac.logger` (`log/info`,
`log/warn`, `log/error`, `log/debug`). Only **info and above** are
spec-worthy in feature assertions. See the
[logging skill](https://raw.githubusercontent.com/slagyr/agent-lib/main/skills/logging/SKILL.md)
for general principles.

| Event | Level | Source | Coverage |
|---|---|---|---|
| `:session/created` | info | `session/storage.clj` | `features/session/storage.feature` |
| `:session/opened` | info | `session/storage.clj` | `features/session/storage.feature` |
| `:session/compaction-started` | info | `session/logging.clj` | `features/context/compaction.feature` |
| `:session/compaction-failed` | error | `cli/chat/single_turn.clj` | `features/context/compaction.feature` |
| `:session/compaction-stopped` | warn | `cli/chat/single_turn.clj` | commented in `features/context/compaction.feature` |
| `:chat/response-failed` | error | `cli/chat/single_turn.clj` | `features/chat/logging.feature` |
| `:chat/error-not-stored` | warn | `cli/chat/single_turn.clj` | untestable without storage failure injection |
| `:tool/execute-failed` | error | `tool/registry.clj` | `features/tools/execution.feature` |
| `:server/starting` | info | `cli/server.clj` | `features/server/command.feature` |
| `:server/started` | info | `cli/server.clj` | `features/server/command.feature` |
| `:server/dev-mode-enabled` | info | `server/app.clj` | `features/server/dev-reload.feature` |
