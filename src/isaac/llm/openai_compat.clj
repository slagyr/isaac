;; mutation-tested: 2026-04-08
(ns isaac.llm.openai-compat
  (:require
    [clojure.string :as str]
    [cheshire.core :as json]
    [isaac.auth.store :as auth-store]
    [isaac.llm.http :as llm-http]
    [isaac.llm.tool-loop :as tool-loop]))

;; region ----- Auth -----

(defn- decode-jwt-payload [token]
  (when (and token (str/includes? token "."))
    (let [[_ payload _] (str/split token #"\." 3)]
      (when payload
        (try
          (let [decoder (java.util.Base64/getUrlDecoder)
                bytes   (.decode decoder payload)]
            (json/parse-string (String. bytes "UTF-8") true))
          (catch Exception _ nil))))))

(defn- extract-account-id [tokens]
  (let [payload (or (decode-jwt-payload (:access tokens))
                    (decode-jwt-payload (:id-token tokens)))
        auth    (or (get payload "https://api.openai.com/auth")
                    (some (fn [[k v]]
                            (when (= "https://api.openai.com/auth" (str/replace (str k) #"^:" ""))
                              v))
                          payload))]
    (or (:chatgpt_account_id auth)
        (get auth "chatgpt_account_id")
        (:chatgpt_account_id payload)
        (get payload "chatgpt_account_id"))))

(defn- resolve-oauth-tokens [{:keys [auth name] :as config}]
  (when (= "oauth-device" auth)
    (let [state-dir (or (:auth-dir config) (:state-dir config) (str (System/getProperty "user.home") "/.isaac"))
          tokens    (auth-store/load-tokens state-dir name)]
      (when (and tokens (not (auth-store/token-expired? tokens)))
        tokens))))

(defn- missing-auth-error [{:keys [auth apiKey name] :as config}]
  (cond
    (:simulate-provider config)
    nil

     (= "oauth-device" auth)
     (when-not (resolve-oauth-tokens config)
       {:error   :auth-missing
       :message "Missing OpenAI ChatGPT login. Run `isaac auth login --provider openai-chatgpt` first."})

    (str/blank? apiKey)
    (let [[provider-name env-var] (case name
                                    "grok"       ["Grok" "GROK_API_KEY"]
                                    "openai"     ["OpenAI" "OPENAI_API_KEY"]
                                    "openai-api" ["OpenAI" "OPENAI_API_KEY"]
                                    ["OpenAI" "OPENAI_API_KEY"])]
      {:error   :auth-missing
       :message (str "Missing " provider-name " API key. Set " env-var " or configure provider :apiKey.")})))

(defn- provider-base-url [{:keys [auth baseUrl]}]
  (if (= "oauth-device" auth)
    (if (or (nil? baseUrl) (str/includes? baseUrl "api.openai.com"))
      "https://chatgpt.com/backend-api/codex"
      baseUrl)
    (or baseUrl "http://localhost:11434/v1")))

(defn- llm-http-opts [config]
  (cond-> {}
    (:session-key config)       (assoc :session-key (:session-key config))
    (:simulate-provider config) (assoc :simulate-provider (:simulate-provider config))))

(defn- auth-headers [config]
  (let [oauth-tokens (resolve-oauth-tokens config)
        oauth-token  (:access oauth-tokens)
        account-id   (or (extract-account-id oauth-tokens)
                         (when (= "openai-chatgpt" (:simulate-provider config)) "grover-account"))
        api-key      (:apiKey config)
        token        (or oauth-token api-key)]
    (cond-> {"content-type" "application/json"}
      token                         (assoc "Authorization" (str "Bearer " token))
      account-id                    (assoc "ChatGPT-Account-Id" account-id)
      (= "oauth-device" (:auth config)) (assoc "originator" "isaac"))))

;; endregion ^^^^^ Auth ^^^^^

;; region ----- SSE Event Processing -----

(defn process-sse-event
  "Accumulate an OpenAI-compatible SSE event into the running state."
  [data accumulated]
  (let [delta (get-in data [:choices 0 :delta])]
    (cond-> accumulated
      (:content delta) (update :content str (:content delta))
      (:model data)    (assoc :model (:model data))
      (:usage data)    (assoc :usage (:usage data)))))

;; endregion ^^^^^ SSE Event Processing ^^^^^

;; region ----- Response Parsing -----

(defn- extract-tool-calls [tool-calls]
  (when (seq tool-calls)
    (mapv (fn [tc]
            {:type      "toolCall"
             :id        (:id tc)
             :name      (get-in tc [:function :name])
             :arguments (let [args (get-in tc [:function :arguments])]
                          (if (string? args)
                            (json/parse-string args true)
                            args))})
          tool-calls)))

(defn- parse-usage [usage]
  {:input-tokens  (or (:prompt_tokens usage) (:input_tokens usage) 0)
   :output-tokens (or (:completion_tokens usage) (:output_tokens usage) 0)})

(defn- chat-completions-request? [{:keys [auth]}]
  (not= "oauth-device" auth))

(defn- ->responses-output [content]
  (cond
    (string? content) content
    (nil? content)    ""
    :else             (json/generate-string content)))

(defn- sanitize-responses-message [{:keys [call_id content output role tool_call_id tool_calls type]}]
  (cond
    (= "function_call_output" type)
    {:type    "function_call_output"
     :call_id call_id
     :output  (->responses-output output)}

    (= "tool" role)
    {:type    "function_call_output"
     :call_id tool_call_id
     :output  (->responses-output content)}

    (and (= "assistant" role) (seq tool_calls))
    (mapv (fn [tc]
            {:type      "function_call"
             :call_id   (or (:id tc) (get-in tc [:function :id]))
             :name      (or (:name tc) (get-in tc [:function :name]))
             :arguments (or (when (string? (:arguments tc)) (:arguments tc))
                            (get-in tc [:function :arguments])
                            "{}")})
          tool_calls)

    :else
    {:role role :content content}))

(defn- responses-request-base [model input]
  {:model model
   :input input
   :store false})

(defn- ->responses-request [{:keys [model messages system tools]}]
  (let [all-messages (cond->> messages
                        system (into [{:role "system" :content system}]))
        instructions (->> all-messages
                          (filter #(= "system" (:role %)))
                          (map :content)
                          (remove str/blank?)
                          (str/join "\n\n"))
        input        (->> all-messages
                          (remove #(= "system" (:role %)))
                          (mapcat #(let [r (sanitize-responses-message %)]
                                     (if (vector? r) r [r])))
                          vec)]
    (cond-> (responses-request-base model input)
      (seq tools)                       (assoc :tools tools)
      (not (str/blank? instructions)) (assoc :instructions instructions))))

(defn- ->codex-responses-request [request]
  (let [base (->responses-request request)]
    (if (contains? base :instructions)
      base
      (assoc base :instructions ""))))

(defn- process-responses-sse-event [data accumulated]
  (case (:type data)
    "response.output_text.delta"
    (update accumulated :content str (:delta data))

    "response.output_item.added"
    (let [item (:item data)]
      (if (= "function_call" (:type item))
        (update accumulated :tool-calls conj {:id        (:id item)
                                              :name      (:name item)
                                              :arguments {}
                                              :raw-args  ""})
        accumulated))

    "response.function_call_arguments.delta"
    (update accumulated :tool-calls
            (fn [tool-calls]
              (mapv (fn [tool-call]
                      (if (= (:id tool-call) (:item_id data))
                        (update tool-call :raw-args str (:delta data))
                        tool-call))
                    tool-calls)))

    "response.function_call_arguments.done"
    (update accumulated :tool-calls
            (fn [tool-calls]
              (mapv (fn [tool-call]
                      (if (= (:id tool-call) (:item_id data))
                        (let [raw-args (:raw-args tool-call)]
                          (-> tool-call
                              (assoc :arguments (if (str/blank? raw-args)
                                                  {}
                                                  (json/parse-string raw-args true)))
                              (dissoc :raw-args)))
                        tool-call))
                    tool-calls)))

    "response.completed"
    (let [response (:response data)]
      (cond-> accumulated
        response        (assoc :response response)
        (:model response) (assoc :model (:model response))
        (:usage response) (assoc :usage (:usage response))))

    accumulated))

;; endregion ^^^^^ Response Parsing ^^^^^

(defn- next-loop-count [loops]
  (inc loops))

(defn- initial-token-counts []
  {:input-tokens 0 :output-tokens 0})

(defn- initial-loop-count []
  0)

(defn- continue-tool-loop? [tool-calls loops max-loops]
  (and (seq tool-calls) (< loops max-loops)))

(defn- chat-with-completions-api [config base-url headers request]
  (let [url  (str base-url "/chat/completions")
        resp (llm-http/post-json! url headers request (llm-http-opts config))]
    (if (:error resp)
      resp
      (let [choice     (first (:choices resp))
            msg        (:message choice)
            tool-calls (extract-tool-calls (:tool_calls msg))
            usage      (parse-usage (:usage resp))]
        {:message    (cond-> {:role "assistant" :content (or (:content msg) "")}
                             (seq tool-calls) (assoc :tool_calls (mapv (fn [tc]
                                                                         {:function {:name      (:name tc)
                                                                                     :arguments (:arguments tc)}})
                                                                       tool-calls)))
         :model      (:model resp)
         :tool-calls tool-calls
         :usage      usage
         :_headers   headers}))))

(defn- chat-stream-with-completions-api [config base-url headers request on-chunk]
  (let [url     (str base-url "/chat/completions")
        body    (assoc request :stream true)
        initial {:role "assistant" :content "" :model nil :usage {}}
        result  (llm-http/post-sse! url headers body on-chunk process-sse-event initial (llm-http-opts config))]
    (if (:error result)
      result
      (let [usage (parse-usage (:usage result))]
        {:message  {:role "assistant" :content (:content result)}
         :model    (:model result)
         :usage    usage
         :_headers headers}))))

(defn- chat-stream-with-responses-api [config base-url headers request on-delta]
  (let [url      (str base-url "/responses")
        body     (assoc (->codex-responses-request request) :stream true)
        initial  {:role "assistant" :content "" :model nil :usage {} :response nil :tool-calls []}
        result   (llm-http/post-sse! url headers body
                                     (fn [chunk]
                                       (when (= "response.output_text.delta" (:type chunk))
                                         (on-delta {:delta {:text (:delta chunk)}})))
                                     process-responses-sse-event initial (llm-http-opts config))]
    (if (:error result)
      result
      (let [tool-calls (:tool-calls result)]
        {:message  (cond-> {:role "assistant" :content (:content result)}
                           (seq tool-calls) (assoc :tool_calls (mapv (fn [tc]
                                                                       {:id       (:id tc)
                                                                        :type     "function"
                                                                        :function {:name      (:name tc)
                                                                                   :arguments (:arguments tc)}})
                                                                     tool-calls)))
         :model      (:model result)
         :tool-calls tool-calls
         :usage      (parse-usage (:usage result))
         :_headers   headers}))))


;; region ----- Public API -----

(defn chat
  "Send a non-streaming chat completions request."
  [request & [{:keys [provider-config] :as opts}]]
  (let [config   (or provider-config {})
        base-url (provider-base-url config)
        auth-err (missing-auth-error config)]
    (if auth-err
      auth-err
      (let [headers (auth-headers config)]
        (if (chat-completions-request? config)
          (chat-with-completions-api config base-url headers request)
          (chat-stream-with-responses-api config base-url headers request (fn [_] nil)))))))

(defn chat-stream
  "Send a streaming chat completions request via SSE."
  [request on-chunk & [{:keys [provider-config] :as opts}]]
  (let [config   (or provider-config {})
        base-url (provider-base-url config)
        auth-err (missing-auth-error config)]
    (if auth-err
      auth-err
      (let [headers (auth-headers config)]
        (if (chat-completions-request? config)
          (chat-stream-with-completions-api config base-url headers request on-chunk)
          (chat-stream-with-responses-api config base-url headers request on-chunk))))))

(defn followup-messages
  "Build the next iteration's :messages vector for OpenAI Chat Completions /
   Responses API. Assistant message carries tool_calls in the function-call
   wire format; tool replies are role=tool with tool_call_id."
  [request response tool-calls tool-results]
  (let [assistant-msg {:role       "assistant"
                       :content    (get-in response [:message :content])
                       :tool_calls (mapv (fn [tc]
                                           {:id       (:id tc)
                                            :type     "function"
                                            :function {:name      (:name tc)
                                                       :arguments (json/generate-string (:arguments tc))}})
                                         tool-calls)}
        result-msgs   (mapv (fn [tc result]
                              {:role         "tool"
                               :tool_call_id (:id tc)
                               :content      result})
                            tool-calls
                            tool-results)]
    (into (conj (vec (:messages request)) assistant-msg) result-msgs)))

(defn chat-with-tools
  "Execute a chat with tool call loop. Thin shim over isaac.llm.tool-loop/run."
  [request tool-fn & [opts]]
  (tool-loop/run
    (fn [req] (chat req opts))
    followup-messages
    request
    tool-fn
    (select-keys opts [:max-loops])))

;; endregion ^^^^^ Public API ^^^^^
