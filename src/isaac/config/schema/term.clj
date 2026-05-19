(ns isaac.config.schema.term
  "Render apron schemas as terminal-friendly ANSI-colored text.

   spec->term takes a spec (optionally with options) and returns a string.
   Options: {:color? true/false (default true), :paths? true/false (default true),
             :path-prefix vector of segments, :title string, :width int (default 80)}."
  (:require [c3kit.apron.schema :as schema]
            [c3kit.apron.schema.doc :as doc]
            [clojure.string :as s]))

(defn- ansi [color? code text]
  (if color? (str "\033[" code "m" text "\033[0m") text))

(defn- bold       [o t] (ansi (:color? o) "1"    t))
(defn- dim        [o t] (ansi (:color? o) "2"    t))
(defn- yellow     [o t] (ansi (:color? o) "33"   t))
(defn- magenta    [o t] (ansi (:color? o) "35"   t))
(defn- bold-cyan  [o t] (ansi (:color? o) "1;36" t))
(defn- bold-green [o t] (ansi (:color? o) "1;32" t))

(defn- type-label [t] (name t))

(declare short-phrase)
(declare root-title)

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

(defn- colored-short [opts spec]
  (let [spec (schema/normalize-spec spec)]
    (if (:name spec)
      (bold-green opts (name (:name spec)))
      (dim opts (base-type spec)))))

(defn- colored-type-phrase [opts spec]
  (let [spec (schema/normalize-spec spec)]
    (cond
      (and (= :map (:type spec))
           (or (:key-spec spec) (:value-spec spec)))
      (let [k (some->> (:key-spec spec)   (colored-short opts))
            v (some->> (:value-spec spec) (colored-short opts))]
        (cond
          (and k v) (str (dim opts "map of ") k " " (dim opts "→") " " v)
          v         (str (dim opts "map ") (dim opts "→") " " v)
          k         (str (dim opts "map of ") k)))

      (:name spec)
      (str (dim opts (base-type spec))
           " " (dim opts "→")
           " " (bold-green opts (name (:name spec))))

      (= :seq (:type spec))
      (str (dim opts "seq of ") (colored-type-phrase opts (:spec spec)))

      :else
      (dim opts (base-type spec)))))

(defn- pad-right [text width]
  (let [needed (- width (count text))]
    (if (pos? needed)
      (str text (apply str (repeat needed " ")))
      text)))

(defn- path-str [segments]
  (when (seq segments)
    (s/join "." segments)))

(defn- bracketed-path [opts path]
  (str (dim opts "[") (magenta opts path) (dim opts "]")))

