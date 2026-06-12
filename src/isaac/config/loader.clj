;; mutation-tested: 2026-05-06
(ns isaac.config.loader
  (:require
    [c3kit.apron.env :as c3env]
    [c3kit.apron.schema :as cs]
    [clj-yaml.core :as yaml]
    [clojure.edn :as edn]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.config.berths :as berths]
    [isaac.config.companion :as companion]
    [isaac.config.paths :as paths]
    [isaac.config.schema-base :as schema-base]
    [isaac.config.schema-compose :as schema-compose]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.nexus :as nexus]
    [isaac.schema.registered-in :as registered-in]))

;; region ----- Helpers -----

(def env-overrides* (atom {}))
;; Snapshot of the <root>/.env file, locked at load time (see
;; lock-dotenv!). Avoids re-reading the file on every ${VAR} lookup and removes
;; the need to thread/bind root through the substitution pipeline.
(defonce ^:private dotenv* (atom {}))

(defn- runtime-fs
  ([] (or (fs/instance) (throw (ex-info "config.loader requires :fs in system" {}))))
  ([opts] (or (fs/instance opts) (throw (ex-info "config.loader requires :fs in system" {})))))

(defn- exists?* [path]
  (fs/exists? (runtime-fs) path))

(defn- slurp* [path]
  (fs/slurp (runtime-fs) path))

(defn- children* [path]
  (fs/children (runtime-fs) path))

(defn- read-dotenv [root]
  (let [path (when root (str root "/.env"))]
    (if (and path (exists?* path))
      (let [props (doto (java.util.Properties.)
                    (.load (java.io.StringReader. (or (slurp* path) ""))))]
        (into {} (map (fn [k] [k (.getProperty props k)])) (.stringPropertyNames props)))
      {})))

(defn- lock-dotenv!
  "Snapshots <root>/.env into dotenv*. Called once per load so ${VAR}
   substitution reads a locked map rather than re-reading the file."
  [root]
  (reset! dotenv* (read-dotenv root)))

(defn clear-env-overrides! []
  (reset! env-overrides* {})
  (reset! dotenv* {}))

(defn set-env-override! [name value]
  (swap! env-overrides* assoc name value))

(defn env [name]
  (or (get @env-overrides* name)                            ;; TODO - MDM: c3env allows overrides.  Why reimplement?
      (c3env/env name)
      (get @dotenv* name)))

(def ^:private ->id schema-base/->id)

(defn- runtime-schema [spec]
  (schema-base/strip-validation-annotations spec))

(defn- cached-root-schema []
  (schema-compose/cached-root-schema))

(defn- source-path [relative]
  (str "config/" relative))

(defn- missing-config-message [root]
  (str "no config found; run `isaac init` or create " root "/config/isaac.edn"))

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

(declare schema-for)

(defn- normalize-defaults
  ([defaults] (normalize-defaults (cached-root-schema) defaults))
  ([root-schema defaults]
   (let [result (cs/conform (runtime-schema (schema-for root-schema :defaults)) defaults)]
     (if (cs/error? result) {} result))))

(defn- normalize-crew
  ([crew] (normalize-crew (cached-root-schema) crew))
  ([root-schema crew]
   (let [result (cs/conform (runtime-schema (schema-for root-schema :crew)) crew)]
     (if (cs/error? result) {} result))))

(defn- normalize-model
  ([model] (normalize-model (cached-root-schema) model))
  ([root-schema model]
   (let [result (cs/conform (runtime-schema (schema-for root-schema :models)) model)]
     (if (cs/error? result) {} result))))

(defn- collect-unknown-key-warnings [warnings kind id entity entity-schema]
  (let [entity-fields (schema-base/schema-fields entity-schema)]
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
      (some (fn [dir-name]
              (or (seq (read-entity-files root dir-name))
                  (seq (read-md-files root dir-name))))
            (schema-compose/entity-dir-names))))

