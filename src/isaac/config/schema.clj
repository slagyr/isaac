(ns isaac.config.schema
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.path :as path]
    [clojure.string :as str]))

(defn ->id [value]
  (cond
    (keyword? value) (name value)
    (string? value)  value
    (nil? value)     nil
    :else            (str value)))

;; region ----- Entity Schemas -----

(def defaults
  {:crew  {:type      :string
           :coerce    [->id]
           :default   "main"
           :doc       "Default crew member id"
           :required? false}
   :model {:type      :string
           :coerce    [->id]
           :default   "llama"
           :doc       "Default model alias"
           :required? false}})

(def crew
  {:id    {:type      :string
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
           :required? false}})

(def model
  {:id            {:type      :string
                   :coerce    [->id]
                   :doc       "Model alias; must match filename when present"
                   :required? false}
   :model         {:type      :string
                   :doc       "Provider-specific model name or id"
                   :required? true
                   :validate  schema/present?
                   :message   "must be present"}
   :provider      {:type      :string
                   :coerce    [->id]
                   :doc       "Provider alias"
                   :required? true
                   :validate  schema/present?
                   :message   "must be present"}
   :context-window {:type      :int
                    :doc       "Context window size in tokens"
                    :required? false}})

(def provider
  {:api                     {:type      :string
                             :doc       "Provider API adapter (e.g. \"anthropic\", \"ollama\")"
                             :required? false}
   :api-key                 {:type      :string
                             :doc       "API key"
                             :required? false}
   :auth-key                {:type      :string
                             :doc       "Authentication key"
                             :required? false}
   :assistant-base-url      {:type      :string
                             :doc       "Base URL for assistant endpoints"
                             :required? false}
   :base-url                {:type      :string
                             :doc       "API base URL"
                             :required? false}
   :headers                 {:type      :ignore
                             :validate  #(or (nil? %) (map? %))
                             :message   "must be a map"
                             :doc       "Extra HTTP headers to include in requests"
                             :required? false}
   :id                      {:type      :string
                             :coerce    [->id]
                             :doc       "Provider id; must match filename when present"
                             :required? false}
   :name                    {:type      :string
                             :doc       "Display name"
                             :required? false}
   :originator              {:type      :string
                             :doc       "X-Originator header value"
                             :required? false}
   :response-format         {:type      :string
                             :doc       "Response format hint"
                             :required? false}
   :stream-supports-tool-calls {:type      :boolean
                                :doc       "Whether streaming mode supports tool calls"
                                :required? false}
   :supports-system-role    {:type      :boolean
                             :doc       "Whether the provider accepts a system role message"
                             :required? false}
   :token                   {:type      :string
                             :doc       "Authentication token (alias for api-key)"
                             :required? false}})

(def root
  {:acp       {:type      :ignore
                :validate  #(or (nil? %) (map? %))
                :message   "must be a map"
                :doc       "Agent Communication Protocol configuration"
                :required? false}
   :crew      {:type      :ignore
               :validate  #(or (nil? %) (map? %))
               :message   "must be a map"
               :doc       "Crew member configurations (map of id -> crew entity)"
               :required? false}
   :defaults  {:type      :ignore
               :validate  #(or (nil? %) (map? %))
               :message   "must be a map"
               :doc       "Default crew and model selections"
               :required? false}
   :dev       {:type      :ignore
               :doc       "Development mode flag"
               :required? false}
   :gateway   {:type      :ignore
               :validate  #(or (nil? %) (map? %))
               :message   "must be a map"
               :doc       "Gateway server configuration"
               :required? false}
    :models    {:type      :ignore
                :validate  #(or (nil? %) (map? %))
                :message   "must be a map"
                :doc       "Model configurations (map of id -> model entity)"
                :required? false}
    :prefer-entity-files {:type      :boolean
                          :default   false
                          :doc       "Prefer crew/*.edn, models/*.edn, and providers/*.edn for new entities"
                          :required? false}
    :providers {:type      :ignore
                :validate  #(or (nil? %) (map? %))
                :message   "must be a map"
                :doc       "Provider configurations (map of id -> provider entity)"
                :required? false}
   :server    {:type      :ignore
               :validate  #(or (nil? %) (map? %))
               :message   "must be a map"
               :doc       "HTTP server configuration"
               :required? false}})

;; endregion ^^^^^ Entity Schemas ^^^^^

;; region ----- Schema Registry -----

(def entity-schemas
  {:crew      crew
   :defaults  defaults
   :models    model
   :providers provider
   :root      root})

(def defaults-doc-spec {:doc (:doc (:defaults root)) :name :defaults :schema defaults :type :map})
(def crew-doc-spec {:name :crew :schema crew :type :map})
(def model-doc-spec {:name :model :schema model :type :map})
(def provider-doc-spec {:name :provider :schema provider :type :map})

(def root-doc-schema
  (assoc root
    :defaults defaults-doc-spec
    :crew {:doc (:doc (:crew root))
           :required? false
           :type :map
           :value-spec crew-doc-spec}
    :models {:doc (:doc (:models root))
             :required? false
             :type :map
             :value-spec model-doc-spec}
    :providers {:doc (:doc (:providers root))
                :required? false
                :type :map
                :value-spec provider-doc-spec}))

(def root-doc-spec {:name :root :schema root-doc-schema :type :map})

(def schema-paths
  {"crew"      crew-doc-spec
   "defaults"  defaults-doc-spec
   "model"     model-doc-spec
   "models"    (:models root-doc-schema)
   "provider"  provider-doc-spec
   "providers" (:providers root-doc-schema)
   "root"      root-doc-spec})

(def ^:private entity-collections #{:crew :models :providers})

(def top-level-keys (set (keys root)))
(def defaults-keys (set (keys defaults)))
(def crew-keys (set (keys crew)))
(def model-keys (set (keys model)))
(def provider-keys (set (keys provider)))

(defn entity-schema [kind]
  (get entity-schemas kind))

(defn- normalize-schema-path [path-str]
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
    (get schema-paths "root")

    :else
    (or (get schema-paths path-str)
        (path/schema-at root-doc-schema path-str))))

(defn schema-for-data-path [path-str]
  (or (schema-for-path path-str)
      (when-let [normalized (normalize-schema-path path-str)]
        (path/schema-at root-doc-schema normalized))))

(defn conform-entity [kind entity]
  (schema/conform (entity-schema kind) entity))

(defn conform-entity! [kind entity]
  (schema/conform! (entity-schema kind) entity))

(defn conform-entities [kind entities]
  (loop [remaining (seq entities)
         conformed {}
         errors {}]
    (if remaining
      (let [[id entity] (first remaining)
            result      (conform-entity kind entity)]
        (if (schema/error? result)
          (recur (next remaining) conformed (assoc errors id result))
          (recur (next remaining) (assoc conformed id result) errors)))
      (if (seq errors) errors conformed))))

(defn conform-entities! [kind entities]
  (let [result (conform-entities kind entities)]
    (if (schema/error? result)
      (throw (ex-info "Unconformable entities" result))
      result)))

;; endregion ^^^^^ Schema Registry ^^^^^

;; region ----- Conformance -----

;; endregion ^^^^^ Conformance ^^^^^

;; region ----- Compatibility Aliases -----

(def defaults-schema defaults)
(def crew-schema crew)
(def model-schema model)
(def provider-schema provider)
(def root-schema root)

;; endregion ^^^^^ Compatibility Aliases ^^^^^
