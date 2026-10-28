---
# isaac-4qxr
title: 'Epic: goal-driven crews — borrow what makes Claude Code and Grok Build effective'
status: draft
type: epic
priority: high
tags:
    - agent
created_at: 2026-10-28T08:08:18Z
updated_at: 2026-10-28T08:13:45Z
---

Micah, 2026-10-28: "I find myself using Claude Code and Grokbot because it's just so much more convenient. I think one of the reasons is that those harnesses are really good at focusing on goals and using the tools at hand to achieve those goals. I would like Isaac to be just as useful, if not more useful. I don't know if it's really goals that do the trick but I'd like to borrow ideas from Claude Code and Grokbot."

## Aim

Isaac crews should pursue a task to the end as well as Claude Code and Grok Build do: hold the goal, plan, use their tools well, check their own work, and stop only when it's done or truly blocked. We don't yet know which features make the difference, so the first child bean is a study, not a build.

## Candidates (to be confirmed by the study)

- **A goal that survives.** The task stays in view across many cycles and compactions, rather than scrolling away (Claude Code re-reads instructions after compaction).
- **A working plan / todo list** the model writes and checks off, shown to the person, with a nudge when it goes unused.
- **Done means done.** A turn that ends with a lead-in sentence and no work is not success (isaac-zz6d). Before ending, the model checks the goal is met.
- **Background work and notifications.** Long commands and subagents run in the background and report back (isaac-p5d3).
- **Stateful reminders** when something the model relies on changed (isaac-p5kt).
- **Plan mode:** look and propose before touching anything, when asked.
- **Tool affordances:** exact-replace edits, fast grep/glob, parallel tool calls, and outputs sized to fit (isaac-p69v).
- **Budgets that wrap up rather than cut off** (isaac-9udu, isaac-x4j2).

## First child: the study

Run the same small real task (one bean) through Claude Code, Grok Build and an Isaac crew on comparable models. From the transcripts, compare cycles, tool calls, re-reads, compactions, wall time, and whether it finished. Read Claude Code (from inside) and the Grok Build source for the mechanisms behind any differences. Deliver a short written comparison and a ranked list of gaps, each with a proposed child bean.

## Acceptance

The epic closes when its children do. The study's acceptance: the comparison document exists, names the task and models used, and every proposed child bean is filed as a draft under this epic.

## Direction (2026-10-28, Micah, later the same night)

The setting is Micah's everyday use: talking to Isaac over Toad (ACP), Discord, iMessage and the other comms, not bean work. What's missing there is **proactivity and follow-through**: Isaac should carry a request to the end without being re-prompted, keep working through the obstacles it can clear itself, report back when done, and bring things up on its own when it should. The study's task should therefore include a conversational request made over a comm (e.g. a multi-step errand from Discord), not only a bean.