(defn- schema-for
  ([kind] (schema-for (cached-root-schema) kind))
  ([root-schema kind]
   (schema-compose/schema-for-kind root-schema kind)))

(defn- schema-error-entries [prefix result]
  (letfn [(segment-name [segment]
            (cond
              (keyword? segment) (name segment)
              (string? segment) (str/replace-first segment #"^:" "")
              :else (str segment)))
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
    {:band   (cond-> band
                     (:value result) (assoc :prompt (:value result)))
     :errors []}))

(defn- top-level-warnings
  ([data] (top-level-warnings (cached-root-schema) data))
  ([root-schema data]
   (reduce (fn [acc key]
             (if (contains? (schema-base/schema-fields root-schema) key)
               acc
               (conj acc (warning (name key) "unknown key"))))
           []
           (keys data))))

(defn- root-entity-warning-kinds []
  (remove #{:cron} (schema-compose/merge-root-entity-kinds)))

(defn- root-entity-warnings
  ([raw-data] (root-entity-warnings (cached-root-schema) raw-data))
  ([root-schema raw-data]
   (reduce (fn [warnings kind]
             (reduce-kv (fn [acc id entity]
                          (if (map? entity)
                            (collect-unknown-key-warnings acc (name kind) (->id id) entity (schema-for root-schema kind))
                            acc))
                        warnings
                        (get raw-data kind {})))
           []
           (root-entity-warning-kinds))))

(defn- root-config-warnings
  ([raw-data] (root-config-warnings (cached-root-schema) raw-data))
  ([root-schema raw-data]
   (concat (top-level-warnings root-schema raw-data)
           (root-entity-warnings root-schema raw-data))))

(defn- read-root-config [root {:keys [raw-parse-errors? substitute-env?] :as opts}]
  (let [overlay (overlay-for opts paths/root-filename)
        path    (str root "/" paths/root-filename)]
    (cond
      overlay
      (let [{:keys [content relative]} overlay]
        (try
          (let [raw-data             (read-edn-string content substitute-env?)
                {:keys [cron errors]} (resolve-cron-prompts root raw-data)
                data                 (cond-> raw-data
                                             (:cron raw-data) (assoc :cron cron))]
            {:data     data
             :errors   (vec errors)
             :warnings []
             :sources  [(source-path relative)]})
          (catch Exception _
            {:data nil :errors [{:key paths/root-filename :value "EDN syntax error"}] :warnings [] :sources []})))

      (exists?* path)
      (let [{raw-data :data error :error} (read-edn-file path substitute-env? raw-parse-errors?)]
        (if error
          {:data nil :errors [{:key paths/root-filename :value error}] :warnings [] :sources []}
          (let [{:keys [cron errors]} (resolve-cron-prompts root raw-data)
                data                  (cond-> raw-data
                                              (:cron raw-data) (assoc :cron cron))]
            {:data     data
             :errors   (vec errors)
             :warnings []
             :sources  [(source-path paths/root-filename)]})))

      :else
      {:data nil :errors [] :warnings [] :sources []})))

(defn- validate-root-config
  ([result] (validate-root-config (cached-root-schema) result))
  ([root-schema {:keys [data] :as result}]
   (if-not data
     result
     (let [root-result     (cs/conform (runtime-schema root-schema) data)
           defaults-result (when-let [defaults (:defaults data)]
                             (cs/conform (runtime-schema (schema-for root-schema :defaults)) defaults))]
       (-> result
           (update :errors into (concat
                                  (when (cs/error? root-result) (schema-error-entries nil root-result))
                                  (when (and defaults-result (cs/error? defaults-result))
                                    (schema-error-entries "defaults" defaults-result))))
           (assoc :warnings (root-config-warnings root-schema data)))))))

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
                root-schema     (cached-root-schema)
                root-result     (cs/conform (runtime-schema root-schema) data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform (runtime-schema (schema-for root-schema :defaults)) defaults))]
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
                root-schema     (cached-root-schema)
                root-result     (cs/conform (runtime-schema root-schema) data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform (runtime-schema (schema-for root-schema :defaults)) defaults))]
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

