;; mutation-tested: 2026-05-06
(ns isaac.config.loader
  (:require
    [c3kit.apron.env :as c3env]
    [c3kit.apron.schema :as cs]
    [clojure.edn :as edn]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.config.companion :as companion]
    [isaac.config.paths :as paths]
    [isaac.config.schema :as schema]
    [isaac.fs :as fs]
    [isaac.llm.providers :as llm-providers]
    [isaac.llm.registry :as registry]
    [isaac.logger :as log]
    [isaac.module.loader :as module-loader]
    [isaac.system :as system]))

;; region ----- Helpers -----

(def env-overrides* (atom {}))
(def ^:dynamic *isaac-home* nil)

(defn- isaac-env-path []
  (when *isaac-home*
    (str *isaac-home* "/.isaac/.env")))

(defn- isaac-env-value [name]
  (when-let [path (isaac-env-path)]
    (when (fs/exists? path)
      (let [props (doto (java.util.Properties.)
                    (.load (java.io.StringReader. (or (fs/slurp path) ""))))]
        (.getProperty props name)))))

(defn clear-env-overrides! []
  (reset! env-overrides* {}))

(defn set-env-override! [name value]
  (swap! env-overrides* assoc name value))

(defn env [name]
  (or (get @env-overrides* name)
      (c3env/env name)
      (isaac-env-value name)))

(def ^:private ->id schema/->id)

(defn- source-path [relative]
  (str "config/" relative))

(defn- missing-config-message [home]
  (str "no config found; run `isaac init` or create " home "/.isaac/config/isaac.edn"))

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
    (string? value)     (substitute-env value)
    (map? value)        (into {} (map (fn [[k v]] [k (substitute-env-recursive v)]) value))
    (sequential? value) (mapv substitute-env-recursive value)
    :else               value))

(defn- read-edn-string [content substitute-env?]
  (-> content
      edn/read-string
      ((fn [value]
         (if substitute-env?
           (substitute-env-recursive value)
           value)))))

(defn- read-edn-file [path substitute-env? raw-parse-errors?]
  (try
    {:data (read-edn-string (fs/slurp path) substitute-env?)}
    (catch Exception e
      {:error (if raw-parse-errors?
                (.getMessage e)
                "EDN syntax error")})))

(def ^:private present? companion/present?)

(defn- assoc-error [result key value]
  (update result :errors conj {:key key :value value}))

(defn- normalize-defaults [defaults]
  (let [result (cs/conform schema/defaults defaults)]
    (if (cs/error? result) {} result)))

(defn- normalize-crew [crew]
  (let [result (cs/conform schema/crew crew)]
    (if (cs/error? result) {} result)))

(defn- normalize-model [model]
  (let [result (cs/conform schema/model model)]
    (if (cs/error? result) {} result)))

(defn- collect-unknown-key-warnings [warnings kind id entity entity-schema]
  (let [entity-fields (schema/schema-fields entity-schema)]
    (reduce (fn [acc key]
              (if (contains? entity-fields key)
                acc
                (conj acc (warning (str kind "." id "." (name key)) "unknown key"))))
            warnings
            (keys entity))))

(defn- read-entity-files [root dir-name]
  (let [dir (str root "/" dir-name)]
    (->> (or (fs/children dir) [])
         (filter #(has-ext? % ".edn"))
         sort
         (mapv (fn [name]
                  {:id       (subs name 0 (- (count name) 4))
                   :path     (str dir "/" name)
                   :relative (str dir-name "/" name)})))))

(defn- read-md-files [root dir-name]
  (let [dir (str root "/" dir-name)]
    (->> (or (fs/children dir) [])
         (filter #(has-ext? % ".md"))
         sort
         (mapv (fn [name]
                 {:id       (subs name 0 (- (count name) 3))
                   :path     (str dir "/" name)
                   :relative (str dir-name "/" name)})))))

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
    (fs/slurp path)))

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
      (fs/exists? (str root "/" paths/root-filename))
      (seq (read-entity-files root "crew"))
      (seq (read-entity-files root "cron"))
      (seq (read-entity-files root "hooks"))
      (seq (read-entity-files root "models"))
      (seq (read-entity-files root "providers"))
      (seq (read-md-files root "crew"))
      (seq (read-md-files root "hooks"))
      (seq (read-md-files root "models"))
      (seq (read-md-files root "providers"))
      (seq (read-md-files root "cron"))))

