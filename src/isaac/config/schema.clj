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

(defn- addressed-band? [band]
  (some (comp seq band) [:crew :crew-tags :session :session-tags]))

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
                 :context-mode {:type        :keyword
                                :validate    #(or (nil? %) (contains? #{:full :reset} %))
                                :message     "must be one of :full, :reset"
                                :description "Default transcript replay mode for new turns"}
                 :compaction {:type        :map
                              :schema      compaction-schema/config-schema
                              :description "Default compaction policy for sessions"}
                 :history-retention {:type        :keyword
                                     :validate    #(or (nil? %) (contains? #{:prune :retain} %))
                                     :message     "must be one of :prune, :retain"
                                     :description "Default transcript history retention policy for new sessions"}
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
            :context-mode {:type        :keyword
                           :validate    #(or (nil? %) (contains? #{:full :reset} %))
                           :message     "must be one of :full, :reset"
                           :description "How much transcript history to replay each turn"}
            :history-retention {:type        :keyword
                                :validate    #(or (nil? %) (contains? #{:prune :retain} %))
                                :message     "must be one of :prune, :retain"
                                :description "Transcript history retention policy for new sessions"}
             :effort     {:type        :int
                          :description "Effort level override for this crew member (0-10)"}
             :max-in-flight {:type        :int
                             :validate    #(or (nil? %) (pos-int? %))
                             :message     "must be a positive integer"
                             :description "Maximum concurrent in-flight turns for this crew member"}
             :cwd        {:type        :string
                          :validate    #(or (nil? %) (and (string? %) (str/starts-with? % "/")))
                          :message     "must be an absolute path"
                          :description "Default workdir for new sessions on this crew"}
            :tools      tools
            :compaction {:type :map :schema compaction-schema/config-schema}
            :tags       {:type        :ignore
                         :set-type?   true
                         :validate    #(or (nil? %) (and (set? %) (every? keyword? %)))
                         :message     "must be a set of keywords"
                         :description "Flat set of keyword tags for discovery and routing"}}})

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
            :compaction          {:type        :map
                                  :schema      compaction-schema/config-schema
                                  :description "Compaction policy override for this model"}
             :history-retention   {:type        :keyword
                                   :validate    #(or (nil? %) (contains? #{:prune :retain} %))
                                   :message     "must be one of :prune, :retain"
                                  :description "Transcript history retention policy for sessions created against this model"}
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
                                          :validations [[:present-when? :auth "api-key"]]
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
             :compaction                 {:type        :map
                                          :schema      compaction-schema/config-schema
                                          :description "Compaction policy override for this provider"}
             :stream-supports-tool-calls {:type        :boolean
                                          :description "Whether streaming mode supports tool calls"}
            :supports-system-role       {:type        :boolean
                                         :description "Whether the provider accepts a system role message"}
            :token                      {:type        :string
                                         :description "Authentication token (alias for api-key)"}
            :history-retention          {:type        :keyword
                                         :validate    #(or (nil? %) (contains? #{:prune :retain} %))
                                         :message     "must be one of :prune, :retain"
                                         :description "Transcript history retention policy for sessions created against this provider"}}})

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
   :schema      {:auth       {:type        :map
                              :description "Server-wide inbound auth configuration"
                              :schema      {:token {:type :string :description "Bearer token for inbound HTTP requests"}}}
                 :host       {:type :string :description "Bind host"}
                 :port       {:type :int :description "Bind port"}
                 :hot-reload {:type :boolean :description "Enable config hot-reload watcher"}}})

(def comm-instance
  {:name   :comm
   :type   :map
   :schema {:type {:type        :string
                   :coerce      [->id]
                   :description "Manifest comm kind to instantiate"
                   :validations [:comm-exists?]
                   :options-from :comms}
            :crew {:type        :string
                   :coerce      [->id]
                   :description "Crew id this comm routes into"
                   :validations [:crew-exists?]}}})

(def channels
  {:name        :channels
   :type        :map
   :description "Legacy communication channel configuration"
   :schema      {}})

(def sessions
  {:name        :sessions
   :type        :map
   :description "Session storage configuration"
   :schema      {:store           {:type        :keyword
                                   :description "Session store implementation: :memory, :jsonl-edn-sidecar (default), :jsonl-edn-index"}
                 :naming-strategy {:type        :ignore
                                   :validate    #(or (keyword? %) (string? %))
                                    :message     "must be a keyword or string"
                                    :description "Session naming strategy"}}})