(defn- header-with-path [opts header path]
  (if (and (:paths? opts) path)
    (str header "  " (bracketed-path opts path))
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

(defn- options-line [opts spec indent]
  (when-let [src (:options-from spec)]
    (when-let [resolver (get (:options-resolvers opts) src)]
      (let [vals (sort (map str (resolver)))]
        (when (seq vals)
          [(str indent (bold-green opts (str "options: " (s/join ", " vals))))])))))

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
        desc      (when-let [d (:description spec)]
                    (map #(str indent %) (wrap d desc-w)))
        default   (when (contains? spec :default)
                    [(str indent (bold-green opts (str "default: " (pr-str (:default spec)))))])
        ex        (when (contains? spec :example)
                    [(str indent (bold-green opts (str "example: " (pr-str (:example spec)))))])
        options   (options-line opts spec indent)]
    (s/join "\n" (concat [header] desc default ex options))))

(defn- object-section [schema-map opts path-prefix]
  (let [required (set (doc/required-fields schema-map))
        entries  (sort-by (comp name key) schema-map)
        name-w   (apply max 4 (map #(count (name (key %))) entries))]
    (s/join "\n\n" (map #(field-block name-w required path-prefix % opts) entries))))

(defn- description-lines [description opts]
  (when description
    (let [indent "  "
          desc-w (max 20 (- (:width opts) (count indent)))]
      (map #(str indent %) (wrap description desc-w)))))

(defn- collection-row [opts path-prefix label label-w sub-spec sub-segment]
  (when sub-spec
    (header-with-path opts
                      (str "  " (bold-cyan opts (pad-right label label-w))
                           "  " (colored-type-phrase opts sub-spec))
                      (path-str (conj path-prefix sub-segment)))))

(defn- collection-section [spec opts path-prefix]
  (let [label-w   5 ;; widest label is "value"
        key-row   (collection-row opts path-prefix "key"   label-w (:key-spec spec)   "key")
        value-row (collection-row opts path-prefix "value" label-w (:value-spec spec) "value")
        header    (str "  " (dim opts "map of"))
        desc      (description-lines (:description spec) opts)
        rows      (cond-> [header]
                    key-row    (conj key-row)
                    value-row  (conj value-row)
                    (seq desc) (conj "")
                    (seq desc) (into desc))]
    (s/join "\n" rows)))

(defn- leaf-block [opts spec path-prefix]
  (let [indent    "  "
        desc-w    (max 20 (- (:width opts) (count indent)))
        type-line (header-with-path opts
                                    (str indent
                                         (colored-type-phrase opts spec)
                                         (when (:required? spec) (yellow opts " *required")))
                                    (path-str path-prefix))
        options   (options-line opts spec indent)
        default   (when (contains? spec :default)
                    (str indent (bold-green opts (str "default: " (pr-str (:default spec))))))
        ex        (when (contains? spec :example)
                    (str indent (bold-green opts (str "example: " (pr-str (:example spec))))))
        desc      (when-let [d (:description spec)]
                    (map #(str indent %) (wrap d desc-w)))
        trailing  (concat options (remove nil? [default ex]) desc)]
    (s/join "\n" (cons type-line trailing))))

(defn- section [opts title body]
  (let [rule-width (min 60 (max 10 (- (:width opts) 4)))
        rule       (apply str (repeat rule-width "─"))]
    (str "\n" (bold opts title) "\n" (dim opts rule) "\n\n" body)))

(defn- lookup [m k]
  (or (get m k)
      (get m (keyword k))
      (when (keyword? k) (get m (name k)))))

(defn- lookup-in* [m ks]
  (reduce lookup m ks))

(defn- dynamic-surface [path-prefix]
  (let [[surface subject & tail] path-prefix]
    (when (and surface subject)
      {:field-path tail
       :subject    subject
       :surface    surface})))

(defn- manifest-entries [module-index manifest-key]
  (->> module-index
       (mapcat (fn [[module-id entry]]
                 (for [[variant extension] (get-in entry [:manifest manifest-key])]
                   {:extension extension
                    :module-id  module-id
                    :variant    (name variant)})))
       (sort-by :variant)))

(defn- variant-type [surface config subject]
  (case surface
    "comms"          (some-> (lookup-in* config [:comms subject :type]) str)
    "providers"      (some-> (or (lookup-in* config [:providers subject :type])
                                  (lookup-in* config [:providers subject :from]))
                              str)
    "slash-commands" subject
    "tools"          subject
    nil))

(defn- selected-manifest-entries [{:keys [config declared-module-ids module-index]} {:keys [surface subject]}]
  (let [declared?     (> (count declared-module-ids) 1)
        manifest-key  ({"comms" :comm "providers" :provider "slash-commands" :slash-commands "tools" :tools} surface)
        entries       (manifest-entries module-index manifest-key)
        subject-value? (= "value" subject)]
    (case surface
      "comms"          (cond
                          (and subject-value? declared?) entries
                          (and subject-value? (not declared?)) []
                          :else (if-let [slot-type (variant-type surface config subject)]
                                  (filter #(= slot-type (:variant %)) entries)
                                  (if declared? entries [])))
      "providers"      (cond
                          (and subject-value? declared?) entries
                          (and subject-value? (not declared?)) []
                          :else (if-let [provider-type (variant-type surface config subject)]
                                  (filter #(= provider-type (:variant %)) entries)
                                  []))
      "slash-commands" (if subject-value? [] (filter #(= subject (:variant %)) entries))
      "tools"          (if subject-value? entries (filter #(= subject (:variant %)) entries))
      [])))

(defn- manifest-field-spec [field-schema field-path]
  (when (seq field-path)
    (let [[field-name & tail] field-path]
      (when-let [spec (lookup field-schema field-name)]
        (if (seq tail)
          (when (= :map (:type (schema/normalize-spec spec)))
            (manifest-field-spec (:schema (schema/normalize-spec spec)) tail))
          spec)))))

(defn- field-title [variant field-path]
  (str "[" variant "] " (s/join "." field-path)))

(defn- type-line [spec]
  (str "type: " (base-type (schema/normalize-spec spec))))

(defn- manifest-description-lines [opts spec]
  (when-let [description (:description (schema/normalize-spec spec))]
    (let [indent "  "
          desc-w (max 20 (- (:width opts) (count indent)))]
      (map #(str indent %) (wrap description desc-w)))))

(defn- manifest-field-block [opts variant field-path spec]
  (s/join "\n" (concat [(field-title variant field-path)
                         (str "  " (type-line spec))]
                        (manifest-description-lines opts spec))))

(defn- manifest-entry-blocks [opts entries]
  (->> entries
       (mapcat (fn [{:keys [extension variant]}]
                 (for [[field-name spec] (sort-by key (:schema extension))]
                   (manifest-field-block opts variant [(name field-name)] spec))))
       (remove s/blank?)
       vec))

(defn- base-field-block [opts [field-name raw-spec]]
  (let [spec    (schema/normalize-spec raw-spec)
        indent  "  "
        options (options-line opts spec indent)]
    (s/join "\n"
            (concat [(str ":" (name field-name))
                     (str indent (type-line spec))]
                    (manifest-description-lines opts spec)
                    options))))

(defn- base-object-body [root-spec opts]
  (when (seq (dissoc (:schema root-spec) :*))
    (->> (sort-by key (dissoc (:schema root-spec) :*))
         (map #(base-field-block opts %))
         (s/join "\n\n"))))

(defn- manifest-render [opts root-spec path-prefix]
  (when-let [{:keys [field-path subject surface] :as surface-path} (dynamic-surface path-prefix)]
    (let [selected  (selected-manifest-entries opts surface-path)
          title     (or (:title opts) (root-title opts root-spec path-prefix))
          static?   (contains? (set (keys (:schema root-spec))) (keyword (first field-path)))]
      (cond
        (and (empty? selected) (seq field-path))
        nil

        (and (= "value" subject) (seq field-path) static?)
        nil

        (and (empty? selected) (not= surface "comms"))
        nil

        (empty? field-path)
        (let [base-body     (base-object-body root-spec opts)
              manifest-body (->> (manifest-entry-blocks opts selected)
                                 (s/join "\n\n"))
              parts         (remove s/blank? [base-body manifest-body])]
          (when (seq parts)
            (section opts title (s/join "\n\n" parts))))

        :else
        (let [matches (->> selected
                           (keep (fn [{:keys [extension variant]}]
                                   (when-let [spec (manifest-field-spec (:schema extension) field-path)]
                                     (manifest-field-block opts variant field-path spec)))))]
          (when (seq matches)
            (section opts title (s/join "\n\n" matches))))))))

(defn- shape [spec]
  (cond
    (and (= :map (:type spec)) (:schema spec))                           :object
    (and (= :map (:type spec)) (or (:value-spec spec) (:key-spec spec))) :collection
    :else                                                                :leaf))

(defn- root-title [opts spec path-prefix]
  (let [path        (path-str path-prefix)
        entity      (some-> (:name spec) name)
        collection? (= :collection (shape spec))
        path        (or path entity)
        suffix      (cond
                      (and collection? (not entity)) "map"
                      entity                         entity)
        parts       (remove s/blank? [(bracketed-path opts path) suffix "schema"])]
    (s/join " " parts)))

(defn- child-specs [spec path-prefix]
  (let [spec (schema/normalize-spec spec)]
    (case (:type spec)
      :map    (concat (for [[k child] (dissoc (:schema spec) :*)]
                        [child (conj path-prefix (name k))])
                      (when-let [v (:value-spec spec)]
                        [[v (conj path-prefix "value")]])
                      (when-let [k (:key-spec spec)]
                        [[k (conj path-prefix "key")]]))
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

(defn- render-section [opts spec path-prefix]
  (let [title (root-title opts spec path-prefix)
        body  (case (shape spec)
                :object     (object-section (dissoc (:schema spec) :*) opts path-prefix)
                :collection (collection-section spec opts path-prefix)
                :leaf       (leaf-block opts spec path-prefix))]
    (section opts title body)))

(def ^:private default-opts {:color? true :paths? true :width 80})

(defn spec->term
  ([spec] (spec->term spec {}))
  ([spec opts]
   (let [opts        (merge default-opts opts)
         root-spec   (schema/normalize-spec spec)
         path-prefix (vec (:path-prefix opts))
         custom      (manifest-render opts root-spec path-prefix)]
     (if custom
       custom
       (when-not (:fallback? opts)
       (let [root-title (or (:title opts) (root-title opts root-spec path-prefix))
             root-body  (case (shape root-spec)
                          :object     (object-section (dissoc (:schema root-spec) :*) opts path-prefix)
                          :collection (collection-section root-spec opts path-prefix)
                          :leaf       (leaf-block opts root-spec path-prefix))
             root-sec   (section opts root-title root-body)
             named-subs (when (:deep? opts)
                          (let [root-name (some-> (:name root-spec) name)]
                            (for [[nm {:keys [path spec]}] (sort-by key (collect-named root-spec {} path-prefix))
                                  :when  (not= nm root-name)]
                              (render-section opts spec path))))]
         (s/join "\n\n" (cons root-sec named-subs))))))))
