(ns isaac.config.schema
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.path :as path]
    [clojure.string :as str]
    [isaac.session.compaction-schema :as compaction-schema]))

(defn ->id [value]
  (cond
    (keyword? value) (name value)
    (string? value) value
    (nil? value) nil
    :else (str value)))

(defn schema-fields [spec]
  (:schema spec))

(defn strip-validation-annotations [node]
  (cond
    (map? node)
    (let [node (dissoc node :validations)]
      (into {} (map (fn [[k v]] [k (strip-validation-annotations v)])) node))

    (vector? node)
    (mapv strip-validation-annotations node)

    :else
    node))

;; region ----- Entity Schemas -----

(def defaults
  {:name        :defaults
   :type        :map
   :description "Default crew and model selections"
   :schema      {:crew   {:type        :string
                          :coerce      [->id]
                          :default     "main"
                          :description "Default crew member id"
                          :validations [:crew-exists?]}
                 :model  {:type        :string
                          :coerce      [->id]
                          :default     "llama"
                          :description "Default model alias"
                          :validations [:model-exists?]}
                 :effort {:type        :int
                          :description "Default effort level (0-10) when not overridden by provider/model/crew/session"}}})

(def tools
  {:name        :tools
   :type        :map
   :description "Tool configuration"
   :schema      {:allow       {:type        :seq
                               :spec        {:type        :keyword
                                             :validations [:tool-exists?]}
                               :description "Allowed tool names"}
                 :directories {:type        :seq
                               :spec        {:type     :ignore
                                             :validate #(or (= :cwd %) (string? %))
                                             :message  "must be :cwd or an absolute path string"}
                               :description "Allowed directories; :cwd expands to session cwd, strings are absolute paths"}}})

(def crew
  {:name   :crew
   :type   :map
   :schema {:id         {:type        :string
                         :coerce      [->id]
                         :description "Crew member id; must match filename when present"}
            :model      {:type        :string
                         :coerce      [->id]
                         :description "ID of the model this crew member uses."
                         :validations [:model-exists?]}
            :provider   {:type        :string
                         :coerce      [->id]
                         :description "Provider id for direct provider/model crews"
                         :validations [:provider-exists?]}
            :soul       {:type        :string
                         :description "The personality of this crew member. Alternatively saved at config/crew/<id>.md"}
            :effort     {:type        :int
                         :description "Effort level override for this crew member (0-10)"}
            :tools      tools
            :compaction {:type :map :schema compaction-schema/config-schema}}})

(def model
  {:name   :model
   :type   :map
   :schema {:id                  {:type        :string
                                  :coerce      [->id]
                                  :description "Model alias; must match filename when present"}
            :model               {:type        :string
                                  :description "Provider-specific model name or id"
                                  :required?   true
                                  :validate    schema/present?
                                  :message     "must be present"}
            :provider            {:type        :string
                                  :coerce      [->id]
                                  :description "Provider alias"
                                  :required?   true
                                  :validate    schema/present?
                                  :message     "must be present"
                                  :validations [:provider-exists?]}
            :context-window      {:type        :int
                                  :description "Context window size in tokens"}
            :effort              {:type        :int
                                  :description "Effort level override for this model (0-10)"}
            :allows-effort       {:type        :boolean
                                  :description "Whether to attach :effort to requests for this model (default true)"}
            :thinking-budget-max {:type        :int
                                  :description "Anthropic: max thinking budget tokens at effort=10; scales linearly. Default 32000."}
            :think-mode          {:type        :keyword
                                  :description "Ollama: how :effort is translated. :bool (default) → think true/false; :levels → \"low\"|\"medium\"|\"high\"."}}})

(def provider
  {:name   :provider
   :type   :map
   :schema {:api                        {:type        :string
                                         :coerce      [->id]
                                         :description "Provider API adapter (e.g. \"anthropic\", \"ollama\")"
                                         :validations [:llm-api-exists?]}
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
            :type                       {:type        :string
                                         :coerce      [->id]
                                         :description "Manifest provider id to inherit template from"
                                         :validations [:manifest-provider-exists?]}
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
            :effort                     {:type        :int
                                         :description "Effort level for this provider (0-10)"}
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
   :schema      {}
   :key-spec    {:type :string}
   :value-spec  {:type   :map
                 :schema {:type {:type        :string
                                 :coerce      [->id]
                                 :description "Manifest comm kind to instantiate"
                                 :validations [:comm-exists?]}
                          :impl {:type        :string
                                 :coerce      [->id]
                                 :description "Manifest comm kind to instantiate (v1 alias for :type)"
                                 :validations [:comm-exists?]}
                          :crew {:type        :string
                                 :coerce      [->id]
                                 :description "Crew id this comm routes into"
                                 :validations [:crew-exists?]}}}})

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

(def slash-command
  {:name        :slash-command
   :type        :map
   :description "Slash command configuration"
   :schema      {:command-name {:type        :string
                                :description "Alternative name for the command"}}})

(def slash-commands
  {:name        :slash-commands
   :type        :map
   :description "Slash command configuration"
   :key-spec    {:type :string}
   :value-spec  {:type slash-command}})

(def cron-job
  {:name        :cron-job
   :type        :map
   :description "Cron job configuration"
   :schema      {:expr   {:type        :string
                          :description "5-field cron expression"}
                 :crew   {:type        :string
                          :coerce      [->id]
                          :description "Crew id to run the job as"
                          :validations [:crew-exists?]}
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
                               :description "Crew id to run the hook as"
                               :validations [:crew-exists?]}
                 :id          {:type        :string
                               :coerce      [->id]
                               :description "Hook id; must match filename when present"}
                 :model       {:type        :string
                               :coerce      [->id]
                               :description "Optional model override for the hook session"
                               :validations [:model-exists?]}
                 :session-key {:type        :string
                               :description "Session key to use for dispatched turns"}
                 :template    {:type        :string
                               :description "Rendered message template for the hook body"}}})

(def hooks
  {:name        :hooks
   :type        :map
   :description "Webhook configuration"
   :schema      {:auth hook-auth}
   :key-spec    {:type :string}
   :value-spec  hook})

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
                 :session-store       {:type        :map
                                       :description "Session store configuration"
                                       :schema      {:impl {:type        :keyword
                                                            :description "Implementation: :memory, :jsonl-edn-sidecar (default), or :jsonl-edn-index"}}}
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

(defn clear-schemas!
  "No-op retained for tests that reset ambient config schema state."
  []
  nil)

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
