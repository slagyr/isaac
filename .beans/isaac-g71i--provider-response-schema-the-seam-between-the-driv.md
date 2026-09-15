---
# isaac-g71i
title: 'Provider response schema: the seam between the drive and adapters'
status: draft
type: feature
priority: high
created_at: 2026-09-15T22:03:49Z
updated_at: 2026-09-15T22:03:49Z
---

## Problem

The drive and tool loop read raw provider wire keys and guess conventions (surveyed on agent main cd90423, 2026-09-15):

- Token usage parsed three times (turn.clj:60-92, turn.clj:588-594, tool_loop.clj:24-35; plus claude-code LoopDriver) from `:input_tokens`, `:cache_read_input_tokens`, `[:input_tokens_details :cached_tokens]`, `:prompt_eval_count`, etc. `provider-prompt-tokens` adds cache to input for every provider, which double-counts OpenAI (input already includes cached). **Production impact:** scrapper (chatgpt gpt-5.6-sol) records ~2x its real context (808,835-char request stamped 476,909 tokens; 19:45Z compaction fired at gauge 841,998 for a ~1.43M-char request), so workers compact at ~40% of the window instead of 80%.
- Stop reason: `:stop_reason` then `:done_reason` (turn.clj:313-314).
- Reasoning and tool calls probed at up to three nesting depths (`[:response :response ...]` turn.clj:82/312/688; `[:message :tool_calls]` dispatch.clj:24, turn.clj:417-456/685-692/1507, tool_loop.clj:16).
- Rate limits: raw `[:body :retry_after]` (provider_wall.clj:83); context overflow by regex over error messages.
- Streaming: HTTP adapters pass raw SSE/NDJSON events to on-chunk (http.clj process-sse-lines / post-ndjson-stream!); the drive probes `[:message :content]`, `[:delta :text]`, `[:choices 0 :delta :content]`, and reasoning three ways (turn.clj:362-391). Responses API text deltas (`{:delta "..."}`) match none of these, so chatgpt likely does not stream incrementally (unverified).
- `protocol.clj` already has `response`/`usage` schemas, but `validate-response` is called only in adapter specs, never in production and never by the claude-code module; `usage` does not define whether input includes cache.

## Decisions (2026-09-15, Micah)

- The schema is the seam: it defines everything the drive needs from a response and everything each provider must provide. Providers know how to count their tokens and report them.
- `:usage :prompt-tokens` = full context processed (cached portions included); cache/reasoning counts are optional parts of totals, cost/stats only.
- `:message` wrapper removed; `:content` at top level (worth the churn).
- `:stop-reason` normalized by adapters to a closed set; the drive branches only on `:cancelled` today, more branches later.
- Reasoning effort is NOT reported back by providers (provider strings are lossy: 7-9 all map to "high"); the drive records the 0-10 effort it requested.
- Stream chunks become adapter-translated deltas; no `:done` chunk; chat-stream's return value is authoritative. (Stream consumers to be traced fully during implementation — Micah OK.)
- Drive-internal shapes (`loop-result`, `turn-usage`, `unavailable`) are documented in the same namespace.
- Keep `:response-id` (Micah: we should start using it — see isaac-5l31).
- Accepted additions on top of rev 2: `tool-call :arguments-error`; `:refused` stop reason; optional `:usage` on errors; dispatch validates without coercing.

## Schema (rev 3)