(defn- schema-for [kind]
  (case kind
    :cron      schema/cron-job
    :crew      schema/crew
    :defaults  schema/defaults
    :hooks     schema/hook
    :models    schema/model
    :providers schema/provider))

(defn- schema-error-entries [prefix result]
  (mapv (fn [[field message]]
          {:key   (if prefix
                    (str prefix "." (name field))
                    (name field))
           :value message})
        (cs/message-map result)))

(defn- load-companion-text [path]
  (when path
    {:exists? (fs/exists? path)
     :text    (when (fs/exists? path)
                (fs/slurp path))}))

(defn- resolve-crew-soul [id data load-fn]
  (let [result (companion/resolve-text {:inline  (:soul data)
                                        :load-fn load-fn})]
    {:data   (cond-> data
               (:value result) (assoc :soul (:value result)))
     :error  (when (and (:inline? result) (:companion-exists? result))
                {:key   (str "crew." id ".soul")
                 :value "must be set in .edn OR .md"})}))

(defn- resolve-cron-prompt [id job load-fn relative]
  (let [result (companion/resolve-text {:inline  (:prompt job)
                                        :load-fn load-fn})
        errors (cond-> []
                 (and (not (:inline? result)) (not (:companion-exists? result)))
                 (conj {:key   (str "cron." id ".prompt")
                        :value (str "required (inline or " relative ")")})

                 (and (not (:inline? result)) (:companion-empty? result))
                 (conj {:key   (str "cron." id ".prompt")
                        :value "must not be empty"}))]
    (when (and (:inline? result) (:companion-exists? result))
      (log/warn :config/companion-inline-wins :field :prompt :key (str "cron." id) :path relative))
    {:job    (cond-> job
               (:value result) (assoc :prompt (:value result)))
     :errors errors}))

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
  (let [result (companion/resolve-text {:inline  (:template hook)
                                        :load-fn load-fn})
        errors (cond-> []
                 (and (not (:inline? result)) (not (:companion-exists? result)))
                 (conj {:key   (str "hooks." id ".template")
                        :value (str "required (inline or " relative ")")})

                 (and (not (:inline? result)) (:companion-empty? result))
                 (conj {:key   (str "hooks." id ".template")
                        :value "must not be empty"}))]
    (when (and (:inline? result) (:companion-exists? result))
      (log/warn :config/companion-inline-wins :field :template :key (str "hooks." id) :path relative))
    {:errors errors
     :hook   (cond-> hook
               (:value result) (assoc :template (:value result)))}))

(defn- top-level-warnings [data]
  (reduce (fn [acc key]
            (if (contains? (schema/schema-fields schema/root) key)
              acc
              (conj acc (warning (name key) "unknown key"))))
          []
          (keys data)))

(defn- load-root-config [root {:keys [raw-parse-errors? substitute-env?] :as opts}]
  (let [overlay  (overlay-for opts paths/root-filename)
        path     (str root "/" paths/root-filename)]
    (cond
      overlay
      (let [{:keys [content relative]} overlay]
        (try
          (let [raw-data        (read-edn-string content substitute-env?)
                {:keys [cron errors]} (resolve-cron-prompts root raw-data)
                data            (cond-> raw-data
                                  (:cron raw-data) (assoc :cron cron))
                root-result     (cs/conform schema/root data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform schema/defaults defaults))]
            {:data     data
             :errors   (vec (concat errors
                                    (when (cs/error? root-result) (schema-error-entries nil root-result))
                                     (when (and defaults-result (cs/error? defaults-result))
                                       (schema-error-entries "defaults" defaults-result))))
             :warnings (top-level-warnings raw-data)
             :sources  [(source-path relative)]})
          (catch Exception _
            {:data nil :errors [{:key paths/root-filename :value "EDN syntax error"}] :warnings [] :sources []})))

      (fs/exists? path)
      (let [{raw-data :data error :error} (read-edn-file path substitute-env? raw-parse-errors?)]
        (if error
          {:data nil :errors [{:key paths/root-filename :value error}] :warnings [] :sources []}
          (let [{:keys [cron errors]} (resolve-cron-prompts root raw-data)
                data            (cond-> raw-data
                                  (:cron raw-data) (assoc :cron cron))
                root-result     (cs/conform schema/root data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform schema/defaults defaults))]
            {:data     data
             :errors   (vec (concat errors
                                    (when (cs/error? root-result) (schema-error-entries nil root-result))
                                     (when (and defaults-result (cs/error? defaults-result))
                                       (schema-error-entries "defaults" defaults-result))))
             :warnings (top-level-warnings raw-data)
             :sources  [(source-path paths/root-filename)]})))

      :else
      {:data nil :errors [] :warnings [] :sources []})))