(def hail-band
  {:name        :hail-band
   :type        :map
   :description "Hail band declaration"
   :schema      {:crew         {:type        :seq
                                :spec        {:type :string}
                                :description "Explicit crew ids eligible for the band"}
                 :crew-tags    {:type        :seq
                                :spec        {:type :keyword}
                                :description "Tags crews must carry"}
                 :session      {:type        :seq
                                :spec        {:type :string}
                                :description "Explicit session ids eligible for the band"}
                 :session-tags {:type        :seq
                                :spec        {:type :keyword}
                                :description "Tags sessions must carry"}
                 :reach        {:type        :keyword
                                :validate    #(or (nil? %) (contains? #{:one :all} %))
                                :message     "must be one of :one, :all"
                                :description "How many listeners receive the hail"}
                 :prompt       {:type        :string
                                :description "Optional companion markdown prompt for the band"}
                 :*            {:addressing {:validate addressed-band?
                                             :message  "must include at least one of :crew, :crew-tags, :session, :session-tags"}}}})

(def hail
  {:name        :hail
   :type        :map
   :description "Hail band declarations"
   :snapshot-only? true
   :key-spec    {:type :string}
   :value-spec  hail-band})

(def slash-command
  {:name        :slash-command
   :type        :map
   :description "Slash command configuration"
   :schema      {:command-name {:type        :string
                                :description "Alternative name for the command"}}})

(def slash-commands
  {:name        :slash-commands
  :type        :map
   :snapshot-only? true
   :description "Slash command configuration"
   :key-spec    {:type :string}
   :value-spec  slash-command})

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
   :description "Retired webhook auth configuration"
   :schema      {:token {:type     :string
                         :validate (constantly false)
                         :message  "retired; use :server :auth :token"}}})

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
                 :port {:type :int :description "Bind port"}}})

(def root
  {:name        :isaac
   :type        :map
   :description "Isaac's root level schema"
   :schema      {:acp                 acp
                 :channels            channels
                 :comms               {:description "Communication channel configurations (map of name -> comm config)"
                                       :type        :map
                                       :name        "comms table"
                                       :key-spec    {:type :string}
                                       :value-spec  comm-instance}
                 :crew                {:description "Crew member configurations (map of id -> crew config)"
                                        :type        :map
                                        :name        "crew table"
                                        :snapshot-only? true
                                        :key-spec    {:type :string}
                                        :value-spec  crew}
                 :defaults            defaults
                 :dev                 {:type        :boolean
                                       :default     false
                                       :description "Development mode flag"}
                 :gateway             gateway
                 :hail                hail
                 :hooks               hooks
                 :modules             {:type        :map
                                       :key-spec    {:type :keyword}
                                       :value-spec  {:type :map}
                                       :message     "must be a map of id to coordinate (legacy vector shape)"
                                       :description "Declared modules as a map of module id to tools.deps coordinate"}
                 :models              {:description "Model configurations (map of id -> model config)"
                                        :type        :map
                                        :name        "model table"
                                        :snapshot-only? true
                                        :key-spec    {:type :string}
                                        :value-spec  model}
                 :prompt-dir-names    {:type        :map
                                       :key-spec    {:type :string}
                                       :value-spec  {:type :string}
                                       :description "Directory-name to prompt-type mapping for prepared prompts"}
                 :prompt-paths        {:type        :seq
                                       :spec        {:type :string}
                                       :description "Extra roots to scan for prepared prompts"}
                 :prefer-entity-files {:type        :boolean
                                       :default     false
                                       :description "Prefer crew/*.edn, models/*.edn, and providers/*.edn for new entities"}
                 :providers           {:description "Provider configurations (map of id -> provider config)"
                                        :type        :map
                                        :name        "provider table"
                                        :snapshot-only? true
                                        :key-spec    {:type :string}
                                        :value-spec  provider}
                 :command-paths       {:type        :seq
                                       :spec        {:type :string}
                                       :description "Extra typed roots to scan for prepared commands"}
                 :cron                {:description "Cron job configurations (map of job name -> cron job config)"
                                       :type        :map
                                       :name        "cron table"
                                       :key-spec    {:type :string}
                                       :value-spec  cron-job}
                 :skill-paths         {:type        :seq
                                       :spec        {:type :string}
                                       :description "Extra typed roots to scan for prepared skills"}
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

(defn clear-schemas!
  "No-op retained for tests that reset ambient config schema state."
  []
  nil)

(def ^:private entity-collections #{:crew :hail :models :providers})

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
