---
# isaac-5n68
title: Model-family defaults (prompt text, sampling) that users can override per model; GLM gets an emphatic batching line
status: todo
type: task
priority: normal
created_at: 2026-09-21T04:58:11Z
updated_at: 2026-09-21T04:58:11Z
---

## Why

GLM-5.3 batched 0 of 418 tool responses across two real beans (cgxa, nq4c).
It already has `parallel_tool_calls: true` (isaac-rr7u) and Isaac's one generic
batching hint in the system prompt.

Every harness we read solves this with **per-model-family system prompt
text**, not with per-turn reminders:

- **Codex:** per-model prompt files. `gpt_5_2_prompt.md` carries "Parallelize
  tool calls whenever possible…". The other model prompts do not.
- **OpenCode:** `session/system.ts` picks a whole prompt by matching the model
  id (anthropic, gpt, gemini, kimi, default). Each words batching differently.
  Kimi's is the most emphatic ("HIGHLY RECOMMENDED"). It also sets sampling
  per model in code (`provider/transform.ts`: temperature 1.0 for glm-4.6/4.7).
- **Grok Build:** no batching line in its main prompt, because Grok batches
  unprompted.

Isaac has one hint for every model and no per-model prompt text. Requiring
users to write prompt text for each model they configure is a burden, so Isaac
should ship sensible defaults per model family and let the user override them.

## Design

1. **Built-in family defaults, as data.** Ship them in isaac-agent resources
   (edn, not code): each entry has a match on the model id and the fields it
   supplies. For example:

   ```clojure
   {:glm    {:match #"(?i)glm"
             :prompt "You can call many tools in one response. When calls are
                      independent (reads, greps, globs, separate files), issue
                      them ALL in the same response. One call per response is
                      the slow path; wait only when one call's output feeds
                      the next."}
    :claude {:match #"(?i)claude"}    ;; batches unprompted: nothing extra
    ...}
   ```

   Fields for now: `:prompt` (appended to the system prompt for that model
   only). Sampling (`:temperature`) is a candidate field. See the note below
   before adding it.

2. **The model entry overrides, field by field.** A user's
   `models/<id>.edn` may set the same fields. They win over the family default,
   merged per field. An explicit `nil` turns a family default off. With no
   family match and no user fields, the request is exactly what it is today.

3. **Where the text lands.** It goes in the system prompt, next to the existing
   generic hint, in the cached prefix, which is where every other harness puts
   it. This tests **wording** per model, not placement. If GLM is still at zero
   after this, the per-turn route is next (see isaac-p5kt).

## Temperature note

Isaac sends no `temperature` today. The live GLM request body keys were
`(:messages :model :parallel_tool_calls :reasoning_effort :stream
:stream_options :tools)`. The server default applies, and on OpenAI-compatible
APIs that is usually 1.0 already. So "set GLM to 1.0" may change nothing.
Check Fireworks' default before counting it as a lever.

## Done when

- family defaults load from resources; the model-id match is spec'd,
  including no match
- the user model entry overrides per field, and explicit `nil` disables (specs)
- a model with no family match and no user fields sends byte-identical
  requests to today (spec)
- the `:prompt` text reaches the system prompt for that model only (spec)
- the model schema documents the new fields; `isaac config validate` accepts
  them; editing them on a running server takes effect with no restart
- `bb verify` and `bb jvm-spec` are both green
- deployed to zanebot. **Measured on a real GLM bean**, from
  `:tool-calls-count` in server.log: the batching rate with the sample size
  stated. If it stays at zero, report that. It means wording was not the
  lever.

## Also worth trying, independently

A/B `reasoning_effort` on the glm-5-3 model entry. It is config-only, it
hot-reloads, and the gauge can now measure it (isaac-f5tn).
