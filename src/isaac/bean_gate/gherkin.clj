(ns isaac.bean-gate.gherkin
  "Splits a .feature file into comparable blocks: the feature header, each
   Background, and each scenario (with the tag and comment lines before it)."
  (:require [clojure.string :as str]))

(def ^:private block-keywords
  ["Feature:" "Background:" "Scenario Outline:" "Scenario Template:" "Scenario:" "Example:" "Rule:"])

(defn- keyword-of [trimmed]
  (when-not (str/starts-with? trimmed "Examples:")
    (some #(when (str/starts-with? trimmed %) %) block-keywords)))

(defn- kind-of [kw]
  (case kw
    "Feature:"    :feature
    "Background:" :background
    "Rule:"       :rule
    :scenario))

(defn tag-line? [trimmed] (str/starts-with? trimmed "@"))

(defn- comment-line? [trimmed] (str/starts-with? trimmed "#"))

(defn tags [trimmed] (set (str/split trimmed #"\s+")))

(defn strip-wip
  "A tag line with the @wip token removed (whitespace collapsed); nil when
   nothing remains. Non-tag lines come back unchanged."
  [trimmed]
  (if (tag-line? trimmed)
    (let [kept (remove #(= "@wip" %) (str/split trimmed #"\s+"))]
      (when (seq kept) (str/join " " kept)))
    trimmed))

(defn- new-block [kw line-no prefix line]
  {:kind (kind-of kw) :title line :line line-no :lines (conj prefix line)})

(defn blocks
  "Parses text into blocks. Each block: {:kind :title :line :lines}, where :line
   is the 1-based line of its keyword and :lines are its trimmed, non-blank lines
   (tags/comments that precede the keyword included)."
  [text]
  (let [flush (fn [blocks pending]
                (if (empty? pending)
                  blocks
                  (if (seq blocks)
                    (update blocks (dec (count blocks)) update :lines into pending)
                    [{:kind :preamble :title "(preamble)" :line 1 :lines pending}])))]
    (loop [[[n raw] & more :as lines] (map-indexed (fn [i l] [(inc i) l]) (str/split-lines (or text "")))
           blocks  []
           pending []]
      (if (empty? lines)
        (flush blocks pending)
        (let [t (str/trim raw)]
          (cond
            (str/blank? t)                           (recur more blocks pending)
            (or (tag-line? t) (comment-line? t))     (recur more blocks (conj pending t))
            :else
            (if-let [kw (keyword-of t)]
              (recur more (conj blocks (new-block kw n pending t)) [])
              (recur more (-> (flush blocks pending)
                              (as-> b (if (seq b) b [{:kind :preamble :title "(preamble)" :line 1 :lines []}]))
                              (as-> b (update b (dec (count b)) update :lines conj t)))
                     []))))))))

(defn own-tags
  "Tags on the tag lines directly attached to this block."
  [block]
  (->> (:lines block) (take-while #(or (tag-line? %) (comment-line? %))) (filter tag-line?) (mapcat tags) set))

(defn wip? [block] (contains? (own-tags block) "@wip"))

(defn normalized
  "The block's lines with @wip stripped — the form that is compared."
  [block]
  (vec (keep strip-wip (:lines block))))

(defn header [blocks] (first (filter #(= :feature (:kind %)) blocks)))

(defn scenarios [blocks] (filter #(= :scenario (:kind %)) blocks))
