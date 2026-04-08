(ns isaac.llm.openai-compat
  (:require
    [clojure.string :as str]
    [cheshire.core :as json]
    [isaac.auth.store :as auth-store]
    [isaac.llm.http :as llm-http]))

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

(defn- resolve-oauth-tokens [{:keys [auth name]}]
  (when (= "oauth-device" auth)
    (let [state-dir (str (System/getProperty "user.home") "/.isaac")
          tokens    (auth-store/load-tokens state-dir name)]
      (when (and tokens (not (auth-store/token-expired? tokens)))
        tokens))))

(defn- provider-base-url [{:keys [auth baseUrl]}]
  (if (= "oauth-device" auth)
    (if (or (nil? baseUrl) (str/includes? baseUrl "api.openai.com"))
      "https://chatgpt.com/backend-api/codex"
      baseUrl)
    (or baseUrl "http://localhost:11434/v1")))

(defn- auth-headers [config]
  (let [oauth-tokens (resolve-oauth-tokens config)
        oauth-token  (:access oauth-tokens)
        account-id   (extract-account-id oauth-tokens)
        api-key     (:apiKey config)
        token       (or oauth-token api-key)]
    (cond-> {"content-type" "application/json"}
      token      (assoc "Authorization" (str "Bearer " token))
      account-id (assoc "chatgpt-account-id" account-id))))

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
  {:inputTokens  (or (:prompt_tokens usage) (:input_tokens usage) 0)
   :outputTokens (or (:completion_tokens usage) (:output_tokens usage) 0)})

(defn- chat-completions-request? [{:keys [auth]}]
  (not= "oauth-device" auth))

(defn- sanitize-responses-message [{:keys [role content]}]
  {:role role :content content})

(defn- ->responses-request [{:keys [model messages system]}]
  (let [all-messages (cond->> messages
                       system (into [{:role "system" :content system}]))
        instructions (->> all-messages
                          (filter #(= "system" (:role %)))
                          (map :content)
                          (remove str/blank?)
                          (str/join "\n\n"))
        input        (->> all-messages
                          (remove #(= "system" (:role %)))
                          (mapv sanitize-responses-message)
                          vec)]
    (cond-> {:model model
             :input input
             :store false}
      (not (str/blank? instructions)) (assoc :instructions instructions))))

(defn- extract-output-text [output]
  (->> output
       (filter #(= "message" (:type %)))
       (mapcat :content)
       (filter #(= "output_text" (:type %)))
       (map :text)
       (apply str)))

(defn- parse-responses-result [resp headers]
  {:message  {:role "assistant" :content (or (extract-output-text (:output resp)) "")}
   :model    (:model resp)
   :usage    (parse-usage (:usage resp))
   :_headers headers})

(defn- process-responses-sse-event [data accumulated]
  (case (:type data)
    "response.output_text.delta"
    (update accumulated :content str (:delta data))

    "response.completed"
    (let [response (:response data)]
      (cond-> accumulated
        response        (assoc :response response)
        (:model response) (assoc :model (:model response))
        (:usage response) (assoc :usage (:usage response))))

    accumulated))

;; endregion ^^^^^ Response Parsing ^^^^^

;; region ----- Public API -----

(defn chat
  "Send a non-streaming chat completions request."
  [request & [{:keys [provider-config] :as opts}]]
  (let [config   (or provider-config {})
        base-url (provider-base-url config)
        headers  (auth-headers config)]
    (if (chat-completions-request? config)
      (let [url  (str base-url "/chat/completions")
            resp (llm-http/post-json! url headers request)]
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
             :_headers   headers})))
      (let [url  (str base-url "/responses")
            resp (llm-http/post-json! url headers (->responses-request request))]
        (if (:error resp) resp (parse-responses-result resp headers))))))

(defn chat-stream
  "Send a streaming chat completions request via SSE."
  [request on-chunk & [{:keys [provider-config] :as opts}]]
  (let [config   (or provider-config {})
        base-url (provider-base-url config)
        headers  (auth-headers config)]
    (if (chat-completions-request? config)
      (let [url     (str base-url "/chat/completions")
            body    (assoc request :stream true)
            initial {:role "assistant" :content "" :model nil :usage {}}
            result  (llm-http/post-sse! url headers body on-chunk process-sse-event initial)]
        (if (:error result)
          result
          (let [usage (parse-usage (:usage result))]
            {:message  {:role "assistant" :content (:content result)}
             :model    (:model result)
             :usage    usage
             :_headers headers})))
      (let [url      (str base-url "/responses")
            body     (assoc (->responses-request request) :stream true)
            initial  {:role "assistant" :content "" :model nil :usage {} :response nil}
            result   (llm-http/post-sse! url headers body
                                         (fn [chunk]
                                           (when (= "response.output_text.delta" (:type chunk))
                                             (on-chunk {:delta {:text (:delta chunk)}})))
                                         process-responses-sse-event initial)]
        (if (:error result)
          result
          {:message  {:role "assistant" :content (:content result)}
           :model    (:model result)
           :usage    (parse-usage (:usage result))
           :_headers headers})))))

(defn chat-with-tools
  "Execute a chat with tool call loop."
  [request tool-fn & [{:keys [max-loops] :or {max-loops 10} :as opts}]]
  (loop [req          request
         all-tools    []
         total-usage  {:inputTokens 0 :outputTokens 0}
         loops        0]
    (let [response (chat req opts)]
      (if (:error response)
        response
        (let [usage      (:usage response)
              merged     (merge-with + total-usage usage)
              tool-calls (:tool-calls response)]
          (if (and (seq tool-calls) (< loops max-loops))
            (let [assistant-msg {:role       "assistant"
                                 :content    (get-in response [:message :content])
                                 :tool_calls (mapv (fn [tc]
                                                     {:id       (:id tc)
                                                      :type     "function"
                                                      :function {:name      (:name tc)
                                                                 :arguments (json/generate-string (:arguments tc))}})
                                                   tool-calls)}
                  tool-results  (mapv (fn [tc]
                                        {:role         "tool"
                                         :tool_call_id (:id tc)
                                         :content      (tool-fn (:name tc) (:arguments tc))})
                                      tool-calls)
                  new-messages  (into (conj (vec (:messages req)) assistant-msg) tool-results)]
              (recur (assoc req :messages new-messages)
                     (into all-tools tool-calls)
                     merged (inc loops)))
            {:response     response
             :tool-calls   all-tools
             :token-counts merged}))))))

;; endregion ^^^^^ Public API ^^^^^