(defn- merge-root-entity-with-schema [entity-schema result kind]
  (reduce (fn [acc [id entity]]
            (let [id       (->id id)
                  warnings (collect-unknown-key-warnings [] (name kind) id entity entity-schema)
                  entity   (cs/conform (runtime-schema entity-schema) entity)
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

(defn- merge-root-entity
  ([result kind]
   (merge-root-entity-with-schema (schema-for kind) result kind))
  ([root-schema result kind]
   (merge-root-entity-with-schema (schema-for root-schema kind) result kind)))

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
    (if (contains? (schema-compose/frontmatter-entity-dirs) dir-name)
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
  (if-not (map? raw-data)
    {:data raw-data :error nil :extra-errors []}
    (let [{:keys [companion entity-dir]} (schema-compose/descriptor-for kind)
          load-md? (= format :md-frontmatter)
          load-fn  (fn [] {:exists? true :text body})]
      (case (:field companion)
        :soul
        (let [{resolved-data :data companion-error :error}
              (resolve-crew-soul id raw-data (if load-md?
                                               load-fn
                                               #(load-companion-text (str root "/" (paths/soul-relative id)))))]
          {:data resolved-data :error companion-error :extra-errors []})

        :prompt
        (if (= kind :hail)
          (let [{resolved-band :band prompt-errors :errors}
                (resolve-hail-prompt id raw-data #(load-companion-text (str root "/" entity-dir "/" id ".md")))]
            {:data resolved-band :error nil :extra-errors prompt-errors})
          (let [relative (paths/cron-relative id)
                {resolved-job :job prompt-errors :errors}
                (resolve-cron-prompt id raw-data (if load-md?
                                                   load-fn
                                                   #(load-companion-text (str root "/" relative)))
                                     relative)]
            {:data resolved-job :error nil :extra-errors prompt-errors}))

        :template
        (let [relative (paths/hook-relative id)
              {resolved-hook :hook template-errors :errors}
              (resolve-hook-template id raw-data (if load-md?
                                                   load-fn
                                                   #(load-companion-text (str root "/" relative)))
                                     relative)]
          {:data resolved-hook :error nil :extra-errors template-errors})

        {:data raw-data :error nil :extra-errors []}))))

(defn- finalize-entity-load-with-schema [entity-schema result kind id relative data extra-errors]
  (let [warnings    (collect-unknown-key-warnings [] (name kind) id data entity-schema)
        entity      (cs/conform (runtime-schema entity-schema) data)
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

(defn- finalize-entity-load
  ([result kind id relative data extra-errors]
   (finalize-entity-load-with-schema (schema-for kind) result kind id relative data extra-errors))
  ([root-schema result kind id relative data extra-errors]
   (finalize-entity-load-with-schema (schema-for root-schema kind) result kind id relative data extra-errors)))

(defn- load-entity-file
  ([result root kind entry substitute-env? raw-parse-errors?]
   (let [{:keys [format id relative]} entry
         {raw-data :data error :error body :body} (read-entity-entry entry substitute-env? raw-parse-errors?)
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
  ([root-schema result root kind {:keys [format id relative] :as entry} substitute-env? raw-parse-errors?]
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
       (finalize-entity-load root-schema result kind id relative data extra-errors)))))

(defn- dangling-entry-kind [kind]
  (case kind
    :hooks "hook"
    :models "model"
    :providers "provider"
    (name kind)))

(defn- dangling-md-warnings [root root-data opts]
  (let [root-data (or root-data {})
        inline-ids (fn [kind]
                     (let [ids (->> (keys (get root-data kind {})) (map ->id) set)]
                       (if (= kind :hooks)
                         (into ids (->> (keys (get root-data :hooks {}))
                                        (filter string?)
                                        (map ->id)))
                         ids)))
        file-ids   (fn [dir-name] (->> (entity-files root dir-name opts) :files (map :id) set))
        warn-for   (fn [kind dir-name]
                     (->> (read-md-files root dir-name)
                          (remove #(contains? (into (inline-ids kind) (file-ids dir-name)) (:id %)))
                          (mapv #(warning (:relative %)
                                          (str "dangling: no matching " (dangling-entry-kind kind) " entry")))))]
    (vec (mapcat (fn [[kind {:keys [entity-dir]}]]
                   (when entity-dir
                     (warn-for kind entity-dir)))
                 (schema-compose/descriptors)))))

(defn- declared-module-api-ids [config]
  (let [builtin-index (module-loader/builtin-index)
        module-index  (merge builtin-index (:module-index config))
        modules      (:modules config)]
    (if (and (some? modules) (not (map? modules)))
      (->> (keys builtin-index)
           (keep #(get module-index %))
           (mapcat #(keys (get-in % [:manifest :isaac.server/llm-api])))
           (map clojure.core/name)
           set)
      (let [declared-ids (into (set (keys builtin-index))
                               (->> (keys modules)
                                    (map ->id)
                                    (map keyword)))]
        (->> declared-ids
             (keep #(get module-index %))
             (mapcat #(keys (get-in % [:manifest :isaac.server/llm-api])))
             (map clojure.core/name)
             set)))))

(defn- manifest-capability-ids [config kind]
  (->> (merge (module-loader/builtin-index) (:module-index config))
       vals
       (mapcat #(keys (get-in % [:manifest kind])))
       (map ->id)
       set))

(defn- manifest-provider-ids [config]
  ;; Phase 7 (isaac-ho18): provider templates moved to the
  ;; :isaac.server/provider-template berth.
  (->> (manifest-capability-ids config :isaac.server/provider-template) sort vec))

(defn- module-instantiated-provider-ids
  "Materialized provider ids contributed by third-party modules to
   :isaac.server/provider. Core's contributions to that berth ship
   ready-to-use providers; the user's instantiated providers live in
   the :providers config slot and are picked up separately by
   :registered-in?'s config-side read."
  [config]
  (->> (or (:module-index config) {})
       (remove (fn [[id _]] (= id :isaac.core)))
       (mapcat (fn [[_ entry]] (keys (get-in entry [:manifest :isaac.server/provider]))))
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
  ;; Phase 8 (isaac-qqgv): comm contributions live at
  ;; :isaac.server/comm instead of the deleted :comm extension kind.
  (->> (concat (keys (:comms config))
               (manifest-capability-ids config :isaac.server/comm))
       (map ->id)
       distinct
       sort
       vec))

(defn- find-manifest-entry [config section name]
  (let [kw (keyword (->id name))]
    (some (fn [[_ entry]]
            (get-in entry [:manifest section kw]))
          (merge (module-loader/builtin-index) (:module-index config)))))

(defn- find-tool-manifest-entry [config tool-name]
  ;; Phase 6 (isaac-w7o5): tool contributions live at :isaac.server/tools
  ;; (the berth), not under :tools.
  (find-manifest-entry config :isaac.server/tools tool-name))

(defn- find-slash-command-manifest-entry [config command-name]
  (find-manifest-entry config :isaac.server/slash-commands command-name))

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
  {:model-exists? (exists-ref :model-exists? known-model-ids "references undefined model")
   :crew-exists?  (exists-ref :crew-exists? known-crew-ids "references undefined crew")})

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
  (let [known-values {:model-exists? (known-model-ids config)
                      :crew-exists?  (known-crew-ids config)}]
    {:raw          config
     :known-values known-values
     :known-sets   (into {} (map (fn [[predicate values]] [predicate (set values)])) known-values)}))

(defn- dotted-path [segments]
  (str/join "." segments))

(defn- path-segment [segment]
  (cond
    (qualified-keyword? segment) (str (namespace segment) "/" (name segment))
    (keyword? segment)           (name segment)
    :else                        (->id segment)))

(defn- validation-source-file [root key]
  (let [[head id] (str/split key #"\." 3)
        entity-file (when (and root id)
                      (str root "/" head "/" id ".edn"))]
    (cond
      (and entity-file (exists?* entity-file)) (str "config/" head "/" id ".edn")
      :else "config/isaac.edn")))

(defn- validation-error-entry
  ([root key ref-def value]
   (validation-error-entry root key ref-def value nil))
  ([root key ref-def value override-message]
  (let [known-fn (:known ref-def)]
    {:key          key
     :value        (or override-message (:message ref-def))
     :file         (validation-source-file root key)
     :bad-value    (->id value)
     :valid-values (when known-fn (known-fn))})))

(defn- resolve-ref-def [validation]
  (let [[ref-key & args] (if (vector? validation) validation [validation])
        ref-def (try (cs/lex! :validations ref-key) (catch Throwable _ nil))]
    (cond
      (and (fn? ref-def) (seq args)) (apply ref-def args)
      :else ref-def)))

(defn- annotation-errors* [root path spec value & [entity field-key]]
  (let [path-str   (dotted-path path)
        own-errors (->> (:validations spec)
                        (keep (fn [validation]
                                (when-let [ref-def (resolve-ref-def validation)]
                                  (let [present-validation? (or (= :present? validation)
                                                                (and (vector? validation)
                                                                     (= :present? (first validation))))
                                        run-on-nil?         present-validation?]
                                    (try
                                      (let [invalid? (case (:scope ref-def)
                                                       :entity (not ((:validate ref-def) entity field-key))
                                                       (and (or (some? value) run-on-nil?)
                                                            (not ((:validate ref-def) value))))]
                                        (when invalid?
                                          (validation-error-entry root path-str ref-def value)))
                                      (catch clojure.lang.ExceptionInfo e
                                        (validation-error-entry root path-str ref-def value
                                                                (or (:message (ex-data e))
                                                                    (ex-message e))))))))))
        map-errors (when (and (= :map (:type spec)) (map? value))
                     (concat
                       (mapcat (fn [[field-key field-spec]]
                                 (annotation-errors* root (conj path (path-segment field-key)) field-spec (get value field-key) value field-key))
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
  ([config] (semantic-errors config nil (cached-root-schema)))
  ([config root] (semantic-errors config root (cached-root-schema)))
  ([config root schema-spec]
   (binding [*config*                    (validation-context config)
             ;; Merge core in so :registered-in? sees foundation-declared
             ;; berths (e.g. :isaac.server/tools) and their contributions
             ;; from the platform manifest, not just user modules.
             registered-in/*module-index* (merge (module-loader/builtin-index)
                                                 (:module-index config))
             ;; The :registered-in? primitive also checks config-side
             ;; contributions (user-config keys at the berth's :config
             ;; :path) — e.g. user-instantiated providers under [:providers]
             ;; register as contributions to :isaac.server/provider.
             registered-in/*config*       (or (:raw config) config)]
     (annotation-errors* root [] schema-spec config))))

;; region ----- Tool config validation -----

(defn- type-message [field-spec]
  (or (:message field-spec)
      (when-let [field-type (:type field-spec)]
        (case field-type
          :boolean "must be a boolean"
          :int "must be an integer"
          :keyword "must be a keyword"
          :map "must be a map"
          :seq "must be a seq"
          :string "must be a string"
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
  ;; Phase 8 (isaac-qqgv) finished the migration — every extension
  ;; kind now lives under a :isaac.server/* berth.
  [:isaac.server/comm :isaac.server/provider-template :isaac.server/slash-commands :isaac.server/tools])

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
                  (get-in entry [:manifest :isaac.server/comm])))
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

(defn- find-comm-extension
  ;; Phase 8 (isaac-qqgv): comm contributions moved to :isaac.server/comm.
  [module-index impl-kw]
  (some (fn [[_id entry]]
          (get-in entry [:manifest :isaac.server/comm impl-kw]))
        module-index))

(defn- check-comms [config module-index root-schema]
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
                                                                  :ignore-keys (schema-compose/comm-base-fields root-schema))]
                        {:errors   (into errors (:errors result))
                         :warnings (into warnings (:warnings result))}))))
                {:errors [] :warnings []}
                comms)))))

;; endregion ^^^^^ Comm slot validation ^^^^^

;; region ----- Provider type schema validation -----

(defn- find-provider-manifest-entry [module-index type-name]
  (let [type-kw (keyword (->id type-name))]
    (some (fn [[_id entry]]
            (get-in entry [:manifest :isaac.server/provider-template type-kw]))
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
                                   (merge (module-loader/builtin-index) module-index) type-name)
                          schema (:schema entry)
                          prefix (str "providers." (->id provider-id))]
                      (if (nil? schema)
                        {:errors errors :warnings warnings}
                        (let [result (validate-manifest-config prefix provider-cfg schema :ignore-keys #{:from :type} :warn-unknown? false)]
                          {:errors   (into errors (:errors result))
                           :warnings (into warnings (:warnings result))}))))))
              {:errors [] :warnings []}
              raw-providers))))

(defn- resolved-provider-errors [config raw-providers root-schema]
  (let [resolve-provider (requiring-resolve 'isaac.config.resolve/resolve-provider)
        provider-schema  (schema-compose/provider-entity-schema root-schema)]
    (mapcat (fn [[provider-id provider-cfg]]
              (when (or (:type provider-cfg) (:from provider-cfg))
                (when-let [resolved (resolve-provider config provider-id)]
                  (annotation-errors* nil ["providers" (->id provider-id)] provider-schema resolved resolved nil))))
            raw-providers)))

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

(defn- normalize-crew-config
  ([crew-block] (normalize-crew-config (cached-root-schema) crew-block))
  ([root-schema crew-block]
   (let [old-crew-list (or (:list crew-block) [])]
    (cond
      (modern-crew-map? crew-block)
      (into {} (map (fn [[id entity]] [(->id id) (normalize-crew root-schema entity)])) crew-block)

      (seq old-crew-list)
      (into {} (map (fn [entity] [(->id (:id entity)) (normalize-crew root-schema entity)])) old-crew-list)

      :else
      {}))))

(defn- normalize-model-config
  ([cfg crew-block] (normalize-model-config (cached-root-schema) cfg crew-block))
  ([root-schema cfg crew-block]
   (let [old-models (or (:models crew-block) {})]
    (cond
      (and (map? (:models cfg))
           (not (vector? (:models cfg)))
           (not (:providers (:models cfg))))
      (into {} (map (fn [[id entity]] [(->id id) (normalize-model root-schema entity)])) (:models cfg))

      (seq old-models)
      (into {} (map (fn [[id entity]] [(->id id) (normalize-model root-schema entity)])) old-models)

      :else
      {}))))

(defn- normalize-provider-config
  ([cfg] (normalize-provider-config (cached-root-schema) cfg))
  ([_root-schema cfg]
   (let [old-providers (or (get-in cfg [:models :providers]) [])]
    (cond
      (map? (:providers cfg))
      (into {} (map (fn [[id entity]] [(->id id) entity])) (:providers cfg))

      (seq old-providers)
      (into {} (map (fn [entity] [(->id (or (:id entity) (:name entity))) (dissoc entity :name)])) old-providers)

      :else
      {}))))

(defn- assoc-present-keys [result source keys]
  (reduce (fn [acc k]
            (if (contains? source k)
              (assoc acc k (get source k))
              acc))
          result
          keys))

(def ^:private extra-present-config-keys [:dev :module-index :root])

(defn- present-config-keys [root-schema]
  (concat extra-present-config-keys
          (remove (schema-compose/normalized-config-keys)
                  (keys (schema-base/schema-fields root-schema)))))

(defn normalize-config
  ([cfg] (normalize-config (cached-root-schema) cfg))
  ([root-schema cfg]
   (let [crew-block    (or (:crew cfg) {})
        defaults      (or (:defaults cfg) (:defaults crew-block) {})
        new-cron      (normalize-cron-config cfg)
        new-crew      (normalize-crew-config root-schema crew-block)
        new-models    (normalize-model-config root-schema cfg crew-block)
        new-providers (normalize-provider-config root-schema cfg)]
    (assoc-present-keys {:defaults  (normalize-defaults root-schema defaults)
                         :crew      new-crew
                         :models    new-models
                         :providers new-providers
                         :cron      new-cron}
                        (cond-> cfg
                                (contains? cfg :cron) (assoc :cron new-cron))
                        (present-config-keys root-schema)))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Loading -----

(defn load-config-result
  [& [{:keys [root raw-parse-errors? substitute-env? skip-entity-files? data-path-overlay]
       :or   {substitute-env? true}
       :as   opts}]]
  (let [fs*  (runtime-fs opts)
        opts (assoc opts :fs fs* :substitute-env? substitute-env?)]
    (nexus/-with-nested-nexus {:fs fs*}
                              (lock-dotenv! root)
                              (let [config-root (paths/config-root root)]
                                (if-not (config-files-present? config-root opts)
                                  {:config          {:root root}
                                   :errors          [{:key "config" :value (missing-config-message root)}]
                                   :missing-config? true
                                   :warnings        []
                                   :sources         []}
                                  (let [root-read       (read-root-config config-root opts)
                                        root-data       (:data root-read)
                                        discovery-input (cond-> {}
                                                          (contains? root-data :modules) (assoc :modules (:modules root-data)))
                                        discovery       (module-loader/discover! discovery-input {:root root
                                                                                                  :cwd  (System/getProperty "user.dir")})
                                        effective-schema (schema-compose/cache-composed! (:index discovery))
                                        {root-errors :errors root-warnings :warnings root-sources :sources}
                                        (validate-root-config effective-schema root-read)
                                        entity-kinds     (->> (schema-compose/descriptors)
                                                              (keep (fn [[kind {:keys [entity-dir]}]]
                                                                      (when entity-dir [kind entity-dir])))
                                                              vec)
                                        entity-files-by-kind
                                        (into {} (map (fn [[kind dir]]
                                                        [kind (entity-files config-root dir opts)])
                                                      entity-kinds))
                                        md-warnings      (dangling-md-warnings config-root root-data opts)
                                        base-config      (normalize-config effective-schema (or root-data {}))
                                        result           {:config          base-config
                                                          :errors          root-errors
                                                          :missing-config? false
                                                          :warnings        (vec (concat root-warnings
                                                                                        (mapcat :warnings (vals entity-files-by-kind))
                                                                                        md-warnings))
                                                          :sources         root-sources
                                                          :root            (normalize-config effective-schema (or root-data {}))}
                                        result           (reduce (fn [acc kind]
                                                                   (merge-root-entity effective-schema acc kind))
                                                                 result
                                                                 (schema-compose/merge-root-entity-kinds))
                                        result           (if skip-entity-files?
                                                             result
                                                             (reduce (fn [acc [kind _dir]]
                                                                       (reduce (fn [a entity-file]
                                                                                 (load-entity-file effective-schema a config-root kind
                                                                                                     entity-file substitute-env? raw-parse-errors?))
                                                                               acc
                                                                               (:files (get entity-files-by-kind kind))))
                                                                     result
                                                                     entity-kinds))
                                        config           (update (:config result) :defaults #(normalize-defaults effective-schema %))
                                        config           (if data-path-overlay
                                                           (assoc-in config (:path data-path-overlay) (:value data-path-overlay))
                                                           config)
                                        config           (assoc config
                                                           :module-index (:index discovery)
                                                           :root root)
                                        raw-providers    (merge (get-in result [:root :providers])
                                                                (get-in result [:raw :providers]))
                                        comms-check      (if (berths/claims-path? (:index discovery) [:comms])
                                                           {:errors [] :warnings []}
                                                           (check-comms config (:index discovery) effective-schema))
                                        manifest-check   (manifest-ref-errors (:index discovery))
                                        comm-type-check  (comm-reserved-schema-errors (:index discovery))
                                        tools-check      (check-tools config)
                                        slash-check      (check-slash-commands config)
                                        providers-check  (check-provider-types config raw-providers (:index discovery))
                                        resolved-pcheck  (resolved-provider-errors config raw-providers effective-schema)
                                        compaction-check (check-crew-compaction config)
                                        errors           (->> (concat (semantic-errors config config-root effective-schema)
                                                                      (:errors discovery)
                                                                      manifest-check
                                                                      comm-type-check
                                                                      (:errors comms-check)
                                                                      (:errors tools-check)
                                                                      (:errors slash-check)
                                                                      (:errors providers-check)
                                                                      resolved-pcheck
                                                                      (:errors compaction-check))
                                                            (into (:errors result))
                                                            (berths/normalize-errors (:index discovery)))]
                                    {:config   config
                                     :errors   (vec (sort-by :key errors))
                                     :warnings (vec (sort-by :key (concat (:warnings result) (:warnings comms-check) (:warnings tools-check) (:warnings slash-check) (:warnings providers-check))))
                                     :sources  (vec (sort (:sources result)))}))))))

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
  "THE loader: load config from `root` (read via `fs`), validate it, commit
   it as the process-wide snapshot, and return the value. Call once at an entry
   point, then thread the returned value onward (or read the snapshot). Throws
   ex-info {:errors [...]} carrying ALL validation/coercion errors when the
   config is invalid (a missing config is not an error — it commits the empty
   default). `reason` documents the call site."
  [root fs reason]
  (let [{:keys [config errors missing-config?]}
        (load-config-result {:root root :fs fs})]
    (when (and (seq errors) (not missing-config?))
      (throw (ex-info (str "invalid configuration in " root)
                      {:errors errors :root root})))
    (set-snapshot! config reason)
    config))

(defn load-config
  "Compatibility wrapper for older module repos. Loads config and returns only
   the config value without committing it as the process snapshot."
  ([] (:config (load-config-result)))
  ([opts] (:config (load-config-result opts))))

(defn root
  "Returns the resolved root. Test fixtures install an explicit
   :root on the nexus via -with-nested-nexus and that wins; otherwise the
   loaded config carries :root (derived from home). Production never
   installs the nexus slot, so the config snapshot is authoritative there."
  []
  (or (nexus/get :root)
      (:root (snapshot "root resolution — ambient config fallback"))))

;; endregion ^^^^^ Ambient Config Snapshot ^^^^^

;; region ----- Workspace -----

(defn resolve-workspace
  [crew-id & [{:keys [root] :as opts}]]
  (let [fs*       (runtime-fs opts)
        crew-dir  (str root "/crew/" crew-id)
        isaac-dir (str root "/workspace-" crew-id)
        ;; Legacy ~/.openclaw lives beside ~/.isaac, so it only applies when the
        ;; root is a .isaac directory under a user home.
        oc-dir    (when (str/ends-with? (str root) "/.isaac")
                    (str (subs root 0 (- (count root) (count "/.isaac")))
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

;; Module-loader registration: dispatched by module.loader when reading
;; user-supplied config for a module's :tools or :slash-commands entry.
(module-loader/register-handler! :user-config
                                 (fn [root-key entry-id]
                                   (let [snap (snapshot "module :user-config handler — ambient config lookup")]
                                     (or (get-in snap [root-key entry-id])
                                         (get-in snap [root-key (keyword entry-id)])))))

;; endregion ^^^^^ Resolution ^^^^^
