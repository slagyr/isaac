(ns mdm.isaac.llm.ollama
  "Ollama LLM implementation."
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.config :as config]
            [mdm.isaac.llm.core :as llm]))

(defn ollama-url []
  (get-in config/active [:llm :url] "http://localhost:11434"))

(defn ollama-model []
  (get-in config/active [:llm :model] "mistral:latest"))

(defn- parse-response
  "Parse Ollama response body into content."
  [body]
  (-> body utilc/<-json-kw :message :content))

(defn- parse-tool-calls
  "Parse tool calls from Ollama response if present."
  [body]
  (let [parsed (utilc/<-json-kw body)
        tool-calls (get-in parsed [:message :tool_calls])]
    (when (seq tool-calls)
      (mapv (fn [tc]
              {:name (get-in tc [:function :name])
               :arguments (get-in tc [:function :arguments])})
            tool-calls))))

(defn- post-chat!
  "Make POST request to Ollama chat API."
  [payload]
  (let [response (rest/post! (str (ollama-url) "/api/chat") {:body payload})]
    (if (= 200 (:status response))
      (:body response)
      (throw (ex-info "Ollama chat request failed"
                      {:status (:status response) :body (:body response)})))))

(defmethod llm/chat :ollama [prompt]
  (let [payload {:model    (ollama-model)
                 :messages [{:role "user" :content prompt}]
                 :stream   false}
        body (post-chat! payload)]
    (parse-response body)))

(defmethod llm/chat-with-tools :ollama [messages tools]
  (let [payload {:model    (ollama-model)
                 :messages messages
                 :tools    tools
                 :stream   false}
        body (post-chat! payload)]
    {:content    (parse-response body)
     :tool-calls (parse-tool-calls body)}))
