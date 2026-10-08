---
# isaac-3dnw
title: 'isaac-agent: system-section berth — modules contribute ordered sections of the system prompt'
status: completed
type: feature
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T22:00:48Z
parent: isaac-pcm3
---

Part of the prompts-and-habits epic (isaac-pcm3). Design and scenarios approved by Micah 2026-10-08.

## Problem

Nothing outside `isaac-agent` can add text to the system prompt. The agent's berths are tools, tool-providers, llm-api, slash-commands, provider, provider-template, resource-pool-types, context-mode, session-observer and comm. The system text is built from four positional arguments in `isaac.agent.llm.prompt.builder/build-system-text` (soul, boot files, rules text, skill menu text), filled by direct calls to the prompt catalog in `isaac.agent.drive.turn` (around line 1407).

## Proposal

A new berth, working name `:isaac.agent/system-sections`. A contributor is called once per turn with the turn's facts (crew id, session cwd, config, root, fs) and returns:

- section text, or nil for nothing;
- optionally, tool names to grant for the turn (the skill menu grants `prompt__load` and `prompt__list` this way today).

The soul stays in the agent: it is a crew field and always comes first.

## Constraints

- **Stable order.** The system prefix is cached (isaac-s0ho). Sections need a declared order, and sections that change often must sort after those that do not.
- **Sections keep their identity.** `isaac.agent.drive.accounting` reports tokens per part (`:boot-files-tokens`, `:skill-menu-tokens`, and so on). The berth should carry a section id so accounting, `/status` and compaction keep a per-section breakdown instead of one lump.
- **Never blocks a turn.** A contributor that throws or is slow is skipped and logged; the turn proceeds. Same spirit as the ruling that turns never wait on MCP.
- **No provider knowledge.** A section is text plus tool names. Nothing API-specific crosses the berth.

## First step

Convert the existing three sections (boot files, rules, skill menu) to in-agent contributors of the new berth, with no behavior change. That proves the berth before anything moves out of the repo.

## Touch points today

`builder.clj`, `drive/turn.clj`, `drive/accounting.clj`, `session/compaction.clj`, `session/context.clj`, `session/cli.clj`, `bridge/status.clj`, `llm/api/messages.clj`, `llm/api/protocol.clj` all pass the four named pieces around.

## Likely repo scope

`isaac-agent`.

## Approved design (Micah, 2026-10-08)

- Berth: `:isaac.agent/system-sections`. Manifest entry: `{<section-id> {:factory <sym> :order <int>}}`.
- The contributor is called once per turn with the turn's facts (crew id, session cwd, config, root, fs) and returns `{:text "..." :tools #{"name" ...}}`, or nil for nothing.
- `:order` is a position, not a priority. Nothing is dropped. Sections are sorted ascending by `:order`, section id breaking ties, and joined with blank lines after the soul.
- The number belongs to the section. A contributor orders its own content (rules stay global-before-project, then by name).
- Built-ins take 100 (boot files), 200 (rules), 300 (skill menu). A module's section defaults to 500. Dreamed habits will take 900.
- The soul has no number; it is always first and stays in the agent. The agent's trailing framing (session identity, nonce, tool-batching hint) is not part of the berth.
- Tokens are logged per section as `<section-id>-tokens` on `:turn/request-sent`. The existing `boot-files-tokens`, `rules-tokens` and `skill-menu-tokens` keys are unchanged.
- A contributor that throws is skipped; the turn runs. Logged at warn as `:system-section/failed` with `:section` and `:module`.

## Scenarios (committed `@wip` on isaac-agent main f771ca6, `features/module/system_section_extension.feature`)

1. a module's section appears in the cached system prompt, built from the turn's crew (line 39)
2. sections render in their declared order, after the soul (line 45)
3. a section can grant tools for the turn (line 58)
4. a contributor that throws is skipped and the turn still runs (line 66)
5. each section's tokens are reported separately (line 87)
6. activating the module registers its section (line 101)

## Step ledger

