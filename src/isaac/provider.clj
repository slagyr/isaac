(ns isaac.provider
  "Protocol for an LLM provider — the gateway to a thinking-engine
   (Anthropic, OpenAI, Ollama, Claude SDK, Grover test stub).

   Implementations live alongside their wire code in isaac.llm.<name>.
   The `make` factory in isaac.drive.dispatch resolves a (name, config)
   pair to a Provider instance — it lives there to avoid a cycle, since
   the impl namespaces all require this one for the protocol."
  (:require
    [c3kit.apron.schema :as schema]
    [clojure.string :as str]))

(defprotocol Provider
  (chat
    [this request]
    "One-shot LLM call. Returns a normalized response map with
     :message, :model, :usage, optional :tool-calls.")

  (chat-stream
    [this request on-chunk]
    "Streaming LLM call. Calls on-chunk per text delta as it arrives.
     Returns the final accumulated response (same shape as chat).")

  (followup-messages
    [this request response tool-calls tool-results]
    "Build the next iteration's :messages vector for the tool loop in
     this provider's wire format. Used by isaac.llm.tool-loop/run
     between chat iterations.")

  (config
    [this]
    "Return this provider's raw (kebab-case) config map. Used for
     introspection — e.g. `:stream-supports-tool-calls`. The wire
     format used for outbound calls is kept inside the deftype.")

  (display-name
    [this]
    "Original provider name string — `anthropic`, `openai-codex`,
     `grover:openai`, etc. Used for log lines and observability."))

;; --- Response Schema ---
;;
;; Every Provider's chat and chat-stream returns one of two shapes:
;;
;;   Success: a Response map (see below)
;;   Failure: an Error map ({:error keyword :message? string :status? int})
;;
;; Callers (tool-loop, dispatch logging, turn.clj) check `(:error response)`
;; first to disambiguate. A Response carries the parsed assistant message,
;; the model that produced it, normalized usage, and (when present) the
;; tool calls the model wants Isaac to run.

(def tool-call
  {:name        :tool-call
   :type        :map
   :description "Normalized tool call, provider-agnostic. The :raw field
                 preserves provider-specific wire payload when present
                 (e.g., Ollama's :function map) for round-tripping."
   :schema      {:id        {:type :string :description "Stable id; UUID-ish for providers without one"}
                 :name      {:type :string :description "Tool name to invoke"}
                 :arguments {:type :ignore :description "Parsed args (map). Coerced to map by the provider."}
                 :raw       {:type :ignore :description "Optional pass-through of the original wire payload"}}})

(def usage
  {:name        :usage
   :type        :map
   :description "Normalized token accounting"
   :schema      {:input-tokens  {:type :int :description "Prompt-side token count"}
                 :output-tokens {:type :int :description "Completion-side token count"}
                 :cache-read     {:type :int :description "Tokens served from prompt cache (Anthropic)"}
                 :cache-write    {:type :int :description "Tokens written to prompt cache (Anthropic)"}}})

(def assistant-message
  {:name        :assistant-message
   :type        :map
   :description "The assistant's reply, in a wire shape close to OpenAI's.

                 NOTE on snake_case: :tool_calls is intentionally NOT kebab-cased.
                 It carries the provider's native wire format (OpenAI's tool_calls
                 array, Anthropic's tool_use blocks adapted, etc.) so it can be
                 round-tripped into the next request body unchanged. The outer
                 Response :tool-calls (kebab-case) is the normalized form for
                 iteration; this :tool_calls is for wire faithfulness."
   :schema      {:role       {:type :string :description "Always \"assistant\" for chat returns"}
                 :content    {:type :ignore :description "String, or empty when the turn is purely tool-using"}
                 :tool_calls {:type :ignore :description "Optional. Provider-native wire shape (kept for followup-messages)."}}})

(def response
  {:name        :provider-response
   :type        :map
   :description "Successful return shape from Provider/chat and Provider/chat-stream.
                 Errors are returned as a separate {:error _ :message? _} map.

                 NOTE on the leading underscore: :_headers follows the Clojure
                 convention of marking diagnostic / non-canonical fields. It
                 carries the raw HTTP response headers when present, useful
                 for rate-limit debugging and incident triage. Production code
                 should not branch on it; it's there for the human reading logs."
   :schema      {:message    assistant-message
                 :model      {:type :string :description "Model id the provider chose to record on this response"}
                 :tool-calls {:type :seq :spec tool-call
                              :description "Normalized tool calls, empty/absent when none"}
                 :usage      usage
                 :_headers   {:type :ignore :description "Optional raw response headers, for diagnostics only"}}})

(def error-response
  {:name        :provider-error
   :type        :map
   :description "Failure return shape. :error is a keyword (:auth-missing,
                 :auth-failed, :connection-refused, :llm-error, :unknown,
                 :timeout, etc.). Callers branch on (:error response)."
   :schema      {:error   {:type :keyword :description "Error category"}
                 :message {:type :string  :description "Human-readable detail"}
                 :status  {:type :int     :description "HTTP status when applicable"}
                 :body    {:type :ignore  :description "Optional raw error body from the provider"}}})

(defn error?
  "True when `response` is a Provider error rather than a successful Response."
  [response]
  (some? (:error response)))

(defn validate-response
  "Validate a Provider response against the response schema. Returns the
   value unchanged on success, or throws with a structured error. Use in
   debug or test paths — production code branches on `error?` and reads
   fields directly without coercion."
  [value]
  (schema/conform! response value))

;; --- Construction ---

(defn- simulated-provider-target [provider]
  (when (str/starts-with? provider "grover:")
    (subs provider (count "grover:"))))

(defn- simulated-provider-config [provider]
  (case provider
    "openai"         {:api "openai-compatible" :api-key "grover" :base-url "https://api.openai.com/v1"
                      :name "openai" :simulate-provider "openai"}
    "openai-api"     {:api "openai-compatible" :api-key "grover" :base-url "https://api.openai.com/v1"
                      :name "openai-api" :simulate-provider "openai-api"}
    "openai-codex"   {:api "openai-compatible" :auth "oauth-device" :base-url "https://api.openai.com/v1"
                      :name "openai-chatgpt" :simulate-provider "openai-chatgpt"}
    "openai-chatgpt" {:api "openai-compatible" :auth "oauth-device" :base-url "https://api.openai.com/v1"
                      :name "openai-chatgpt" :simulate-provider "openai-chatgpt"}
    "grok"           {:api "openai-compatible" :api-key "grover" :base-url "https://api.x.ai/v1"
                      :name "grok" :simulate-provider "grok"}
    nil))

(defn- provider-config->wire-config [provider-config]
  (cond-> provider-config
    (:api-key provider-config)                               (assoc :apiKey (:api-key provider-config))
    (:auth-key provider-config)                              (assoc :authKey (:auth-key provider-config))
    (:assistant-base-url provider-config)                    (assoc :assistantBaseUrl (:assistant-base-url provider-config))
    (:base-url provider-config)                              (assoc :baseUrl (:base-url provider-config))
    (:response-format provider-config)                       (assoc :responseFormat (:response-format provider-config))
    (contains? provider-config :stream-supports-tool-calls)  (assoc :streamSupportsToolCalls (:stream-supports-tool-calls provider-config))
    (contains? provider-config :supports-system-role)        (assoc :supportsSystemRole (:supports-system-role provider-config))))

(defn- real-provider-defaults [provider]
  (case provider
    "openai-codex"   {:api "openai-compatible" :auth "oauth-device" :name "openai-chatgpt"}
    "openai-chatgpt" {:api "openai-compatible" :auth "oauth-device" :name "openai-chatgpt"}
    nil))

(defn- normalize [provider provider-config]
  (if-let [target (simulated-provider-target provider)]
    [target (merge (simulated-provider-config target) (or provider-config {}))]
    (if-let [defaults (real-provider-defaults provider)]
      [provider (merge defaults (or provider-config {}))]
      [provider provider-config])))

(defn resolve-api
  "Resolve a (name, config) to its api string, or nil if unknown."
  [provider provider-config]
  (let [[provider provider-config] (normalize provider provider-config)]
    (or (:api provider-config)
        (cond
          (= provider "claude-sdk")               "claude-sdk"
          (= provider "grover")                   "grover"
          (str/starts-with? provider "anthropic") "anthropic-messages"
          (= provider "ollama")                   "ollama"
          :else                                     nil))))

(defn ollama-opts [provider-config]
  {:base-url    (or (:base-url provider-config) "http://localhost:11434")
   :session-key (:session-key provider-config)})

(defn wire-opts
  "Wire-config map suitable for impl chat fns — i.e. {:provider-config wire-config}."
  [provider-config]
  {:provider-config (provider-config->wire-config provider-config)})

(defn normalize-pair [name provider-config]
  (normalize name provider-config))

(defn api-of
  "Resolve a Provider instance's api string. Equivalent to resolve-api on
   (display-name, config) — convenient for callers that need to dispatch
   on api family (e.g., choosing the anthropic prompt builder)."
  [p]
  (resolve-api (display-name p) (config p)))

;; --- Registry ---
;;
;; Each provider implementation registers itself by api keyword. The factory
;; takes (name, raw-cfg) and returns a Provider instance. Built-in providers
;; self-register at namespace load time; third parties do the same in their
;; own namespace.

(defonce ^:private -registry (atom {}))

(defn register!
  "Register a Provider factory under the given provider-key keyword.
   factory: (fn [name cfg] -> Provider)
   Returns the provider-key keyword for chaining."
  [provider-key factory]
  (swap! -registry assoc provider-key factory)
  provider-key)

(defn unregister!
  "Remove the factory registered for `provider-key`."
  [provider-key]
  (swap! -registry dissoc provider-key)
  provider-key)

(defn factory-for
  "Return the factory registered for `provider-key`, or nil if none."
  [provider-key]
  (get @-registry provider-key))

(defn registered-apis
  "Return the set of provider keywords that have a factory registered."
  []
  (set (keys @-registry)))
