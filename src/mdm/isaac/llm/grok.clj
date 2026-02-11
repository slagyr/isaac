(ns mdm.isaac.llm.grok
  "Grok (xAI) LLM implementation - OpenAI-compatible API."
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.config :as config]
            [mdm.isaac.llm.core :as llm]
            [mdm.isaac.secret.core :as secret]))

(defn grok-url []
  (get-in config/active [:llm :url] "https://api.x.ai/v1"))

(defn grok-model []
  (get-in config/active [:llm :model] "grok-3-latest"))

(defn- api-key []
  (secret/get-secret "GROK_API_KEY"))

(defn- parse-content
  "Parse assistant content from Grok response body."
  [body]
  (-> body utilc/<-json-kw :choices first :message :content))

(defn- parse-tool-calls
  "Parse tool calls from Grok response if present."
  [body]
  (let [parsed (utilc/<-json-kw body)
        tool-calls (-> parsed :choices first :message :tool_calls)]
    (when (seq tool-calls)
      (mapv (fn [tc]
              {:name (get-in tc [:function :name])
               :arguments (utilc/<-json-kw (get-in tc [:function :arguments]))})
            tool-calls))))

(defn- post-chat!
  "Make POST request to Grok chat completions API."
  [payload]
  (let [response (rest/post! (str (grok-url) "/chat/completions")
                             {:headers {"Authorization" (str "Bearer " (api-key))
                                        "Content-Type" "application/json"}
                              :body payload})]
    (if (= 200 (:status response))
      (:body response)
      (throw (ex-info "Grok chat request failed"
                      {:status (:status response) :body (:body response)})))))

(defmethod llm/chat :grok [prompt]
  (let [payload {:model (grok-model)
                 :messages [{:role "user" :content prompt}]}
        body (post-chat! payload)]
    (parse-content body)))

(defmethod llm/chat-with-tools :grok [messages tools]
  (let [payload {:model (grok-model)
                 :messages messages
                 :tools tools}
        body (post-chat! payload)]
    {:content (parse-content body)
     :tool-calls (parse-tool-calls body)}))
