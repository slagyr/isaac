(ns isaac.bean-gate.core
  "Bean Gate: the planner records a feature baseline on a bean; verify proves the
   bean's contract and scenarios reached main unchanged except for @wip removal."
  (:require [babashka.fs :as fs]
            [clojure.set :as set]
            [clojure.string :as str]
            [isaac.bean-gate.bean :as bean]
            [isaac.bean-gate.gherkin :as g]
            [isaac.bean-gate.git :as git]))

;; region Shared

(defn module-dir
  "The module checkout for repo: --dir override, else <isaac-root>/../<repo>."
  [root dirs repo]
  (str (fs/absolutize (or (get dirs repo) (fs/path (fs/parent (fs/absolutize root)) repo)))))

(defn- checkout? [dir] (and (fs/directory? dir) (git/ok? dir "rev-parse" "--git-dir")))

(defn- short [sha] (if sha (git/short-sha sha) "working tree"))

;; endregion

;; region baseline

(defn parse-spec
  "\"repo:path[:line…]\" → {:repo :path :lines}, or nil when malformed."
  [s]
  (let [[repo path & lines] (str/split s #":")]
    (when (and (not (str/blank? repo)) (not (str/blank? path)) (every? #(re-matches #"\d+" %) lines))
      (cond-> {:repo repo :path path}
        (seq lines) (assoc :lines (mapv parse-long lines))))))

(defn- baseline-spec-errors [{:keys [repo path lines]} text]
  (let [blocks     (g/blocks text)
        header-wip (some-> (g/header blocks) g/wip?)
        by-line    (into {} (map (juxt :line identity) (g/scenarios blocks)))]
    (if lines
      (for [ln lines
            :let [b (get by-line ln)]
            :when (or (nil? b) (not (or header-wip (g/wip? b))))]
        (if b
          (str repo ":" path ":" ln " — scenario is not @wip; nothing to implement")
          (str repo ":" path ":" ln " — no Scenario keyword on that line")))
      (when-not (or header-wip (some g/wip? (g/scenarios blocks)))
        [(str repo ":" path " — no @wip scenario; nothing to implement")]))))

(defn baseline
  "Records the baseline for bean id. Returns {:ok? :lines :errors}."
  [{:keys [root id specs dirs]}]
  (let [file (bean/bean-file root id)]
    (if-not file
      {:ok? false :errors [(str "no bean file .beans/" id "--*.md under " root)]}
      (let [repos   (distinct (map :repo specs))
            heads   (into {} (for [repo repos
                                   :let [dir (module-dir root dirs repo)]]
                               [repo (cond
                                       (not (checkout? dir)) {:error (str repo " — no git checkout at " dir " (pass --dir " repo "=<path>)")}
                                       :else (if-let [err (git/fetch! dir)]
                                               {:error (str repo " — git fetch origin failed: " err)}
                                               (if-let [sha (git/resolve-rev dir "origin/main")]
                                                 {:dir dir :sha sha}
                                                 {:error (str repo " — no origin/main in " dir)})))]))
            results (for [{:keys [repo path lines] :as spec} specs
                          :let [{:keys [dir sha error]} (get heads repo)]]
                      (if error
                        {:errors [error]}
                        (if-let [text (git/show-file dir sha path)]
                          (let [errors (baseline-spec-errors spec text)]
                            (if (seq errors)
                              {:errors errors}
                              {:line (str "feature-blob: " repo " " path " " (git/blob-id dir sha path)
                                          (when lines (str " " (str/join "," lines))))}))
                          {:errors [(str repo ":" path " — not on origin/main (" (git/short-sha sha) ")")]})))
            errors  (distinct (mapcat :errors results))]
        (if (seq errors)
          {:ok? false :errors errors}
          (let [lines (concat (for [repo repos] (str "feature-baseline: " repo " " (:sha (get heads repo))))
                              (map :line results))
                text  (slurp file)]
            (spit file (str (when-not (str/ends-with? text "\n") "\n") "\n" (str/join "\n" lines) "\n") :append true)
            {:ok? true :lines (vec lines) :file file}))))))

;; endregion

;; region verify — contract

(defn- contract-failures
  "Contract lines are append-only from the first version that carries a baseline."
  [versions]
  (let [gated (drop-while #(not (bean/gated? (:text %))) versions)]
    (for [[prev cur] (partition 2 1 gated)
          :let [missing (set/difference (bean/contract-lines (:text prev)) (bean/contract-lines (:text cur)))]
          [_ line] (sort-by second missing)]
      (str "contract line removed or edited in " (short (:commit cur)) ": " line))))

(def ^:private worker-trailer #"(?m)^Isaac-Session:\s*isaac-(work|verify)\S*")

(defn- baseline-author-failures
  "A baseline line introduced by a worker or verifier session commit fails."
  [root versions]
  (let [gated   (drop-while #(not (bean/gated? (:text %))) versions)
        current (filter #(= :gate (first %)) (bean/contract-lines (:text (last versions))))
        intro   (fn [entry] (:commit (first (filter #(contains? (bean/contract-lines (:text %)) entry) gated))))
        commits (distinct (keep intro current))]
    (for [c commits
          :let [msg (bean/commit-message root c)]
          :when (re-find worker-trailer msg)]
      (str "baseline line introduced by a worker/verifier session commit " (short c)
           " (" (str/trim (first (re-find worker-trailer msg))) "); only the planner records baselines"))))

;; endregion

;; region verify — features

(defn- block-diff [expected actual]
  (let [e (set expected) a (set actual)]
    (concat (map #(str "      - " %) (remove a expected))
            (map #(str "      + " %) (remove e actual)))))

(defn- intact-failures
  "Every baseline block (with @wip stripped) appears verbatim in the checked file."
  [repo path base-blocks cur-blocks]
  (let [have   (frequencies (map g/normalized cur-blocks))
        by-ttl (group-by #(g/strip-wip (:title %)) cur-blocks)
        need   (frequencies (map g/normalized base-blocks))]
    (for [b base-blocks
          :let [n (g/normalized b)]
          :when (< (get have n 0) (get need n))]
      (let [twin (first (get by-ttl (g/strip-wip (:title b))))]
        (str/join "\n"
                  (cons (str repo " " path ": baselined block " (pr-str (:title b)) " (baseline line " (:line b) ")"
                             (if twin " was changed" " is missing"))
                        (when twin (block-diff n (g/normalized twin)))))))))

(defn- live-failures
  "The bean's scenarios no longer carry @wip, nor does a baselined @wip feature line."
  [repo path lines base-blocks cur-blocks]
  (let [header-wip (some-> (g/header base-blocks) g/wip?)
        mine       (if lines
                     (filter #(contains? (set lines) (:line %)) (g/scenarios base-blocks))
                     (if header-wip (g/scenarios base-blocks) (filter g/wip? (g/scenarios base-blocks))))
        by-norm    (group-by g/normalized cur-blocks)]
    (concat
      (when (and header-wip (some-> (g/header cur-blocks) g/wip?))
        [(str repo " " path ": the Feature line still carries @wip")])
      (for [b mine
            :let [cur (first (get by-norm (g/normalized b)))]
            :when (and cur (g/wip? cur))]
        (str repo " " path ": scenario " (pr-str (:title b)) " still carries @wip")))))

(defn- blob-text [dir {:keys [blob]} baseline-sha path]
  (let [{:keys [exit out]} (git/run dir "cat-file" "-p" blob)]
    (if (zero? exit) out (git/show-file dir baseline-sha path))))

;; endregion

;; region verify — worker diff

(defn parse-diff
  "Parses `git diff -U0 --no-renames` output into [{:path :hunks [{:minus [] :plus []}]}]."
  [text]
  (let [files (reduce
                (fn [files line]
                  (cond
                    (str/starts-with? line "diff --git ")
                    (conj files {:path nil :old nil :hunks [] :in-hunk false})

                    (empty? files) files

                    (and (not (:in-hunk (peek files))) (str/starts-with? line "--- "))
                    (update files (dec (count files)) assoc :old (str/replace (subs line 4) #"^a/" ""))

                    (and (not (:in-hunk (peek files))) (str/starts-with? line "+++ "))
                    (update files (dec (count files)) assoc :path (str/replace (subs line 4) #"^b/" ""))

                    (str/starts-with? line "@@")
                    (update files (dec (count files)) #(-> % (assoc :in-hunk true) (update :hunks conj {:minus [] :plus []})))

                    (and (:in-hunk (peek files)) (str/starts-with? line "-"))
                    (update-in files [(dec (count files)) :hunks] #(update % (dec (count %)) update :minus conj (subs line 1)))

                    (and (:in-hunk (peek files)) (str/starts-with? line "+"))
                    (update-in files [(dec (count files)) :hunks] #(update % (dec (count %)) update :plus conj (subs line 1)))

                    :else files))
                []
                (str/split-lines text))]
    (for [{:keys [path old hunks]} files]
      {:path (if (or (nil? path) (= "/dev/null" path)) old path) :hunks hunks})))

(defn wip-removal-only?
  "True when a -U0 hunk only drops @wip tokens from tag lines."
  [{:keys [minus plus]}]
  (loop [[m & ms :as minus] minus plus plus]
    (if (empty? minus)
      (empty? plus)
      (let [t (str/trim m)]
        (cond
          (not (and (g/tag-line? t) (contains? (g/tags t) "@wip"))) false
          (nil? (g/strip-wip t)) (recur ms plus)
          (and (seq plus) (= (g/strip-wip t) (str/join " " (str/split (str/trim (first plus)) #"\s+")))) (recur ms (rest plus))
          :else false)))))

(defn- worker-diff-failures [repo dir baselined [from to]]
  (let [{:keys [exit out err]} (git/run dir "diff" "-U0" "--no-color" "--no-ext-diff" "--no-renames" from to "--" "*.feature")]
    (if-not (zero? exit)
      [(str repo ": git diff " (short from) ".." (short to) " failed: " (str/trim err))]
      (for [{:keys [path hunks]} (parse-diff out)
            :let [bad (remove wip-removal-only? hunks)]
            :when (or (seq bad) (and (seq hunks) (not (contains? baselined path))))]
        (let [h (or (first bad) (first hunks))]
          (str/join "\n"
                    (concat [(str repo " " path ": worker diff " (short from) ".." (short to)
                                  (if (contains? baselined path)
                                    " changes the feature beyond @wip removal"
                                    " edits a feature file the bean did not baseline"))]
                            (map #(str "      - " %) (take 6 (:minus h)))
                            (map #(str "      + " %) (take 6 (:plus h))))))))))

;; endregion

;; region verify — per repo

(defn- checked-commits
  "{:tip sha :diffs [[from to]…] :mode …} or {:error msg}."
  [repo dir main-shas ref origin-main]
  (if (seq main-shas)
    (let [resolved (map (fn [s] [s (git/resolve-rev dir s)]) main-shas)
          missing  (keep (fn [[s r]] (when-not r s)) resolved)
          shas     (keep second resolved)]
      (cond
        (seq missing) {:error (str repo ": main-sha " (str/join ", " missing) " not found in " dir)}
        :else
        (let [off-main (remove #(and origin-main (git/ancestor? dir % origin-main)) shas)
              tip      (first (filter (fn [s] (every? #(git/ancestor? dir % s) shas)) shas))]
          (cond
            (seq off-main) {:error (str repo ": main-sha " (str/join ", " (map short off-main)) " is not on origin/main")}
            (nil? tip)     {:error (str repo ": main-sha lines do not share a tip commit")}
            :else          {:tip tip :mode (str "main-sha " (short tip))
                            :diffs (for [s shas] [(or (git/resolve-rev dir (str s "^")) (str s "^")) s])}))))
    (let [ref-sha (git/resolve-rev dir (or ref "HEAD"))
          base    (when (and ref-sha origin-main) (git/resolve-rev dir (git/git dir "merge-base" origin-main ref-sha)))]
      (cond
        (nil? ref-sha) {:error (str repo ": ref " (or ref "HEAD") " does not resolve in " dir)}
        (nil? base)    {:error (str repo ": no merge-base between " (or ref "HEAD") " and origin/main")}
        :else          {:tip ref-sha :mode (str (or ref "HEAD") " " (short ref-sha)) :diffs [[base ref-sha]]}))))

(defn- sanity-failures
  "Check 1: the baseline sha is on origin/main and each recorded blob matches it."
  [repo dir sha origin-main blobs]
  (let [known? (git/resolve-rev dir sha)]
    (concat
      (when-not origin-main [(str repo ": no origin/main in " dir)])
      (when-not known? [(str repo ": feature-baseline " (short sha) " not found in " dir)])
      (when (and origin-main known? (not (git/ancestor? dir sha origin-main)))
        [(str repo ": feature-baseline " (short sha) " is not on origin/main")])
      (for [{:keys [path blob]} blobs
            :let [actual (git/blob-id dir sha path)]
            :when (not= actual blob)]
        (str repo " " path ": recorded blob " (short blob) " but " (short sha) ":" path " is " (short actual))))))

(defn- feature-failures
  "Checks 4 and 5 for one baselined file at the checked commit."
  [repo dir sha tip {:keys [path lines] :as b}]
  (let [base-text (blob-text dir b sha path)
        cur-text  (git/show-file dir tip path)]
    (cond
      (nil? base-text) [(str repo " " path ": baseline blob " (short (:blob b)) " unavailable")]
      (nil? cur-text)  [(str repo " " path ": missing at " (short tip))]
      :else (let [bb (g/blocks base-text) cb (g/blocks cur-text)]
              (concat (intact-failures repo path bb cb)
                      (live-failures repo path lines bb cb))))))

(defn- repo-report [{:keys [root dirs refs]} {:keys [repo sha]} blobs main-shas]
  (let [dir (module-dir root dirs repo)]
    (if-not (checkout? dir)
      {:failures [(str repo ": no git checkout at " dir " (pass --dir " repo "=<path>)")]}
      (let [fetch-err   (git/fetch! dir)
            notes       (when fetch-err [(str repo ": fetch failed, using local refs: " fetch-err)])
            origin-main (git/resolve-rev dir "origin/main")
            sanity      (sanity-failures repo dir sha origin-main blobs)
            {:keys [tip diffs mode error]} (checked-commits repo dir main-shas (get refs repo) origin-main)]
        (if error
          {:notes notes :failures (concat sanity [error])}
          {:mode     mode
           :notes    notes
           :failures (concat sanity
                             (mapcat #(feature-failures repo dir sha tip %) blobs)
                             (mapcat #(worker-diff-failures repo dir (set (map :path blobs)) %) diffs))})))))

;; endregion

(defn verify
  "Checks bean id. Returns {:status :pass|:fail|:ungated|:error :failures :notes :checked}."
  [{:keys [root id] :as opts}]
  (let [file (bean/bean-file root id)]
    (if-not file
      {:status :error :failures [(str "no bean file .beans/" id "--*.md under " root)]}
      (let [text (slurp file)]
        (if-not (bean/gated? text)
          {:status :ungated}
          (let [{:keys [baselines blobs main-shas]} (bean/in-force text)
                versions (bean/history root file)
                orphans  (for [{:keys [repo path]} blobs :when (not (contains? baselines repo))]
                           (str repo " " path ": feature-blob without a feature-baseline for " repo))
                reports  (for [[repo b] (sort-by key baselines)]
                           (assoc (repo-report opts b (filter #(= repo (:repo %)) blobs) (get main-shas repo)) :repo repo))
                failures (concat orphans
                                 (contract-failures versions)
                                 (baseline-author-failures root versions)
                                 (mapcat :failures reports))]
            {:status   (if (seq failures) :fail :pass)
             :failures (vec failures)
             :notes    (vec (mapcat :notes reports))
             :checked  (vec (keep #(when (:mode %) (str (:repo %) " @ " (:mode %))) reports))}))))))
