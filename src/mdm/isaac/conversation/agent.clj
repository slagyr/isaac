(ns mdm.isaac.conversation.agent
  "Conversation agent - tool execution loop for chat.
   Calls LLM with tools, executes tool calls, loops until final response."
  (:require [clojure.data.json :as json]
            [mdm.isaac.conversation.prompt :as prompt]
            [mdm.isaac.tool.core :as tool]))

;; Tool format conversion

(def ^:private type-map
  {:string "string"
   :number "number"
   :long "integer"
   :boolean "boolean"})

(defn- param->json-schema [[param-name {:keys [type]}]]
  [param-name {:type (get type-map type "string")}])

(defn- required-params [params]
  (->> params
       (filter (fn [[_ v]] (:required v)))
       (mapv (fn [[k _]] (name k)))))

(defn tool->llm-tool
  "Convert an Isaac tool definition to OpenAI function-calling format."
  [{:keys [name description params]}]
  {:type "function"
   :function {:name (clojure.core/name name)
              :description description
              :parameters {:type "object"
                           :properties (into {} (map param->json-schema params))
                           :required (required-params params)}}})

(defn registry->llm-tools
  "Convert all registered tools to LLM function-calling format."
  []
  (mapv tool->llm-tool (tool/all-tools)))

;; Tool execution

(defn execute-tool-call
  "Execute a single tool call. Returns the result as a JSON string."
  [{:keys [name arguments]}]
  (let [tool-name (keyword name)
        result (tool/execute! tool-name arguments {:caller :isaac})]
    (json/write-str result)))

;; Message building

(defn- tool-call->api-format
  "Convert a tool call to OpenAI API format for the assistant message."
  [tool-call]
  {:id (str "call_" (:name tool-call))
   :type "function"
   :function {:name (:name tool-call)
              :arguments (json/write-str (:arguments tool-call))}})

(defn- tool-result-message
  "Build a tool result message for the LLM."
  [tool-call result-str]
  {:role "tool"
   :tool_call_id (str "call_" (:name tool-call))
   :content result-str})

(defn tool-calls->messages
  "Build assistant message (with all tool calls) + tool result messages.
   Returns [assistant-message, tool-message-1, tool-message-2, ...]."
  [tool-calls results]
  (into [{:role "assistant"
          :tool_calls (mapv tool-call->api-format tool-calls)}]
        (map tool-result-message tool-calls results)))

;; LLM interaction

(defn respond!
  "Call the LLM with messages and tools. Returns the raw LLM response."
  [messages tools llm-fn]
  (llm-fn messages tools))

;; Agent loop

(def ^:private default-max-iterations 10)

(defn agent-loop!
  "Run the agent loop: call LLM, execute tools, repeat until done.
   Returns {:response \"...\" :messages [...]}."
  ([messages tools llm-fn] (agent-loop! messages tools llm-fn {}))
  ([messages tools llm-fn {:keys [max-iterations] :or {max-iterations default-max-iterations}}]
   (loop [msgs messages
          iteration 0]
     (if (>= iteration max-iterations)
       {:response (str "Stopped: reached max iterations (" max-iterations ")")
        :messages msgs}
       (let [{:keys [content tool-calls]} (respond! msgs tools llm-fn)]
         (if (seq tool-calls)
           (let [results (mapv execute-tool-call tool-calls)
                 tc-msgs (tool-calls->messages tool-calls results)
                 new-msgs (into msgs tc-msgs)]
             (recur new-msgs (inc iteration)))
           {:response content
            :messages (conj msgs {:role "assistant" :content content})}))))))

;; Public API

(defn chat!
  "Send a user message through the agent loop.
   Options:
     :llm-fn - (fn [messages tools] -> {:content ... :tool-calls [...]})
     :history - atom holding conversation message history
   Returns {:response \"...\"}."
  [user-message {:keys [llm-fn history]}]
  (let [history-val (or (some-> history deref) [])
        messages (conj history-val {:role "user" :content user-message})
        llm-messages (into [(prompt/system-message)] messages)
        tools (registry->llm-tools)
        result (agent-loop! llm-messages tools llm-fn)
        ;; Strip system message from stored history
        user-history (filterv #(not= "system" (:role %)) (:messages result))]
    (when history
      (reset! history user-history))
    {:response (:response result)}))
