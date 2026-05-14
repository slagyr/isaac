(ns isaac.module.manifest
  (:require
    [c3kit.apron.schema :as schema]
    [clojure.edn :as edn]
    [isaac.logger :as log]))

(def ^:private kind-entry-spec
  {:type       :map
   :key-spec   {:type :keyword}
   :value-spec {:type :any}})

(def manifest-schema
  {:name   :module/manifest
   :type   :map
   :schema {:id            {:type     :keyword
                            :validate schema/present?
                            :message  "is required"}
            :bootstrap     {:type :ignore}
            :route         {:type :ignore}
            :version       {:type     :string
                            :validate schema/present?
                            :message  "must be present"}
            :description   {:type :string}
            :comm          kind-entry-spec
            :llm/api       kind-entry-spec
            :provider      kind-entry-spec
            :slash-command kind-entry-spec
            :tool          kind-entry-spec
            :hook          kind-entry-spec}})

(def ^:private known-meta-keys #{:id :version :description :bootstrap :route})
(def ^:private known-extend-kinds #{:comm :hook :llm/api :provider :slash-command :tool})
(def ^:private known-keys (into known-meta-keys known-extend-kinds))

(defn- validate-bootstrap! [path manifest]
  (when (and (contains? manifest :bootstrap)
             (some? (:bootstrap manifest))
             (not (symbol? (:bootstrap manifest))))
    (throw (ex-info "bootstrap must be a symbol"
                    {:field :bootstrap :path path}))))

(defn- validate-v2-entries! [path manifest]
  (doseq [kind known-extend-kinds
          [extension-id extension] (get manifest kind)]
    (when (contains? extension :isaac/factory)
      (throw (ex-info ":isaac/factory is no longer supported; use :factory"
                      {:field :isaac/factory :extension-id extension-id
                       :kind kind :path path})))
    (when (contains? #{:comm :llm/api :slash-command :tool :hook} kind)
      (when-not (symbol? (:factory extension))
        (throw (ex-info ":factory is required and must be a symbol"
                        {:field :factory :extension-id extension-id
                         :kind kind :path path}))))
    (when (= :provider kind)
      (when-not (map? (:template extension))
        (throw (ex-info ":template is required for provider entries"
                        {:field :template :extension-id extension-id
                         :kind kind :path path}))))))

(defn- valid-route-key? [route-key]
  (and (vector? route-key)
       (= 2 (count route-key))
       (keyword? (first route-key))
       (string? (second route-key))))

(defn- validate-routes! [path manifest]
  (doseq [[route-key handler] (:route manifest)]
    (when-not (valid-route-key? route-key)
      (throw (ex-info "route key must be [method path]"
                      {:field :route :path path :route-key route-key})))
    (when-not (symbol? handler)
      (throw (ex-info "route handler must be a symbol"
                      {:field :route :path path :route-key route-key :handler handler})))))

(defn read-manifest [path]
  (let [raw (edn/read-string (slurp path))]
    (when (contains? raw :entry)
      (throw (ex-info "entry is not supported; use :bootstrap"
                      {:field :entry :path path})))
    (when (contains? raw :extends)
      (throw (ex-info "use top-level kind keys instead of :extends"
                      {:field :extends :path path})))
    (when (contains? raw :requires)
      (throw (ex-info ":requires is no longer supported in v2 manifests"
                      {:field :requires :path path})))
    (doseq [[k v] raw]
      (cond
        (contains? known-keys k) nil
        (map? v) (throw (ex-info (str "unknown extension kind: " k)
                                 {:kind k :path path}))
        :else    (log/warn :manifest/unknown-key :key k :path path)))
    (let [manifest (schema/conform! manifest-schema raw)]
      (validate-bootstrap! path manifest)
      (validate-v2-entries! path manifest)
      (validate-routes! path manifest)
      manifest)))
