---
# isaac-iea2
title: "Split 'the output contains' into 'the stdout contains' + 'the reply contains'"
status: completed
type: task
priority: low
created_at: 2026-04-23T16:59:47Z
updated_at: 2026-04-23T20:17:09Z
---

## Description

'the output contains' (cli.clj:147) reads :output from two sources: stdout for CLI process invocations, and MemoryComm reply for in-memory turns. The name implies stdout, which misleads when the scenario is comm-neutral.

Split into:
- 'the stdout contains X' — CLI-scoped, reads the captured stdout writer directly
- 'the reply contains X' — comm-neutral, reads the reply the user saw (MemoryComm output, or the analogous reply in other comms)

Same split for siblings: 'the output matches:', 'the output lines match:', 'the output lines contain in order:', 'the output does not contain X', 'the output has at least N lines', 'the output eventually contains X'.

Update feature files: CLI features → stdout variants; bridge/session/drive features → reply variants.

Acceptance:
1. The new stdout and reply step variants are registered
2. grep -rn 'the output' features/ returns no matches
3. bb features and bb spec pass

