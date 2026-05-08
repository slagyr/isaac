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
            :provider       {:type        :string
                             :coerce      [->id]
                             :description "Provider id for direct provider/model crews"}
            :soul             {:type        :string
                                :description "The personality of this crew member. Alternatively saved at config/crew/<id>.md"}
            :reasoning-effort {:type        :string
                                :coerce      [->id]
                                :description "Reasoning effort level override for this crew member (none|low|medium|high)"}
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
                              :description "Context window size in tokens"}
             :reasoning-effort {:type        :string
                                :coerce      [->id]
                                :description "Reasoning effort level (none|low|medium|high)"}}})

(def provider
  {:name   :provider
   :type   :map
   :schema {:api                        {:type        :string
                                          :coerce      [->id]
                                          :description "Provider API adapter (e.g. \"anthropic\", \"ollama\")"}
             :auth                       {:type        :string
                                          :description "Authentication mode (e.g. \"oauth-device\")"}
             :api-key                    {:type        :string
                                          :description "API key"}
             :auth-key                   {:type        :string
                                          :description "Authentication key"}
             :assistant-base-url         {:type        :string
                                          :description "Base URL for assistant endpoints"}
             :base-url                   {:type        :string
                                          :description "API base URL"}
             :from                       {:type        :string
                                          :coerce      [->id]
                                          :description "Provider id to inherit defaults from"}
             :headers                    {:type        :map
                                          :key-spec    {:type :string}
                                          :value-spec  {:type :string}
                                          :description "Extra HTTP headers to include in requests"}
             :id                         {:type        :string
                                          :coerce      [->id]
                                          :description "Provider id; must match filename when present"}
             :models                     {:type        :seq
                                          :spec        {:type :string}
                                          :description "Canonical model ids served by this provider"}
             :name                       {:type        :string
                                          :description "Display name"}
             :originator                 {:type        :string
                                          :description "X-Originator header value"}
            :response-format            {:type        :string
                                         :description "Response format hint"}
            :reasoning-effort           {:type        :string
                                         :description "Reasoning effort level (none|low|medium|high)"}
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
   :schema      {:proxy-max-reconnects         {:type        :int
                                                :description "Maximum reconnect attempts for ACP proxy"}
                 :proxy-reconnect-delay-ms     {:type        :int
                                                :description "Base reconnect delay in milliseconds for ACP proxy"}
                 :proxy-reconnect-max-delay-ms {:type        :int
                                                :description "Maximum reconnect delay in milliseconds for ACP proxy"}}})

(def server
  {:name        :server
   :type        :map
   :description "HTTP server configuration"
   :schema      {:host       {:type :string :description "Bind host"}
                 :port       {:type :int :description "Bind port"}
                 :hot-reload {:type :boolean :description "Enable config hot-reload watcher"}}})

(def comms
  {:name        :comms
   :type        :map
   :description "Communication channel configuration"
   :schema      {}})

(def channels
  {:name        :channels
   :type        :map
   :description "Legacy communication channel configuration"
   :schema      {}})

(def sessions
  {:name        :sessions
   :type        :map
   :description "Session storage configuration"
   :schema      {:naming-strategy {:type        :ignore
                                    :validate    #(or (keyword? %) (string? %))
                                    :message     "must be a keyword or string"
                                    :description "Session naming strategy"}}})

(def slash-commands
  {:name        :slash-commands
   :type        :map
   :description "Slash command configuration"
   :key-spec    {:type :string}
   :value-spec  {:type :map}})

(def cron-job
  {:name        :cron-job
   :type        :map
   :description "Cron job configuration"
   :schema      {:expr   {:type        :string
                          :description "5-field cron expression"}
                 :crew   {:type        :string
                          :coerce      [->id]
                          :description "Crew id to run the job as"}
                 :prompt {:type        :string
                          :description "Prompt sent when the cron job fires"}}})

(def hook-auth
  {:name        :hook-auth
   :type        :map
   :description "Webhook auth configuration"
   :schema      {:token {:type :string :description "Bearer token required for inbound hooks"}}})

(def hook
  {:name        :hook
   :type        :map
   :description "Webhook receiver configuration"
   :schema      {:crew        {:type        :string
                               :coerce      [->id]
                               :description "Crew id to run the hook as"}
                 :id          {:type        :string
                               :coerce      [->id]
                               :description "Hook id; must match filename when present"}
                 :model       {:type        :string
                               :coerce      [->id]
                               :description "Optional model override for the hook session"}
                 :session-key {:type        :string
                               :description "Session key to use for dispatched turns"}
                 :template    {:type        :string
                               :description "Rendered message template for the hook body"}}})

(def hooks
  {:name        :hooks
   :type        :map
   :description "Webhook configuration"
   :schema      {:auth hook-auth}})

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
                 :hooks               hooks
                 :modules             {:type        :map
                                       :key-spec    {:type :keyword}
                                       :value-spec  {:type :map}
                                       :message     "must be a map of id to coordinate (legacy vector shape)"
                                       :description "Declared modules as a map of module id to tools.deps coordinate"}
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
                  :slash-commands      slash-commands
                  :sessions            sessions
                  :server              server
                  :tools               {:description "Tool configurations (map of tool name -> config)"
                                       :type        :map
                                       :key-spec    {:type :keyword}
                                       :value-spec  {:type :map}}
                 :tz                  {:type        :string
                                       :description "IANA timezone name for cron evaluation"}}})

;; endregion ^^^^^ Entity Schemas ^^^^^

;; region ----- Schema Registry -----

(defonce ^:private tool-schemas* (atom {}))
(defonce ^:private tool-provider-schemas* (atom {}))
(defonce ^:private slash-command-schemas* (atom {}))

(defn register-schema!
  "Register a config schema by kind and name.
   :tool        — name is a tool name (string/keyword); schema-fields is a c3kit field specs map.
   :tool-provider — name is {:tool t :provider p}; schema-fields is a c3kit field specs map.
   :slash-command — name is a slash command id (string/keyword); schema-fields is a c3kit field specs map."
  [kind name schema-fields]
  (case kind
    :tool          (swap! tool-schemas* assoc (->id name) schema-fields)
    :tool-provider (swap! tool-provider-schemas*
                          assoc [(->id (:tool name)) (->id (:provider name))] schema-fields)
    :slash-command (swap! slash-command-schemas* assoc (->id name) schema-fields)))

(defn tool-schema
  "Returns registered schema fields for a tool, or nil if none registered."
  [tool-name]
  (get @tool-schemas* (->id tool-name)))

(defn registered-providers
  "Returns set of provider name strings registered for a tool."
  [tool-name]
  (into #{} (keep (fn [[t p]] (when (= (->id tool-name) t) p)) (keys @tool-provider-schemas*))))

(defn provider-schema
  "Returns registered schema fields for a tool+provider, or nil if none registered."
  [tool-name provider-name]
  (get @tool-provider-schemas* [(->id tool-name) (->id provider-name)]))

(defn slash-command-schema
  "Returns registered schema fields for a slash command, or nil if none registered."
  [command-name]
  (get @slash-command-schemas* (->id command-name)))

(defn clear-schemas!
  "Clear all registered tool and provider schemas. Intended for test teardown."
  []
  (reset! tool-schemas* {})
  (reset! tool-provider-schemas* {})
  (reset! slash-command-schemas* {}))

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