| Step | Status |
|---|---|
| `an Isaac root at "…"` | existing |
| `the isaac file "…" exists with:` | existing |
| `the isaac EDN file "…" exists with:` | existing |
| `the following sessions exist:` | existing |
| `the following model responses are queued:` | existing |
| `the user sends "…" on session "…"` | existing |
| `manifest berths are processed for the loaded config` | existing |
| `the prompt "…" on session "…" matches:` | existing |
| `the prompt has tools:` | existing |
| `session "…" has transcript matching:` | existing |
| `the log has entries matching:` | existing |

No new steps. New test code is two fixture modules under `isaac-agent/modules/`, alongside `isaac.slash.echo`:

- `isaac.section.beacon`: section `:beacon` (default order) with text `Beacon lit for <crew id>.`, granting the tool `beacon__ping`, which the module also contributes through the tools berth.
- `isaac.section.squall`: section `:squall` whose contributor always throws.

## Acceptance

- `@wip` is removed from `features/module/system_section_extension.feature` and all six scenarios pass.
- No behavior change for the built-in sections: these features pass with no edits to their scenarios: `features/prompts/rules.feature`, `features/prompts/skill_activation.feature`, `features/prompts/prompt_tools.feature`, `features/prompts/session-identity.feature`, `features/session/request_accounting.feature`.
- `build-system-text` no longer takes boot files, rules text and skill menu text as separate positional arguments; `drive/turn.clj` no longer calls the prompt catalog to build the system text.
- The berth is declared in `resources/isaac-manifest.edn` with a description, and the agent handbook chapter mentions it.
- The contributor contract uses the extend-plus-defaults pattern if it is a protocol; `bb jvm-spec` passes.

```
cd isaac-agent && bb features features/module/system_section_extension.feature && bb features features/prompts features/session/request_accounting.feature && bb ci && bb jvm-spec
```


feature-baseline: isaac-agent f771ca6c9ab7600cb803f24200470ae7d476d956
feature-blob: isaac-agent features/module/system_section_extension.feature 951bc96340bd73ed0ff26b46a89c86b7949c398b

## Worker checkpoint (2026-10-08)

Done: implemented ordered system-section berth on `isaac-agent` branch `bean/isaac-3dnw` (c1ef3cb, pushed); six new scenarios, prompt regression features, `bb ci` (1925 specs; 896 features, one preexisting pending), and `bb jvm-spec` passed. `bb bean-gate verify isaac-3dnw --dir isaac-agent=../isaac-agent-isaac-3dnw` returned PASS before landing. Rebasing on `origin/main` at 2bcf03b and rerunning `bb ci` passed.

Next: landing blocked by `git merge --squash bean/isaac-3dnw` against `isaac-agent` main 2311a74; conflict in `bb.edn` because isaac-quji added `isaac.slash.semaphore` to the same dependency map where this branch adds both `isaac.section` fixtures. Aborted the squash with `git reset --hard origin/main` in the dedicated main worktree; bean branch remains clean and pushed. Per gated landing protocol, do not resolve the conflict blind; planner must direct/adjust handoff. Resume at `isaac-agent-izc1/bb.edn:29` after planner response; rebase `isaac-agent-isaac-3dnw` against current `origin/main`, then rerun suite and gate before landing.

## Planner note (2026-10-08)

Keep both. `isaac.slash.semaphore` is isaac-quji's fixture, already on main. `isaac.section.beacon` and `isaac.section.squall` are this bean's. The squash conflict in `bb.edn` is the dependency map taking both lines. Resolve by keeping `isaac.slash.echo`, `isaac.slash.semaphore`, `isaac.section.beacon`, and `isaac.section.squall`. No other file was in conflict. Rebase onto current `origin/main`, rerun the gate, then land.

## Landed on main (2026-10-08)

main-sha: isaac-agent 477f5fa257f11a60e12a326295074a88ce356240

Rebased onto origin/main with all four fixtures in bb.edn. `bb ci` (1919 specs, 903 features, 1 preexisting pending), `bb jvm-spec` (1919 specs), and `bb bean-gate verify isaac-3dnw --dir isaac-agent=../isaac-agent-izc1` passed on the landed squash commit.
