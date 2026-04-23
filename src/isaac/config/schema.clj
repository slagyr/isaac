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
  {:name        :defaults
   :type        :map
   :description "Default crew and model selections"
   :schema      {:crew  {:type        :string
                         :coerce      [->id]
                         :default     "main"
                         :description "Default crew member id"}
                 :model {:type        :string
                         :coerce      [->id]
                         :default     "llama"
                         :description "Default model alias"}}})

(def tools
  {:name        :tools
   :type        :map
   :description "Tool configuration"
   :schema      {:allow       {:type        :seq
                               :spec        {:type :keyword}
                               :description "Allowed tool names"}
                 :directories {:type        :seq
                               :spec        {:type     :ignore
                                             :validate #(or (= :cwd %) (string? %))
                                             :message  "must be :cwd or an absolute path string"}
                               :description "Allowed directories; :cwd expands to session cwd, strings are absolute paths"}}})

(def crew
  {:name   :crew
   :type   :map
   :schema {:id    {:type        :string
                    :coerce      [->id]
                    :description "Crew member id; must match filename when present"}
            :model {:type        :string
                    :coerce      [->id]
                    :description "ID of the model this crew member uses."}
            :soul  {:type        :string
                    :description "The personality of this crew member. Alternatively saved at config/crew/<id>.md"}
            :tools tools}})

(def model
  {:name   :model
   :type   :map
   :schema {:id             {:type        :string
                             :coerce      [->id]
                             :description "Model alias; must match filename when present"}
            :model          {:type        :string
                             :description "Provider-specific model name or id"
                             :required?   true
                             :validate    schema/present?
                             :message     "must be present"}
            :provider       {:type        :string
                             :coerce      [->id]
                             :description "Provider alias"
                             :required?   true
                             :validate    schema/present?
                             :message     "must be present"}
            :context-window {:type        :int
                             :description "Context window size in tokens"}}})

(def provider
  {:name   :provider
   :type   :map
   :schema {:api                        {:type        :string
                                         :description "Provider API adapter (e.g. \"anthropic\", \"ollama\")"}
            :api-key                    {:type        :string
                                         :description "API key"}
            :auth-key                   {:type        :string
                                         :description "Authentication key"}
            :assistant-base-url         {:type        :string
                                         :description "Base URL for assistant endpoints"}
            :base-url                   {:type        :string
                                         :description "API base URL"}
            :headers                    {:type        :map
                                         :key-spec    {:type :string}
                                         :value-spec  {:type :string}
                                         :description "Extra HTTP headers to include in requests"}
            :id                         {:type        :string
                                         :coerce      [->id]
                                         :description "Provider id; must match filename when present"}
            :name                       {:type        :string
                                         :description "Display name"}
            :originator                 {:type        :string
                                         :description "X-Originator header value"}
            :response-format            {:type        :string
                                         :description "Response format hint"}
            :stream-supports-tool-calls {:type        :boolean
                                         :description "Whether streaming mode supports tool calls"}
            :supports-system-role       {:type        :boolean
                                         :description "Whether the provider accepts a system role message"}
            :token                      {:type        :string
                                         :description "Authentication token (alias for api-key)"}}})

(def acp
  {:name        :acp
   :type        :map
   :description "Agent Communication Protocol configuration"
   :schema      {:proxy-max-reconnects      {:type        :int
                                            :description "Maximum reconnect attempts for ACP proxy"}
                 :proxy-reconnect-delay-ms  {:type        :int
                                            :description "Base reconnect delay in milliseconds for ACP proxy"}
                 :proxy-reconnect-max-delay-ms {:type        :int
                                               :description "Maximum reconnect delay in milliseconds for ACP proxy"}}})

(def server
  {:name        :server
   :type        :map
   :description "HTTP server configuration"
   :schema      {:host {:type :string :description "Bind host"}
                 :port {:type :int :description "Bind port"}}})

(def discord-allow-from
  {:name        :allow-from
   :type        :map
   :description "Discord intake allow-list"
   :schema      {:guilds {:type :seq :spec {:type :string}}
                  :users  {:type :seq :spec {:type :string}}}})

(def discord
  {:name        :discord
   :type        :map
   :description "Discord adapter configuration"
   :schema      {:allow-from discord-allow-from
                  :crew       {:type :string :description "Crew id for Discord sessions"}
                 :message-cap {:type :int :description "Maximum Discord message length before splitting"}
                 :token      {:type :string :description "Discord bot token"}}})

