---
# isaac-nwj3
title: 'Prepared prompts: user-defined commands + skills'
status: draft
type: epic
created_at: 2026-05-26T00:20:55Z
updated_at: 2026-05-26T00:20:55Z
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
