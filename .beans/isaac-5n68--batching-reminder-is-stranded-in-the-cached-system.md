---
# isaac-5n68
title: Per-model extra system prompt on the model entry, placed after the crew soul
status: todo
type: task
priority: normal
created_at: 2026-09-21T04:58:11Z
updated_at: 2026-09-21T16:15:21Z
blocked_by:
    - isaac-jl9p
    - isaac-ruom
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
- **The hard-coded tool-discipline hint becomes this field's default.** Its
  four rules (`parallel-tool-calls-hint`,
  `isaac-agent/src/isaac/llm/turn_instructions.clj`) are good but arbitrary,
  and they are geared to coding; a crew or install used for anything else
  shouldn't get them. They move out of code and into config.

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

5. **The hard-coded hint goes away.** Delete `parallel-tool-calls-hint` and
   the `include-tool-batching-hint?` plumbing. The install-wide value comes from
   `:defaults :model :extra-system-prompt` (isaac-ruom's template rule), and a
   model entry overrides it.
6. **Built-in fallback is empty** (recommended; Micah to confirm). Isaac ships
   no opinion. Each install that wants the four rules writes them in config,
   typically `"${file:prompts/tool-discipline.md}"` (isaac-jl9p). zanebot does
   exactly that as part of the deploy, so its behavior doesn't change. An install
   that configures nothing stops getting coding advice, which is intended.
7. **Compaction still leaves it out.** The summary call has no tools; keep
   today's exclusion (`session/compaction.clj:153`).
8. **Large text lives in a file** via `"${file:…}"` (isaac-jl9p). Nothing
   specific to this field.

## Naming candidates

- `:extra-system-prompt`: says it adds rather than replaces. Recommended.
- `:system-prompt`: short, but reads as though it replaces the whole prompt.
- `:instructions`: avoid. It clashes with the Responses API wire field of the
  same name, which confuses anyone reading request bodies.
- `:guidance`: avoid. Isaac already uses "guidance" for per-turn framing.

## Done when

- the field is in the model schema, documented as additive;
  `isaac config validate` accepts it
- with the text configured in `:defaults` or the model entry, the prompt
  carries it directly after the soul; with nothing configured, no tool-discipline
  text appears (specs)
- `parallel-tool-calls-hint` and `include-tool-batching-hint?` are gone
- the text appears directly after the soul, for that model only (spec)
- `--with-model` uses the override model's text (spec)
- every `build-system-text` caller passes it, including `messages.clj:83`
  (spec); the Claude CLI path carries it (spec)
- editing it on a running server takes effect with no restart
- `bb verify` and `bb jvm-spec` are both green
- deployed; zanebot's `:defaults :model :extra-system-prompt` points at a
  `prompts/tool-discipline.md` holding today's four rules (so its non-GLM
  crews see no change); the GLM text above added to `models/glm-5-3.edn`;
  **measured on a real GLM bean** from `:tool-calls-count` in server.log, with
  the batching rate and sample size stated. If it stays at zero, report that.

## Open (Micah)

- **Final name.** `:extra-system-prompt` is the working name.
- **Model-only, or cascade like effort?** Today's placement is model entry
  plus `:defaults :model`. Should a crew also be able to override it, the way
  crew > model > provider works for effort? A crew doing non-coding work is the
  case for it.
- **Built-in fallback empty?** See design point 6.
- **An always-on install prompt.** Micah wants behavior an install can impose
  "no matter what crew is operating." With override semantics a model's own
  text *replaces* the `:defaults` text, so this field alone can't guarantee
  that. Global rules (`~/.isaac/prompts/rules/*.md`) are always included today
  and may already be the answer. Decide before building.

## Also worth trying, independently

A/B `reasoning_effort` on the glm-5-3 model entry. High effort plans in fine
steps (call, observe, decide); medium may plan coarser and batch more. It's
config-only, it hot-reloads, and the gauge can measure it (isaac-f5tn).

## Out of scope

Built-in per-model defaults, per the decision above. How to help users know
which models need text is an open question, not this bean.
