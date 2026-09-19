(ns isaac.bean-gate
  "Planner baseline + contract/feature gate. Pure babashka + git CLI."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as process]
    [clojure.string :as str]))

(def usage
  (str "Usage:\n"
       "  bb bean-gate baseline <bean-id> <repo>:<path>[:<line>…] … [--dir repo=path] [--isaac path]\n"
       "  bb bean-gate verify <bean-id> [--dir repo=path] [--ref repo=ref] [--isaac path]\n"
       "  bb bean-gate --help\n"))

(defn- sh
  ([opts & args]
   (let [dir  (or (:dir opts) ".")
         r    (apply process/shell {:dir (str dir) :out :string :err :string :continue true} args)]
     (assoc r :out (or (:out r) "") :err (or (:err r) "")))))

(defn- git
  ([dir & args]
   (apply sh {:dir dir} (into ["git"] args))))

(defn- git-ok [dir & args]
  (let [r (apply git dir args)]
    (when-not (zero? (:exit r))
      (throw (ex-info (str "git failed " args "\n" (:err r) (:out r))
                      {:exit (:exit r) :out (:out r) :err (:err r)})))
    (str/trim-newline (:out r))))

(defn- fail [exit message]
  {:exit exit :message (str/trim-newline (str message))})

(defn- ok [message]
  {:exit 0 :message (str/trim-newline (str message))})

(defn- parse-kv [s]
  (let [i (str/index-of s "=")]
    (when i
      [(subs s 0 i) (subs s (inc i))])))

