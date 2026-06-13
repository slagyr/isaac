(ns isaac.config.schema.manifest
  "Build a schema-tree augmented with manifest-supplied fields.

   The static config schema in `isaac.config.schema.root` knows about :comms,
   :tools, :providers, and :slash-commands as map-of surfaces, but their
   field-level shapes live in module manifests. This namespace folds the
   manifest schemas into the static tree so that path resolution and
   rendering can walk a single, fully populated schema instead of branching
   between static and manifest code paths.

   Each manifest-supplied field is annotated with :isaac/variant so the
   renderer (or any other consumer) can show which manifest contributed it.

   Hygiene: module authors must use unique field names within a surface.
   If two variants of `:comm` both declare `:token`, the first one wins —
   merging cannot disambiguate without losing the single-key contract."
  (:require [c3kit.apron.schema :as schema]))

(defn- annotate [spec variant]
  (assoc (schema/normalize-spec spec) :isaac/variant variant))

(defn- annotate-fields [fields variant]
  (into {} (map (fn [[k v]] [k (annotate v variant)])) fields))

(defn- variants [module-index kind]
  (for [[_ entry]   module-index
        [name ext]  (get-in entry [:manifest kind])]
    [(clojure.core/name name) ext]))

(defn- merge-into-value-spec [surface-spec entries]
  (let [add-fields (fn [acc [variant ext]]
                     (merge (annotate-fields (:schema ext) variant) acc))
        merged     (reduce add-fields (:schema (:value-spec surface-spec)) entries)]
    (assoc-in surface-spec [:value-spec :schema] merged)))

(defn- keyed-variant-schema [surface-spec entries]
  (let [variant-spec (fn [[variant ext]]
                       [(keyword variant)
                        {:type           :map
                         :isaac/variant  variant
                         :schema         (annotate-fields (:schema ext) variant)}])]
    (assoc surface-spec :schema (into {} (map variant-spec) entries))))

(defn enrich-root
  "Returns `root` with every manifest surface enriched in-place."
  [root module-index]
  (-> root
      ;; :comms needs no enrichment — the comm berth claims [:comms], so
      ;; the composed root already gathers impl fields (with
      ;; :isaac/variant annotations) via :dynamic-schema.
      ;; Phase 7 (isaac-ho18): provider templates and slash-command
      ;; contributions live under :isaac.server/* berths. Phase 6 did
      ;; the same for :tools.
      (update-in [:schema :tools]          keyed-variant-schema   (variants module-index :isaac.server/tools))
      (update-in [:schema :slash-commands] keyed-variant-schema   (variants module-index :isaac.server/slash-commands))))
