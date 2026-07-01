---
# isaac-15y9
title: Hail delivery embeds metadata and promoted params into the prompt
status: todo
type: task
priority: normal
created_at: 2026-07-01T16:59:46Z
updated_at: 2026-07-01T16:59:46Z
---

## Context / Motivation
Hails are the mechanism for autonomous handoffs between crews/sessions (plan <-> work <-> verify, exact session returns, etc.).

- Band-targeted hails (without explicit :prompt) render the band template using :params (bean-id, submitter-session, thread_id, notification-comm, etc.).
- Direct/session-targeted hails, or any hail supplying an explicit :prompt ( "promoted" handoffs ), bypass the band template entirely. The supplied :prompt is used as-is for the turn :input.
- As a result, :params are invisible to the model in those cases.
- Models also do not reliably see the *current* session id, the originating/submitter session id, thread context, or hail id unless manually threaded through prompts or extra tool calls.
- For exact session handbacks ("return this bean to the precise prior worker session that has context"), the receiving model needs the target session id and the full context embedded.
- Current prepare.clj only does `render-band-prompt` when no :prompt is present. Custom :prompt + params means the params are lost for the model.
- Thread-id and reply-to help correlation but are not automatically surfaced in the delivered prompt text.

This leads to fragile handoffs: models must be told to "include submitter-session in your call" and then "use it as the session key", and manually stuff bean data into every handoff prompt. Overhead and error-prone for rich loops.

## Goals
- Every delivered hail's effective :prompt (the turn input) always contains hail metadata in a standard, model-friendly format.
- Metadata to embed (at minimum):
  - Current/target session id (the one receiving the hail)
  - Originating / submitter session (when known from handoff)
  - Hail id, thread-id, reply-to
  - Any :params data (flattened or as EDN block), so bean-id etc. are visible
  - From-crew / hail origin info
- When a caller supplies :prompt + :params (promoted/direct hails), the implementation must *embed* the params (and metadata) into the final prompt before delivery. Do not drop them.
- Band template rendering continues to work as before (params injected via template).
- Direct/session hails (explicit prompt or no band) become first-class and data-rich.
- No change to storage or core record shape; enrichment happens at prepare/delivery time.
- Models see the data automatically in their turn input / system prompt context.
- Support for exact-session handoffs becomes reliable and low-friction ("use the session id from the hail metadata").

## Non-goals (for this bean)
- Changing how callers construct hails (they can still pass params + prompt; the system will promote/embed).
- Full structured data objects in every turn (keep it text in the prompt for now).
- Access control or redaction on the embedded metadata.
- Automatic derivation of submitter session without the hailer passing it.

## Acceptance Scenarios / Evidence
- Band hail (no prompt): params still render into band template as today.
- Direct hail with prompt + params: the delivered prompt to the model contains both the caller's prompt text *and* the params data (e.g. a "Hail params:" or "Embedded data:" block with bean-id=..., submitter-session=...).
- Session-targeted handoff (exact return): the prompt received by the target session includes the target session id explicitly + the embedded params from the handoff.
- Metadata always present: every turn triggered by a hail delivery contains (at top or in a consistent section):
  - Hail session: <id>
  - Thread: <id>
  - Hail id: ...
  - Submitter/origin info when present
- Agent can rely on this without extra tools for basic handoff loops (plan-review style flows succeed deterministically).
- Existing orchestration tests (happy-path, verify-fail, plan-review) pass without models having to manually re-embed data.
- No breakage to current band usage or direct hails that don't supply params.

See related features in isaac-hail/features/ (extend or add hail-metadata.feature, promote-params.feature if they exist; update prepare tests).

## Implementation Outline
- Enhance `isaac.hail.prepare` (or a new enrich-metadata step):
  - Always compute a metadata block (current session if known at prepare time? or at delivery).
  - `embed-params-into-prompt` helper: if :prompt present and :params present, append/prepend a canonical section:
    ```
    [Hail data / params]
    bean-id: ...
    submitter-session: ...
    ...
    ```
    (or EDN block, or "The following structured data was passed with this hail: ...")
  - Merge metadata (session, thread-id, hail-id, origin) similarly.
- Call the new embedding in `render-band-prompt` path *and* when :prompt is supplied.
- Delivery worker / launch path (delivery_worker.clj): before setting :input, ensure the enriched prompt (with metadata) is used. At delivery time we know the target :session, so inject "This hail is being delivered to session: <id>".
- Update `enrich` in prepare.clj to run the new step after/instead of just band render.
- CLI / HTTP / tool paths continue to accept :prompt and :params; the system handles promotion.
- Update docs in cli, tool/hail.clj, band templates, and hail-bean-* skills to mention that params will be visible even with custom prompts.
- Add / update tests in isaac-hail (prepare tests, delivery tests, features for metadata and embedding).
- Possibly expose a cheap way for skills to reference "the hail metadata section".
- Update orchestration test docs (plan-review etc.) to rely on the embedded data.

Key files:
- isaac-hail/src/isaac/hail/prepare.clj
- isaac-hail/src/isaac/hail/delivery_worker.clj
- isaac-hail/src/isaac/tool/hail.clj (doc updates)
- isaac-hail/cli, http
- Features and specs under isaac-hail/features and spec/

## Open Questions / Follow-ups
- Exact format of the embedded metadata block? (Keep simple and greppable / LLM friendly. Suggest a standard header like "--- Hail metadata ---")
- Should :params always be embedded as pretty EDN or key: value?
- When both band template + explicit prompt? (Explicit wins, but still embed params/metadata into the explicit one.)
- Inject at prepare time vs. delivery time (delivery knows the final session).
- Backward compat for existing delivered hails.
- Long-term: maybe always include a small structured "hail-context" map in addition to the text prompt.

## Size / Priority
Small-to-medium. Mostly in prepare + delivery enrichment. High leverage for reliable agent orchestration handoffs and exact-session flows.

(Drafted 2026-07-01 per discussion on promoted hails, params visibility, and session metadata injection.)