(defn- parse-target [s]
  (let [colon (str/index-of s ":")]
    (when colon
      (let [repo (subs s 0 colon)
            rest (subs s (inc colon))
            ;; path may contain no further colon except line list after last colon
            ;; form: path or path:line,line
            m    (re-matches #"(.+):(\d+(?:,\d+)*)$" rest)]
        (if m
          {:repo repo :path (nth m 1) :lines (mapv parse-long (str/split (nth m 2) #","))}
          {:repo repo :path rest :lines nil})))))

(defn- parse-args [args]
  (loop [xs    (vec args)
         acc   {:positional [] :dirs {} :refs {} :isaac nil}]
    (if-let [a (first xs)]
      (cond
        (or (= a "--help") (= a "-h"))
        (assoc acc :help true)

        (= a "--dir")
        (let [[k v] (parse-kv (second xs))]
          (recur (subvec xs 2) (update acc :dirs assoc k v)))

        (= a "--ref")
        (let [[k v] (parse-kv (second xs))]
          (recur (subvec xs 2) (update acc :refs assoc k v)))

        (= a "--isaac")
        (recur (subvec xs 2) (assoc acc :isaac (second xs)))

        (str/starts-with? a "--")
        (assoc acc :error (str "unknown flag: " a))

        :else
        (recur (subvec xs 1) (update acc :positional conj a)))
      acc)))

(defn- find-bean-file [isaac-dir bean-id]
  (let [dir  (fs/file isaac-dir ".beans")
        hits (when (fs/directory? dir)
               (->> (fs/list-dir dir)
                    (filter #(and (str/starts-with? (fs/file-name %) (str bean-id "--"))
                                  (str/ends-with? (fs/file-name %) ".md")))
                    (map str)
                    vec))]
    (first hits)))

(defn- module-dir [isaac-dir dirs repo]
  (or (get dirs repo)
      (str (fs/absolutize (fs/path isaac-dir ".." repo)))))

(defn- fetch! [mod-dir]
  (git mod-dir "fetch" "origin"))

(defn- origin-main [mod-dir]
  (git-ok mod-dir "rev-parse" "origin/main"))

(defn- blob-at [mod-dir sha path]
  (let [r (git mod-dir "rev-parse" (str sha ":" path))]
    (when (zero? (:exit r))
      (str/trim-newline (:out r)))))

(defn- show-at [mod-dir sha path]
  (let [r (git mod-dir "show" (str sha ":" path))]
    (when (zero? (:exit r))
      (:out r))))

(defn- ancestor? [mod-dir anc desc]
  (zero? (:exit (git mod-dir "merge-base" "--is-ancestor" anc desc))))

(defn- trim-right [s]
  (str/replace s #"\s+$" ""))

(defn- tag-line? [s]
  (boolean (re-matches #"\s*@\S.*" s)))

(defn- scenario-kw? [s]
  (boolean (re-matches #"\s*Scenario(?: Outline)?:.*" s)))

(defn- background-kw? [s]
  (boolean (re-matches #"\s*Background:.*" s)))

(defn- feature-kw? [s]
  (boolean (re-matches #"\s*Feature:.*" s)))

(defn- drop-wip-token [line]
  (let [tokens (->> (str/split (str/trim line) #"\s+")
                    (remove #(= "@wip" %)))
        kept   (str/join " " tokens)]
    (if (str/blank? kept) nil kept)))

(defn- normalize-lines [lines]
  (->> lines
       (map trim-right)
       (remove str/blank?)
       (keep (fn [l]
               (if (tag-line? l)
                 (drop-wip-token l)
                 l)))
       vec))

(defn- numbered [text]
  (map-indexed (fn [i l] {:n (inc i) :text l})
               (str/split-lines (or text ""))))

(defn- take-until [pred xs]
  (split-with (complement pred) xs))

(defn- parse-feature [text]
  (let [rows (numbered text)]
    (loop [xs     rows
           header []
           bg     nil
           scens  []]
      (if-let [{:keys [text] :as row} (first xs)]
        (cond
          (and (empty? header) (or (tag-line? text) (feature-kw? text) (str/blank? text)
                                   (str/starts-with? (str/trim text) "#")))
          (if (feature-kw? text)
            (let [[desc rest] (take-until #(or (background-kw? (:text %))
                                               (scenario-kw? (:text %))
                                               (tag-line? (:text %)))
                                          (rest xs))]
              (recur rest (into (conj header row) desc) bg scens))
            (recur (rest xs) (conj header row) bg scens))

          (background-kw? text)
          (let [[body rest] (take-until #(or (scenario-kw? (:text %)) (tag-line? (:text %)))
                                        (rest xs))]
            (recur rest header (into [row] body) scens))

          (or (scenario-kw? text) (tag-line? text) (str/starts-with? (str/trim text) "#"))
          (let [[preamble rest*] (if (scenario-kw? text)
                                   [[] xs]
                                   (let [[tags more] (take-until #(scenario-kw? (:text %)) xs)]
                                     [tags more]))]
            (if-let [kw (first rest*)]
              (let [[body rest] (take-until #(or (scenario-kw? (:text %))
                                                 (and (tag-line? (:text %))
                                                      (not (scenario-kw? (:text %)))))
                                            (rest rest*))
                    ;; stop preamble+body before next scenario's tags: take-until tag-line
                    ;; will also stop at this scenario's own tags if we already consumed them.
                    block (into (vec preamble) (into [kw] body))
                    start (:n kw)]
                (recur rest header bg (conj scens {:start start :rows block})))
              (recur (rest xs) header bg scens)))

          :else
          (recur (rest xs) header bg scens))
        {:header     header
         :background bg
         :scenarios  scens}))))

(defn- tags-of [rows]
  (->> rows
       (map :text)
       (filter tag-line?)
       (mapcat #(str/split (str/trim %) #"\s+"))
       set))

(defn- wip? [rows]
  (contains? (tags-of rows) "@wip"))

(defn- feature-wip? [parsed]
  (wip? (:header parsed)))

(defn- scenario-title [rows]
  (some #(when (scenario-kw? (:text %)) (str/trim (:text %))) rows))

(defn- block-lines [rows]
  (mapv :text rows))

(defn- normalized-block [rows]
  (normalize-lines (block-lines rows)))

(defn- all-blocks [parsed]
  (cond-> []
    (seq (:header parsed)) (conj {:kind :header :lines (normalized-block (:header parsed))})
    (:background parsed)   (conj {:kind :background :lines (normalized-block (:background parsed))})
    true                   (into (map (fn [s]
                                        {:kind  :scenario
                                         :start (:start s)
                                         :title (scenario-title (:rows s))
                                         :lines (normalized-block (:rows s))})
                                      (:scenarios parsed)))))

(defn- selected-scenarios [parsed lines]
  (if (seq lines)
    (filter (fn [s] (some #(= (:start s) %) lines)) (:scenarios parsed))
    (filter #(wip? (:rows %)) (:scenarios parsed))))

(defn- append-lines! [bean-path lines]
  (let [cur  (slurp bean-path)
        body (str (if (str/ends-with? cur "\n") cur (str cur "\n"))
                  (str/join "\n" lines) "\n")]
    (spit bean-path body)
    (str/join "\n" lines)))

(defn- field-lines [text prefix]
  (->> (str/split-lines (or text ""))
       (keep (fn [l]
               (when (str/starts-with? (str/trim l) prefix)
                 (str/trim l))))
       vec))

(defn- last-by [xs key-fn]
  (vec (vals (reduce (fn [m l]
                       (assoc m (key-fn l) l))
                     {}
                     (vec xs)))))

(defn- parse-baseline-line [l]
  (when-let [[_ repo sha] (re-matches #"feature-baseline:\s+(\S+)\s+(\S+)\s*" l)]
    {:repo repo :sha sha :line l}))

(defn- parse-blob-line [l]
  (when-let [[_ repo path blob rest] (re-matches #"feature-blob:\s+(\S+)\s+(\S+)\s+(\S+)(?:\s+([\d,]+))?\s*" l)]
    {:repo  repo
     :path  path
     :blob  blob
     :lines (when rest (mapv parse-long (str/split rest #",")))
     :line  l}))

(defn- parse-main-sha-line [l]
  (when-let [[_ repo sha] (re-matches #"main-sha:\s+(\S+)\s+(\S+)\s*" l)]
    {:repo repo :sha sha}))

(defn- in-force-baselines [text]
  (last-by (filter identity (map parse-baseline-line (field-lines text "feature-baseline:")))
           (fn [x] (:repo x))))

(defn- in-force-blobs [text]
  (last-by (filter identity (map parse-blob-line (field-lines text "feature-blob:")))
           (fn [x] [(:repo x) (:path x)])))

(defn- in-force-main-shas [text]
  (filter identity (map (fn [l] (parse-main-sha-line (str/trim l)))
                        (str/split-lines (or text "")))))

(defn- heading? [l]
  (re-matches #"##\s+.*" (str/trim l)))

(defn- acceptance-or-exceptions? [l]
  (boolean (re-find #"(?i)^##\s+(Acceptance|Exceptions)\b" (str/trim l))))

(defn- contract-lines [text]
  (let [ls     (str/split-lines (or text ""))
        fields (->> ls
                    (map str/trim)
                    (filter #(or (str/starts-with? % "feature-baseline:")
                                 (str/starts-with? % "feature-blob:")))
                    set)
        under  (loop [xs ls
                      in? false
                      acc []]
                 (if-let [l (first xs)]
                   (cond
                     (acceptance-or-exceptions? l)
                     (recur (rest xs) true acc)

                     (and in? (heading? l))
                     (recur (rest xs) false acc)

                     (and in? (not (str/blank? (str/trim l))))
                     (recur (rest xs) true (conj acc (str/trim l)))

                     :else
                     (recur (rest xs) in? acc))
                   acc))]
    (into fields under)))

(defn- bean-rel [isaac-dir bean-path]
  (str (fs/relativize (fs/absolutize isaac-dir) (fs/absolutize bean-path))))

(defn- file-history [isaac-dir rel]
  (let [r (git isaac-dir "log" "--follow" "--name-only" "--pretty=format:%H" "--" rel)]
    (when (zero? (:exit r))
      (->> (str/split-lines (:out r))
           (remove str/blank?)
           (partition 2)
           (map (fn [[sha path]] {:sha sha :path path}))
           vec))))

(defn- show-isaac [isaac-dir sha path]
  (let [r (git isaac-dir "show" (str sha ":" path))]
    (when (zero? (:exit r))
      (:out r))))

(defn- trailers [isaac-dir sha]
  (let [body (git-ok isaac-dir "log" "-1" "--format=%B" sha)]
    (->> (str/split-lines body)
         (keep (fn [l]
                 (when-let [[_ k v] (re-matches #"([A-Za-z0-9-]+):\s*(.*)" l)]
                   [k v])))
         (into {}))))

(defn- baseline-cmd [opts]
  (let [{:keys [positional dirs isaac]} opts
        bean-id (first positional)
        targets (rest positional)
        isaac   (or isaac (str (fs/cwd)))]
    (cond
      (str/blank? bean-id)
      (fail 2 usage)

      (empty? targets)
      (fail 2 usage)

      :else
      (if-let [bean-path (find-bean-file isaac bean-id)]
        (try
          (let [parsed-targets (mapv parse-target targets)]
            (when (some nil? parsed-targets)
              (throw (ex-info "bad target" {:exit 2 :message usage})))
            (let [appended (volatile! [])]
              (doseq [{:keys [repo path lines]} parsed-targets]
                (let [mod-dir (module-dir isaac dirs repo)]
                  (when-not (fs/directory? mod-dir)
                    (throw (ex-info (str "missing checkout: " repo)
                                    {:exit 2 :message (str "missing checkout: " repo " (" mod-dir ")")})))
                  (fetch! mod-dir)
                  (let [sha (origin-main mod-dir)
                        txt (show-at mod-dir sha path)]
                    (when-not txt
                      (throw (ex-info "missing file"
                                      {:exit 1 :message (str "file does not exist at origin/main: " path)})))
                    (let [parsed (parse-feature txt)
                          sel    (selected-scenarios parsed lines)
                          fwip   (feature-wip? parsed)]
                      (when (and (empty? sel) (not fwip))
                        (throw (ex-info "no wip"
                                        {:exit 1 :message (str "no @wip to implement: " path
                                                               (when (seq lines)
                                                                 (str " " (str/join "," lines))))})))
                      (when (seq lines)
                        (doseq [n lines]
                          (let [hit (some #(= (:start %) n) (:scenarios parsed))]
                            (when-not hit
                              (throw (ex-info "missing scenario"
                                              {:exit 1 :message (str path ":" n " is not a Scenario line")})))
                            (when-not (or fwip (some #(and (= (:start %) n) (wip? (:rows %))) (:scenarios parsed)))
                              (throw (ex-info "no wip at line"
                                              {:exit 1 :message (str "no @wip to implement: " path ":" n)}))))))
                      (let [blob (blob-at mod-dir sha path)
                            bl   (str "feature-baseline: " repo " " sha)
                            fl   (str "feature-blob: " repo " " path " " blob
                                      (when (seq lines) (str " " (str/join "," lines))))]
                        (vswap! appended conj bl fl))))))
              (let [printed (append-lines! bean-path @appended)]
                (ok printed))))
          (catch clojure.lang.ExceptionInfo e
            (if-let [ex (ex-data e)]
              (fail (or (:exit ex) 1) (or (:message ex) (.getMessage e)))
              (fail 1 (.getMessage e)))))
        (fail 2 (str "bean not found: " bean-id))))))

(defn- short-diff [want got]
  (str "expected block:\n  " (str/join "\n  " want)
       "\nactual blocks did not contain it"
       (when (seq got) (str "\nnearby:\n  " (str/join "\n  " (first got))))))

(defn- contains-block? [hay needle]
  (let [n (count needle)]
    (boolean (some (fn [i]
                     (= needle (subvec hay i (+ i n))))
                   (range 0 (inc (- (count hay) n)))))))

(defn- file-normalized-lines [text]
  (normalize-lines (str/split-lines (or text ""))))

(defn- worker-diff [mod-dir from to]
  (let [r (git mod-dir "diff" "--unified=0" (str from ".." to) "--" "*.feature" "**/*.feature")]
    (:out r)))

(defn- parse-diff [diff]
  ;; returns [{:file path :hunks [{:old [lines] :new [lines]}]}]
  (loop [ls (str/split-lines (or diff ""))
         file nil
         acc  []
         hunk {:old [] :new []}]
    (if-let [l (first ls)]
      (cond
        (str/starts-with? l "diff --git")
        (let [acc (if (and file (or (seq (:old hunk)) (seq (:new hunk))))
                    (conj acc {:file file :hunk hunk})
                    acc)
              m   (re-find #"b/(.+)$" l)]
          (recur (rest ls) (or (second m) file) acc {:old [] :new []}))

        (str/starts-with? l "+++ b/")
        (recur (rest ls) (subs l 6) acc hunk)

        (str/starts-with? l "--- ")
        (recur (rest ls) file acc hunk)

        (str/starts-with? l "@@")
        (let [acc (if (and file (or (seq (:old hunk)) (seq (:new hunk))))
                    (conj acc {:file file :hunk hunk})
                    acc)]
          (recur (rest ls) file acc {:old [] :new []}))

        (str/starts-with? l "+")
        (recur (rest ls) file acc (update hunk :new conj (subs l 1)))

        (str/starts-with? l "-")
        (recur (rest ls) file acc (update hunk :old conj (subs l 1)))

        :else
        (recur (rest ls) file acc hunk))
      (cond-> acc
        (and file (or (seq (:old hunk)) (seq (:new hunk))))
        (conj {:file file :hunk hunk})))))

(defn- wip-only-hunk? [{:keys [old new]}]
  (let [old-n (normalize-lines old)
        new-n (normalize-lines new)]
    ;; dropping @wip from tag lines: after normalize, both sides equal
    ;; and every old line was a tag line (or blank, already dropped)
    (and (= old-n new-n)
         (every? #(or (tag-line? %) (str/blank? %)) old)
         (every? #(or (tag-line? %) (str/blank? %)) new)
         (some #(str/includes? % "@wip") old))))

(defn- merge-base [mod-dir a b]
  (git-ok mod-dir "merge-base" a b))

(defn- verify-cmd [opts]
  (let [{:keys [positional dirs refs isaac]} opts
        bean-id (first positional)
        isaac   (or isaac (str (fs/cwd)))]
    (cond
      (str/blank? bean-id)
      (fail 2 usage)

      :else
      (if-let [bean-path (find-bean-file isaac bean-id)]
        (let [bean-txt  (slurp bean-path)
              baselines (in-force-baselines bean-txt)
              blobs     (in-force-blobs bean-txt)
              mains     (in-force-main-shas bean-txt)
              failures  (volatile! [])
              note!     (fn [m] (vswap! failures conj m))]
          (if (empty? baselines)
            (fail 2 "no feature-baseline: use the verify path")
            (do
              ;; 1. baseline sanity
              (doseq [{:keys [repo sha]} baselines]
                (let [mod-dir (module-dir isaac dirs repo)]
                  (fetch! mod-dir)
                  (let [om (origin-main mod-dir)]
                    (when-not (ancestor? mod-dir sha om)
                      (note! (str "baseline sha " sha " is not an ancestor of origin/main for " repo))))))
              (doseq [{:keys [repo path blob]} blobs]
                (let [mod-dir (module-dir isaac dirs repo)
                      base    (some #(when (= (:repo %) repo) %) baselines)
                      sha     (:sha base)
                      actual  (when sha (blob-at mod-dir sha path))]
                  (when-not (= blob actual)
                    (note! (str "recorded blob for " repo " " path " does not match "
                                sha ":" path " (have " (or actual "missing") ", want " blob ")")))))

              ;; 2. contract append-only
              (let [rel  (bean-rel isaac bean-path)
                    hist (file-history isaac rel)
                    with-bl (drop-while (fn [{:keys [sha path]}]
                                          (let [t (show-isaac isaac sha path)]
                                            (empty? (field-lines (or t "") "feature-baseline:"))))
                                        (reverse hist))
                    versions (concat (map (fn [{:keys [sha path]}]
                                            {:sha sha :text (show-isaac isaac sha path)})
                                          with-bl)
                                     [{:sha "WORKTREE" :text bean-txt}])]
                (doseq [[prev cur] (partition 2 1 versions)]
                  (when (and prev cur (:text prev) (:text cur))
                    (let [a (contract-lines (:text prev))
                          b (contract-lines (:text cur))
                          missing (remove b a)]
                      (when (seq missing)
                        (note! (str "contract line removed at " (:sha cur) ": "
                                    (str/join "; " missing))))))))

              ;; 3. planner commits
              (let [rel  (bean-rel isaac bean-path)
                    hist (file-history isaac rel)]
                (doseq [{:keys [sha path]} hist]
                  (when-let [t (show-isaac isaac sha path)]
                    (when (seq (field-lines t "feature-baseline:"))
                      (let [parent-t (let [p (git isaac "rev-parse" (str sha "^"))]
                                       (when (zero? (:exit p))
                                         (show-isaac isaac (str/trim-newline (:out p)) path)))
                            introduced? (or (nil? parent-t)
                                            (empty? (field-lines (or parent-t "") "feature-baseline:"))
                                            (not= (set (field-lines t "feature-baseline:"))
                                                  (set (field-lines (or parent-t "") "feature-baseline:")))
                                            (not= (set (field-lines t "feature-blob:"))
                                                  (set (field-lines (or parent-t "") "feature-blob:"))))]
                        (when introduced?
                          (let [tr (trailers isaac sha)
                                sess (get tr "Isaac-Session" "")]
                            (when (or (str/starts-with? sess "isaac-work")
                                      (str/starts-with? sess "isaac-verify"))
                              (note! (str "baseline line introduced by " sess " commit " sha))))))))))

              ;; 4–6 per repo
              (doseq [{:keys [repo sha]} baselines]
                (let [mod-dir   (module-dir isaac dirs repo)
                      main-hits (filter #(= repo (:repo %)) mains)
                      check-sha (or (some :sha (seq main-hits))
                                    (get refs repo)
                                    (git-ok mod-dir "rev-parse" "HEAD"))
                      from      (if (seq main-hits)
                                  (str check-sha "^")
                                  (try (merge-base mod-dir "origin/main" check-sha)
                                       (catch Exception _ "origin/main")))
                      repo-blobs (filter #(= repo (:repo %)) blobs)]
                  (doseq [{:keys [path blob lines]} repo-blobs]
                    (let [base-txt (show-at mod-dir sha path)
                          now-txt  (show-at mod-dir check-sha path)]
                      (when-not now-txt
                        (note! (str "baselined file missing at " check-sha ": " path)))
                      (when (and base-txt now-txt)
                        (let [base-p (parse-feature base-txt)
                              now-p  (parse-feature now-txt)
                              now-norm (file-normalized-lines now-txt)
                              blocks (all-blocks base-p)]
                          ;; 4. every baseline block appears
                          (doseq [b blocks]
                            (when-not (contains-block? now-norm (:lines b))
                              (note! (str path " lost or reworded block"
                                          (when (:title b) (str " (" (:title b) ")"))
                                          (when (some #(str/includes? (str %) "@slow")
                                                      (mapcat :lines (all-blocks now-p)))
                                            " (tag @slow)")
                                          "\n" (short-diff (:lines b)
                                                           (map :lines (all-blocks now-p)))))))
                          ;; 5. bean scenarios not still @wip
                          (let [sel (selected-scenarios base-p lines)
                                now-by-title (into {}
                                                   (map (fn [s] [(scenario-title (:rows s)) s])
                                                        (:scenarios now-p)))]
                            (when (and (empty? lines) (feature-wip? now-p) (feature-wip? base-p))
                              (note! (str path " feature still @wip")))
                            (doseq [s sel]
                              (let [title (scenario-title (:rows s))
                                    now-s (get now-by-title title)]
                                (when (or (nil? now-s) (wip? (:rows now-s)))
                                  (note! (str path " scenario still @wip: " title))))))))))
                  ;; 6. worker diff
                  (let [diff  (worker-diff mod-dir from check-sha)
                        hunks (parse-diff diff)
                        baselined-paths (set (map :path repo-blobs))]
                    (doseq [{:keys [file hunk]} hunks]
                      (cond
                        (not (contains? baselined-paths file))
                        (note! (str file " changed in worker diff but is not baselined"))

                        (not (wip-only-hunk? hunk))
                        (note! (str file " worker diff is not a pure @wip removal")))))))

              (if (seq @failures)
                (fail 1 (str/join "\n" @failures))
                (ok "pass")))))
        (fail 2 (str "bean not found: " bean-id))))))

(defn run
  "Parse argv and return {:exit n :message s}."
  [args]
  (let [opts (parse-args args)]
    (cond
      (:help opts) (ok usage)
      (:error opts) (fail 2 (str (:error opts) "\n" usage))
      (= "baseline" (first (:positional opts)))
      (baseline-cmd (update opts :positional subvec 1))
      (= "verify" (first (:positional opts)))
      (verify-cmd (update opts :positional subvec 1))
      :else (fail 2 usage))))

(defn -main [& args]
  (let [{:keys [exit message]} (run args)]
    (when-not (str/blank? message)
      (println message))
    (System/exit (or exit 0))))
