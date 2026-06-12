(ns isaac.config.schema
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.path :as path]
    [clojure.string :as str]
    [isaac.config.schema-base :as schema-base]
    [isaac.session.compaction-schema :as compaction-schema]))

(def ->id schema-base/->id)
(def schema-fields schema-base/schema-fields)
(def strip-validation-annotations schema-base/strip-validation-annotations)

;; region ----- Entity Schemas -----

(def defaults
  {:name        :defaults
   :type        :map
   :description "Default crew and model selections"
   :schema      {:crew   {:type        :id
                          :default     "main"
                          :description "Default crew member id"
                          :validations [:crew-exists?]}
                 :model  {:type        :id
                          :default     "llama"
                          :description "Default model alias"
                          :validations [:model-exists?]}
                 :context-mode {:type        :keyword
                                :validations [[:one-of? :full :reset]]
                                :description "Default transcript replay mode for new turns"}
                 :compaction {:type        :map
                              :schema      compaction-schema/config-schema
                              :description "Default compaction policy for sessions"}
                 :history-retention {:type        :keyword
                                     :validations [[:one-of? :prune :retain]]
                                     :description "Default transcript history retention policy for new sessions"}
                  :effort {:type        :int
                           :description "Default effort level (0-10) when not overridden by provider/model/crew/session"}}})

(def tools
  {:name        :tools
   :type        :map
   :description "Tool configuration"
   :schema      {:allow       {:type        :seq
                               :spec        {:type        :keyword
                                             :validations [[:registered-in? :isaac.server/tools]]}
                               :description "Allowed tool names"}
                 :directories {:type        :seq
                               :spec        {:type     :ignore
                                             :validations [:cwd-or-path?]}
                               :description "Allowed directories; :cwd expands to session cwd, strings are absolute paths"}}})

(def crew
  {:name   :crew
   :type   :map
   :schema {:id         {:type        :id
                         :description "Crew member id; must match filename when present"}
            :model      {:type        :id
                         :description "ID of the model this crew member uses."
                         :validations [:model-exists?]}
            :provider   {:type        :id
                         :description "Provider id for direct provider/model crews"
                         :validations [[:registered-in? :isaac.server/provider [:providers]]]}
            :soul       {:type        :string
                         :description "The personality of this crew member. Alternatively saved at config/crew/<id>.md"}
            :context-mode {:type        :keyword
                           :validations [[:one-of? :full :reset]]
                           :description "How much transcript history to replay each turn"}
            :history-retention {:type        :keyword
                                :validations [[:one-of? :prune :retain]]
                                :description "Transcript history retention policy for new sessions"}
             :effort     {:type        :int
                          :description "Effort level override for this crew member (0-10)"}
             :max-in-flight {:type        :int
                             :validations [:positive?]
                             :description "Maximum concurrent in-flight turns for this crew member"}
             :cwd        {:type        :string
                          :validations [:absolute-path?]
                          :description "Default workdir for new sessions on this crew"}
            :tools      tools
            :compaction {:type :map :schema compaction-schema/config-schema}
            :tags       {:type        :ignore
                         :set-type?   true
                         :validations [:keyword-set?]
                         :description "Flat set of keyword tags for discovery and routing"}}})

