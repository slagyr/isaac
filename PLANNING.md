# Planning Guide for Isaac

Advice for the next agent that works with Micah as a planner for Isaac.

## Gherkin Style

### Entertaining tests

Test data should be fun to read. A sad lemon in the fridge is better
than "test value 1". Hieronymus's emergency lettuce beats "sample
content". Tests are documentation — make people smile when they read
them.

### Tables over narrative, always

Micah prefers code-like precision. If you find yourself writing
"when the client does X", stop and figure out how to say it as a table
of data. Narrative steps hide the protocol details that matter.

### Request/response symmetry

If the request is tabularized, the response is tabularized. Same
dot-path structure on both sides.

### Strip protocol noise, keep semantic anchors

Constants like `jsonrpc: "2.0"` don't belong in tables — the step
definition adds them. But pivotal fields like `id` that correlate
requests and responses DO belong visible. When a field is really
pivotal, promote it to the step phrase:

```gherkin
When the ACP client sends request 30:
  | key    | value          |
  | method | session/prompt |
```

not

```gherkin
When the ACP client sends:
  | key    | value          |
  | id     | 30             |
  | method | session/prompt |
```

### Domain-scoped step names

`ACP client` and `ACP agent`, not just `client` and `agent`. The Isaac
project has its own "agent" concept (main, researcher, etc.), so
unqualified "agent" is ambiguous. Always scope.

### `#comment` column for intent

We added `#comment` as a meta column that matchers ignore. Use it to
document *why* a row has the values it does, especially when values
are calculated:

```gherkin
Given agent "main" has sessions:
  | key                         | totalTokens | #comment                  |
  | agent:main:cli:direct:user1 | 95          | exceeds 90% of 100 window |
```

No more one-off steps just to convey intent.

### Symmetric Given and Then

If `Then session "X" has transcript matching:` accepts columns like
`type, message.role, message.content`, then `Given session "X" has
transcript:` should accept the same columns. The author of a scenario
shouldn't learn two schemas.

### EDN over JSON in table cells

Use `["Once " "upon " "a " "time..."]` (EDN) over
`["Once ","upon ","a ","time..."]` (JSON) for Clojure-native data.
It reads more natively in Clojure step code.

### Async request/response pattern

Every request is fire-and-forget. `When` sends and returns
immediately. `Then the ACP agent sends response N:` awaits and matches
by id. No "pending" or "without awaiting" phrasing needed. Matches the
protocol's actual semantics.

### Regex assertions can hide shape bugs

`the output matches` runs each row as a regex and passes if the
pattern is found anywhere in the output. That means a `(pr-str ...)`
one-liner passes the same assertions as a fully formatted multi-line
render — the content is there, just all on one line. When the *shape*
matters, add a structural assertion alongside:

```gherkin
Then the output matches:
  | pattern   |
  | :crew     |
  | :defaults |
And the output has at least 5 lines
```

This is exactly how the pretty-print bug shipped in `isaac config` —
scenarios only checked that specific keys appeared, never that the
output was multi-line.

### Log assertions on every mutation

Every scenario that performs a mutation or notable lifecycle event
should assert its log entry — `Then the log has entries matching:`.
Missing log assertions let logging silently die; a regression where
a mutation stops emitting info-level events goes unnoticed. Info and
above are spec-worthy; debug traces are not.

Event-keyword convention: `:domain/action` — `:config/set`,
`:session/compaction-started`, `:tool/exec`, `:auth/login`. Keep it
grep-friendly.

## Planning Workflow

### Feature-first, always

Gherkin scenarios come before beads. Draft scenarios in chat as text,
iterate until approved, write to files with `@wip`, commit, THEN
create the bead. Never write production code before scenarios exist.

### Beads without scenarios stay deferred

A bead can be *created* as a deferred placeholder (title, rough
description, design notes) to capture intent. But it only becomes
eligible for the active queue — opened, pickable by a worker — after
its Gherkin scenarios are drafted, committed with `@wip`, and
referenced from the bead description or acceptance. An open bead
with no scenarios has no acceptance contract; workers can't verify
they're done.

### Scenarios force abstractions

When you want a real seam (not cargo-culted), write a scenario that
exercises it from the outside. `MemoryChannel` forces the `Channel`
protocol to be real. An ACP compaction scenario forces ACP to use the
shared chat flow. Pick the scenario that would break if someone took
the lazy path.

### `@wip` is the implementation contract

When you commit scenarios with `@wip`, that's the spec for the
implementation bead. The bead's Definition of Done is "remove `@wip`
and the scenario passes." Don't change the scenario semantics during
implementation — if the implementation and scenario diverge, raise the
mismatch.

