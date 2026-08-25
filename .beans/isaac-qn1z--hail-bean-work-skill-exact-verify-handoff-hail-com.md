---
# isaac-qn1z
title: 'hail-bean-work skill: exact verify-handoff hail command; never probe the CLI with placeholders'
status: todo
type: task
priority: high
tags:
    - toolbox
created_at: 2026-08-25T22:45:37Z
updated_at: 2026-08-25T22:45:37Z
---

Repo: **isaac** (`.toolbox/skills/hail-bean-work/SKILL.md`; also
`.toolbox/commands/work.md` "Hail-driven bootstrap").

## Problem

The work skill's "Hand off to verify" section says only "Hail verify
band/session per deployment convention". On 2026-08-25 the isaac-work-1 agent
spent three `isaac hail send --help` calls in six minutes trying to discover
the form, then ran a syntax probe with placeholder values
(`--reply-to x --params '{:bean-id "t"}'`) that enqueued live hail `9164c8dc`,
burned a verify turn and a plan turn, and paged Micah. The doc gap caused the
probe; the missing `--dry-run` (sibling bean, isaac-hail) made the probe
expensive.

## Change

Replace "Hand off to verify" with a copy-pasteable section:

1. `beans update <id> --tag=unverified` (stay `in-progress`), commit + push
   `.beans/`.
2. Send the verify hail. The band name comes from the delivery's data block
   (`:verify-band`, currently `isaac-verify`); the reply-to is the id of the
   hail you are working (`:id` in the delivery, e.g. `fdfd518e`):

       isaac hail send --band <verify-band> --reply-to <this-hail-id> \
         --params '{:bean-id "<bean-id>"}'

   The band template supplies the prompt; do not pass `--prompt` unless you
   need to add notes for the verifier.
3. Conflict / clarification goes to `:plan-band` the same way, with a
   `--prompt` explaining the question.

Plus a short **Do not probe the CLI** rule: every `isaac hail send` that parses
is a real send. Use `--help` to read flags, and `--dry-run` (once the
isaac-hail bean lands) to check syntax. Never run it with placeholder values
like `x` or `t`.

Mirror the same command (one line) in `work.md` "Hail-driven bootstrap" so an
agent that reads only work.md still finds it.

## Acceptance

- SKILL.md and work.md contain the literal command above with the three
  placeholders; no "per deployment convention" hand-wave remains.
- The `hail-bean-verify` / `hail-bean-plan` skills live outside this repo
  (`~/.isaac/prompts/skills` on zanebot); if they carry the same vague
  wording, note it in the bean body for a follow-up rather than editing them
  here.
- Docs-only bean: no product code or tests. Hand off `tag=unverified` as usual.
