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

(defn- field-block [name-width required [k raw-spec] opts]
  (let [spec      (schema/normalize-spec raw-spec)
        padded-nm (pad-right (name k) name-width)
        header    (str "  " (bold-cyan opts padded-nm)
                       "  " (colored-type-phrase opts spec)
                       (when (contains? required k) (yellow opts " *required")))
        indent    (apply str (repeat (+ 4 name-width) " "))
        desc-w    (max 20 (- (:width opts) (count indent)))
        desc      (when-let [d (:description spec)]
                    (map #(str indent %) (wrap d desc-w)))
        ex        (when (contains? spec :example)
                    [(str indent (green opts (str "example: " (pr-str (:example spec)))))])]
    (s/join "\n" (concat [header] desc ex))))

(defn- object-section [schema-map opts]
  (let [required (set (doc/required-fields schema-map))
        entries  (sort-by (comp name key) schema-map)
        name-w   (apply max 4 (map #(count (name (key %))) entries))]
    (s/join "\n\n" (map #(field-block name-w required % opts) entries))))

(defn- leaf-block [opts spec]
  (let [header (colored-type-phrase opts spec)
        indent "  "
        desc-w (max 20 (- (:width opts) (count indent)))
        desc   (when-let [d (:description spec)]
                 (map #(str indent %) (wrap d desc-w)))
        ex     (when (contains? spec :example)
                 [(str indent (green opts (str "example: " (pr-str (:example spec)))))])]
    (s/join "\n" (concat [header] desc ex))))

(defn- collect-named [spec acc]
  (let [spec (schema/normalize-spec spec)
        acc  (if (:name spec) (assoc acc (name (:name spec)) spec) acc)
        kids (case (:type spec)
               :map    (concat (vals (dissoc (:schema spec) :*))
                               (when-let [k (:key-spec spec)]   [k])
                               (when-let [v (:value-spec spec)] [v]))
               :seq    [(:spec spec)]
               :one-of (:specs spec)
               [])]
    (reduce (fn [acc k] (collect-named k acc)) acc kids)))

(defn- section [opts title body]
  (let [rule-width (min 60 (max 10 (- (:width opts) 4)))
        rule       (apply str (repeat rule-width "─"))]
    (str (bold opts title) "\n" (dim opts rule) "\n" body)))

(def ^:private default-opts {:color? true :width 80 :deep? true})

(defn spec->term
  ([spec] (spec->term spec {}))
  ([spec opts]
   (let [opts       (merge default-opts opts)
         spec       (schema/normalize-spec spec)
         sm         (map-schema spec)
         named      (collect-named spec {})
         deep?      (:deep? opts)
         named-subs (when deep?
                      (for [[nm s] (sort-by key named)
                            :let   [inner-sm (map-schema s)]
                            :when  (and inner-sm
                                        (not= nm (some-> (:name spec) name)))]
                        (section opts nm (object-section inner-sm opts))))
         root       (cond
                      sm   (section opts
                                    (if (:name spec) (name (:name spec)) "Schema")
                                    (object-section sm opts))
                      :else (leaf-block opts spec))]
     (s/join "\n\n" (cons root named-subs)))))
