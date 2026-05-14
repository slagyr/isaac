(ns isaac.module.manifest
  (:require
    [c3kit.apron.schema :as schema]
    [clojure.edn :as edn]
    [isaac.logger :as log]))

(def manifest-schema
  {:name   :module/manifest
   :type   :map
   :schema {:id          {:type     :keyword
                          :validate schema/present?
                          :message  "is required"}
            :bootstrap   {:type :ignore}
            :version     {:type     :string
                          :validate schema/present?
                          :message  "must be present"}
            :description {:type :string}
            :requires    {:type   :seq
                          :spec   {:type :keyword}
                          :coerce #(if (nil? %) [] %)}
            :extends     {:type       :map
                          :key-spec   {:type :keyword}
                          :value-spec {:type       :map
                                       :key-spec   {:type :keyword}
                                       :value-spec {:type :any}}}}})

(def ^:private known-keys (set (keys (:schema manifest-schema))))
(def ^:private known-extend-kinds #{:comm :hook :llm/api :provider :slash-command :tool})

(defn- validate-bootstrap! [path manifest]
  (when (and (contains? manifest :bootstrap)
             (some? (:bootstrap manifest))
             (not (symbol? (:bootstrap manifest))))
    (throw (ex-info "bootstrap must be a symbol"
                    {:field :bootstrap :path path}))))

(defn- validate-factories! [path manifest]
  (doseq [[kind extensions] (:extends manifest)
          [extension-id extension] extensions]
    (let [factory (:isaac/factory extension)]
      (when (contains? #{:comm :hook :llm/api :slash-command} kind)
        (when-not (symbol? factory)
          (throw (ex-info "isaac/factory is required"
                          {:field        :isaac/factory
                           :extension-id extension-id
                           :kind         kind
                           :path         path}))))
      (when (and (contains? extension :isaac/factory)
                 (some? factory)
                 (not (symbol? factory)))
        (throw (ex-info "isaac/factory must be a symbol"
                        {:field        :isaac/factory
                         :extension-id extension-id
                         :kind         kind
                         :path         path}))))))

(defn- validate-extend-kinds! [path manifest]
  (doseq [kind (keys (:extends manifest))]
    (when-not (contains? known-extend-kinds kind)
      (throw (ex-info (str "unknown extends kind: " kind)
                      {:kind kind :path path})))))

(defn read-manifest [path]
  (let [raw     (edn/read-string (slurp path))
        unknown (remove known-keys (keys raw))]
    (when (contains? raw :entry)
      (throw (ex-info "entry is not supported; use :bootstrap"
                      {:field :entry :path path})))
    (doseq [k unknown]
      (log/warn :manifest/unknown-key :key k :path path))
    (let [manifest (schema/conform! manifest-schema raw)]
      (validate-extend-kinds! path manifest)
      (validate-bootstrap! path manifest)
      (validate-factories! path manifest)
      manifest)))
