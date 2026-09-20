(ns isaac.bean-gate.bean
  "Reads a bean markdown file: its gate lines (feature-baseline, feature-blob,
   main-sha) and its contract lines, plus the file's git history."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [isaac.bean-gate.git :as git]))

;; region Locating

(defn bean-file
  "The bean's file under <root>/.beans, or nil."
  [root id]
  (let [dir (fs/path root ".beans")]
    (when (fs/directory? dir)
      (first (sort (map str (fs/glob dir (str id "--*.md"))))))))

(defn relative-path [root file] (str (fs/relativize (fs/path root) (fs/path file))))

;; endregion

;; region Gate lines

(def ^:private words #(str/split (str/trim %) #"\s+"))

(defn- parse-lines-field [s]
  (mapv parse-long (str/split s #",")))

(defn parse-gate-line
  "Parses one feature-baseline / feature-blob / main-sha line, or nil."
  [line]
  (let [line (str/trim line)]
    (cond
      (str/starts-with? line "feature-baseline:")
      (let [[repo sha] (words (subs line (count "feature-baseline:")))]
        (when (and repo sha) {:kind :baseline :repo repo :sha sha}))

      (str/starts-with? line "feature-blob:")
      (let [[repo path blob lines] (words (subs line (count "feature-blob:")))]
        (when (and repo path blob)
          (cond-> {:kind :blob :repo repo :path path :blob blob}
            lines (assoc :lines (parse-lines-field lines)))))

      (str/starts-with? line "main-sha:")
      (let [[repo sha] (words (subs line (count "main-sha:")))]
        (when (and repo sha) {:kind :main-sha :repo repo :sha sha})))))

(defn- fence? [line] (str/starts-with? (str/triml line) "```"))

(defn- with-fences
  "Pairs each line with whether it sits inside a ``` code fence."
  [lines]
  (second (reduce (fn [[in? acc] l]
                    (if (fence? l)
                      [(not in?) (conj acc [l true])]
                      [in? (conj acc [l in?])]))
                  [false []]
                  lines)))

(defn gate-lines
  "Gate lines outside code fences (a fenced example is documentation, not a gate)."
  [text]
  (keep (fn [[l fenced?]] (when-not fenced? (parse-gate-line l))) (with-fences (str/split-lines text))))

(defn in-force
  "The gate as recorded in text: the last baseline per repo, the last blob per
   (repo, path), and every main-sha per repo."
  [text]
  (let [lines (gate-lines text)]
    {:baselines (reduce (fn [m {:keys [repo] :as l}] (assoc m repo l)) {} (filter #(= :baseline (:kind %)) lines))
     :blobs     (vals (reduce (fn [m {:keys [repo path] :as l}] (assoc m [repo path] l)) {} (filter #(= :blob (:kind %)) lines)))
     :main-shas (reduce (fn [m {:keys [repo sha]}] (update m repo (fnil conj []) sha)) {} (filter #(= :main-sha (:kind %)) lines))}))

(defn gated? [text] (boolean (some #(= :baseline (:kind %)) (gate-lines text))))

;; endregion

;; region Front matter

(defn status
  "The status in the bean's YAML front matter, or nil."
  [text]
  (let [lines (str/split-lines text)]
    (when (= "---" (some-> (first lines) str/trim))
      (->> (rest lines)
           (take-while #(not= "---" (str/trim %)))
           (some #(some-> (re-matches #"(?i)status:\s*['\"]?([^'\"]+?)['\"]?\s*" %) second))))))

(defn completed? [text] (= "completed" (status text)))

;; endregion

;; region Contract lines

(defn- body-lines
  "Lines after the YAML front matter."
  [text]
  (let [lines (str/split-lines text)]
    (if (= "---" (some-> (first lines) str/trim))
      (let [end (->> (map-indexed vector (rest lines))
                     (some (fn [[i l]] (when (= "---" (str/trim l)) (inc i)))))]
        (if end (drop (inc end) lines) lines))
      lines)))

(defn- contract-heading [line]
  (let [t (str/trimr line)]
    (when (or (str/starts-with? t "## Acceptance") (str/starts-with? t "## Exceptions"))
      t)))

(defn contract-lines
  "Set of [section line] for every feature-baseline / feature-blob line and every
   non-blank line under a `## Acceptance…` or `## Exceptions` heading."
  [text]
  (loop [[[line fenced?] & more :as lines] (with-fences (body-lines text)) section nil acc #{}]
    (if (empty? lines)
      acc
      (let [t (str/trimr line)]
        (cond
          (and (not fenced?) (str/starts-with? t "## "))
          (recur more (contract-heading t) acc)

          (and (not fenced?) (#{:baseline :blob} (:kind (parse-gate-line t))))
          (recur more section (conj acc [:gate (str/trim t)]))

          (and section (not (str/blank? t)))
          (recur more section (conj acc [section t]))

          :else (recur more section acc))))))

;; endregion

;; region History

(defn history
  "Versions of the bean file, oldest first: [{:commit sha :text s}…], following
   renames, with the working-tree file last as {:commit nil}."
  [root file]
  (let [rel     (relative-path root file)
        log     (git/git root "log" "--follow" "--name-only" "--format=@@%H" "--" rel)
        entries (->> (str/split-lines log)
                     (remove str/blank?)
                     (reduce (fn [acc l]
                               (if (str/starts-with? l "@@")
                                 (conj acc {:commit (subs l 2)})
                                 (cond-> acc
                                   (and (seq acc) (nil? (:path (peek acc))))
                                   (update (dec (count acc)) assoc :path l))))
                             [])
                     (filter :path))
        commits (reverse entries)
        texts   (mapv (fn [{:keys [commit path]}] {:commit commit :text (git/show-file root commit path)}) commits)
        wt      (slurp file)]
    (if (= wt (:text (peek texts)))
      texts
      (conj texts {:commit nil :text wt}))))

(defn commit-message [root commit] (git/git root "show" "-s" "--format=%B" commit))

;; endregion
