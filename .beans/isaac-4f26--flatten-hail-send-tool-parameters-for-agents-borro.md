---
# isaac-4f26
title: Flatten hail-send tool parameters for agents; borrow frequencies schema; avoid EDN strings
status: in-progress
type: task
priority: high
created_at: 2026-06-30T21:52:07Z
updated_at: 2026-06-30T21:58:13Z
---

**Goal**
Improve the `hail-send` tool (the one presented to LLMs/agents) so it is easier and less error-prone to use for band handoffs and other dispatches.

**Proposed changes**
1. Flatten the parameters. Remove the `:frequencies` (or "frequencies") wrapper.
   - Address keys go directly at the top level of the tool call arguments, next to `params`, `prompt`, `thread_id`, `reply_to`.
   - All tool parameter names must use conventional snake_case (e.g. `session_tags`, `thread_id`, `reply_to`, `with_crew`, not hyphens or camelCase).
   - Example new call shape (what the LLM emits):
     `{"band": "orchistration-verify", "params": {"bean_id": "foo", "notification_comm": ...}}`
   - In the handler, collect the top-level frequency-related keys and build the internal `:frequencies` map for the hail record (CLI/HTTP/queue shape stays the same).

2. Borrow descriptions from the existing frequencies schema.
   - In `hail-send-tool-factory`, construct the `properties` for the address keys by pulling from `isaac.session.frequencies/frequencies-schema` (for description, type hints, etc.).
   - Add `band` as an explicit top-level key (with appropriate description).
   - This keeps the authoritative descriptions and allowed values in one place.

3. Avoid EDN in the parameters.
   - Change the `params` description in the schema to indicate a JSON object/map.
   - The LLM tool runtime already delivers parsed JSON structures (maps, arrays, primitives).
   - The handler/tool can do any necessary conversion (e.g. keywordize for internal use) without the caller ever writing EDN strings.
   - Deprecate/remove the "or EDN string" option from the tool schema and docs for `params`.

**Scope / files likely touched**
- `isaac-hail/src/isaac/tool/hail.clj` (main changes to factory + handler)
- Test updates:
  - `isaac-hail/spec/isaac/tool/hail_spec.clj`
  - `isaac-hail/feature-steps/isaac/hail_hlt1_steps.clj` (crew-calls-hail-send construction)
  - `isaac-hail/features/crew-tool.feature`
  - `isaac-hail/features/hail-threading.feature` and other features using the table-driven hail-send
- Any high-level docs or SKILLs that mention the call shape (orchestration skills are mostly descriptive today)
- Possibly small updates in `isaac-hail/src/isaac/hail/cli.clj` or http if we want consistency comments, but user request is focused on the tool.

**Out of scope**
- Changing the internal hail record format (`:frequencies` stays).
- Changing the CLI (`isaac hail send`) or HTTP `/hail/send` interfaces.
- Changes to band declaration schema or router.

**Acceptance criteria (runnable)**
- `bb spec` in isaac-hail passes.
- `bb features` (or relevant hail features) pass, with updated test invocations using the flat snake_case shape.
- The generated tool definition (what the LLM sees via `tool-definitions`) has:
  - No "frequencies" wrapper property.
  - Top-level properties for band + borrowed freq keys (in snake_case), with good descriptions from the schema.
  - `params` described purely as object (no EDN).
- Calling the tool (in test or manually) with flat snake_case args succeeds and produces a hail with correct `:frequencies` and `:params`.
- Old nested or hyphenated calls in tests are migrated.
- No regression for existing handoff usage in orchestration tests / happy path.

**Why / benefits**
- Directly addresses the recent "frequency" vs "frequencies" and "session-tags leakage" problems that caused undeliverable hails.
- Enforces snake_case convention for all tool parameters.
- LLMs handle flat structured objects much more reliably than wrapped + EDN text.
- Single source of truth for frequencies key descriptions and semantics.
- Cleaner agent experience for the very common "hail to the next band" pattern.

See chat history on hail tool schema iterations and the orchistration happy-path verification.
