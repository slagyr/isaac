(ns isaac.llm.api.openai.shared
  "Shared auth and wire utilities for OpenAI-family providers.
   Used by isaac.llm.api.openai-completions and isaac.llm.api.openai-responses."
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.llm.auth.store :as auth-store]
    [isaac.llm.followup :as followup]))

;; region ----- Auth -----

(defn decode-jwt-payload [token]
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

(defn resolve-oauth-tokens [{:keys [auth name] :as config}]
  (when (= "oauth-device" auth)
    (when-let [state-dir (or (:auth-dir config) (:state-dir config))]
      (let [tokens (auth-store/load-tokens state-dir name)]
        (when (and tokens (not (auth-store/token-expired? tokens)))
          tokens)))))

(defn missing-auth-error [{:keys [auth apiKey name] :as config}]
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
                                    ["OpenAI" "OPENAI_API_KEY"])]
      {:error   :auth-missing
       :message (str "Missing " provider-name " API key. Set " env-var " or configure provider :apiKey.")})))

(defn provider-base-url [{:keys [baseUrl]}]
  (or baseUrl "http://localhost:11434/v1"))

(defn llm-http-opts [config]
  (cond-> {}
    (:session-key config)       (assoc :session-key (:session-key config))
    (:simulate-provider config) (assoc :simulate-provider (:simulate-provider config))))

(defn auth-headers [config]
  (let [oauth-tokens (resolve-oauth-tokens config)
        oauth-token  (:access oauth-tokens)
        account-id   (or (extract-account-id oauth-tokens)
                         (when (= "openai-chatgpt" (:simulate-provider config)) "grover-account"))
        api-key      (:apiKey config)
        token        (or oauth-token api-key)]
    (cond-> {"content-type" "application/json"}
      token                             (assoc "Authorization" (str "Bearer " token))
      account-id                        (assoc "ChatGPT-Account-Id" account-id)
      (= "oauth-device" (:auth config)) (assoc "originator" "isaac"))))

;; endregion ^^^^^ Auth ^^^^^

(defn parse-usage [usage]
  {:input-tokens  (or (:prompt_tokens usage) (:input_tokens usage) 0)
   :output-tokens (or (:completion_tokens usage) (:output_tokens usage) 0)})

(defn followup-messages
  "Build the next iteration's :messages vector. Assistant message carries
   tool_calls in OpenAI function-call wire format; tool replies are role=tool."
  [request response tool-calls tool-results]
  (let [assistant-msg {:role       "assistant"
                       :content    (get-in response [:message :content])
                       :tool_calls (mapv (fn [tc]
                                           {:id       (:id tc)
                                            :type     "function"
                                            :function {:name      (:name tc)
                                                       :arguments (json/generate-string (:arguments tc))}})
                                         tool-calls)}
        result-msgs   (followup/map-tool-results tool-calls tool-results
                                                 (fn [tc result]
                                                   {:role         "tool"
                                                    :tool_call_id (:id tc)
                                                    :content      result}))]
    (followup/append-followup-messages request assistant-msg result-msgs)))