(def model
  {:name   :model
   :type   :map
   :schema {:id                  {:type        :id
                                  :description "Model alias; must match filename when present"}
            :model               {:type        :string
                                  :description "Provider-specific model name or id"
                                  :required?   true
                                  :validations [:present?]}
            :provider            {:type        :id
                                  :description "Provider alias"
                                  :required?   true
                                  :validations [:present? [:registered-in? :isaac.server/provider [:providers]]]}
             :context-window      {:type        :int
                                   :description "Context window size in tokens"}
            :compaction          {:type        :map
                                  :schema      compaction-schema/config-schema
                                  :description "Compaction policy override for this model"}
             :history-retention   {:type        :keyword
                                   :validations [[:one-of? :prune :retain]]
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
   :schema {:api                        {:type        :id
                                         :description "Provider API adapter (e.g. \"anthropic\", \"ollama\")"
                                         :validations [[:registered-in? :isaac.server/llm-api]]}
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
            :type                       {:type        :id
                                         :description "Manifest provider id to inherit template from"
                                         :validations [[:registered-in? :isaac.server/provider-template]]}
            :headers                    {:type        :map
                                         :key-spec    {:type :string}
                                         :value-spec  {:type :string}
                                         :description "Extra HTTP headers to include in requests"}
            :id                         {:type        :id
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
                                         :validations [[:one-of? :prune :retain]]
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
   :schema {:type {:type        :id
                   :description "Manifest comm kind to instantiate"
                   :validations [[:registered-in? :isaac.server/comm [:comms]]]
                   :options-from :comms}
            :crew {:type        :id
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
                                   :validations [:keyword-or-string?]
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
                                :validations [[:one-of? :one :all]]
                                :description "How many listeners receive the hail"}
                 :prompt       {:type        :string
                                :description "Optional companion markdown prompt for the band"}
                 :addressing   {:type        :ignore
                                :validations [[:requires-any? :crew :crew-tags :session :session-tags]]
                                :description "Derived: bands must address crews or sessions"}}})

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
                 :crew   {:type        :id
                          :description "Crew id to run the job as"
                          :validations [:crew-exists?]}
                 :prompt {:type        :string
                          :description "Prompt sent when the cron job fires"}}})

(def hook-auth
  {:name        :hook-auth
   :type        :map
   :description "Retired webhook auth configuration"
   :schema      {:token {:type     :string
                         :validations [[:retired? "use :server :auth :token"]]}}})

(def hook
  {:name        :hook
   :type        :map
   :description "Webhook receiver configuration"
   :schema      {:crew        {:type        :id
                               :description "Crew id to run the hook as"
                               :validations [:crew-exists?]}
                 :id          {:type        :id
                               :description "Hook id; must match filename when present"}
                 :model       {:type        :id
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

(def field-comms
  {:description "Communication channel configurations (map of name -> comm config)"
   :type        :map
   :name        "comms table"
   :key-spec    {:type :string}
   :value-spec  comm-instance})

(def field-crew
  {:description "Crew member configurations (map of id -> crew config)"
   :type        :map
   :name        "crew table"
   :snapshot-only? true
   :key-spec    {:type :string}
   :value-spec  crew})

(def field-models
  {:description "Model configurations (map of id -> model config)"
   :type        :map
   :name        "model table"
   :snapshot-only? true
   :key-spec    {:type :string}
   :value-spec  model})

(def field-prompt-dir-names
  {:type        :map
   :key-spec    {:type :string}
   :value-spec  {:type :string}
   :description "Directory-name to prompt-type mapping for prepared prompts"})

(def field-prompt-paths
  {:type        :seq
   :spec        {:type :string}
   :description "Extra roots to scan for prepared prompts"})

(def field-prefer-entity-files
  {:type        :boolean
   :default     false
   :description "Prefer crew/*.edn, models/*.edn, and providers/*.edn for new entities"})

(def field-providers
  {:description "Provider configurations (map of id -> provider config)"
   :type        :map
   :name        "provider table"
   :snapshot-only? true
   :key-spec    {:type :string}
   :value-spec  provider})

(def field-command-paths
  {:type        :seq
   :spec        {:type :string}
   :description "Extra typed roots to scan for prepared commands"})

(def field-cron
  {:description "Cron job configurations (map of job name -> cron job config)"
   :type        :map
   :name        "cron table"
   :key-spec    {:type :string}
   :value-spec  cron-job})

(def field-skill-paths
  {:type        :seq
   :spec        {:type :string}
   :description "Extra typed roots to scan for prepared skills"})

(def field-skill-menu-threshold
  {:type        :int
   :validations [:non-negative?]
   :description "Max skill count to inject into the cached prompt before falling back to list_skills"})

(def field-tools
  {:description "Tool configurations (map of tool name -> config)"
   :type        :map
   :key-spec    {:type :keyword}
   :value-spec  {:type :map}})

(def field-tz
  {:type        :string
   :description "IANA timezone name for cron evaluation"})

(def root
  (assoc schema-base/base-root :schema
         (merge (schema-fields schema-base/base-root)
                {:acp                 acp
                 :channels            channels
                 :comms               field-comms
                 :crew                field-crew
                 :defaults            defaults
                 :gateway             gateway
                 :hail                hail
                 :hooks               hooks
                 :models              field-models
                 :prompt-dir-names    field-prompt-dir-names
                 :prompt-paths        field-prompt-paths
                 :prefer-entity-files field-prefer-entity-files
                 :providers           field-providers
                 :command-paths       field-command-paths
                 :cron                field-cron
                 :skill-paths         field-skill-paths
                 :skill-menu-threshold field-skill-menu-threshold
                 :slash-commands      slash-commands
                 :sessions            sessions
                 :server              server
                 :tools               field-tools
                 :tz                  field-tz})))

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