### Acceptance criteria must be runnable

Every bead with `@wip` scenarios should include exact
`bb features features/path/to/file.feature:LINE` commands. Don't write
"verify scenario X passes" — write the exact command. Workers will
copy-paste.

### Existing scenarios that change behavior get `@wip`

When an existing scenario's expected behavior changes (not adding a
new scenario, changing an existing one), mark the whole scenario
`@wip` until the implementation catches up. Don't leave
passing-but-wrong scenarios. Failing scenarios ARE the spec for the
next bead.

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

Other agents work on beads in parallel. Files you read earlier may
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

### Draft in small chunks

When drafting scenarios (or any dense code), show one or two at a
time — not a whole feature file. Micah reads in a terminal pane and
long blocks overflow the view. Draft → confirm → move on. Applies to
step definitions, schemas, and other multi-item structures too. A
rejected batch of twelve scenarios is worse than twelve single
scenarios reviewed one-by-one.

## Traps to Avoid

### Worker premature-close

Beads get closed before the work is actually done. Multiple beads in
this project have been closed 2-3 times. Always verify with
`bb features <file:line>` before trusting. Always check that `@wip`
was removed AND the scenario passes.

### Narrative steps for readability

Avoid reaching for English prose because it "reads better." Micah will
push back every time. Tables are clearer if you commit to them.

### One-off steps that hide intent

Steps like `the session totalTokens exceeds 90% of the context window`
feel intent-revealing but they proliferate and can't be combined. The
`#comment` column pattern handles this cleanly — don't fall back to
one-offs.

### Forgetting the `@wip` convention

When an existing scenario's expected behavior changes, mark the whole
scenario `@wip` until the implementation catches up. Don't leave
passing-but-wrong scenarios.

### Scoping too broadly

Epic beads are fine. "Let me implement this in one bead" when the
scope is 200 LoC across 5 files is not. If a bead touches
`process-user-input!` or other coupling points, it's probably too big.

### Casual "OpenClaw" references

There are multiple things called "OpenClaw" and there's also an older
version of Isaac that was branched off for preservation. Don't
reference OpenClaw casually — ask what he means, or be specific about
which thing you're looking at.

### Dated defers bite back

`bd defer <id> --until=2026-05-01` on a backlog bead quietly ticks
down and surprises whoever's triaging when the date arrives. For
"not now, no specific deadline," use `bd defer <id>` with **no**
`--until` flag. Un-defer with `bd update <id> --status=open` — NOT
`--defer=""`, which clears the date but leaves the status deferred.

Rule of thumb: only use dated defers when there's a real external
dependency (a release date, an upstream fix, an infra upgrade).
Everything else is undated backlog.

### Deceptive default fallbacks

If the system silently falls back to a bundled default that looks
like user content, it lies. Early `isaac config` on a fresh install
printed a crafted-looking config the user never wrote — a new
operator couldn't tell what was real vs fabricated. Prefer fail-fast
with guidance ("no config found, create `~/.isaac/config/isaac.edn`")
or an explicit `init` bootstrap, not silent materialization.
Applies to any "helpful" fallback that looks identical to an
intentional configuration.

## Project Knowledge

### Architecture anchors

- Session keys: `agent:<id>:<channel>:<chatType>:<conversation>`
- JSONL transcripts + JSON index, OpenClaw-compatible format (in
  progress — see isaac-z59)
- Compaction with `firstKeptEntryId` — transcript is append-only, the
  prompt is a view over it
- `Channel` protocol for multi-UI support (CLI, ACP, Memory, future
  web/Discord/Slack) — chat flow is UI-agnostic
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
- `MemoryChannel` (see `isaac.channel.memory`) — primary test vehicle
  for chat flows; records events in an atom
- Grover is the in-memory LLM test double; queue responses with
  `the following model responses are queued:` and EDN vectors for
  chunked streaming
- Speclj 3.12.3 gives line-numbered failures
- `#comment` column in matcher tables is ignored by assertions

### Key files to know

- `src/isaac/cli/chat.clj` — `process-user-input!` is the core chat
  flow; coupling point for many beads
- `src/isaac/session/storage.clj` — JSONL + JSON index persistence
- `src/isaac/context/manager.clj` — compaction logic
- `src/isaac/channel/*.clj` — Channel protocol implementations
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

## Final Advice

Feature-first, tables over prose, forcing abstractions through tests,
concise communication, and trust that failing `@wip` scenarios are
the best documentation of what's next. The rest follows.
