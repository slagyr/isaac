---
# isaac-cdqk
title: Stateless context strategy for hook sessions
status: draft
type: feature
priority: high
created_at: 2026-05-15T21:42:03Z
updated_at: 2026-05-15T21:42:03Z
---

## Problem

Hook sessions accumulate unbounded JSONL history. Every turn replays the entire conversation into the model's context, which causes three observable failure modes — all watched live on zanebot today:

1. **Pinky still checks `AGENDA.md` on every location firing** even though the new template never asks for it — she's following the *old* template embedded in prior conversation turns.
2. **Pinky adds `## Section` headers and bullet points** despite the new templates calling for flat tagged lines — file structure from prior turns is conditioning the format.
3. **Each hook firing takes 2-3 tool calls instead of 1**: a `read AGENDA.md` (location only), a `read memory_file`, then an `edit`. Token cost compounds: location hit 56k tokens in this morning's burst.

Compaction does fire on these sessions when the window is hit, and it correctly uses `memory_write` to summarize prior turns into pinky's memory file. That's acceptable (data log + summaries coexist in the same file by design), but it's a symptom of the underlying problem: hook turns are independent — each one says "here's a fresh data point, log it" — and there's no benefit to replaying any prior turn at all. The memory file is the persistent record; the JSONL is just a log.

The existing compaction machinery (`src/isaac/session/compaction.clj`) is built around "context too big → summarize older turns." For hooks, summarizing is the wrong move — we want to *replay nothing*. JSONL log stays intact on disk; the model just doesn't see it.

## Desired direction

Add a **session context strategy** that says how much history (if any) gets replayed into the model on each turn. Hook sessions opt into a strategy that sends only soul + this turn's prompt.

### Design choice (refine before working)

Two shapes to pick between:

**A. New compaction strategy `:stateless`.** Fits into the existing `:compaction :strategy` field. Compaction step runs every turn and reduces effective history to zero.
- Pro: reuses existing config surface, comm events, status reporting
- Con: stretches the meaning of "compaction" — it's really "don't replay history"

**B. New concept: `:context-policy` separate from compaction.** A `:context-policy :stateless | :windowed | :full` field on crew or session. Determines what gets fed to the model independent of when/how compaction runs.
- Pro: clean separation — compaction = "shrink when too big"; policy = "what to replay in the first place"
- Con: new concept to thread through the codebase

I lean **B** (cleaner mental model). A is a smaller diff if you'd rather move fast.

### Where the policy is declared

- **Per-crew default** in crew config (pinky → `:stateless`)
- **Per-session override** for sessions that genuinely need to differ
- **Per-hook override** on the hook config if some hook ever needs to remember (none today)

Hook sessions inherit pinky's default, so no per-hook tuning needed for current setup.

### Compaction interaction

With stateless hook sessions, compaction never has cause to fire (the model sees zero history every turn, so context never grows toward the window). Default chat sessions keep current behavior. No changes to compaction internals required.

## Acceptance (sharpen during refinement)

- [ ] Pinky-owned hook sessions send soul + this-turn prompt only — no prior JSONL turns in context
- [ ] Verify with a Zaap sync: input tokens per hook drop to ~soul + payload, regardless of how much JSONL history exists
- [ ] JSONL log on disk is unaffected — durable record of every turn
- [ ] Default chat sessions (CLI, ACP) keep current behavior unless explicitly opted in
- [ ] Status command (`isaac status`) reports the policy so it's not hidden
- [ ] **Follow-on validation**: re-run the burst-of-hooks scenario; pinky should follow new templates (no AGENDA check, no `## Section` freelancing, ~1 tool call per firing)

## Out of scope

- Per-message context filtering ("keep tool defs, drop assistant replies")
- Compacting the on-disk JSONL itself — separate concern; the log is the log
- Cron/webhook context policy for non-hook channels — same machinery should work but not in scope here
- Template adherence improvements — those flow naturally from removing history replay; no separate fix needed

## Sequencing

This bean is **draft pending gherkin scenarios.** User has stated they can't spec it in this session. Order of work before implementation:

- [ ] Draft gherkin scenarios covering: hook session with stateless policy, hook session inheriting from crew default, default session unchanged behavior, policy reporting in status
- [ ] Pick design A vs B
- [ ] Move bean from draft → todo

Implementation order once specs land:

- [ ] Add `:context-policy` (or `:strategy :stateless`) to crew schema
- [ ] Thread policy through `bridge/dispatch!` to the turn pipeline
- [ ] Turn pipeline respects policy: stateless → skip history loader
- [ ] Status command surfaces it
- [ ] Spec coverage matches the gherkin scenarios
- [ ] Set pinky → `:stateless` in zanebot config
- [ ] Smoke-test with a Zaap sync

## Related

- isaac-p7k1 (turn-building funnel) — the funnel becomes the natural place to enforce the context policy on the turn it dispatches; **this bean should land *after* p7k1** so the policy fits cleanly into the canonical adapter shape
- Pinky allowlist + template rewrites (already shipped to zanebot) — those fixes only stick once stateless is in; until then, prior conversation history continues to override the new template behavior