(def comms
  {:name        :comms
   :type        :map
   :description "Communication channel configuration"
   :schema      {:discord discord}})

(def channels
  {:name        :channels
   :type        :map
   :description "Legacy communication channel configuration"
   :schema      {:discord discord}})

(def sessions
  {:name        :sessions
   :type        :map
   :description "Session storage configuration"
   :schema      {:naming-strategy {:type        :ignore
                                   :validate    #(or (keyword? %) (string? %))
                                   :message     "must be a keyword or string"
                                   :description "Session naming strategy"}}})

(def cron-job
  {:name        :cron-job
   :type        :map
   :description "Cron job configuration"
   :schema      {:expr  {:type        :string
                          :description "5-field cron expression"}
                   :crew  {:type        :string
                          :coerce      [->id]
                          :description "Crew id to run the job as"}
                   :prompt {:type        :string
                            :description "Prompt sent when the cron job fires"}}})

(def gateway
  {:name        :gateway
   :type        :map
   :description "Gateway server configuration (ACP WebSocket)"
   :schema      {:host {:type :string :description "Bind host"}
                 :port {:type :int :description "Bind port"}
                 :auth {:type        :map
                        :description "Gateway auth configuration"
                        :schema      {:mode  {:type :keyword :description "Auth mode (e.g. :token)"}
                                      :token {:type :string :description "Auth token (env-substituted)"}}}}})

(def root
  {:name        :isaac
   :type        :map
   :description "Isaac's root level schema"
   :schema      {:acp                 acp
                  :channels            channels
                  :comms               comms
                  :crew                {:description "Crew member configurations (map of id -> crew config)"
                                        :type        :map
                                        :name        "crew table"
                                       :key-spec    {:type :string}
                                       :value-spec  crew}
                 :defaults            defaults
                 :dev                 {:type        :boolean
                                       :default     false
                                       :description "Development mode flag"}
                 :gateway             gateway
                 :models              {:description "Model configurations (map of id -> model config)"
                                       :type        :map
                                       :name        "model table"
                                       :key-spec    {:type :string}
                                       :value-spec  model}
                 :prefer-entity-files {:type        :boolean
                                       :default     false
                                       :description "Prefer crew/*.edn, models/*.edn, and providers/*.edn for new entities"}
                  :providers           {:description "Provider configurations (map of id -> provider config)"
                                        :type        :map
                                        :name        "provider table"
                                        :key-spec    {:type :string}
                                        :value-spec  provider}
                  :cron                {:description "Cron job configurations (map of job name -> cron job config)"
                                        :type        :map
                                        :name        "cron table"
                                        :key-spec    {:type :string}
                                        :value-spec  cron-job}
                  :sessions            sessions
                  :server              server
                  :tz                  {:type        :string
                                        :description "IANA timezone name for cron evaluation"}}})

;; endregion ^^^^^ Entity Schemas ^^^^^

;; region ----- Schema Registry -----

(def ^:private entity-collections #{:crew :models :providers})

(defn- normalize-template-path [path-str]
  (let [segments (path/parse path-str)]
    (when (seq segments)
      (path/unparse
        (map (fn [segment]
               (if (and (= :key (first segment)) (= :value (second segment)))
                 [:key :value]
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
                          [:key :value]
                          segment))
                     segments)))))

(defn- parent-path-and-key-suffix [path-str]
  (let [suffix ".key"]
    (when (and path-str (str/ends-with? path-str suffix) (> (count path-str) (count suffix)))
      (subs path-str 0 (- (count path-str) (count suffix))))))

(defn schema-for-path [path-str]
  (cond
    (or (nil? path-str) (str/blank? path-str))
    root

    :else
    (try
      (or (path/schema-at root path-str)
          (when-let [normalized (normalize-template-path path-str)]
            (path/schema-at root normalized))
          (when-let [parent-path (parent-path-and-key-suffix path-str)]
            (:key-spec (schema-for-path parent-path))))
      (catch Exception _ nil))))

(defn schema-for-data-path [path-str]
  (try
    (or (schema-for-path path-str)
        (when-let [normalized (normalize-data-path path-str)]
          (path/schema-at root normalized)))
    (catch Exception _ nil)))

;; endregion ^^^^^ Schema Registry ^^^^^
