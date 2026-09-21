---
# isaac-5n68
title: Per-model turn reminders in model config; GLM gets a batching nudge beside its tool results
status: todo
type: task
priority: normal
created_at: 2026-09-21T04:58:11Z
updated_at: 2026-09-21T04:58:11Z
---

## Why

GLM-5.3 batched 0 of 418 tool responses across two real beans (cgxa, nq4c),
even with `parallel_tool_calls: true` on the wire (isaac-rr7u). Given the
instruction one step from the decision, it batched 4 of 4 probes. Where the
instruction sits matters, and today the only batching hint is in the cached
system prefix, ~100K tokens back.

Claude and GPT batch without being reminded. So whether a model needs a
reminder is a property of the **model**, not of the API adapter: GLM and grok
share the chat-completions adapter, and grok batches.

## Design

1. **Model config declares reminders.** New optional field on the model schema:

   ```clojure
   ;; models/glm-5-3.edn
   :reminders [{:text  "Issue all independent tool calls in this response together; wait only when one call's output feeds the next."
                :after {:single-call-cycles 3}}]
   ```

   No `:reminders` means no reminder, which covers every model we have today.
   It hot-reloads, so the text and threshold can be tuned without a deploy.

2. **The tool loop decides when.** It counts consecutive cycles whose response
   carried exactly one tool call. When the count reaches the threshold, it puts
   the reminder on the request for that follow-up and resets the count. A
   model that ignores the reminder is nudged every N cycles, not every cycle. A
   model that batches never sees it. The count is a tool-loop fact; the loop
   learns nothing about beans or git.

3. **The drive wraps it; adapters only attach it.** The drive already holds
   the nonce, so it wraps the text in the trusted block before handing it
   over. The reminder sits right next to tool output, which is untrusted, so
   it must read as the harness speaking.

4. **Each adapter's existing `followup-messages` attaches it in its own wire
   shape.** No new protocol method, which means no JVM `AbstractMethodError`
   risk and no cross-repo change:
   - chat-completions / responses: a trailing `{:role "user"}` message after
     the tool results
   - messages (Anthropic): an extra `{:type "text"}` block **inside** the
     tool_result user message, since roles must alternate
   - claude-cli, ollama, grover: ignore it. The CLI runs its own loop.

5. **Append-only.** Nothing is stripped, so the prefix cache is never
   disturbed and there is no breakpoint invariant to guard. Reminders stay
   cheap by being rare (conditional), not by being deduplicated. This is how
   Claude Code's own `<system-reminder>` nudges behave.

## Done when

- `:reminders` is in the model schema and validated; editing it on a running
  server takes effect with no restart
- a model with no `:reminders` sends byte-identical requests to today (spec)
- the reminder fires only after N consecutive single-call cycles (N from
  config), then resets; a batched response also resets the count (specs)
- the reminder is wrapped in the nonce trusted block (spec)
- per-adapter attachment shapes as listed above (specs for chat-completions,
  responses and messages)
- `bb verify` and `bb jvm-spec` are both green
- deployed; `:reminders` added to zanebot's `models/glm-5-3.edn`
- **measured on a real GLM bean**, from `:tool-calls-count` in server.log:
  a non-zero batching rate, with the sample size stated. If it stays at
  zero, report that: it disproves the placement theory, which is still worth
  knowing

## Not in scope

Converting `api/Api` to `extend` with a defaults map (the pattern that lets
new protocol methods have defaults) is worth doing, but this bean does not
need it. File it separately if wanted.

## Also worth trying, independently

A/B `reasoning_effort` on the glm-5-3 model entry. It is config-only, it
hot-reloads, and the session gauge can now measure the difference
(isaac-f5tn).