(defn- merge-root-entity [result kind]
  (reduce (fn [acc [id entity]]
            (let [id        (->id id)
                  warnings  (collect-unknown-key-warnings [] (name kind) id entity (schema-for kind))
                  entity    (cs/conform (schema-for kind) entity)
                  explicit  (:id entity)]
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
       :data (read-edn-string frontmatter substitute-env?)}
      {:error (str relative " is missing EDN frontmatter")})
    (catch Exception e
      {:error (if raw-parse-errors?
                (.getMessage e)
                "EDN syntax error")})))

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

    :else
    {:data raw-data :error nil :extra-errors []}))

(defn- finalize-entity-load [result kind id relative data extra-errors]
  (let [warnings    (collect-unknown-key-warnings [] (name kind) id data (schema-for kind))
        entity      (cs/conform (schema-for kind) data)
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
  (let [root-data     (or root-data {})
        inline-ids     (fn [kind] (->> (keys (get root-data kind {})) (map ->id) set))
        hook-inline-ids (->> (keys (get root-data :hooks {}))
                             (filter string?)
                             (map ->id)
                             set)
        file-ids       (fn [dir-name] (->> (entity-files root dir-name opts) :files (map :id) set))
        warn-for       (fn [dir-name entry-kind matching-ids]
                         (->> (read-md-files root dir-name)
                              (remove #(contains? matching-ids (:id %)))
                              (mapv #(warning (:relative %) (str "dangling: no matching " entry-kind " entry")))))]
    (vec (concat
           (warn-for "crew" "crew" (into (inline-ids :crew) (file-ids "crew")))
           (warn-for "hooks" "hook" (into hook-inline-ids (file-ids "hooks")))
           (warn-for "models" "model" (into (inline-ids :models) (file-ids "models")))
           (warn-for "providers" "provider" (into (inline-ids :providers) (file-ids "providers")))
           (warn-for "cron" "cron" (into (inline-ids :cron) (file-ids "cron")))))))

(defn- registered-api-ids []
  (requiring-resolve 'isaac.drive.dispatch/make-provider)
  (->> ((requiring-resolve 'isaac.llm.api/registered-apis))
       (map name)
       set))

(defn- known-provider-ids [config]
  (->> (concat (keys (:providers config))
               (keys (llm-providers/module-providers (:module-index config)))
               registry/built-in-providers)
       (map ->id)
       distinct
       sort
       vec))

(defn- provider-errors [config known-provider?]
  (let [known-apis (registered-api-ids)]
    (mapcat (fn [[provider-id provider-cfg]]
              (concat
                (when-let [api-id (some-> (:api provider-cfg) ->id)]
                  (when-not (contains? known-apis api-id)
                    [{:key   (str "providers." provider-id ".api")
                      :value "unknown api"}]))
                (when-let [from-id (some-> (:from provider-cfg) ->id)]
                  (when-not (contains? known-provider? from-id)
                    [{:key   (str "providers." provider-id ".from")
                      :value "unknown provider"}]))))
            (:providers config))))

(defn- semantic-errors [config]
  (let [crew            (:crew config)
        hooks           (->> (:hooks config)
                             (filter (fn [[id _]] (string? id)))
                             (into {}))
        models          (:models config)
        known-providers (known-provider-ids config)
        known-provider? (set known-providers)
        cron            (:cron config)
        defaults        (:defaults config)]
    (vec
      (concat
        (when-let [crew-id (:crew defaults)]
          (when-not (contains? crew crew-id)
            [{:key "defaults.crew"
              :value (str "references undefined crew \"" crew-id "\"")}]))
        (when-let [model-id (:model defaults)]
          (when-not (contains? models model-id)
            [{:key "defaults.model"
              :value (str "references undefined model \"" model-id "\"")}]))
        (mapcat (fn [[crew-id crew-cfg]]
                  (let [model-id    (:model crew-cfg)
                        provider-id (:provider crew-cfg)]
                    (concat
                      (when (and model-id (nil? provider-id) (not (contains? models model-id)))
                        [{:key   (str "crew." crew-id ".model")
                          :value (str "references undefined model \"" model-id "\"")}])
                      (when (and provider-id (not (contains? known-provider? provider-id)))
                        [{:key   (str "crew." crew-id ".provider")
                          :value (str "references undefined provider \"" provider-id
                                      "\" (known: " (str/join ", " known-providers) ")")}]))))
                crew)
        (mapcat (fn [[model-id model-cfg]]
                  (let [provider-id (:provider model-cfg)]
                    (when (and provider-id (not (contains? known-provider? provider-id)))
                      [{:key   (str "models." model-id ".provider")
                        :value (str "references undefined provider \"" provider-id
                                    "\" (known: " (str/join ", " known-providers) ")")}])) )
                models)
        (provider-errors config known-provider?)
        (mapcat (fn [[job-id job-cfg]]
                  (let [crew-id (:crew job-cfg)]
                    (when (and crew-id (not (contains? crew crew-id)))
                      [{:key   (str "cron." job-id ".crew")
                        :value (str "references undefined crew \"" crew-id "\"")}])) )
                cron)
        (mapcat (fn [[hook-id hook-cfg]]
                  (let [crew-id  (:crew hook-cfg)
                        model-id (:model hook-cfg)]
                    (concat
                      (when (and crew-id (not (contains? crew crew-id)))
                        [{:key   (str "hooks." hook-id ".crew")
                          :value (str "references undefined crew \"" crew-id "\"")}])
                      (when (and model-id (not (contains? models model-id)))
                        [{:key   (str "hooks." hook-id ".model")
                          :value (str "references undefined model \"" model-id "\"")}]))))
                hooks)))))

;; region ----- Tool config validation -----

(defn- tool-type-matches? [field-spec value]
  (case (:type field-spec)
    :string  (string? value)
    :int     (integer? value)
    :boolean (boolean? value)
    :keyword (keyword? value)
    true))

(defn- check-tool-config [prefix tool-cfg known-fields registered-providers]
  (reduce
    (fn [{:keys [errors warnings]} [field-kw field-val]]
      (let [field-key  (str prefix "." (name field-kw))
            field-spec (get known-fields field-kw)]
        (cond
          (nil? field-spec)
          {:errors errors :warnings (conj warnings {:key field-key :value "unknown key"})}

          (not (tool-type-matches? field-spec field-val))
          {:errors   (conj errors {:key field-key :value (str "must be a " (name (:type field-spec)))})
           :warnings warnings}

          (and (= :provider field-kw) (seq registered-providers))
          (if (contains? registered-providers (some-> field-val name))
            {:errors errors :warnings warnings}
            {:errors errors :warnings (conj warnings {:key field-key :value "unknown provider"})})

          :else
          {:errors errors :warnings warnings})))
    {:errors [] :warnings []}
    tool-cfg))

(defn- required-tool-errors [prefix known-fields tool-cfg]
  (reduce
    (fn [errors [field-kw field-spec]]
      (if (and (:required? field-spec) (nil? (get tool-cfg field-kw)))
        (conj errors {:key (str prefix "." (name field-kw)) :value "required"})
        errors))
    []
    known-fields))

(defn- check-tools [config]
  (let [tools-config (:tools config)]
    (if (empty? tools-config)
      {:errors [] :warnings []}
      (reduce
        (fn [{:keys [errors warnings]} [tool-kw tool-cfg]]
          (let [tool-name   (name tool-kw)
                tool-fields (schema/tool-schema tool-name)]
            (if (nil? tool-fields)
              {:errors errors :warnings warnings}
              (let [reg-providers (schema/registered-providers tool-name)
                    active-prov   (some-> (:provider tool-cfg) name)
                    prov-fields   (when (and active-prov (contains? reg-providers active-prov))
                                    (schema/provider-schema tool-name active-prov))
                    known-fields  (merge tool-fields prov-fields)
                    prefix        (str "tools." tool-name)
                    req-errors    (required-tool-errors prefix known-fields tool-cfg)
                    check         (check-tool-config prefix tool-cfg known-fields reg-providers)]
                {:errors   (into errors (concat req-errors (:errors check)))
                 :warnings (into warnings (:warnings check))}))))
         {:errors [] :warnings []}
         tools-config))))

(declare type-valid?)

(defn- check-slash-command-config [prefix cmd-cfg known-fields]
  (reduce (fn [{:keys [errors warnings]} [field-kw field-val]]
            (let [field-key (str prefix "." (name field-kw))
                  field-spec (get known-fields field-kw)]
              (cond
                (some? field-spec)
                (if (type-valid? field-spec field-val)
                  {:errors errors :warnings warnings}
                  {:errors   (conj errors {:key field-key :value (str "must be a " (name (:type field-spec)))})
                   :warnings warnings})

                :else
                {:errors errors :warnings (conj warnings {:key field-key :value "unknown key"})})))
          {:errors [] :warnings []}
          cmd-cfg))

(defn- check-slash-commands [config]
  (let [commands-config (:slash-commands config)]
    (if (empty? commands-config)
      {:errors [] :warnings []}
      (reduce (fn [{:keys [errors warnings]} [command-kw command-cfg]]
                (let [command-name (name command-kw)
                      known-fields (schema/slash-command-schema command-name)]
                  (if (nil? known-fields)
                    {:errors errors :warnings warnings}
                    (let [prefix (str "slash-commands." command-name)
                          check  (check-slash-command-config prefix command-cfg known-fields)]
                      {:errors   (into errors (:errors check))
                       :warnings (into warnings (:warnings check))}))))
              {:errors [] :warnings []}
              commands-config))))

;; endregion ^^^^^ Tool config validation ^^^^^

;; region ----- Comm slot validation -----

(def ^:private static-comm-impls
  (set (keys (schema/schema-fields schema/comms))))

(defn- impl->kw [impl-val]
  (cond
    (keyword? impl-val) impl-val
    (string? impl-val)  (keyword impl-val)
    :else               nil))

(defn- find-impl-extends [module-index impl-kw]
  (some (fn [[_id entry]]
          (get-in entry [:manifest :extends :comm impl-kw]))
        module-index))

(defn- type-valid? [field-spec value]
  (case (:type field-spec)
    :string  (string? value)
    :int     (integer? value)
    :boolean (boolean? value)
    :keyword (keyword? value)
    true))

(defn- check-comm-slot [prefix non-impl impl-fields]
  (reduce (fn [{:keys [errors warnings]} [field-kw field-val]]
            (let [field-key (str prefix "." (name field-kw))
                  field-spec (get impl-fields field-kw)]
              (cond
                (some? field-spec)
                (if (type-valid? field-spec field-val)
                  {:errors errors :warnings warnings}
                  {:errors   (conj errors {:key field-key :value (str "must be a " (name (:type field-spec)))})
                   :warnings warnings})

                :else
                {:errors errors :warnings (conj warnings {:key field-key :value "unknown key"})})))
          {:errors [] :warnings []}
          non-impl))

(defn- check-comms [config module-index]
  (let [comms (:comms config)]
    (if (empty? comms)
      {:errors [] :warnings []}
      (reduce (fn [{:keys [errors warnings]} [slot-id slot-cfg]]
                (let [impl-kw  (impl->kw (:impl slot-cfg))
                      static?  (contains? static-comm-impls impl-kw)
                      non-impl (dissoc slot-cfg :impl)]
                  (if (or static? (nil? impl-kw) (empty? non-impl))
                    {:errors errors :warnings warnings}
                    (let [impl-fields (find-impl-extends module-index impl-kw)
                          prefix      (str "comms." (name slot-id))
                          result      (check-comm-slot prefix non-impl impl-fields)]
                      {:errors   (into errors (:errors result))
                       :warnings (into warnings (:warnings result))}))))
              {:errors [] :warnings []}
              comms))))

;; endregion ^^^^^ Comm slot validation ^^^^^

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
  (let [crew-block     (or (:crew cfg) {})
         defaults       (or (:defaults cfg) (:defaults crew-block) {})
        new-cron       (normalize-cron-config cfg)
        new-crew       (normalize-crew-config crew-block)
        new-models     (normalize-model-config cfg crew-block)
        new-providers  (normalize-provider-config cfg)]
    (assoc-present-keys {:defaults  (normalize-defaults defaults)
                         :crew      new-crew
                         :models    new-models
                         :providers new-providers
                         :cron      new-cron}
                       (cond-> cfg
                         (contains? cfg :cron) (assoc :cron new-cron))
                       [:channels :comms :hooks :server :sessions :slash-commands :gateway :cron :tz :dev :acp :prefer-entity-files :modules :module-index :tools])))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Loading -----

(defn load-config-result
  [& [{:keys [home raw-parse-errors? substitute-env? skip-entity-files? data-path-overlay]
        :or   {home (System/getProperty "user.home") substitute-env? true}
        :as   opts}]]
  (binding [*isaac-home* home]
    (let [root (paths/config-root home)
          opts (assoc opts :substitute-env? substitute-env?)]
        (if-not (config-files-present? root opts)
        {:config          {}
         :errors          [{:key "config" :value (missing-config-message home)}]
         :missing-config? true
         :warnings        []
         :sources         []}
          (let [{root-data :data root-errors :errors root-warnings :warnings root-sources :sources} (load-root-config root opts)
                crew-files  (entity-files root "crew" opts)
                cron-files  (entity-files root "cron" opts)
                hook-files  (entity-files root "hooks" opts)
                model-files (entity-files root "models" opts)
                provider-files (entity-files root "providers" opts)
                md-warnings (dangling-md-warnings root root-data opts)
               base-config (normalize-config (or root-data {}))
               result      {:config   base-config
                             :errors   root-errors
                             :missing-config? false
                             :warnings (vec (concat root-warnings
                                                    (:warnings crew-files)
                                                    (:warnings cron-files)
                                                    (:warnings hook-files)
                                                    (:warnings model-files)
                                                    (:warnings provider-files)
                                                    md-warnings))
                             :sources  root-sources
                             :root     (normalize-config (or root-data {}))}
               result      (reduce merge-root-entity result [:crew :cron :models :providers])
               result      (cond-> result
                             (not skip-entity-files?)
                              (as-> r (reduce (fn [acc entity-file] (load-entity-file acc root :crew entity-file substitute-env? raw-parse-errors?)) r (:files crew-files))
                                    (reduce (fn [acc entity-file] (load-entity-file acc root :cron entity-file substitute-env? raw-parse-errors?)) r (:files cron-files))
                                    (reduce (fn [acc entity-file] (load-entity-file acc root :hooks entity-file substitute-env? raw-parse-errors?)) r (:files hook-files))
                                    (reduce (fn [acc entity-file] (load-entity-file acc root :models entity-file substitute-env? raw-parse-errors?)) r (:files model-files))
                                    (reduce (fn [acc entity-file] (load-entity-file acc root :providers entity-file substitute-env? raw-parse-errors?)) r (:files provider-files))))
              config      (update (:config result) :defaults normalize-defaults)
              config      (if data-path-overlay
                            (assoc-in config (:path data-path-overlay) (:value data-path-overlay))
                            config)
              discovery   (module-loader/discover! config {:state-dir (str home "/.isaac")
                                                           :cwd       (System/getProperty "user.dir")})
              config      (assoc config :module-index (:index discovery))
              comms-check  (check-comms config (:index discovery))
              tools-check  (check-tools config)
              slash-check  (check-slash-commands config)
              errors       (into (:errors result) (concat (semantic-errors config) (:errors discovery) (:errors comms-check) (:errors tools-check) (:errors slash-check)))]
          {:config   config
            :errors   (vec (sort-by :key errors))
            :warnings (vec (sort-by :key (concat (:warnings result) (:warnings comms-check) (:warnings tools-check) (:warnings slash-check))))
            :sources  (vec (sort (:sources result)))})))))

(defn load-config
  [& [opts]]
  (:config (apply load-config-result (when opts [opts]))))

;; endregion ^^^^^ Loading ^^^^^

;; region ----- Ambient Config Snapshot -----

(defn- config-atom []
  (or (system/get :config)
      (let [cfg* (atom nil)]
        (system/register! :config cfg*)
        cfg*)))

(defn snapshot
  "Returns the current process-wide config, or nil if not yet initialized.
   Updated by set-snapshot! at server boot and on every hot reload."
  []
  @(config-atom))

(defn set-snapshot!
  "Sets the process-wide current config. Call at boot and on config reload."
  [cfg]
  (reset! (config-atom) cfg)
  cfg)

;; endregion ^^^^^ Ambient Config Snapshot ^^^^^

;; region ----- Workspace -----

(defn resolve-workspace
  [crew-id & [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [crew-dir  (str home "/.isaac/crew/" crew-id)
        oc-dir    (str home "/.openclaw/workspace-" crew-id)
        isaac-dir (str home "/.isaac/workspace-" crew-id)]
    (cond
      (some? (fs/children crew-dir))  crew-dir
      (some? (fs/children oc-dir))    oc-dir
      (some? (fs/children isaac-dir)) isaac-dir
      :else                           nil)))

(defn read-workspace-file
  [crew-id filename & [{:as opts}]]
  (when-let [ws-dir (resolve-workspace crew-id opts)]
    (let [path (str ws-dir "/" filename)]
      (when (fs/exists? path)
        (fs/slurp path)))))

;; endregion ^^^^^ Workspace ^^^^^

;; region ----- Resolution -----

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

(defn resolve-crew [cfg crew-id]
  (let [cfg      (normalize-config cfg)
        crew-id  (->id crew-id)
        defaults (:defaults cfg)]
    (merge (when-let [model-id (:model defaults)] {:model model-id})
           (get-in cfg [:crew crew-id] {}))))

(defn resolve-crew-context [cfg crew-id & [opts]]
  (let [cfg            (normalize-config cfg)
        crew-id        (->id crew-id)
        crew-cfg       (resolve-crew cfg crew-id)
        model-id       (or (:model crew-cfg) (get-in cfg [:defaults :model]))
        model-cfg      (or (get-in cfg [:models model-id])
                           (when-let [provider-id (:provider crew-cfg)]
                             {:model model-id :provider provider-id}))
        provider-id    (:provider model-cfg)
        provider-cfg   (merge (or (resolve-provider cfg provider-id) {})
                              (select-keys model-cfg [:enforce-context-window :reasoning-effort])
                              (select-keys crew-cfg [:reasoning-effort])
                              {:module-index (:module-index cfg)})]
    {:soul            (or (:soul crew-cfg)
                           (read-workspace-file crew-id "SOUL.md" opts)
                           "You are Isaac, a helpful AI assistant.")
     :model           (:model model-cfg)
     :provider        (when provider-id
                        ((requiring-resolve 'isaac.drive.dispatch/make-provider)
                         provider-id provider-cfg))
     :context-window  (or (:context-window model-cfg)
                          (:context-window provider-cfg)
                          32768)}))

(defn server-config [config]
  (let [config (normalize-config config)
        dev    (get config :dev)]
    {:port (or (get-in config [:server :port])
               (get-in config [:gateway :port])
               6674)
     :host (or (get-in config [:server :host])
               (get-in config [:gateway :host])
               "0.0.0.0")
     :hot-reload (let [hot-reload (get-in config [:server :hot-reload])]
                   (if (boolean? hot-reload) hot-reload true))
     :dev  (cond
              (boolean? dev) dev
              (string? dev)  (contains? #{"1" "true" "yes" "on"} (str/lower-case dev))
              :else          false)}))

;; endregion ^^^^^ Resolution ^^^^^
