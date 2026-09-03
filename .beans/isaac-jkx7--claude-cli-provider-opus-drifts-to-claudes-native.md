---
# isaac-jkx7
title: 'claude-cli provider: opus drifts to Claude''s native <invoke> tool syntax; unparsed calls end the turn early with fabricated results'
status: draft
type: bug
priority: high
tags:
    - claude-cli
    - tool-protocol
created_at: 2026-09-03T22:20:42Z
updated_at: 2026-09-03T22:20:42Z
---

Observed 2026-09-03 after scrapper/prowl moved to :claude-opus (claude-cli provider) during the grok credit outage.

**isaac-lqbc** (hail 1aeaacc1, isaac-work-1, 21:58–22:05): the turn ran 8 real tool calls in isaac's `<tool_call>{json}</tool_call>` fence format, then the model switched mid-turn to Claude Code's native format — `<invoke name="exec__run"><parameter name="command">…</parameter></invoke>` — followed by a fabricated tool result ("OK / b55d4964 plan: draft isaac-lqbc…", not the real git log). `isaac.llm.api.claude-cli/parse-tool-calls` only recognises the fence (`tool-call-open`), so the invoke block was treated as reply text: the drive ended the turn as a verdict, the hail was marked :delivered, no claim, no branch — the bean sat in todo looking dispatched. **tono-vac8** on tono-work-1 (22:06, 46s, executed-tools []) is the same failure: 1932 output tokens, stored content ": parens 3462 3462", one `<invoke` block. Rate today: isaac-work-1 2 of 48 assistant messages contain `<invoke`; tono-work-1 1 of 2; isaac-work-2 0 of 177.

Why it matters: a drifted cycle is silent — no error, no escalation, the delivery looks successful, and the model may hallucinate the tool output it never got. This is the textual tool protocol's structural weakness (noted 2026-09-02: "tool-call fidelity depends on the model honoring the contract").

Fix (all three; the first makes it robust):
1. `parse-tool-calls` also accepts Claude's native call shapes: `<invoke name="X"><parameter name="k">v</parameter>…</invoke>` (optionally inside `<function_calls>`), mapping parameters to the JSON arguments map (JSON-looking values parsed, else strings). Both syntaxes yield identical tool-call maps.
2. A cycle whose text contains an unparsed call-shaped block (`<invoke`, `<function_calls`, or a fence that failed to parse) is a PROTOCOL VIOLATION, not a reply: the drive re-prompts the same cycle once with a corrective message quoting the contract, logs `:claude-cli/tool-syntax-drift`, and only then fails the turn with `:error :tool-protocol` (never a silent verdict). Hail treats that error as a retryable attempt, not :delivered.
3. Strip model-written text after a parsed call block before execution so fabricated results never enter the transcript.

Runnable acceptance to write (@wip, features/llm/claude_cli*.feature): (1) a scripted claude-cli response using the invoke syntax executes the tool exactly like the fence; (2) fence-then-invoke in one turn executes both; (3) an unparseable call-shaped block triggers one corrective re-prompt and, if it persists, ends the turn with :error :tool-protocol and the hail delivery is NOT marked delivered; (4) text after a call block is not persisted as assistant content. Related: isaac-ozv9, isaac-kn7y, isaac-vuto (claude-cli usage stamp).
