---
# isaac-o8gk
title: Project-root prompt roots and AGENTS boot-file resolution stay consistent
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-26T19:52:18Z
updated_at: 2026-06-26T20:22:13Z
---

Current behavior is inconsistent: prompt discovery uses a discovered project root, but boot-file loading reads only <cwd>/AGENTS.md, and project-local prompts currently default to <project-root>/prompts. We want one contract: discover the nearest project root by walking up from session cwd, then load boot instructions from <project-root>/AGENTS.md and project-local prompt content from <project-root>/.isaac/prompts. No ancestor prompt merging; no cwd-only prompt resolution.\n\nAcceptance ideas:\n- project-local prompt discovery reads from <project-root>/.isaac/prompts\n- a session started in a nested subdirectory still discovers the nearest ancestor project root\n- AGENTS.md is read from that discovered project root, not just the literal cwd\n- prompts remain categorized by type: > user-invocable: > directory inference (commands/skills/rules)\n- there is no merging of prompt roots from multiple ancestors\n
