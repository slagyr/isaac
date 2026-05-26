;; mutation-tested: 2026-05-06
(ns isaac.config.loader
  (:require
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.env :as c3env]
    [c3kit.apron.schema :as cs]
    [clj-yaml.core :as yaml]
    [clojure.edn :as edn]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.config.companion :as companion]
    [isaac.config.paths :as paths]
    [isaac.config.schema :as schema]
    [isaac.fs :as fs]
    [isaac.llm.provider :as llm-provider]
    [isaac.llm.providers :as llm-providers]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.nexus :as nexus]))

;; region ----- Helpers -----

(def env-overrides* (atom {}))
;; Snapshot of the <state-dir>/.env file, locked at load time (see
;; lock-dotenv!). Avoids re-reading the file on every ${VAR} lookup and removes
;; the need to thread/bind state-dir through the substitution pipeline.
(defonce ^:private dotenv* (atom {}))

(defn- runtime-fs
  ([]      (or (fs/instance) (throw (ex-info "config.loader requires :fs in system" {}))))
  ([opts]  (or (fs/instance opts) (throw (ex-info "config.loader requires :fs in system" {})))))

(defn- exists?* [path]
  (fs/exists? (runtime-fs) path))

(defn- slurp* [path]
  (fs/slurp (runtime-fs) path))

(defn- children* [path]
  (fs/children (runtime-fs) path))

(defn- read-dotenv [state-dir]
  (let [path (when state-dir (str state-dir "/.env"))]
    (if (and path (exists?* path))
      (let [props (doto (java.util.Properties.)
                    (.load (java.io.StringReader. (or (slurp* path) ""))))]
        (into {} (map (fn [k] [k (.getProperty props k)])) (.stringPropertyNames props)))
      {})))

(defn- lock-dotenv!
  "Snapshots <state-dir>/.env into dotenv*. Called once per load so ${VAR}
   substitution reads a locked map rather than re-reading the file."
  [state-dir]
  (reset! dotenv* (read-dotenv state-dir)))

(defn clear-env-overrides! []
  (reset! env-overrides* {})
  (reset! dotenv* {}))

(defn set-env-override! [name value]
  (swap! env-overrides* assoc name value))

(defn env [name]
  (or (get @env-overrides* name)
      (c3env/env name)
      (get @dotenv* name)))

(def ^:private ->id schema/->id)

(defn- runtime-schema [spec]
  (schema/strip-validation-annotations spec))

(defn- source-path [relative]
  (str "config/" relative))

(defn- missing-config-message [state-dir]
  (str "no config found; run `isaac init` or create " state-dir "/config/isaac.edn"))

(defn- warning [key value]
  {:key key :value value})

(defn- has-ext? [path ext]
  (str/ends-with? path ext))

(declare overlay-relative)

