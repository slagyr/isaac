(ns mdm.isaac.tool.core
  "Tool system for Isaac - allows Isaac to perform actions during conversations."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]))

;; Tool registry - atom holding map of tool-name -> tool-definition
(defonce ^:private registry (atom {}))

(defn register!
  "Register a tool. Tool must have :name, :description, and :execute keys."
  [tool]
  (swap! registry assoc (:name tool) tool))

(defn get-tool
  "Get a tool by name. Returns nil if not found."
  [tool-name]
  (get @registry tool-name))

(defn all-tools
  "Get all registered tools."
  []
  (vals @registry))

(defn clear!
  "Clear all registered tools. Useful for testing."
  []
  (reset! registry {}))

(defn execute!
  "Execute a tool by name with given params.
   Returns the result of the tool's execute function,
   or an error map if tool not found."
  [tool-name params]
  (if-let [tool (get-tool tool-name)]
    ((:execute tool) params)
    {:status :error
     :message (str "Unknown tool: " (name tool-name))}))

(defn- format-param
  "Format a parameter definition for the prompt."
  [[param-name {:keys [type required]}]]
  (str "    - " (name param-name) " (" (name type) ")"
       (when required " [required]")))

(defn tools-prompt
  "Generate a prompt section describing available tools."
  []
  (let [tools (all-tools)]
    (if (empty? tools)
      ""
      (str "## Available Tools\n"
           "You can use tools by including a line like: TOOL_CALL: tool-name {\"param\": \"value\"}\n\n"
           (->> tools
                (map (fn [{:keys [name description params]}]
                       (str "### " (clojure.core/name name) "\n"
                            description "\n"
                            (when params
                              (str "Parameters:\n"
                                   (str/join "\n" (map format-param params))
                                   "\n")))))
                (str/join "\n"))))))

(defn parse-tool-call
  "Parse a tool call from LLM response.
   Looks for: TOOL_CALL: tool-name {json-params}
   Returns {:tool :tool-name :params {...}} or nil if no tool call found."
  [response]
  (when-let [match (re-find #"TOOL_CALL:\s*(\S+)\s*(\{[^}]+\})" response)]
    (let [[_ tool-name params-json] match]
      {:tool (keyword tool-name)
       :params (-> params-json
                   (json/read-str :key-fn keyword))})))
