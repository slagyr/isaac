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
   :doc    "Default crew and model selections"
   :schema {:crew  {:type    :string
                    :coerce  [->id]
                    :default "main"
                    :doc     "Default crew member id"}
            :model {:type    :string
                    :coerce  [->id]
                    :default "llama"
                    :doc     "Default model alias"}}})

(def tools
  {:name   :tools
   :type   :map
   :doc    "Tool configuration"
   :schema {:allow       {:type :seq
                          :spec {:type :keyword}
                          :doc  "Allowed tool names"}
            :directories {:type :seq
                          :spec {:type     :ignore
                                 :validate #(or (= :cwd %) (string? %))
                                 :message  "must be :cwd or an absolute path string"}
                          :doc  "Allowed directories; :cwd expands to session cwd, strings are absolute paths"}}})

(def crew
  {:name   :crew
   :type   :map
   :schema {:id    {:type   :string
                    :coerce [->id]
                    :doc    "Crew member id; must match filename when present"}
            :model {:type   :string
                    :coerce [->id]
                    :doc    "Model alias"}
            :soul  {:type :string
                    :doc  "System prompt"}
            :tools tools}})

(def model
  {:name   :model
   :type   :map
   :schema {:id             {:type   :string
                             :coerce [->id]
                             :doc    "Model alias; must match filename when present"}
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
            :context-window {:type :int
                             :doc  "Context window size in tokens"}}})

(def provider
  {:name   :provider
   :type   :map
   :schema {:api                        {:type :string
                                         :doc  "Provider API adapter (e.g. \"anthropic\", \"ollama\")"}
            :api-key                    {:type :string
                                         :doc  "API key"}
            :auth-key                   {:type :string
                                         :doc  "Authentication key"}
            :assistant-base-url         {:type :string
                                         :doc  "Base URL for assistant endpoints"}
            :base-url                   {:type :string
                                         :doc  "API base URL"}
            :headers                    {:type       :map
                                         :key-spec   {:type :string}
                                         :value-spec {:type :string}
                                         :doc        "Extra HTTP headers to include in requests"}
            :id                         {:type   :string
                                         :coerce [->id]
                                         :doc    "Provider id; must match filename when present"}
            :name                       {:type :string
                                         :doc  "Display name"}
            :originator                 {:type :string
                                         :doc  "X-Originator header value"}
            :response-format            {:type :string
                                         :doc  "Response format hint"}
            :stream-supports-tool-calls {:type :boolean
                                         :doc  "Whether streaming mode supports tool calls"}
            :supports-system-role       {:type :boolean
                                         :doc  "Whether the provider accepts a system role message"}
            :token                      {:type :string
                                         :doc  "Authentication token (alias for api-key)"}}})

(def acp
  {:name   :acp
   :type   :map
   :doc    "Agent Communication Protocol configuration"
   :schema {:proxy-max-reconnects {:type :int
                                   :doc  "Maximum reconnect attempts for ACP proxy"}}})

(def server
  {:name   :server
   :type   :map
   :doc    "HTTP server configuration"
   :schema {:host {:type :string :doc "Bind host"}
            :port {:type :int    :doc "Bind port"}}})

(def gateway
  {:name   :gateway
   :type   :map
   :doc    "Gateway server configuration (ACP WebSocket)"
   :schema {:host {:type :string :doc "Bind host"}
            :port {:type :int    :doc "Bind port"}
            :auth {:type   :map
                   :doc    "Gateway auth configuration"
                   :schema {:mode  {:type :keyword :doc "Auth mode (e.g. :token)"}
                            :token {:type :string  :doc "Auth token (env-substituted)"}}}}})

(def root
  {:name   :root
   :type   :map
   :schema {:acp                 acp
            :crew                {:doc        "Crew member configurations (map of id -> crew-entity)"
                                  :type       :map
                                  :key-spec   {:type :string}
                                  :value-spec crew}
            :defaults            defaults
            :dev                 {:type    :boolean
                                  :default false
                                  :doc     "Development mode flag"}
            :gateway             gateway
            :models              {:doc        "Model configurations (map of id -> model entity)"
                                  :type       :map
                                  :value-spec model}
            :prefer-entity-files {:type    :boolean
                                  :default false
                                  :doc     "Prefer crew/*.edn, models/*.edn, and providers/*.edn for new entities"}
            :providers           {:doc        "Provider configurations (map of id -> provider entity)"
                                  :type       :map
                                  :value-spec provider}
            :server              server}})

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
