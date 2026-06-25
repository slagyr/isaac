---
# isaac-59ja
title: comm_send namespaced param keys use '/' — rejected by tool API (breaks every turn for crews with comm_send)
status: todo
type: bug
priority: high
created_at: 2026-06-25T14:52:05Z
updated_at: 2026-06-25T14:52:05Z
---

comm_send serializes namespaced send-schema fields to JSON property keys with a slash (e.g. `discord/target`). The LLM tool API requires property keys to match `^[a-zA-Z0-9_.-]{1,64}$` — `/` is NOT allowed — so the whole tools payload is rejected and the crew can take NO turns.

## Symptom (production, 2026-06-25)
marvin (and every crew granted comm_send) failed EVERY turn with:
`invalid_request_error: tools.4.custom.input_schema.properties: Property keys should match pattern '^[a-zA-Z0-9_.-]{1,64}$'`
Mitigated by removing :comm_send from all crews on zanebot (main/marvin/scrapper/perceptor/prowl/tempest) + restart. hail-send/hail-get unaffected (no namespaced keys).

## Root cause
`isaac-agent/src/isaac/tool/comm_send.clj` `field-json-key` (~line 60):
```clojure
(defn- field-json-key [field]
  (if-let [ns* (namespace field)]
    (str ns* "/" (name field))   ; -> "discord/target" — illegal "/"
    (name field)))
```
The namespacing decision (2s0b/97bf) used `:discord/target` etc. to avoid union-schema collisions — correct for the RECORD, but the `/` is invalid as a JSON-Schema property name for the tool API (Anthropic; OpenAI same pattern).

## Fix (one function)
The namespaced keyword belongs in the delivery RECORD (`:discord/target` — what send! reads). Only the JSON property key needs an API-safe encoding. Change the separator from `/` to an allowed char (`.`, `_`, or `-`; pattern allows `[A-Za-z0-9_.-]`). Recommend `.`:
```clojure
(str ns* "." (name field))   ; -> "discord.target"
```
Because the record is built from the field KEYWORD directly (build-record writes `field`), and `arg-value` looks up the model's value via the same `field-json-key`, changing ONLY this function fixes the schema property name AND the arg lookup while the record keeps `:discord/target`. No decode-to-keyword step exists, so any allowed separator works. (Avoid `-`: field/ns names can contain hyphens.)

## Also update (the test that should have caught this)
- `features/tool/comm_send.feature` — scenarios assert param names `telly/target`, `telly/loft`; change to the encoded form (`telly.target`, `telly.loft`).
- `spec/isaac/tool/comm_send_spec.clj` — same.
- ADD a regression assertion that every comm_send property key matches `^[a-zA-Z0-9_.-]{1,64}$`. The feature 'verified' but gherclj never hits the real API, so it asserted the intended (invalid) shape. This guard closes that gap.

## Acceptance
- comm_send property keys validate against `^[a-zA-Z0-9_.-]{1,64}$`.
- A crew with comm_send can take a turn (tools payload accepted) and successfully comm_send to a Discord channel / iMessage handle end-to-end.
- Record still carries namespaced keys (`:discord/target`) that discord/imessage send! read.
- feature + spec updated; property-key-pattern regression guard added.

## Redeploy + re-grant
After fix + deploy: re-add :comm_send to the crews it was pulled from (main/marvin/scrapper/perceptor/prowl/tempest). Backups of the pre-removal crew files exist on zanebot (config/crew/*.bak.revert.*).

## Notes
Surfaced 2026-06-25 testing comm_send live. Follow-up to isaac-2s0b (namespacing) + isaac-97bf (discord/imessage send-schema). Lesson for PLANNING-PARTNER.md: a scenario can assert the intended shape and still be wrong if it never validates against the real external contract (here, the tool-API property-key pattern).
