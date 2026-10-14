---
# isaac-0x75
title: 'isaac-agent: a prompt whose header does not parse is left out, not fatal'
status: in-progress
type: bug
priority: high
created_at: 2026-10-14T23:21:02Z
updated_at: 2026-10-14T23:22:31Z
---

Scenarios approved by Micah 2026-10-14 ("good"). Micah: "A bad skill header should not cause the app to crash like that."

## What happened

2026-10-14 a crew wrote itself a skill through `exec/run` (`cat > prompts/skills/<name>.md <<EOF`). Its description had an unquoted `: `. For eight minutes every Chat message and ACP turn on that deployment failed with SnakeYAML's "mapping values are not allowed here", and those messages were lost. The same thing happened 2026-09-05 with a hand-written skill.

## Cause

`isaac.agent.prompt.catalog/parse-frontmatter` calls `yaml/parse-string` with no guard, and `resolve-catalog` parses every prompt file at once. Any caller fails as a whole:

- the `:skill-menu` system section: the failure is caught (system-section berth), but the whole menu is lost, good skills included;
- the slash provider (`isaac.agent.slash.prompt`): listing and running commands throw, which killed Chat message handling and ACP turns;
- `prompt__list` / `prompt__load`.

A write-time check cannot help: the crew wrote the file with a shell command.

## Rule

- A prompt file (skill, command or rule) whose header does not parse is left out of the catalog.
- Log `:prompt/header-invalid` at warn with `:path` and the parser's message.
- Everything else loads; turns, the skill menu and slash commands carry on.
- `prompt__list` adds one line per broken file: `- <path relative to the isaac root> (broken): its header does not parse; see the log`.

## Work

1. Guard the parse in `isaac.agent.prompt.catalog`; keep broken files out of the entries but record them on the catalog (e.g. `:broken [{:path … :error …}]`) for `prompt__list`.
2. Render the broken lines in the prompt menu `prompt__list` returns.
3. Remove the three `@wip` tags in `features/prompts/broken_header.feature` and make them pass. No new steps.
4. Unit specs for the parse guard (bad YAML, header that is not a map).
5. Handbook: one troubleshooting entry — quote a description that contains `: `.

## Acceptance

- `features/prompts/broken_header.feature`: all three scenarios pass with no `@wip`.
- Existing prompt features (`catalog`, `prompt_tools`, `skill_activation`, `skill_resources`, slash features) unchanged and green.
- `bb ci` and `bb jvm-spec` green; CI green on main.

feature-baseline: isaac-agent 2d794ad748c62dc763c76e89bd454be45fa58872
feature-blob: isaac-agent features/prompts/broken_header.feature 897e120ce6108983587f50aa15d8c9a3f6feb192