```clojure
(def usage
  {:name :usage :type :map
   :description "Token accounting for ONE model request, provider-neutral. The adapter owns the arithmetic."
   :schema
   {:prompt-tokens      {:type :long :validations [schema/required]
                         :description "Everything processed on the input side — the context size, cached portions included."}
    :output-tokens      {:type :long :validations [schema/required]
                         :description "Everything generated, hidden reasoning included."}
    :reasoning-tokens   {:type :long :description "Portion of :output-tokens that was hidden reasoning."}
    :cache-read-tokens  {:type :long :description "Portion of :prompt-tokens served from cache. Cost/stats only."}
    :cache-write-tokens {:type :long :description "Portion of :prompt-tokens written to cache. Cost/stats only."}}})

(def tool-call
  {:name :tool-call :type :map
   :schema
   {:id              {:type :string :validations [schema/required] :description "Provider call id, or generated UUID"}
    :name            {:type :string :validations [schema/required]}
    :arguments       {:type :map :validations [schema/required] :description "Parsed arguments; {} when none or unparseable"}
    :arguments-error {:type :string :description "Set when the model's arguments could not be parsed; the drive returns it to the model as the tool result."}}})

(def stop-reasons #{:end-turn :tool-use :max-tokens :cancelled :refused :other})

(def reasoning
  {:name :reasoning :type :map
   :description "Reasoning the provider exposed. Effort is not reported back."
   :schema {:summary {:type :string :validations [schema/required]}}})

(def response
  {:name :api-response :type :map
   :description "Successful return from Api/chat and Api/chat-stream."
   :schema
   {:content       {:type :string :validations [schema/required] :description "Assistant text; \"\" for a pure tool-call response."}
    :tool-calls    {:type :seq :spec tool-call :validations [schema/required] :description "[] when none. The only source of tool calls."}
    :stop-reason   {:type :keyword :validations [schema/required] :validate stop-reasons}
    :model         {:type :string :validations [schema/required]}
    :usage         usage
    :reasoning     reasoning
    :response-id   {:type :string :description "Provider response id for stateful chaining."}
    :provider-data {:type :ignore :description "Adapter-private round-trip payload; the drive never reads it."}
    :_headers      {:type :ignore :description "Diagnostics only"}}})

(def error-kinds
  #{:auth-missing :auth-failed :refresh-failed :connection-refused :timeout :stream-stalled
    :cancelled :context-overflow :rate-limited :api-error :llm-error :unknown-provider :unknown})

(def error
  {:name :api-error :type :map
   :schema
   {:error          {:type :keyword :validations [schema/required] :validate error-kinds}
    :message        {:type :string :validations [schema/required]}
    :status         {:type :long}
    :retry-after-ms {:type :long :description "Provider-advised wait, parsed by the adapter."}
    :usage          usage
    :body           {:type :ignore :description "Diagnostics only"}}})

(def stream-chunk
  {:name :stream-chunk :type :map
   :description "Adapter-translated incremental update for on-chunk. No :done chunk; chat-stream's return is authoritative."
   :schema {:text-delta      {:type :string}
            :reasoning-delta {:type :string}}})

;; drive-internal
(def turn-usage
  {:name :turn-usage :type :map
   :description "Usage summed over a turn's requests. Stats/cost only; context size comes from the LAST response's :prompt-tokens."
   :schema {:requests           {:type :long :validations [schema/required]}
            :prompt-tokens      {:type :long :validations [schema/required]}
            :output-tokens      {:type :long :validations [schema/required]}
            :reasoning-tokens   {:type :long}
            :cache-read-tokens  {:type :long}
            :cache-write-tokens {:type :long}}})

(def loop-result
  {:name :loop-result :type :map
   :schema {:response      response            ;; nil when cancelled before a response
            :tool-calls    {:type :seq :spec tool-call :validations [schema/required] :description "Executed this turn, in order"}
            :usage         turn-usage
            :cancelled?    {:type :boolean}
            :loop-request? {:type :boolean :description "Budget exhausted with calls pending"}}})

(def unavailable
  {:name :unavailable :type :map
   :description "Provider-wall classification of an error; built by the drive, never by an adapter."
   :schema {:unavailable?   {:type :boolean :validations [schema/required]}
            :retry-after-ms {:type :long :validations [schema/required]}
            :reason         {:type :keyword :validations [schema/required]}
            :provider       {:type :string}}})
```

## Implementation notes

- **Validate, don't conform.** c3kit apron `validate`/`conform` build their result from schema keys only (`process-fields` starts from `{}`), so both DROP unlisted keys. Dispatch calls `schema/validate`, checks `error?`, logs/returns a `:provider-contract` error naming fields, and passes the adapter's ORIGINAL map on.
- Validate final responses and errors at dispatch only; stream-chunk shape is checked in adapter specs and the shared contract spec (hundreds of chunks per response).
- A shared contract spec that every adapter runs — including module providers (claude-code) — written first; adapters go green against it one at a time; the drive cuts over to schema-only reads last.
- Scope: agent adapters (messages, chat-completions, responses, ollama, grover), tool loop, drive (turn, dispatch, provider-wall), session stamping (`last-input-tokens` from `:prompt-tokens`; cumulative stats from `turn-usage`), claude-code module (LoopDriver produces `loop-result`; removes its own prompt-token workaround). Clean cutover: no wire-key fallbacks remain in drive/tool loop.
- Transcript `:usage` on assistant entries moves to the new key names; old transcript entries are history only (no migration), compaction reads provider stamps.
- Deploy: agent + claude-code module releases together (module id `:isaac.provider.claude-code`).

## Open

- Scenario plan not yet written (planning skill: plan first, then scenarios one at a time).
