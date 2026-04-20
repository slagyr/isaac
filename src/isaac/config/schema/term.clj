(ns isaac.config.schema.term
  "Render apron schemas as terminal-friendly ANSI-colored text.

   spec->term takes a spec (optionally with options) and returns a string.
   Options: {:color? true/false (default true), :width int (default 80)}."
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.doc :as doc]
            [clojure.string :as s]))

(defn- ansi [color? code text]
  (if color? (str "\033[" code "m" text "\033[0m") text))

(defn- bold       [o t] (ansi (:color? o) "1"    t))
(defn- dim        [o t] (ansi (:color? o) "2"    t))
(defn- yellow     [o t] (ansi (:color? o) "33"   t))
(defn- green      [o t] (ansi (:color? o) "32"   t))
(defn- bold-cyan  [o t] (ansi (:color? o) "1;36" t))
(defn- bold-green [o t] (ansi (:color? o) "1;32" t))

(defn- description [spec]
  (or (:description spec) (:doc spec)))

(defn- type-label [t] (name t))

(declare short-phrase)

(defn- base-type [spec]
  (case (:type spec)
    :map    (let [k (some-> (:key-spec spec) short-phrase)
                  v (some-> (:value-spec spec) short-phrase)]
              (cond
                (and k v) (str "map of " k " → " v)
                v         (str "map → " v)
                k         (str "map of " k)
                :else     "map"))
    :seq    (str "seq of " (base-type (schema/normalize-spec (:spec spec))))
    :one-of (str "one of: " (s/join ", " (map #(base-type (schema/normalize-spec %)) (:specs spec))))
    (type-label (:type spec))))

(defn- short-phrase [spec]
  (let [spec (schema/normalize-spec spec)]
    (if (:name spec)
      (name (:name spec))
      (base-type spec))))

(defn- plain-type-phrase [spec]
  (let [spec (schema/normalize-spec spec)]
    (cond
      (:name spec)          (str (base-type spec) " → " (name (:name spec)))
      (= :seq (:type spec)) (str "seq of " (plain-type-phrase (:spec spec)))
      :else                 (base-type spec))))

(defn- map-schema [spec]
  (when (= :map (:type spec))
    (some-> (:schema spec) (dissoc :*))))

(defn- pad-right [text width]
  (let [needed (- width (count text))]
    (if (pos? needed)
      (str text (apply str (repeat needed " ")))
      text)))

(defn- path-str [segments]
  (when (seq segments)
    (s/join "." segments)))

(defn- header-with-path [opts header path]
  (if (and (:paths? opts) path)
    (str header "  " (green opts path))
    header))

(defn- wrap [text width]
  (loop [words (s/split (or text "") #"\s+") line "" out []]
    (cond
      (empty? words) (if (seq line) (conj out line) out)
      :else
      (let [w    (first words)
            cand (if (seq line) (str line " " w) w)]
        (if (<= (count cand) width)
          (recur (rest words) cand out)
          (recur (rest words) w (conj out line)))))))

(defn- colored-short [opts spec]
  (let [spec (schema/normalize-spec spec)]
    (if (:name spec)
      (bold-green opts (name (:name spec)))
      (dim opts (base-type spec)))))

(defn- colored-type-phrase [opts spec]
  (let [spec (schema/normalize-spec spec)]
    (cond
      (:name spec)
      (str (dim opts (base-type spec))
           " " (green opts "→")
           " " (bold-green opts (name (:name spec))))

      (and (= :map (:type spec))
           (or (:key-spec spec) (:value-spec spec)))
      (let [k (some->> (:key-spec spec)   (colored-short opts))
            v (some->> (:value-spec spec) (colored-short opts))]
        (cond
          (and k v) (str (dim opts "map of ") k " " (green opts "→") " " v)
          v         (str (dim opts "map ") (green opts "→") " " v)
          k         (str (dim opts "map of ") k)))

      (= :seq (:type spec))
      (str (dim opts "seq of ") (colored-type-phrase opts (:spec spec)))

      :else
      (dim opts (base-type spec)))))

(defn- field-block [name-width required path-prefix [k raw-spec] opts]
  (let [spec      (schema/normalize-spec raw-spec)
         padded-nm (pad-right (name k) name-width)
         header    (header-with-path opts
                                     (str "  " (bold-cyan opts padded-nm)
                                          "  " (colored-type-phrase opts spec)
                                          (when (or (contains? required k) (:required? spec)) (yellow opts " *required")))
                                     (path-str (conj path-prefix (name k))))
         indent    (apply str (repeat (+ 4 name-width) " "))
         desc-w    (max 20 (- (:width opts) (count indent)))
         desc      (when-let [d (description spec)]
                     (map #(str indent %) (wrap d desc-w)))
        default   (when (contains? spec :default)
                    [(str indent (green opts (str "default: " (pr-str (:default spec)))))])
        ex        (when (contains? spec :example)
                    [(str indent (green opts (str "example: " (pr-str (:example spec)))))])]
    (s/join "\n" (concat [header] desc default ex))))

(defn- object-section [schema-map opts path-prefix]
  (let [required (set (doc/required-fields schema-map))
         entries  (sort-by (comp name key) schema-map)
         name-w   (apply max 4 (map #(count (name (key %))) entries))]
    (s/join "\n\n" (map #(field-block name-w required path-prefix % opts) entries))))

(defn- leaf-block [opts spec]
  (let [header (header-with-path opts
                                 (str (bold-cyan opts "type")
                                      "  "
                                      (colored-type-phrase opts spec)
                                      (when (:required? spec) (yellow opts " *required")))
                                 (:path opts))
         indent "  "
         desc-w (max 20 (- (:width opts) (count indent)))
         desc   (when-let [d (description spec)]
                  (map #(str indent %) (wrap d desc-w)))
        default (when (contains? spec :default)
                  [(str indent (green opts (str "default: " (pr-str (:default spec)))))])
        ex     (when (contains? spec :example)
                 [(str indent (green opts (str "example: " (pr-str (:example spec)))))])]
    (s/join "\n" (concat [header] desc default ex))))

(defn- child-specs [spec path-prefix]
  (let [spec (schema/normalize-spec spec)]
    (case (:type spec)
      :map    (concat (for [[k child] (dissoc (:schema spec) :*)]
                        [child (conj path-prefix (name k))])
                      (when-let [v (:value-spec spec)]
                        [[v (conj path-prefix "_")]])
                      (when-let [k (:key-spec spec)]
                        [[k (conj path-prefix "_key")]]))
      :seq    [[(:spec spec) (conj path-prefix "0")]]
      :one-of (map #(vector % path-prefix) (:specs spec))
      [])))

(defn- collect-named [spec acc path-prefix]
  (let [spec (schema/normalize-spec spec)
         acc  (if (and (:name spec) (not (contains? acc (name (:name spec)))))
                (assoc acc (name (:name spec)) {:path path-prefix :spec spec})
                acc)]
    (reduce (fn [acc [child child-path]] (collect-named child acc child-path))
            acc
            (child-specs spec path-prefix))))

(defn- section [opts title body]
  (let [rule-width (min 60 (max 10 (- (:width opts) 4)))
        rule       (apply str (repeat rule-width "─"))]
    (str (bold opts title) "\n" (dim opts rule) "\n" body)))

(def ^:private default-opts {:color? true :deep? true :paths? true :width 80})

(defn spec->term
  ([spec] (spec->term spec {}))
  ([spec opts]
   (let [opts       (merge default-opts opts)
         root-spec  (schema/normalize-spec spec)
         sm         (map-schema root-spec)
         named      (collect-named root-spec {} [])
         deep?      (:deep? opts)
          named-subs (when deep?
                       (for [[nm {:keys [path spec]}] (sort-by key named)
                             :let   [inner-sm (map-schema spec)]
                             :when  (and inner-sm
                                         (not= nm (some-> (:name root-spec) name)))]
                         (section opts (str nm " config schema") (object-section inner-sm opts path))))
          root-title (or (:title opts) (when (:name root-spec) (name (:name root-spec))) "Schema")
          root       (cond
                       sm   (section opts root-title (object-section sm opts []))
                       (:title opts) (section opts (:title opts) (leaf-block opts root-spec))
                       :else (leaf-block opts root-spec))]
      (s/join "\n\n" (cons root named-subs)))))
