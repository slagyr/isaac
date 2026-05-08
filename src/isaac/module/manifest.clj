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
            :entry       {:type     :ignore
                          :validate some?
                          :message  "is required"}
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
(def ^:private known-extend-kinds #{:comm :provider :slash-command :tool})

(defn- validate-extend-kinds! [path manifest]
  (doseq [kind (keys (:extends manifest))]
    (when-not (contains? known-extend-kinds kind)
      (throw (ex-info (str "unknown extends kind: " kind)
                      {:kind kind :path path})))))

(defn read-manifest [path]
  (let [raw     (edn/read-string (slurp path))
         unknown (remove known-keys (keys raw))]
    (doseq [k unknown]
      (log/warn :manifest/unknown-key :key k :path path))
    (let [manifest (schema/conform! manifest-schema raw)]
      (validate-extend-kinds! path manifest)
      manifest)))
