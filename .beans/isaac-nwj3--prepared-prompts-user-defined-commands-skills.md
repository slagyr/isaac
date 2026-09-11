---
# isaac-nwj3
title: 'Prepared prompts: user-defined commands + skills'
status: draft
type: epic
priority: normal
created_at: 2026-05-26T00:20:55Z
updated_at: 2026-05-26T04:15:19Z
---

## Motivation

Until now Isaac has had no support for prepared/reusable prompts. The hail
feature makes the need immediate: a planner hails the project workers + session
to "start work on bean X." The *how-to-work-a-bean* instructions are largely the
same across projects and shouldn't be duplicated in every hail; workers are also
expected to apply standing skills (TDD, gherclj). We need named, reusable,
parameterized prompts that any producer (hail, cron, CLI) can invoke.

## Two concepts

- **Command** — a named, *parameterized* prompt template you invoke
  ("work a bean"). The entry point.
- **Skill** — a reusable prompt *fragment* you include (TDD, gherclj). Composed
  into commands (and/or crews).

## Recommended approach (reuse existing seams)

Isaac already has the building blocks:
- the **`.md`-companion pattern** (crew souls, cron prompts — "one source,
  inline or `<name>.md`", already a shared helper), and
- **slash-command triage at the bridge**.

So:
1. **User-definable slash commands in config.** A command =
   `config/commands/<name>.md` holding a prompt *template* with `{{param}}`
   placeholders (spirit of config `${VAR}` substitution). The bridge today only
   knows *handler* commands (`/crew` replies); add a second kind —
   **prompt-template** commands that **expand into the turn's input** rather than
   reply.
2. **Skills as includable fragments**, `config/skills/<name>.md`. A command
   declares the skills it pulls in (e.g. `work` → `[:tdd :gherclj]`); the
   resolver appends their bodies. Define each skill once.
3. **Producers reference a command, never inline the prompt.** A hail carries
   `/work isaac-xyz` as its prompt; at delivery the worker's turn input is
   `/work isaac-xyz`, the bridge expands it (substitute `bean`, inline the work
   procedure + TDD + gherclj). The full work prompt lives once in
   `config/commands/work.md`. Cron + CLI get the same expansion for free.

Net: cross-project sameness (command is install-level config; only args vary),
no per-hail duplication, skills applied without copy-paste.

## Open design forks (settle before speccing children)

- **One concept or two** — lean two (command = entry/parameterized; skill =
  fragment/included), sharing md-companion + templating machinery.
- **Where skills attach** — command-composed (lean) vs crew/project-default list.
- **Hail reference shape** — slash command in the prompt (`/work isaac-xyz`,
  reuses bridge triage — lean) vs a structured `:command`/`:args` hail field.
- **Templating** — simple `{{param}}` substitution (lean) vs richer.
- **Metaphor name** — ship-coherent name for the domain ("commands" fits; "skills"
  is the odd one — crew "training"? keep "skills"?).

## Scope / MVP

Keep the MVP tight: prompt-template commands + `{{param}}` substitution + skill
includes + the bridge expanding config-defined commands. Defer fancier skill
mechanics (auto-activation, conditional application) until needed.

## Relationship

- Additive — does **not** block the hail framing work (isaac-ffhl / isaac-ho1s)
  or the rest of the Hail epic (isaac-ugx7). It is *motivated* by hail (the work
  hail wants `/work <bean>`), and unlocks richer hail usage.
- Child beans (commands registry, skills registry, bridge prompt-template
  expansion, the `work` command itself, hail-invokes-command) to follow once the
  forks are settled.


## Discovery, disambiguation, and taxonomy

### Configurable roots
- Isaac-native defaults: install `~/.isaac/config/{commands,skills}/`; project `<project>/.isaac/{commands,skills}/`.
- Configurable **extra roots** (a `:prompt-paths` / `:command-paths` / `:skill-paths` config key) so Isaac adapts to a project's existing layout (`.toolbox/`, `.agents/`, `.claude/`) instead of imposing one. Glob-over-roots reuses Isaac's existing module/step auto-discovery pattern.
- Format: **markdown + EDN frontmatter** (reuses cron's single-md-with-frontmatter pattern). Frontmatter carries metadata (params, included skills); body is the prompt.

### Disambiguation
- A file declares itself via a frontmatter **`type:` field** (`command`, `skill`, …) — works for a flat/mixed rats-nest of markdown, layout-agnostic. Discovery = glob roots -> read `type` -> route to the right registry.
- **Typed roots** as a convenience when dirs already separate kinds (`:command-paths`/`:skill-paths` -> type implied by the path).
- Make `type:` an **open enum** so new kinds are just new values on the same machinery.

### Precedence (layered)
- Available set = **union** of names across layers.
- Name collision = **most-specific layer wins**: install < project (crew reserved, see below).

### Crew-specific (reserved, deferred)
- Crew-specific commands/skills will be supported later as **operator-authored config** (`~/.isaac/config/crew/<id>/...`), **never crew-writable quarters** — quarters are writable by the crew, so crew-authored prompts/skills = self-modification / privilege escalation, which the security posture forbids (same trust boundary as the soul). Precedence fork (project vs crew) to settle then; lean install < crew < project for commands, skills additive.

### Taxonomy (what to support)
The agent-agnostic taxonomy (per the toolbox): skills, commands, rules, modes, agents.
- **agents = crews** (already first-class in Isaac) — do not add a parallel type.
- **rules** = always-on instructions -> land in the **system prompt** (cached, like a project-level soul addition); real and useful but distinct from invoked/included prompts, and touches the injection-guard/soul composition. **Deferred future `type:`.**
- **modes** = behavior presets — speculative, **deferred**.
- **MVP = commands + skills.** The `type:` enum makes rules/modes cheap to add later.

### ACP
- Config-defined commands are first-class slash commands (same bridge triage as builtin; prompt-template kind expands into the turn input vs handler kind that replies).
- **Advertise resolved commands over ACP** (ACP available-commands) so ACP clients (Zed, etc.) can list/invoke them with CLI parity; the advertisement must convey each command's params/usage. Follow-up child bean, not MVP-blocking.


### Disambiguation precedence (multi-signal, for cross-agent compatibility)

Support all common signals so foreign files (Claude/toolbox) need no conversion, but fix a strict precedence so conflicting signals are deterministic — most-explicit wins:

1. **`type:` frontmatter** (`command`/`skill`/…) — author stated it. Highest.
2. **`user-invocable:`** — Claude semantic: `true -> command`, `false -> skill`.
3. **Directory / filename** — `commands/…` -> command; `skills/<name>/SKILL.md` or `skills/<name>.md` -> skill. Weakest (positional).

Resolution = fallthrough: `type:` -> else `user-invocable:` -> else path -> else **skip + warn** (never guess).

- On *conflicting* signals (e.g. a `commands/` file with `type: skill`), the higher-precedence signal wins AND we **log a warning** (don't silently hide a misfiled file).
- **Isaac-native files use `type:`** (canonical); the other two signals exist only to ingest foreign files — tolerate inbound, stay clean outbound.
- Skills accept both `<name>/SKILL.md` (name = dir) and `<name>.md` (flat).


## Loading lifecycle

- **Index frontmatter-first at config-load:** scan all roots, parse only each file's frontmatter -> in-memory index (name, type, description, params, included-skills, path). Cheap; scales to many files. Refresh on config/file hot-reload.
- **Bodies loaded lazily** on invocation/inclusion (cache after first read) — don't hold every body resident.
- **Registry** (same shape as tool/comm registries) keyed by `(type, name)`, holding the frontmatter index + a lazy body-loader. Powers command resolution, disambiguation, `/help`-style listing, and the ACP advertisement — all from frontmatter, no bodies needed.

## Command + skills composition

Invoking `/work isaac-xyz`:
1. Load command body (template); substitute params (`bean=isaac-xyz`).
2. Resolve declared `skills:` -> load each skill body.
3. **Assemble into the turn's USER message** — expanded command + inlined skill bodies. That composed text IS the user turn (stored in the transcript, sent to the model).

- **Skills go in the user message, not the system prompt** — per-invocation content, so inlining keeps the cached system prefix clean (same principle as origin framing). The expansion is a real user turn -> persisted normally -> cached as history on later turns (no re-sending).
- The **expanded** prompt is stored/sent, not the raw `/work isaac-xyz`.

## Deferred: model-driven skill auto-activation

MVP = commands that **explicitly include** skills by name (deterministic). The other mechanic — the model auto-activating a skill by matching its `description` to the task — is a separate, later feature. The frontmatter index already captures `description`, so it's ready when wanted.


## Project scoping (two-tier registry)

Project-local commands/skills must NOT live in the global registry — they are scoped to a project directory. Scope is keyed on the **session's cwd / project root**.

- **Global index** — built once at startup from install roots; shared.
- **Per-project indices** — a cache keyed by **project root** (`root -> index`), **built lazily the first time a session whose cwd is in that root resolves a command/skill** (NOT at startup — Isaac is multi-session and a crew can be in any repo). Frontmatter-only scan; refresh on file/config change.
- **Resolution composes per-turn, never globally merges:** available = `global UNION project-index(session.root)`, precedence **project > global**. A session in project A never sees project B's commands (we compose only the one matching index).
- **Project-root detection:** walk up from cwd to a marker (`.isaac/`, fallback `.git`/boot file), else cwd-is-root. (Sub-decision: walk-up vs cwd-is-root; lean walk-up.)
- **Eviction:** `root -> index` cache is small (frontmatter only) — LRU or process-lifetime is fine.
- **Trust flag (future):** project-local commands are repo-authored — loading them runs repo prompts. Within the trust of running a crew in that repo, but a malicious `.isaac/commands/` in a cloned repo is a supply-chain surface. No trust-gating in MVP; consider trust-on-first-use per project later.


## Storage: the session holds the project catalog (supersedes "lazy on first use")

- **Global registry**: process-level, shared, built once at startup (install roots).
- **The live session holds its project catalog** — a list of commands (name + file [+ description/params]) and an index of skill frontmatter (+ file) for the session's project root. **In-memory runtime state on the live session, NOT persisted** (derived from files; re-scan is cheap; persisting derived data violates config/state discipline). Rebuilt on session load; refreshed on file change (hot-reload).
- **Built at session open/activation** (resolve root -> frontmatter-scan global + project -> store on session) — NOT lazily at first resolution. So the catalog exists before any turn resolves a command (fixes the chicken-and-egg).
- **Resolution per turn** = `session.project-catalog UNION global`, project wins. Body loaded lazily from the winning layer.
- Root detection at session open: walk up from cwd for any `.isaac/` or configured prompt dir; else cwd-is-root.

## Model-facing skills tool — deferred (with auto-activation)

- **Commands** are user/producer-invoked -> the model needs no discovery tool (client discovery = ACP advertisement).
- **Skills (MVP)** are command-included by name (deterministic) -> the model doesn't choose them.
- A model-facing `skills` tool (`list` + `load <name>`) is the right mechanism for the **deferred model-driven activation** (load-on-demand beats injecting every skill description into every prompt). The session already holds the skill frontmatter index, so it is ready to power that tool when built.


## Catalog lifetime — it is a cache, resolved per turn (supersedes "durable on session")

- The catalog is **derived, cheap to rebuild** (frontmatter scan) -> treat as a **cache, not durable state**; files are the source of truth.
- **Resolved per turn**: the bridge needs it to triage `/work` and prompt-build needs it to expand, so it is resolved each turn. Global stays cached from startup (install roots rarely change); only the **project roots** are scanned per turn (a few small frontmatter reads — negligible).
- **Session-level caching is optional** (skip re-scan across a resident session's turns; invalidate on file change). Not authoritative, no required lifetime, fine to rebuild anytime.
- (Supersedes the earlier "stored durably on the session, built at session open" wording.)

## Deferred: model-driven skill activation (the "use me when you code" case)

MVP sidesteps this — commands **pre-include** skills (the `/work` command loads TDD + gherclj), so the worker gets them without the model discovering anything. The ad-hoc case (user says "write code", model should auto-apply project TDD) needs activation, deferred. Mechanism when we build it:

- **Advertise**: inject skill **name + description** (not bodies) into context — short, stable per project, cacheable. The model reads "TDD — use when coding", matches the task, decides to activate.
- **Load on demand**: activation pulls the skill **body** via a **tool** (`load_skill <name>` -> returns body into the turn). The session frontmatter index already holds the descriptions, ready to feed the advertisement.


## MVP instrumentation: time the per-turn catalog resolution

Load cwd (project) prompts at the **start of each turn** (simple; global cached at startup). **Instrument it:** emit a **debug**-level timing log on resolve (`elapsed-ms` + file/command/skill counts) so we gather real numbers before deciding whether session-level caching is worth it. (Info would flood per-turn logs; debug is opt-in for data-gathering.)

## Deferred activation: where skill name+descriptions are injected

Unlike per-turn origin framing (current user turn, uncached), the **skill menu is per-project and stable** — it does not change turn-to-turn within a session — so it goes in the **system prompt** and **caches cleanly** (soul + guard + skill-menu block all in the cached prefix).

- Render a compact block from the session frontmatter index: `name: description` lines ("Available skills (load with the skill tool when a description fits): - tdd: use when writing code; ...").
- The model matches the task to a description and calls **`load_skill <name>`** -> body comes into the turn. Descriptions cached in the prompt; bodies pulled on demand.
- **Large skill sets:** injecting every description bloats the cached system prompt -> fallback to a `list_skills` tool (no always-on menu), possibly threshold-based.
- **Overlaps with the future `rules` type** — both are project-scoped, stable, system-prompt content; likely share one "project context block" slot in the cached system prompt.


## Cache impact of the skill menu (deferred feature)

- **MVP is cache-clean:** skills go in the user turn via command expansion; no skill content in the system prompt, no menu -> the cached prefix never depends on the skill set. Skill edits do not touch the MVP cache.
- **Menu-injection (deferred) is the only place skills hit the cache.** Skill-set changes are dev-time edits, not runtime -> a one-time re-cache on a real change is acceptable.
- **Hard requirement: deterministic menu rendering.** Because we re-resolve per turn, the menu must render byte-identically for an unchanged skill set (stable sort by name + stable formatting). Otherwise the cache busts every turn.
- **Knob:** inject menu (always-aware, skill edits bust cache rarely) vs `list_skills` tool-only (cache-stable, model must query). Churny/large skill sets tilt toward tool-only. Decide when building activation.
- Commands are never in the model prompt (user-invoked, advertised to clients via ACP) -> command changes never affect the prompt cache; this is purely the skill menu.