(defn- split-frontmatter [content]
  (when-let [[_ frontmatter body] (re-matches #"(?s)\A---\r?\n(.*?)\r?\n---\r?\n?(.*)\z" content)]
    {:frontmatter frontmatter
     :body        (str/replace body #"^\r?\n" "")}))

(defn- substitute-env [s]
  (str/replace s #"\$\{([^}]+)\}" (fn [[match var-name]] (or (env var-name) match))))

(defn- substitute-env-recursive [value]
  (cond
    (string? value) (substitute-env value)
    (map? value) (into {} (map (fn [[k v]] [k (substitute-env-recursive v)]) value))
    (sequential? value) (mapv substitute-env-recursive value)
    :else value))

(defn- read-edn-string [content substitute-env?]
  (-> content
      edn/read-string
      ((fn [value]
         (if substitute-env?
           (substitute-env-recursive value)
           value)))))

(defn- read-yaml-string [content substitute-env?]
  (-> (yaml/parse-string content :keywords true)
      ((fn [value]
         (if substitute-env?
           (substitute-env-recursive value)
           value)))))

(defn- read-edn-file [path substitute-env? raw-parse-errors?]
  (try
    {:data (read-edn-string (slurp* path) substitute-env?)}
    (catch Exception e
      {:error (if raw-parse-errors?
                (.getMessage e)
                "EDN syntax error")})))

(def ^:private present? companion/present?)

(defn- assoc-error [result key value]
  (update result :errors conj {:key key :value value}))

(defn- normalize-defaults [defaults]
  (let [result (cs/conform (runtime-schema schema/defaults) defaults)]
    (if (cs/error? result) {} result)))

(defn- normalize-crew [crew]
  (let [result (cs/conform (runtime-schema schema/crew) crew)]
    (if (cs/error? result) {} result)))

(defn- normalize-model [model]
  (let [result (cs/conform (runtime-schema schema/model) model)]
    (if (cs/error? result) {} result)))

(defn- collect-unknown-key-warnings [warnings kind id entity entity-schema]
  (let [entity-fields (schema/schema-fields entity-schema)]
    (reduce (fn [acc key]
              (if (contains? entity-fields key)
                acc
                (conj acc (warning (str kind "." id "." (name key)) "unknown key"))))
            warnings
            (keys entity))))

(defn- read-dir-files [root dir-name ext]
  (let [dir (str root "/" dir-name)]
    (->> (or (children* dir) [])
         (filter #(has-ext? % ext))
         sort
         (mapv (fn [name]
                 {:id       (subs name 0 (- (count name) (count ext)))
                  :path     (str dir "/" name)
                  :relative (str dir-name "/" name)})))))

(defn- read-entity-files [root dir-name] (read-dir-files root dir-name ".edn"))
(defn- read-md-files [root dir-name] (read-dir-files root dir-name ".md"))

(defn- overlay-entry [dir-name ext {:keys [overlay-content] :as opts}]
  (when-let [relative (overlay-relative opts)]
    (when (and (str/starts-with? relative (str dir-name "/"))
               (has-ext? relative ext))
      (let [name (last (str/split relative #"/"))]
        {:id       (subs name 0 (- (count name) (count ext)))
         :relative relative
         :content  overlay-content
         :overlay? true}))))

(defn- with-overlay [files overlay]
  (if overlay
    (conj (vec (remove #(= (:relative overlay) (:relative %)) files)) overlay)
    files))

(defn- entry-content [{:keys [content overlay? path]}]
  (if overlay?
    content
    (slurp* path)))

(defn- frontmatter-md-entry? [entry]
  (boolean (split-frontmatter (entry-content entry))))


(defn- overlay-relative [{:keys [overlay-path]}]
  (when (present? overlay-path)
    overlay-path))

(defn- overlay-for [opts relative]
  (when (= relative (overlay-relative opts))
    {:path     (str "<overlay>/" relative)
     :relative relative
     :content  (:overlay-content opts)
     :overlay? true}))

(defn- config-files-present? [root opts]
  (or (overlay-relative opts)
      (exists?* (str root "/" paths/root-filename))
      (seq (read-entity-files root "crew"))
      (seq (read-entity-files root "cron"))
      (seq (read-entity-files root "hail"))
      (seq (read-entity-files root "hooks"))
      (seq (read-entity-files root "models"))
      (seq (read-entity-files root "providers"))
      (seq (read-md-files root "crew"))
      (seq (read-md-files root "hail"))
      (seq (read-md-files root "hooks"))
      (seq (read-md-files root "models"))
      (seq (read-md-files root "providers"))
      (seq (read-md-files root "cron"))))

(defn- schema-for [kind]
  (case kind
    :cron schema/cron-job
    :crew schema/crew
    :defaults schema/defaults
    :hail schema/hail-band
    :hooks schema/hook
    :models schema/model
    :providers schema/provider))

(defn- schema-error-entries [prefix result]
  (letfn [(segment-name [segment]
            (cond
              (keyword? segment) (name segment)
              (string? segment)  (str/replace-first segment #"^:" "")
              :else              (str segment)))
          (join-path [path segment]
            (if path (str path "." segment) segment))
          (entries [path value]
            (if (map? value)
              (mapcat (fn [[field message]]
                        (entries (join-path path (segment-name field)) message))
                      value)
              [{:key path :value value}]))]
    (vec (mapcat (fn [[field message]]
                   (entries (join-path prefix (segment-name field)) message))
                 (cs/message-map result)))))

(defn- load-companion-text [path]
  (when path
    {:exists? (exists?* path)
     :text    (when (exists?* path)
                (slurp* path))}))

(defn- resolve-crew-soul [id data load-fn]
  (let [result (companion/resolve-text {:inline  (:soul data)
                                        :load-fn load-fn})]
    {:data  (cond-> data
                    (:value result) (assoc :soul (:value result)))
     :error (when (and (:inline? result) (:companion-exists? result))
              {:key   (str "crew." id ".soul")
               :value "must be set in .edn OR .md"})}))

(defn- resolve-companion-field [ns-prefix field-key id entity load-fn relative]
  (let [result (companion/resolve-text {:inline  (get entity field-key)
                                        :load-fn load-fn})
        errors (cond-> []
                       (and (not (:inline? result)) (not (:companion-exists? result)))
                       (conj {:key   (str ns-prefix id "." (name field-key))
                              :value (str "required (inline or " relative ")")})
                       (and (not (:inline? result)) (:companion-empty? result))
                       (conj {:key   (str ns-prefix id "." (name field-key))
                              :value "must not be empty"}))]
    (when (and (:inline? result) (:companion-exists? result))
      (log/warn :config/companion-inline-wins :field field-key :key (str ns-prefix id) :path relative))
    [(cond-> entity (:value result) (assoc field-key (:value result))) errors]))

(defn- resolve-cron-prompt [id job load-fn relative]
  (let [[resolved errors] (resolve-companion-field "cron." :prompt id job load-fn relative)]
    {:job resolved :errors errors}))

(defn- resolve-cron-prompts [root data]
  (reduce-kv (fn [{:keys [cron errors]} id job]
               (let [id       (->id id)
                     relative (paths/cron-relative id)
                     path     (str root "/" relative)
                     resolved (resolve-cron-prompt id job #(load-companion-text path) relative)]
                 {:cron   (assoc cron id (:job resolved))
                  :errors (into errors (:errors resolved))}))
             {:cron {} :errors []}
             (or (:cron data) {})))

(defn- resolve-hook-template [id hook load-fn relative]
  (let [[resolved errors] (resolve-companion-field "hooks." :template id hook load-fn relative)]
    {:hook resolved :errors errors}))

(defn- resolve-hail-prompt [id band load-fn]
  (let [result (companion/resolve-text {:inline  (:prompt band)
                                        :load-fn load-fn})]
    {:band  (cond-> band
              (:value result) (assoc :prompt (:value result)))
     :errors []}))

(defn- top-level-warnings [data]
  (reduce (fn [acc key]
            (if (contains? (schema/schema-fields schema/root) key)
              acc
              (conj acc (warning (name key) "unknown key"))))
          []
          (keys data)))

(defn- root-entity-warnings [raw-data]
  (reduce (fn [warnings [kind entity-schema]]
            (reduce-kv (fn [acc id entity]
                         (if (map? entity)
                           (collect-unknown-key-warnings acc (name kind) (->id id) entity entity-schema)
                           acc))
                       warnings
                       (get raw-data kind {})))
          []
           [[:crew (schema-for :crew)]
            [:hail (schema-for :hail)]
            [:models (schema-for :models)]
            [:providers (schema-for :providers)]]))

(defn- load-root-config [root {:keys [raw-parse-errors? substitute-env?] :as opts}]
  (let [overlay (overlay-for opts paths/root-filename)
        path    (str root "/" paths/root-filename)]
    (cond
      overlay
      (let [{:keys [content relative]} overlay]
        (try
          (let [raw-data        (read-edn-string content substitute-env?)
                {:keys [cron errors]} (resolve-cron-prompts root raw-data)
                data            (cond-> raw-data
                                        (:cron raw-data) (assoc :cron cron))
                root-result     (cs/conform (runtime-schema schema/root) data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform (runtime-schema schema/defaults) defaults))]
             {:data     data
              :errors   (vec (concat errors
                                     (when (cs/error? root-result) (schema-error-entries nil root-result))
                                     (when (and defaults-result (cs/error? defaults-result))
                                       (schema-error-entries "defaults" defaults-result))))
              :warnings (concat (top-level-warnings raw-data)
                                (root-entity-warnings raw-data))
              :sources  [(source-path relative)]})
          (catch Exception _
            {:data nil :errors [{:key paths/root-filename :value "EDN syntax error"}] :warnings [] :sources []})))

      (exists?* path)
      (let [{raw-data :data error :error} (read-edn-file path substitute-env? raw-parse-errors?)]
        (if error
          {:data nil :errors [{:key paths/root-filename :value error}] :warnings [] :sources []}
          (let [{:keys [cron errors]} (resolve-cron-prompts root raw-data)
                data            (cond-> raw-data
                                        (:cron raw-data) (assoc :cron cron))
                root-result     (cs/conform (runtime-schema schema/root) data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform (runtime-schema schema/defaults) defaults))]
            {:data     data
              :errors   (vec (concat errors
                                     (when (cs/error? root-result) (schema-error-entries nil root-result))
                                     (when (and defaults-result (cs/error? defaults-result))
                                       (schema-error-entries "defaults" defaults-result))))
              :warnings (concat (top-level-warnings raw-data)
                                (root-entity-warnings raw-data))
              :sources  [(source-path paths/root-filename)]})))

      :else
      {:data nil :errors [] :warnings [] :sources []})))

(defn- merge-root-entity [result kind]
  (reduce (fn [acc [id entity]]
            (let [id       (->id id)
                  warnings (collect-unknown-key-warnings [] (name kind) id entity (schema-for kind))
                  entity   (cs/conform (runtime-schema (schema-for kind)) entity)
                  explicit (:id entity)]
              (-> acc
                  (update :warnings into warnings)
                  (cond-> (cs/error? entity)
                          (update :errors into (schema-error-entries (str (name kind) "." id) entity)))
                  (cond-> (and explicit (not= explicit id))
                          (assoc-error (str (name kind) "." id ".id") (str "must match filename (got \"" explicit "\")")))
                  (cond-> (not (cs/error? entity))
                          (assoc-in [:config kind id] (dissoc entity :id))))))
          result
          (get-in result [:root kind])))

(defn- read-frontmatter-file [{:keys [relative] :as entry} substitute-env? raw-parse-errors?]
  (try
    (if-let [{:keys [body frontmatter]} (split-frontmatter (entry-content entry))]
      {:body body
       :data (read-yaml-string frontmatter substitute-env?)}
      {:error (str relative " is missing YAML frontmatter")})
    (catch Exception e
      {:error (if raw-parse-errors?
                (.getMessage e)
                "YAML syntax error")})))

(defn- entity-files [root dir-name opts]
  (let [edn-files (-> (read-entity-files root dir-name)
                      (with-overlay (overlay-entry dir-name ".edn" opts)))
        md-files  (-> (read-md-files root dir-name)
                      (with-overlay (overlay-entry dir-name ".md" opts)))]
    (if (#{"crew" "cron" "hooks"} dir-name)
      (let [md-files  (->> md-files
                           (filter frontmatter-md-entry?)
                           (mapv #(assoc % :format :md-frontmatter)))
            edn-files (mapv #(assoc % :format :edn) edn-files)
            md-by-id  (set (map :id md-files))]
        {:files    (vec (sort-by :relative (concat md-files (remove #(contains? md-by-id (:id %)) edn-files))))
         :warnings (mapv (fn [{:keys [id relative]}]
                           (warning relative (str "single-file config overrides legacy " dir-name "/" id ".edn")))
                         (filter #(contains? (set (map :id edn-files)) (:id %)) md-files))})
      {:files    (vec (sort-by :relative (map #(assoc % :format :edn) edn-files)))
       :warnings []})))

(defn- read-entity-entry [entry substitute-env? raw-parse-errors?]
  (let [{:keys [content format overlay? path]} entry]
    (case format
      :md-frontmatter
      (read-frontmatter-file entry substitute-env? raw-parse-errors?)

      (if overlay?
        (try
          {:data (read-edn-string content substitute-env?)}
          (catch Exception e
            {:error (if raw-parse-errors? (.getMessage e) "EDN syntax error")}))
        (read-edn-file path substitute-env? raw-parse-errors?)))))

(defn- resolve-entity-data [root kind id format raw-data body]
  (cond
    (not (map? raw-data))
    {:data raw-data :error nil :extra-errors []}

    (= kind :crew)
    (let [{resolved-data :data companion-error :error}
          (resolve-crew-soul id raw-data (if (= format :md-frontmatter)
                                           (fn [] {:exists? true :text body})
                                           #(load-companion-text (str root "/" (paths/soul-relative id)))))]
      {:data resolved-data :error companion-error :extra-errors []})

    (= kind :cron)
    (let [{resolved-job :job prompt-errors :errors}
          (resolve-cron-prompt id raw-data (if (= format :md-frontmatter)
                                             (fn [] {:exists? true :text body})
                                             #(load-companion-text (str root "/" (paths/cron-relative id))))
                               (paths/cron-relative id))]
      {:data resolved-job :error nil :extra-errors prompt-errors})

    (= kind :hooks)
    (let [{resolved-hook :hook template-errors :errors}
          (resolve-hook-template id raw-data (if (= format :md-frontmatter)
                                               (fn [] {:exists? true :text body})
                                               #(load-companion-text (str root "/" (paths/hook-relative id))))
                                  (paths/hook-relative id))]
      {:data resolved-hook :error nil :extra-errors template-errors})

    (= kind :hail)
    (let [{resolved-band :band prompt-errors :errors}
          (resolve-hail-prompt id raw-data #(load-companion-text (str root "/hail/" id ".md")))]
      {:data resolved-band :error nil :extra-errors prompt-errors})

    :else
    {:data raw-data :error nil :extra-errors []}))

(defn- finalize-entity-load [result kind id relative data extra-errors]
  (let [warnings    (collect-unknown-key-warnings [] (name kind) id data (schema-for kind))
        entity      (cs/conform (runtime-schema (schema-for kind)) data)
        explicit-id (:id entity)
        result      (-> result
                        (update :warnings into warnings)
                        (update :errors into extra-errors))
        result      (if (cs/error? entity)
                      (update result :errors into (schema-error-entries (str (name kind) "." id) entity))
                      result)
        result      (if (and explicit-id (not= explicit-id id))
                      (assoc-error result (str (name kind) "." id ".id") (str "must match filename (got \"" explicit-id "\")"))
                      result)
        result      (if (and (get-in (:config result) [kind id])
                             (get-in (:root result) [kind id]))
                      (assoc-error result (str (name kind) "." id) (str "defined in both isaac.edn and " relative))
                      result)]
    (if (or (some? (get-in (:root result) [kind id]))
            (cs/error? entity))
      (update result :sources conj (source-path relative))
      (-> result
          (assoc-in [:config kind id] (dissoc entity :id))
          (assoc-in [:raw kind id] (dissoc data :id))
          (update :sources conj (source-path relative))))))

(defn- load-entity-file [result root kind {:keys [format id relative] :as entry} substitute-env? raw-parse-errors?]
  (let [{raw-data :data error :error body :body} (read-entity-entry entry substitute-env? raw-parse-errors?)
        {data :data error :error extra-errors :extra-errors}
        (if error
          {:data raw-data :error error :extra-errors []}
          (resolve-entity-data root kind id format raw-data body))]
    (cond
      error
      (if (map? error)
        (update result :errors conj error)
        (assoc-error result relative error))

      (not (map? data))
      (assoc-error result relative "must contain a map")

      :else
      (finalize-entity-load result kind id relative data extra-errors))))

(defn- dangling-md-warnings [root root-data opts]
  (let [root-data       (or root-data {})
        inline-ids      (fn [kind] (->> (keys (get root-data kind {})) (map ->id) set))
        hook-inline-ids (->> (keys (get root-data :hooks {}))
                             (filter string?)
                             (map ->id)
                             set)
        file-ids        (fn [dir-name] (->> (entity-files root dir-name opts) :files (map :id) set))
        warn-for        (fn [dir-name entry-kind matching-ids]
                          (->> (read-md-files root dir-name)
                               (remove #(contains? matching-ids (:id %)))
                               (mapv #(warning (:relative %) (str "dangling: no matching " entry-kind " entry")))))]
    (vec (concat
           (warn-for "crew" "crew" (into (inline-ids :crew) (file-ids "crew")))
            (warn-for "hail" "hail" (into (inline-ids :hail) (file-ids "hail")))
            (warn-for "hooks" "hook" (into hook-inline-ids (file-ids "hooks")))
            (warn-for "models" "model" (into (inline-ids :models) (file-ids "models")))
            (warn-for "providers" "provider" (into (inline-ids :providers) (file-ids "providers")))
            (warn-for "cron" "cron" (into (inline-ids :cron) (file-ids "cron")))))))

(defn- declared-module-api-ids [config]
  (let [module-index (merge (module-loader/core-index) (:module-index config))
        modules      (:modules config)]
    (if (and (some? modules) (not (map? modules)))
      (->> [:isaac.core]
           (keep #(get module-index %))
           (mapcat #(keys (get-in % [:manifest :llm/api])))
           (map clojure.core/name)
           set)
      (let [declared-ids (conj (->> (keys modules)
                                    (map ->id)
                                    (map keyword)
                                    set)
                               :isaac.core)]
        (->> declared-ids
             (keep #(get module-index %))
             (mapcat #(keys (get-in % [:manifest :llm/api])))
             (map clojure.core/name)
             set)))))

(defn- manifest-capability-ids [config kind]
  (->> (merge (module-loader/core-index) (:module-index config))
       vals
       (mapcat #(keys (get-in % [:manifest kind])))
       (map ->id)
       set))

(defn- manifest-provider-ids [config]
  (->> (manifest-capability-ids config :provider) sort vec))

(defn- module-instantiated-provider-ids
  "Provider ids declared by third-party modules. Excludes the core manifest,
   whose :provider entries are templates and do not materialize unless
   instantiated in user config."
  [config]
  (->> (or (:module-index config) {})
       (remove (fn [[id _]] (= id :isaac.core)))
       (mapcat (fn [[_ entry]] (keys (get-in entry [:manifest :provider]))))
       (map ->id)
       set))

(defn- known-provider-ids [config]
  (->> (concat (keys (:providers config))
               (module-instantiated-provider-ids config))
       (map ->id)
       distinct
       sort
       vec))

(defn- known-crew-ids [config]
  (->> (keys (:crew config)) (map ->id) distinct sort vec))

(defn- known-model-ids [config]
  (->> (keys (:models config)) (map ->id) distinct sort vec))

(defn- known-comm-ids [config]
  (->> (concat (keys (:comms config))
               (manifest-capability-ids config :comm))
       (map ->id)
       distinct
       sort
       vec))

(defn- known-tool-ids [config]
  (->> (manifest-capability-ids config :tools)
       sort
       vec))

(defn- find-manifest-entry [config section name]
  (let [kw (keyword (->id name))]
    (some (fn [[_ entry]]
            (get-in entry [:manifest section kw]))
          (merge (module-loader/core-index) (:module-index config)))))

(defn- find-tool-manifest-entry [config tool-name]
  (find-manifest-entry config :tools tool-name))

(defn- find-slash-command-manifest-entry [config command-name]
  (find-manifest-entry config :slash-commands command-name))

(defn- known-llm-api-ids [config]
  (->> (declared-module-api-ids config)
       sort
       vec))

;; Validation refs registered in c3kit.apron.schema's ref registry.
;; Each carries :validate, :message, and :known (rich-error enrichment).
;; All read the document under validation from the *config* dynvar bound
;; at the semantic-errors entry point — apron's standard ref shape is
;; value-local; this is how we give doc-aware refs access to the full
;; config without forking apron.

(def ^:dynamic *config* nil)

(defn- exists-ref [ref-key known-fn message]
  {:validate (fn [value]
               (contains? (or (get-in *config* [:known-sets ref-key])
                              (set (known-fn (or (:raw *config*) *config*))))
                          (->id value)))
   :message  message
   :known    (fn []
               (or (get-in *config* [:known-values ref-key])
                   (known-fn (or (:raw *config*) *config*))))})

(def ^:private existence-refs
  {:llm-api-exists?           (exists-ref :llm-api-exists? known-llm-api-ids "unknown api")
   :tool-exists?              (exists-ref :tool-exists? known-tool-ids "references undefined tool")
   :provider-exists?          (exists-ref :provider-exists? known-provider-ids "references undefined provider")
   :manifest-provider-exists? (exists-ref :manifest-provider-exists? manifest-provider-ids "references provider not defined in any manifest")
   :comm-exists?              (exists-ref :comm-exists? known-comm-ids "references undefined comm")
   :model-exists?             (exists-ref :model-exists? known-model-ids "references undefined model")
   :crew-exists?              (exists-ref :crew-exists? known-crew-ids "references undefined crew")})

(defn- present-when-ref [other-key expected]
  {:scope    :entity
   :validate (fn [entity field-key]
               (or (not= expected (get entity other-key))
                   (cs/present? (get entity field-key))))
   :message  (str "is required when " (name other-key) " is " (->id expected))})

(defonce ^:private _refs-registered
  (do
    (when-let [one-of-ref (try (cs/lex! :validations :one-of) (catch Throwable _ nil))]
      (cs/update-lexicon! :validations assoc :one-of? one-of-ref))
    (doseq [[k v] existence-refs]
      (cs/update-lexicon! :validations assoc k v))
    (cs/update-lexicon! :validations assoc :present-when? present-when-ref)
    true))

(defn- validation-context [config]
  (let [known-values {:llm-api-exists?           (known-llm-api-ids config)
                      :tool-exists?              (known-tool-ids config)
                      :provider-exists?          (known-provider-ids config)
                      :manifest-provider-exists? (vec (manifest-provider-ids config))
                      :comm-exists?              (known-comm-ids config)
                      :model-exists?             (known-model-ids config)
                      :crew-exists?              (known-crew-ids config)}]
    {:raw          config
     :known-values known-values
     :known-sets   (into {} (map (fn [[predicate values]] [predicate (set values)])) known-values)}))

(defn- dotted-path [segments]
  (str/join "." segments))

(defn- validation-source-file [root key]
  (let [[head id] (str/split key #"\." 3)
        entity-file (when (and root id)
                      (str root "/" head "/" id ".edn"))]
    (cond
      (and entity-file (exists?* entity-file)) (str "config/" head "/" id ".edn")
      :else "config/isaac.edn")))

(defn- validation-error-entry [root key ref-def value]
  (let [known-fn (:known ref-def)]
    {:key          key
     :value        (:message ref-def)
     :file         (validation-source-file root key)
     :bad-value    (->id value)
     :valid-values (when known-fn (known-fn))}))

(defn- resolve-ref-def [validation]
  (let [[ref-key & args] (if (vector? validation) validation [validation])
        ref-def          (try (cs/lex! :validations ref-key) (catch Throwable _ nil))]
    (cond
      (and (fn? ref-def) (seq args)) (apply ref-def args)
      :else                          ref-def)))

(defn- annotation-errors* [root path spec value & [entity field-key]]
  (let [path-str   (dotted-path path)
        own-errors (->> (:validations spec)
                        (keep (fn [validation]
                                (when-let [ref-def (resolve-ref-def validation)]
                                  (let [run-on-nil? (or (= :present? validation)
                                                        (and (vector? validation)
                                                             (= :present? (first validation))))
                                        invalid?   (case (:scope ref-def)
                                                     :entity (not ((:validate ref-def) entity field-key))
                                                     (and (or (some? value) run-on-nil?)
                                                          (not ((:validate ref-def) value))))]
                                    (when invalid?
                                      (validation-error-entry root path-str ref-def value)))))))
        map-errors (when (and (= :map (:type spec)) (map? value))
                     (concat
                        (mapcat (fn [[field-key field-spec]]
                                  (annotation-errors* root (conj path (name field-key)) field-spec (get value field-key) value field-key))
                                (:schema spec))
                        (when-let [value-spec (:value-spec spec)]
                          (mapcat (fn [[entity-id entity-value]]
                                    (when-not (contains? (:schema spec) entity-id)
                                      (annotation-errors* root (conj path (->id entity-id)) value-spec entity-value entity-value nil)))
                                  value))))
        seq-errors (when (and (= :seq (:type spec)) (sequential? value) (:spec spec))
                     (mapcat #(annotation-errors* root path (:spec spec) % % nil) value))]
    (vec (concat own-errors map-errors seq-errors))))
(defn- semantic-errors
  ([config] (semantic-errors config nil))
  ([config root]
   (binding [*config* (validation-context config)]
     (annotation-errors* root [] schema/root config))))

;; region ----- Tool config validation -----

(defn- type-message [field-spec]
  (or (:message field-spec)
      (when-let [field-type (:type field-spec)]
        (case field-type
          :boolean "must be a boolean"
          :int     "must be an integer"
          :keyword "must be a keyword"
          :map     "must be a map"
          :seq     "must be a seq"
          :string  "must be a string"
          (str "must be a " (name field-type))))))

(defn- apply-type-messages [field-spec]
  (cond-> (assoc field-spec :message (type-message field-spec))
    (:schema field-spec)
    (update :schema (fn [schema-map]
                      (into {}
                            (map (fn [[field-key nested-spec]] [field-key (apply-type-messages nested-spec)]))
                            schema-map)))

    (:spec field-spec)
    (update :spec apply-type-messages)

    (:value-spec field-spec)
    (update :value-spec apply-type-messages)))

(defn- manifest-schema-spec [field-schema]
  {:type   :map
   :schema (into {}
                 (map (fn [[field-key field-spec]] [field-key (apply-type-messages field-spec)]))
                 field-schema)})

(defn- prefix-entry-key [prefix entry]
  (update entry :key #(str prefix "." %)))

(defn- unknown-field-warnings [prefix config field-schema ignored-keys]
  (reduce-kv (fn [warnings field-key _]
               (if (or (contains? field-schema field-key)
                       (contains? ignored-keys field-key))
                 warnings
                 (conj warnings {:key (str prefix "." (name field-key)) :value "unknown key"})))
             []
             config))

(defn- manifest-schema-errors [prefix config field-schema]
  (let [schema-spec       (manifest-schema-spec field-schema)
        type-result       (cs/validate (runtime-schema schema-spec) config)
        type-errors       (schema-error-entries prefix type-result)
        type-error-keys   (into #{} (map :key) type-errors)
        validation-errors (->> (annotation-errors* nil [] schema-spec config)
                               (map #(prefix-entry-key prefix %))
                               (remove #(contains? type-error-keys (:key %)))
                               vec)]
    (into type-errors validation-errors)))

(defn- validate-manifest-config [prefix config field-schema & {:keys [ignore-keys warn-unknown?] :or {ignore-keys #{} warn-unknown? true}}]
  {:errors   (manifest-schema-errors prefix config field-schema)
   :warnings (if warn-unknown?
               (unknown-field-warnings prefix config field-schema ignore-keys)
               [])})

(def ^:private manifest-schema-kinds
  [:comm :provider :slash-commands :tools])

(defn- verify-manifest-schema-fragment [module-id field-schema]
  (try
    (cs/verify-schema-lexes field-schema)
    []
    (catch Throwable t
      [{:key   (str "modules." (->id module-id))
        :value (if-let [ref (or (:ref (ex-data t))
                                (:lex (ex-data t)))]
                 (str "unregistered ref " ref)
                  (.getMessage t))}])))

(defn- manifest-ref-errors [module-index]
  (mapcat (fn [[module-id entry]]
            (mapcat (fn [kind]
                      (mapcat (fn [[_ extension]]
                                (when-let [field-schema (:schema extension)]
                                  (verify-manifest-schema-fragment module-id field-schema)))
                              (get-in entry [:manifest kind])))
                    manifest-schema-kinds))
          module-index))

(defn- comm-reserved-schema-errors [module-index]
  (mapcat (fn [[module-id entry]]
            (keep (fn [[extension-id extension]]
                    (when (contains? (:schema extension) :type)
                      {:key   (str "modules." (->id module-id))
                       :value (str ":type is the slot discriminator, not a field"
                                   " (comm " (name extension-id) ")")}))
                  (get-in entry [:manifest :comm])))
          module-index))

(defn- one-of-values [field-spec]
  (some (fn [validation]
          (when (and (vector? validation)
                     (contains? #{:one-of :one-of?} (first validation)))
            (into #{} (map ->id) (rest validation))))
        (:validations field-spec)))

(defn- tool-provider-warning [prefix tool-cfg field-schema result]
  (let [provider-key   (str prefix ".provider")
        provider-spec  (:provider field-schema)
        known-values   (one-of-values provider-spec)
        provider-value (some-> (:provider tool-cfg) ->id)]
    (if (and (seq known-values)
             provider-value
             (not (contains? known-values provider-value)))
      {:errors   (remove #(= provider-key (:key %)) (:errors result))
       :warnings (conj (vec (:warnings result)) {:key provider-key :value "unknown provider"})}
      result)))

(defn- check-tools [config]
  (let [tools-config (:tools config)]
    (if (empty? tools-config)
      {:errors [] :warnings []}
      (binding [*config* (validation-context config)]
        (reduce
          (fn [{:keys [errors warnings]} [tool-kw tool-cfg]]
            (let [tool-name   (name tool-kw)
                  tool-fields (:schema (find-tool-manifest-entry config tool-name))]
              (if (nil? tool-fields)
                {:errors errors :warnings warnings}
                (let [prefix (str "tools." tool-name)
                      check  (->> (validate-manifest-config prefix tool-cfg tool-fields)
                                  (tool-provider-warning prefix tool-cfg tool-fields))]
                  {:errors   (into errors (:errors check))
                   :warnings (into warnings (:warnings check))}))))
          {:errors [] :warnings []}
          tools-config)))))

(defn- check-slash-commands [config]
  (let [commands-config (:slash-commands config)]
    (if (empty? commands-config)
      {:errors [] :warnings []}
      (binding [*config* (validation-context config)]
        (reduce (fn [{:keys [errors warnings]} [command-kw command-cfg]]
                  (let [command-name (name command-kw)
                        known-fields (:schema (find-slash-command-manifest-entry config command-name))]
                    (if (nil? known-fields)
                      {:errors errors :warnings warnings}
                      (let [prefix (str "slash-commands." command-name)
                            check  (validate-manifest-config prefix command-cfg known-fields)]
                        {:errors   (into errors (:errors check))
                         :warnings (into warnings (:warnings check))}))))
                {:errors [] :warnings []}
                commands-config)))))

;; endregion ^^^^^ Tool config validation ^^^^^

;; region ----- Comm slot validation -----

(defn- impl->kw [impl-val]
  (cond
    (keyword? impl-val) impl-val
    (string? impl-val) (keyword impl-val)
    :else nil))

(defn- find-comm-extension [module-index impl-kw]
  (some (fn [[_id entry]]
          (get-in entry [:manifest :comm impl-kw]))
        module-index))

(def ^:private comm-base-fields
  (set (keys (:schema schema/comm-instance))))

(defn- check-comms [config module-index]
  (let [comms (:comms config)]
    (if (empty? comms)
      {:errors [] :warnings []}
      (binding [*config* (validation-context config)]
        (reduce (fn [{:keys [errors warnings]} [slot-id slot-cfg]]
                  (let [type-kw (or (impl->kw (:type slot-cfg))
                                    (impl->kw slot-id))]
                    (if (nil? type-kw)
                      {:errors errors :warnings warnings}
                      (let [entry       (find-comm-extension module-index type-kw)
                            schema-flds (or (:schema entry) {})
                            prefix      (str "comms." (name slot-id))
                            result      (validate-manifest-config prefix slot-cfg schema-flds
                                          :ignore-keys comm-base-fields)]
                        {:errors   (into errors (:errors result))
                         :warnings (into warnings (:warnings result))}))))
                {:errors [] :warnings []}
                comms)))))

;; endregion ^^^^^ Comm slot validation ^^^^^

;; region ----- Provider type schema validation -----

(defn- find-provider-manifest-entry [module-index type-name]
  (let [type-kw (keyword (->id type-name))]
    (some (fn [[_id entry]]
            (get-in entry [:manifest :provider type-kw]))
          module-index)))

(defn- check-provider-types [config raw-providers module-index]
  (if (empty? raw-providers)
    {:errors [] :warnings []}
    (binding [*config* (validation-context config)]
      (reduce (fn [{:keys [errors warnings]} [provider-id provider-cfg]]
                (let [type-name (->id (or (:type provider-cfg) (:from provider-cfg)))]
                  (if (nil? type-name)
                    {:errors errors :warnings warnings}
                    (let [entry  (find-provider-manifest-entry
                                   (merge (module-loader/core-index) module-index) type-name)
                          schema (:schema entry)
                          prefix (str "providers." (->id provider-id))]
                      (if (nil? schema)
                        {:errors errors :warnings warnings}
                        (let [result (validate-manifest-config prefix provider-cfg schema :ignore-keys #{:from :type} :warn-unknown? false)]
                          {:errors   (into errors (:errors result))
                           :warnings (into warnings (:warnings result))}))))))
               {:errors [] :warnings []}
               raw-providers))))

(declare resolve-provider)

(defn- resolved-provider-errors [config raw-providers]
  (mapcat (fn [[provider-id provider-cfg]]
            (when (or (:type provider-cfg) (:from provider-cfg))
              (when-let [resolved (resolve-provider config provider-id)]
                (annotation-errors* nil ["providers" (->id provider-id)] schema/provider resolved resolved nil))))
          raw-providers))

;; endregion ^^^^^ Provider type schema validation ^^^^^

;; region ----- Crew compaction validation -----

(def ^:private valid-compaction-strategies #{:rubberband :slinky})

(defn- check-crew-compaction [config]
  (let [crew (:crew config)]
    (if (empty? crew)
      {:errors [] :warnings []}
      (reduce (fn [{:keys [errors warnings]} [crew-id crew-cfg]]
                (let [strategy (:strategy (:compaction crew-cfg))]
                  (if (and (some? strategy) (not (valid-compaction-strategies (keyword strategy))))
                    {:errors   (conj errors {:key   (str "crew." (->id crew-id) ".compaction.strategy")
                                             :value (str "must be one of: "
                                                         (str/join ", " (sort (map name valid-compaction-strategies))))})
                     :warnings warnings}
                    {:errors errors :warnings warnings})))
              {:errors [] :warnings []}
              crew))))

;; endregion ^^^^^ Crew compaction validation ^^^^^

(defn- normalize-cron-config [cfg]
  (if (map? (:cron cfg))
    (into {} (map (fn [[id entity]]
                    [(->id id) (cond-> entity
                                       (:crew entity) (update :crew ->id))]))
          (:cron cfg))
    {}))

(defn- modern-crew-map? [crew-block]
  (and (map? crew-block)
       (empty? (set/intersection #{:defaults :list :models} (set (keys crew-block))))))

(defn- normalize-crew-config [crew-block]
  (let [old-crew-list (or (:list crew-block) [])]
    (cond
      (modern-crew-map? crew-block)
      (into {} (map (fn [[id entity]] [(->id id) (normalize-crew entity)])) crew-block)

      (seq old-crew-list)
      (into {} (map (fn [entity] [(->id (:id entity)) (normalize-crew entity)])) old-crew-list)

      :else
      {})))

(defn- normalize-model-config [cfg crew-block]
  (let [old-models (or (:models crew-block) {})]
    (cond
      (and (map? (:models cfg))
           (not (vector? (:models cfg)))
           (not (:providers (:models cfg))))
      (into {} (map (fn [[id entity]] [(->id id) (normalize-model entity)])) (:models cfg))

      (seq old-models)
      (into {} (map (fn [[id entity]] [(->id id) (normalize-model entity)])) old-models)

      :else
      {})))

(defn- normalize-provider-config [cfg]
  (let [old-providers (or (get-in cfg [:models :providers]) [])]
    (cond
      (map? (:providers cfg))
      (into {} (map (fn [[id entity]] [(->id id) entity])) (:providers cfg))

      (seq old-providers)
      (into {} (map (fn [entity] [(->id (or (:id entity) (:name entity))) (dissoc entity :name)])) old-providers)

      :else
      {})))

(defn- assoc-present-keys [result source keys]
  (reduce (fn [acc k]
            (if (contains? source k)
              (assoc acc k (get source k))
              acc))
          result
          keys))

(defn normalize-config [cfg]
  (let [crew-block    (or (:crew cfg) {})
        defaults      (or (:defaults cfg) (:defaults crew-block) {})
        new-cron      (normalize-cron-config cfg)
        new-crew      (normalize-crew-config crew-block)
        new-models    (normalize-model-config cfg crew-block)
        new-providers (normalize-provider-config cfg)]
    (assoc-present-keys {:defaults  (normalize-defaults defaults)
                         :crew      new-crew
                         :models    new-models
                         :providers new-providers
                         :cron      new-cron}
                        (cond-> cfg
                                (contains? cfg :cron) (assoc :cron new-cron))
                        [:acp :channels :command-paths :comms :cron :dev :gateway :hooks :module-index :modules :prefer-entity-files :prompt-dir-names :prompt-paths :server :sessions :skill-paths :slash-commands :state-dir :tools :tz])))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Loading -----

(defn load-config-result
  [& [{:keys [state-dir raw-parse-errors? substitute-env? skip-entity-files? data-path-overlay]
        :or   {substitute-env? true}
        :as   opts}]]
  (let [fs*  (runtime-fs opts)
        opts (assoc opts :fs fs* :substitute-env? substitute-env?)]
    (nexus/-with-nested-nexus {:fs fs*}
      (lock-dotenv! state-dir)
              (let [root (paths/config-root state-dir)]
                (if-not (config-files-present? root opts)
                  {:config          {:state-dir state-dir}
                   :errors          [{:key "config" :value (missing-config-message state-dir)}]
                   :missing-config? true
                   :warnings        []
                   :sources         []}
                  (let [{root-data :data root-errors :errors root-warnings :warnings root-sources :sources} (load-root-config root opts)
                        crew-files       (entity-files root "crew" opts)
                        cron-files       (entity-files root "cron" opts)
                        hail-files       (entity-files root "hail" opts)
                        hook-files       (entity-files root "hooks" opts)
                        model-files      (entity-files root "models" opts)
                        provider-files   (entity-files root "providers" opts)
                        md-warnings      (dangling-md-warnings root root-data opts)
                        base-config      (normalize-config (or root-data {}))
                        result           {:config          base-config
                                          :errors          root-errors
                                          :missing-config? false
                                          :warnings        (vec (concat root-warnings
                                                                        (:warnings crew-files)
                                                                        (:warnings cron-files)
                                                                        (:warnings hail-files)
                                                                        (:warnings hook-files)
                                                                        (:warnings model-files)
                                                                        (:warnings provider-files)
                                                                        md-warnings))
                                          :sources         root-sources
                                          :root            (normalize-config (or root-data {}))}
                        result           (reduce merge-root-entity result [:crew :cron :hail :models :providers])
                        result           (cond-> result
                                                 (not skip-entity-files?)
                                                 (as-> r (reduce (fn [acc entity-file] (load-entity-file acc root :crew entity-file substitute-env? raw-parse-errors?)) r (:files crew-files))
                                                       (reduce (fn [acc entity-file] (load-entity-file acc root :cron entity-file substitute-env? raw-parse-errors?)) r (:files cron-files))
                                                        (reduce (fn [acc entity-file] (load-entity-file acc root :hail entity-file substitute-env? raw-parse-errors?)) r (:files hail-files))
                                                        (reduce (fn [acc entity-file] (load-entity-file acc root :hooks entity-file substitute-env? raw-parse-errors?)) r (:files hook-files))
                                                       (reduce (fn [acc entity-file] (load-entity-file acc root :models entity-file substitute-env? raw-parse-errors?)) r (:files model-files))
                                                       (reduce (fn [acc entity-file] (load-entity-file acc root :providers entity-file substitute-env? raw-parse-errors?)) r (:files provider-files))))
                        config           (update (:config result) :defaults normalize-defaults)
                        config           (if data-path-overlay
                                           (assoc-in config (:path data-path-overlay) (:value data-path-overlay))
                                           config)
                        discovery        (module-loader/discover! config {:state-dir state-dir
                                                                          :cwd       (System/getProperty "user.dir")})
                        config           (assoc config
                                           :module-index (:index discovery)
                                           :state-dir    state-dir)
                        raw-providers    (merge (get-in result [:root :providers])
                                                (get-in result [:raw :providers]))
                        comms-check      (check-comms config (:index discovery))
                        manifest-check   (manifest-ref-errors (:index discovery))
                        comm-type-check  (comm-reserved-schema-errors (:index discovery))
                        tools-check      (check-tools config)
                        slash-check      (check-slash-commands config)
                        providers-check  (check-provider-types config raw-providers (:index discovery))
                        resolved-pcheck  (resolved-provider-errors config raw-providers)
                        compaction-check (check-crew-compaction config)
                        errors           (into (:errors result) (concat (semantic-errors config root) (:errors discovery) manifest-check comm-type-check (:errors comms-check) (:errors tools-check) (:errors slash-check) (:errors providers-check) resolved-pcheck (:errors compaction-check)))]
                    {:config   config
                     :errors   (vec (sort-by :key errors))
                     :warnings (vec (sort-by :key (concat (:warnings result) (:warnings comms-check) (:warnings tools-check) (:warnings slash-check) (:warnings providers-check))))
                     :sources  (vec (sort (:sources result)))}))))))

(defn load-config
  [& [opts]]
  (:config (apply load-config-result (when opts [opts]))))

;; endregion ^^^^^ Loading ^^^^^

;; region ----- Ambient Config Snapshot -----

(defn- config-atom []
  (or (nexus/get :config)
      (let [cfg* (atom nil)]
        (nexus/register! [:config] cfg*)
        cfg*)))

(defn snapshot
  "Returns the current process-wide config, or nil if not yet initialized.
   Reads ambient config; call ONLY at entry points and wake boundaries (process
   start, request/turn entry, a worker waking from sleep) — in-flight code must
   receive config as a value, not pull a fresh snapshot. `reason` is a short
   string documenting why this site reads ambient config; it keeps such reads
   greppable and reviewable. See set-snapshot!."
  [reason]
  @(config-atom))

(defn set-snapshot!
  "Low-level primitive: reset the process-wide config snapshot to `cfg`. Internal
   to config — callers use load-config! (load + commit) or, for an already-built
   value, dangerously-install-config!. `reason` documents the call site."
  [cfg reason]
  (log/debug :config/set-snapshot :reason reason)
  (reset! (config-atom) cfg)
  cfg)

(defn load-config!
  "THE loader: load config from `opts`, commit it as the process-wide snapshot,
   and return the value. Call once at an entry point, then thread the returned
   value onward (or read the snapshot). `reason` documents the call site."
  [opts reason]
  (set-snapshot! (load-config opts) reason))

(defn state-dir
  "Returns the resolved state directory. Test fixtures install an explicit
   :state-dir on the nexus via -with-nested-nexus and that wins; otherwise the
   loaded config carries :state-dir (derived from home). Production never
   installs the nexus slot, so the config snapshot is authoritative there."
  []
  (or (nexus/get :state-dir)
      (:state-dir (snapshot "state-dir resolution — ambient config fallback"))))

;; endregion ^^^^^ Ambient Config Snapshot ^^^^^

;; region ----- Workspace -----

(defn resolve-workspace
  [crew-id & [{:keys [state-dir] :as opts}]]
  (let [fs*       (runtime-fs opts)
        crew-dir  (str state-dir "/crew/" crew-id)
        isaac-dir (str state-dir "/workspace-" crew-id)
        ;; Legacy ~/.openclaw lives beside ~/.isaac, so it only applies when the
        ;; state dir is a .isaac directory under a user home.
        oc-dir    (when (str/ends-with? (str state-dir) "/.isaac")
                    (str (subs state-dir 0 (- (count state-dir) (count "/.isaac")))
                         "/.openclaw/workspace-" crew-id))]
    (nexus/-with-nested-nexus {:fs fs*}
      (cond
        (some? (children* crew-dir)) crew-dir
        (and oc-dir (some? (children* oc-dir))) oc-dir
        (some? (children* isaac-dir)) isaac-dir
        :else nil))))

(defn read-workspace-file
  [crew-id filename & [{:as opts}]]
  (let [fs* (runtime-fs opts)]
    (nexus/-with-nested-nexus {:fs fs*}
      (when-let [ws-dir (resolve-workspace crew-id opts)]
        (let [path (str ws-dir "/" filename)]
          (when (exists?* path)
            (slurp* path)))))))

;; endregion ^^^^^ Workspace ^^^^^

;; region ----- Resolution -----

(declare resolve-provider)

(defn resolve-provider [cfg provider-id]
  (let [cfg         (normalize-config cfg)
        provider-id (->id provider-id)]
    (when provider-id
      (or (llm-providers/lookup cfg (:module-index cfg) provider-id)
          (when-let [idx (str/index-of provider-id ":")]
            (get-in cfg [:providers (subs provider-id 0 idx)]))))))

(defn parse-model-ref [model-ref]
  (let [idx (str/index-of model-ref "/")]
    (when idx
      {:provider (subs model-ref 0 idx)
       :model    (subs model-ref (inc idx))})))

(defn- model-override-cfg [cfg model-override]
  (or (get-in cfg [:models model-override])
      (get-in cfg [:models (keyword model-override)])
      (parse-model-ref model-override)))

(defn- model-override-provider-opts [cfg provider-cfg model-cfg]
  (merge provider-cfg
         {:module-index (:module-index cfg)}
         (select-keys model-cfg [:enforce-context-window :thinking-budget-max :think-mode])))

(defn- model-override-provider [cfg provider-id provider-cfg model-cfg]
  (when provider-id
    (llm-provider/make-provider provider-id (model-override-provider-opts cfg provider-cfg model-cfg))))

(defn- model-override-context-window [ctx model-cfg provider-cfg]
  (or (:context-window model-cfg)
      (:context-window provider-cfg)
      (:context-window ctx)
      32768))

(declare resolve-crew resolve-provider)

(def default-history-retention :retain)

(defn resolve-history-retention
  "Resolve history retention for a new session from the chain:
   explicit override > crew > model > provider > defaults > :retain."
  [cfg crew-id explicit-retention]
  (let [cfg         (normalize-config (or cfg {}))
        crew-cfg    (resolve-crew cfg crew-id)
        model-id    (or (:model crew-cfg) (get-in cfg [:defaults :model]))
        model-cfg   (or (get-in cfg [:models model-id])
                        (when-let [provider-id (:provider crew-cfg)]
                          {:provider provider-id}))
        provider-id (:provider model-cfg)
        provider-cfg (or (resolve-provider cfg provider-id) {})]
    (or explicit-retention
        (:history-retention crew-cfg)
        (:history-retention model-cfg)
        (:history-retention provider-cfg)
        (get-in cfg [:defaults :history-retention])
        default-history-retention)))

(defn- apply-model-override [cfg ctx model-override]
  (let [cfg          (normalize-config cfg)
        model-cfg    (model-override-cfg cfg model-override)]
    (if-not model-cfg
      ctx
      (let [provider-id  (:provider model-cfg)
            provider-cfg (or (resolve-provider cfg provider-id) {})]
        (assoc ctx
          :model (:model model-cfg)
          :model-cfg model-cfg
          :provider-cfg provider-cfg
          :provider (model-override-provider cfg provider-id provider-cfg model-cfg)
          :context-window (model-override-context-window ctx model-cfg provider-cfg))))))

(defn resolve-crew [cfg crew-id]
  (let [cfg      (normalize-config cfg)
        crew-id  (->id crew-id)
        defaults (:defaults cfg)]
    (merge (when-let [model-id (:model defaults)] {:model model-id})
           (get-in cfg [:crew crew-id] {}))))

(defn resolve-crew-context [cfg crew-id & [opts]]
  (let [cfg          (cond-> cfg
                       (:crew-members opts) (assoc :crew (:crew-members opts))
                       (:models opts)       (assoc :models (:models opts))
                       (:providers opts)    (assoc :providers (:providers opts)))
        cfg          (normalize-config cfg)
        crew-id      (->id crew-id)
        crew-cfg     (resolve-crew cfg crew-id)
        model-id     (or (:model crew-cfg) (get-in cfg [:defaults :model]))
        model-cfg    (or (get-in cfg [:models model-id])
                         (when-let [provider-id (:provider crew-cfg)]
                           {:model model-id :provider provider-id})
                         (when model-id (parse-model-ref model-id)))
        provider-id  (:provider model-cfg)
        provider-cfg (merge (or (resolve-provider cfg provider-id) {})
                            (select-keys model-cfg [:enforce-context-window :thinking-budget-max :think-mode])
                            {:module-index (:module-index cfg)})
        ctx          {:soul           (or (:soul crew-cfg)
                                          (read-workspace-file crew-id "SOUL.md" opts)
                                          "You are Isaac, a helpful AI assistant.")
                      :model          (:model model-cfg)
                      :model-cfg      model-cfg
                      :crew-cfg       crew-cfg
                      :provider-cfg   (or (resolve-provider cfg provider-id) {})
                      :provider       (when provider-id
                                        (llm-provider/make-provider provider-id provider-cfg))
                      :context-window (or (:context-window model-cfg)
                                          (:context-window provider-cfg)
                                          32768)}]
    (if-let [model-override (:model-override opts)]
      (apply-model-override cfg ctx (->id model-override))
      ctx)))

(defn server-config [config]
  (let [config (normalize-config config)
        dev    (get config :dev)]
    {:port       (or (get-in config [:server :port])
                     (get-in config [:gateway :port])
                     6674)
     :host       (or (get-in config [:server :host])
                     (get-in config [:gateway :host])
                     "127.0.0.1")
     :hot-reload (let [hot-reload (get-in config [:server :hot-reload])]
                   (if (boolean? hot-reload) hot-reload true))
     :dev        (cond
                   (boolean? dev) dev
                   (string? dev) (contains? #{"1" "true" "yes" "on"} (str/lower-case dev))
                   :else false)}))

;; Module-loader registration: dispatched by module.loader when reading
;; user-supplied config for a module's :tools or :slash-commands entry.
(module-loader/register-handler! :user-config
  (fn [root-key entry-id]
    (let [snap (snapshot "module :user-config handler — ambient config lookup")]
      (or (get-in snap [root-key entry-id])
          (get-in snap [root-key (keyword entry-id)])))))

;; endregion ^^^^^ Resolution ^^^^^
