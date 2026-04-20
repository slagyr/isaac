(ns isaac.config.schema
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.path :as path]
    [clojure.string :as str]))

(defn ->id [value]
  (cond
    (keyword? value) (name value)
    (string? value) value
    (nil? value) nil
    :else (str value)))

(defn schema-fields [spec]
  (:schema spec))

;; region ----- Entity Schemas -----

(def defaults
  {:name   :defaults
   :type   :map
   :schema {:crew  {:type      :string
                    :coerce    [->id]
                    :default   "main"
                    :doc       "Default crew member id"
                    :required? false}
            :model {:type      :string
                    :coerce    [->id]
                    :default   "llama"
                    :doc       "Default model alias"
                    :required? false}}})

(def crew
  {:name   :crew
   :type   :map
   :schema {:id    {:type      :string
                    :coerce    [->id]
                    :doc       "Crew member id; must match filename when present"
                    :required? false}
            :model {:type      :string
                    :coerce    [->id]
                    :doc       "Model alias"
                    :required? false}
            :soul  {:type      :string
                    :doc       "System prompt"
                    :required? false}
            :tools {:type      :ignore
                    :validate  #(or (nil? %) (map? %))
                    :message   "must be a map"
                    :doc       "Tool configuration"
                    :required? false}}})

(def model
  {:name   :model
   :type   :map
   :schema {:id             {:type      :string
                             :coerce    [->id]
                             :doc       "Model alias; must match filename when present"
                             :required? false}
            :model          {:type      :string
                             :doc       "Provider-specific model name or id"
                             :required? true
                             :validate  schema/present?
                             :message   "must be present"}
            :provider       {:type      :string
                             :coerce    [->id]
                             :doc       "Provider alias"
                             :required? true
                             :validate  schema/present?
                             :message   "must be present"}
            :context-window {:type      :int
                             :doc       "Context window size in tokens"
                             :required? false}}})

(def provider
  {:name   :provider
   :type   :map
   :schema {:api                        {:type      :string
                                         :doc       "Provider API adapter (e.g. \"anthropic\", \"ollama\")"
                                         :required? false}
            :api-key                    {:type      :string
                                         :doc       "API key"
                                         :required? false}
            :auth-key                   {:type      :string
                                         :doc       "Authentication key"
                                         :required? false}
            :assistant-base-url         {:type      :string
                                         :doc       "Base URL for assistant endpoints"
                                         :required? false}
            :base-url                   {:type      :string
                                         :doc       "API base URL"
                                         :required? false}
            :headers                    {:type      :ignore
                                         :validate  #(or (nil? %) (map? %))
                                         :message   "must be a map"
                                         :doc       "Extra HTTP headers to include in requests"
                                         :required? false}
            :id                         {:type      :string
                                         :coerce    [->id]
                                         :doc       "Provider id; must match filename when present"
                                         :required? false}
            :name                       {:type      :string
                                         :doc       "Display name"
                                         :required? false}
            :originator                 {:type      :string
                                         :doc       "X-Originator header value"
                                         :required? false}
            :response-format            {:type      :string
                                         :doc       "Response format hint"
                                         :required? false}
            :stream-supports-tool-calls {:type      :boolean
                                         :doc       "Whether streaming mode supports tool calls"
                                         :required? false}
            :supports-system-role       {:type      :boolean
                                         :doc       "Whether the provider accepts a system role message"
                                         :required? false}
            :token                      {:type      :string
                                         :doc       "Authentication token (alias for api-key)"
                                         :required? false}}})

(def root
  {:name   :root
   :type   :map
   :schema {:acp                 {:type      :ignore
                                  :validate  #(or (nil? %) (map? %))
                                  :message   "must be a map"
                                  :doc       "Agent Communication Protocol configuration"
                                  :required? false}
            :crew                {:doc        "Crew member configurations (map of id -> crew-entity)"
                                  :required?  false
                                  :type       :map
                                  :key-spec   {:type :string}
                                  :value-spec crew}
            :defaults            (assoc defaults
                                   :doc       "Default crew and model selections"
                                   :required? false)
            :dev                 {:type      :ignore
                                  :doc       "Development mode flag"
                                  :required? false}
            :gateway             {:type      :ignore
                                  :validate  #(or (nil? %) (map? %))
                                  :message   "must be a map"
                                  :doc       "Gateway server configuration"
                                  :required? false}
            :models              {:doc        "Model configurations (map of id -> model entity)"
                                  :required?  false
                                  :type       :map
                                  :value-spec model}
            :prefer-entity-files {:type      :boolean
                                  :default   false
                                  :doc       "Prefer crew/*.edn, models/*.edn, and providers/*.edn for new entities"
                                  :required? false}
            :providers           {:doc        "Provider configurations (map of id -> provider entity)"
                                  :required?  false
                                  :type       :map
                                  :value-spec provider}
            :server              {:type      :ignore
                                  :validate  #(or (nil? %) (map? %))
                                  :message   "must be a map"
                                  :doc       "HTTP server configuration"
                                  :required? false}}})

;; endregion ^^^^^ Entity Schemas ^^^^^

;; region ----- Schema Registry -----

(def ^:private entity-collections #{:crew :models :providers})

(defn- normalize-template-path [path-str]
  (let [segments (path/parse path-str)]
    (when (seq segments)
      (path/unparse
        (map (fn [segment]
               (if (and (= :key (first segment)) (= :_ (second segment)))
                 [:wildcard]
                 segment))
             segments)))))

(defn- normalize-data-path [path-str]
  (let [segments (path/parse path-str)]
    (when (seq segments)
      (path/unparse
        (map-indexed (fn [idx segment]
                       (if (and (= 1 idx)
                                (contains? entity-collections (second (first segments)))
                                (#{:key :str} (first segment)))
                         [:wildcard]
                         segment))
                     segments)))))

(defn schema-for-path [path-str]
  (cond
    (or (nil? path-str) (str/blank? path-str))
    root

    :else
    (try
      (or (path/schema-at root path-str)
          (when-let [normalized (normalize-template-path path-str)]
            (path/schema-at root normalized)))
      (catch Exception _ nil))))

(defn schema-for-data-path [path-str]
  (try
    (or (schema-for-path path-str)
        (when-let [normalized (normalize-data-path path-str)]
          (path/schema-at root normalized)))
    (catch Exception _ nil)))

;; endregion ^^^^^ Schema Registry ^^^^^
