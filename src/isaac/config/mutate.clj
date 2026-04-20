(ns isaac.config.mutate
  "Domain mutations for Isaac configuration. Pure functions over
   the config filesystem; no CLI concerns, no stdout, no user-facing
   strings.

   Both set-config and unset-config return a result map of shape:

     {:status   :ok | :invalid | :wildcard | :missing-path | :missing-entity-id
                | :not-found | :invalid-config
      :file     \"<relative-path>\"   ; file that changed (nil on failure)
      :errors   [{:key :value} ...]   ; structured validation errors
      :warnings [{:key :value} ...]}  ; structured warnings"
  (:require
    [c3kit.apron.schema.path :as path]
    [clojure.edn :as edn]
    [isaac.config.loader :as loader]
    [isaac.config.schema :as config-schema]
    [isaac.fs :as fs]))

(def ^:private entity-sections #{:crew :models :providers})
(def ^:private soul-inline-limit 64)

;; region ----- Paths -----

(defn- config-root-path [home]
  (str home "/.isaac/config"))

(defn- config-path [home relative]
  (str (config-root-path home) "/" relative))

(defn- entity-relative-path [kind id]
  (str (name kind) "/" id ".edn"))

(defn- soul-relative-path [id]
  (str "crew/" id ".md"))

(defn- read-edn-path [path]
  (when (fs/exists? path)
    (edn/read-string (fs/slurp path))))

(defn- copy-tree! [source-fs target-fs path]
  (binding [fs/*fs* source-fs]
    (when (fs/exists? path)
      (if (fs/file? path)
        (let [content (fs/slurp path)
              parent  (fs/parent path)]
          (binding [fs/*fs* target-fs]
            (when parent
              (fs/mkdirs parent))
            (fs/spit path content)))
        (do
          (binding [fs/*fs* target-fs]
            (fs/mkdirs path))
          (doseq [child (or (fs/children path) [])]
            (copy-tree! source-fs target-fs (str path "/" child))))))))

;; endregion ^^^^^ Paths ^^^^^

;; region ----- Data navigation -----

(defn- candidate-keys [segment]
  (cond
    (keyword? segment) [segment (name segment)]
    (string? segment)  [(keyword segment) segment]
    :else              [segment]))

(defn- existing-key [m segment]
  (some #(when (contains? m %) %) (candidate-keys segment)))

(defn- new-key [segment]
  (cond
    (keyword? segment) segment
    (string? segment)  (keyword segment)
    :else              segment))

(defn- path-present? [data segments]
  (if (empty? segments)
    true
    (and (map? data)
         (when-let [k (existing-key data (first segments))]
           (path-present? (get data k) (rest segments))))))

(defn- value-at-path [data segments]
  (if (empty? segments)
    data
    (when (map? data)
      (when-let [k (existing-key data (first segments))]
        (value-at-path (get data k) (rest segments))))))

(defn- assoc-path [data segments value]
  (if (empty? segments)
    value
    (let [data  (or data {})
          seg   (first segments)
          k     (or (existing-key data seg) (new-key seg))
          child (get data k)]
      (assoc data k (assoc-path child (rest segments) value)))))

(defn- dissoc-path [data segments]
  (if (and (map? data) (seq segments))
    (if-let [k (existing-key data (first segments))]
      (if-let [more (next segments)]
        (let [child   (dissoc-path (get data k) more)
              updated (if (nil? child) (dissoc data k) (assoc data k child))]
          (when (seq updated) updated))
        (let [updated (dissoc data k)]
          (when (seq updated) updated)))
      data)
    data))

;; endregion ^^^^^ Data navigation ^^^^^

;; region ----- Parse & state -----

(defn- parse-config-path [path-str]
  (let [segments (path/parse path-str)]
    (cond
      (empty? segments)
      {:status :missing-path}

      (some #(#{:wildcard :index} (first %)) segments)
      {:status :wildcard}

      :else
      (let [segments   (mapv second segments)
            root-key   (first segments)
            entity?    (contains? entity-sections root-key)
            field-path (if entity? (subvec segments 2) (subvec segments 1))]
        (cond
          (and entity? (< (count segments) 2))
          {:status :missing-entity-id}

          :else
          {:entity-id     (when entity? (config-schema/->id (second segments)))
           :entity?       entity?
           :field-path    field-path
           :path          path-str
           :root-key      root-key
           :root-path     (if entity? [(first segments) (second segments)] [(first segments)])
           :segments      segments
           :soul?         (and entity? (= :crew root-key) (= [:soul] field-path))
           :whole-entity? (and entity? (= 2 (count segments)))})))))

(defn- config-state [home parsed]
  (let [root-path         (config-path home loader/root-filename)
        root-data         (or (read-edn-path root-path) {})
        entity-relative   (when (:entity? parsed) (entity-relative-path (:root-key parsed) (:entity-id parsed)))
        entity-path       (when entity-relative (config-path home entity-relative))
        entity-data       (or (some-> entity-path read-edn-path) {})
        soul-relative     (when (:soul? parsed) (soul-relative-path (:entity-id parsed)))
        soul-path         (when soul-relative (config-path home soul-relative))]
    {:entity-data           entity-data
     :entity-exists?        (boolean (and entity-path (fs/exists? entity-path)))
     :entity-path           entity-path
     :entity-relative       entity-relative
     :entity-root-exists?   (and (:entity? parsed) (path-present? root-data (:root-path parsed)))
     :inline-entity-soul?   (and (:soul? parsed) (path-present? entity-data [:soul]))
     :inline-root-soul?     (and (:soul? parsed) (path-present? root-data (:segments parsed)))
     :md-exists?            (boolean (and soul-path (fs/exists? soul-path)))
     :prefer-entity-files?  (true? (value-at-path root-data [:prefer-entity-files]))
     :root-data             root-data
     :root-path-exists?     (path-present? root-data (:segments parsed))
     :root-path             root-path
     :soul-path             soul-path
     :soul-relative         soul-relative}))

;; endregion ^^^^^ Parse & state ^^^^^

;; region ----- Plan & apply -----

(defn- update-edn-file [plan relative data]
  (if (nil? data)
    (-> plan
        (update :writes dissoc relative)
        (update :deletes conj relative))
    (-> plan
        (update :deletes disj relative)
        (assoc-in [:writes relative] (pr-str data)))))

(defn- update-text-file [plan relative content]
  (-> plan
      (update :deletes disj relative)
      (assoc-in [:writes relative] content)))

(defn- choose-set-location [parsed state]
  (cond
    (and (:soul? parsed) (:md-exists? state)) :md
    (and (:soul? parsed) (:inline-root-soul? state)) :root
    (and (:soul? parsed) (:inline-entity-soul? state)) :entity
    (and (:entity? parsed) (:entity-root-exists? state)) :root
    (and (:entity? parsed) (:entity-exists? state)) :entity
    (and (:entity? parsed) (:prefer-entity-files? state)) :entity
    :else :root))

(defn- choose-unset-location [parsed state]
  (cond
    (and (:soul? parsed) (:md-exists? state)) :md
    (:root-path-exists? state) :root
    (and (:entity? parsed)
         (or (and (:whole-entity? parsed) (:entity-exists? state))
             (path-present? (:entity-data state) (:field-path parsed)))) :entity
    :else nil))

(defn- use-soul-markdown? [parsed state location value]
  (and (:soul? parsed)
       (= :entity location)
       (not (:md-exists? state))
       (not (:inline-entity-soul? state))
       (string? value)
       (> (count value) soul-inline-limit)))

(defn- set-plan [parsed state value]
  (let [location (choose-set-location parsed state)]
    (cond
      (or (= :md location) (use-soul-markdown? parsed state location value))
      (let [entity-data' (when (:entity-exists? state)
                           (dissoc-path (:entity-data state) [:soul]))]
        (cond-> {:deletes #{} :file (:soul-relative state) :writes {}}
          true (update-text-file (:soul-relative state) value)
          (:entity-exists? state) (update-edn-file (:entity-relative state) entity-data')))

      (= :entity location)
      (let [entity-data' (if (:whole-entity? parsed)
                           value
                           (assoc-path (:entity-data state) (:field-path parsed) value))]
        (-> {:deletes #{} :file (:entity-relative state) :writes {}}
            (update-edn-file (:entity-relative state) entity-data')))

      :else
      (let [root-data' (assoc-path (:root-data state) (:segments parsed) value)]
        (-> {:deletes #{} :file loader/root-filename :writes {}}
            (update-edn-file loader/root-filename root-data'))))))

(defn- unset-plan [parsed state]
  (when-let [location (choose-unset-location parsed state)]
    (case location
      :md
      {:deletes #{(:soul-relative state)} :file (:soul-relative state) :writes {}}

      :entity
      (let [entity-data' (if (:whole-entity? parsed)
                           nil
                           (dissoc-path (:entity-data state) (:field-path parsed)))]
        (-> {:deletes #{} :file (:entity-relative state) :writes {}}
            (update-edn-file (:entity-relative state) entity-data')))

      :root
      (let [root-data' (dissoc-path (:root-data state) (:segments parsed))]
        (-> {:deletes #{} :file loader/root-filename :writes {}}
            (update-edn-file loader/root-filename root-data'))))))

(defn- apply-plan! [home plan]
  (doseq [relative (:deletes plan)]
    (let [path (config-path home relative)]
      (when (fs/exists? path)
        (fs/delete path))))
  (doseq [[relative content] (:writes plan)]
    (let [path   (config-path home relative)
          parent (fs/parent path)]
      (when parent
        (fs/mkdirs parent))
      (fs/spit path content))))

(defn- validate-plan [home plan]
  (let [source-fs (or fs/*fs* (fs/mem-fs))
        stage-fs  (fs/mem-fs)
        root      (config-root-path home)]
    (copy-tree! source-fs stage-fs root)
    (binding [fs/*fs* stage-fs]
      (apply-plan! home plan)
      (loader/load-config-result {:home home}))))

;; endregion ^^^^^ Plan & apply ^^^^^

;; region ----- Public API -----

(defn set-config
  "Writes `value` at dotted `path` under `home`. See ns docstring for
   return shape."
  [home path value]
  (let [parsed (parse-config-path path)]
    (cond
      (:status parsed)
      {:status   (:status parsed)
       :file     nil
       :errors   []
       :warnings []}

      :else
      (let [current (loader/load-config-result {:home home})]
        (if (and (not (:missing-config? current)) (seq (:errors current)))
          {:status :invalid-config :file nil :errors (:errors current) :warnings (:warnings current)}
          (let [state          (config-state home parsed)
                unknown-key?   (nil? (config-schema/schema-for-data-path path))
                extra-warnings (if unknown-key? [{:key path :value "unknown key"}] [])
                plan           (set-plan parsed state value)
                result         (validate-plan home plan)
                warnings       (concat extra-warnings (:warnings result))]
            (if (seq (:errors result))
              {:status :invalid :file nil :errors (:errors result) :warnings warnings}
              (do
                (apply-plan! home plan)
                {:status :ok :file (:file plan) :errors [] :warnings warnings}))))))))

(defn unset-config
  "Removes dotted `path` under `home`. See ns docstring for return shape."
  [home path]
  (let [parsed (parse-config-path path)]
    (cond
      (:status parsed)
      {:status (:status parsed) :file nil :errors [] :warnings []}

      :else
      (let [current (loader/load-config-result {:home home})]
        (if (and (not (:missing-config? current)) (seq (:errors current)))
          {:status :invalid-config :file nil :errors (:errors current) :warnings (:warnings current)}
          (let [state (config-state home parsed)
                plan  (unset-plan parsed state)]
            (cond
              (nil? plan)
              {:status :not-found :file nil :errors [] :warnings []}

              :else
              (let [result (validate-plan home plan)]
                (if (seq (:errors result))
                  {:status :invalid :file nil :errors (:errors result) :warnings (:warnings result)}
                  (do
                    (apply-plan! home plan)
                    {:status :ok :file (:file plan) :errors [] :warnings (:warnings result)}))))))))))

;; endregion ^^^^^ Public API ^^^^^
