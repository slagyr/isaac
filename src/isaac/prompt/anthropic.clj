(ns isaac.prompt.anthropic
  "Anthropic-specific prompt formatting.
   Converts the generic system prompt into the format
   expected by the Anthropic Messages API."
  (:require [isaac.prompt.builder :as builder]))

(defn system-message
  "Build a system message string for the Anthropic Messages API.
   The Anthropic API takes system as a top-level string parameter,
   not as a message in the messages array.
   Accepts an optional sequence of tool maps."
  ([] (system-message nil))
  ([tools] (builder/system-prompt tools)))

(defn user-message
  "Format a user message for the Anthropic Messages API."
  [content]
  {:role "user" :content content})

(defn assistant-message
  "Format an assistant message for the Anthropic Messages API."
  [content]
  {:role "assistant" :content content})

(defn- format-tool-param
  "Format a single parameter for the Anthropic tool schema."
  [[param-name {:keys [type description required]}]]
  [(name param-name)
   (cond-> {:type (name (or type :string))}
     description (assoc :description description))])

(defn format-tool
  "Convert an internal tool map to Anthropic's tool definition format.
   Input:  {:name :read-file :description \"...\" :params {:path {:type :string :required true}}}
   Output: {:name \"read-file\" :description \"...\"
            :input_schema {:type \"object\" :properties {...} :required [...]}}"
  [{:keys [name description params]}]
  (let [properties (into {} (map format-tool-param params))
        required (->> params
                      (filter (fn [[_ v]] (:required v)))
                      (mapv (fn [[k _]] (clojure.core/name k))))]
    (cond-> {:name         (clojure.core/name name)
             :description  description
             :input_schema {:type       "object"
                            :properties properties}}
      (seq required) (assoc-in [:input_schema :required] required))))

(defn format-tools
  "Convert a sequence of internal tool maps to Anthropic format."
  [tools]
  (mapv format-tool tools))

(defn chat-request
  "Build a complete Anthropic Messages API request map.
   Options:
     :model    - model name (default \"claude-sonnet-4-20250514\")
     :messages - vector of message maps
     :tools    - optional vector of internal tool maps
     :system   - optional system prompt override"
  [{:keys [model messages tools system max-tokens]}]
  (let [sys (or system (system-message tools))]
    (cond-> {:model      (or model "claude-sonnet-4-20250514")
             :max_tokens (or max-tokens 4096)
             :system     sys
             :messages   messages}
      (seq tools) (assoc :tools (format-tools tools)))))
