---
# isaac-nwj3
title: 'Prepared prompts: user-defined commands + skills'
status: draft
type: epic
priority: normal
created_at: 2026-05-26T00:20:55Z
updated_at: 2026-05-26T00:49:06Z
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
