---
# isaac-5n68
title: Per-model extra system prompt on the model entry, placed after the crew soul
status: todo
type: task
priority: normal
created_at: 2026-09-21T04:58:11Z
updated_at: 2026-09-21T04:58:11Z
---

## Why

GLM-5.3 batched 0 of 418 tool responses across two real beans (cgxa, nq4c),
even with `parallel_tool_calls: true` (isaac-rr7u) and Isaac's generic batching
hint. The harnesses we read (Codex, OpenCode, Grok Build) all handle this with
per-model system prompt text. Isaac has no way to give one model different
system prompt text from another.

## Decision (Micah, 2026-09-21)

- **All model configuration lives in one place: the model entry.** Not in
  rules. Rules would put model settings in two places.
- **Isaac stays ignorant of specific models.** No built-in table of model
  families and their quirks. Keeping that current is a losing battle. The user
  writes the text for the models they run.

## Design

1. **New optional field on the model entry.** Working name
   `:extra-system-prompt` (final name to be settled; see below):

   ```clojure
   ;; models/glm-5-3.edn
   {:model               "accounts/fireworks/models/glm-5p3"
    :provider            :fireworks
    :context-window      524288
    :extra-system-prompt "You can call many tools in one response. When calls are
                          independent (reads, greps, globs, separate files),
                          issue them ALL in the same response. One call per
                          response is the slow path; wait only when one call's
                          output feeds the next."}
   ```

2. **Placement: directly after the crew soul** in `build-system-text`
   (`isaac-agent/src/isaac/llm/prompt/builder.clj:309`). It goes before
   AGENTS.md, rules and the skill menu. It is static per model, so it stays
   inside the cached prefix.

3. **It follows the model actually used for the turn.** `--with-model` and
   crew switches change the effective model, so the text must come from the
   effective model, not the crew's default.

4. **Every path that builds a system prompt gets it.** `build-system-text`
   has more than one caller. `messages.clj:83` calls it on its own and must
   pass the new argument. The Claude CLI adapter (isaac-claude-code
   `build-system-prompt`) reads the system text off the request, so it picks
   the field up without changes. Confirm that with a spec rather than assume
   it.

5. **Absent means unchanged.** No field means a byte-identical system prompt
   to today.

## Naming candidates

- `:extra-system-prompt`: says it adds rather than replaces. Recommended.
- `:system-prompt`: short, but reads as though it replaces the whole prompt.
- `:instructions`: avoid. It clashes with the Responses API wire field of the
  same name, which confuses anyone reading request bodies.
- `:guidance`: avoid. Isaac already uses "guidance" for per-turn framing.

## Done when

- the field is in the model schema, documented as additive;
  `isaac config validate` accepts it
- no field means a byte-identical system prompt (spec)
- the text appears directly after the soul, for that model only (spec)
- `--with-model` uses the override model's text (spec)
- every `build-system-text` caller passes it, including `messages.clj:83`
  (spec); the Claude CLI path carries it (spec)
- editing it on a running server takes effect with no restart
- `bb verify` and `bb jvm-spec` are both green
- deployed; the GLM text above added to zanebot's `models/glm-5-3.edn`;
  **measured on a real GLM bean** from `:tool-calls-count` in server.log, with
  the batching rate and sample size stated. If it stays at zero, report that.

## Out of scope

Built-in per-model defaults, per the decision above. How to help users know
which models need text is an open question, not this bean.
