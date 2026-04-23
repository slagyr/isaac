(ns isaac.tool.memory
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]))

(def ^:dynamic *now* nil)

(defn- now []
  (or *now* (java.time.Instant/now)))

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (.getName (java.io.File. state-dir)))
    (.getParent (java.io.File. state-dir))
    state-dir))

(defn- crew-id [{:keys [session-key state-dir]}]
  (or (some->> session-key (storage/get-session state-dir) :crew)
      (get-in (config/load-config {:home (state-dir->home state-dir)}) [:defaults :crew])
      "main"))

(defn- memory-dir [state-dir crew-id]
  (str state-dir "/crew/" crew-id "/memory"))

(defn- date-str [instant]
  (str (.toLocalDate (java.time.ZonedDateTime/ofInstant instant java.time.ZoneOffset/UTC))))

(defn- today-path [state-dir crew-id]
  (str (memory-dir state-dir crew-id) "/" (date-str (now)) ".md"))

(defn- lines [content]
  (cond
    (string? content) [content]
    (and (sequential? content) (every? string? content)) (vec content)
    :else nil))

(defn memory-write-tool
  [{:keys [content state-dir session-key]}]
  (if-not state-dir
    {:isError true :error "state-dir is required"}
    (if-let [entries (lines content)]
      (let [crew-id   (crew-id {:session-key session-key :state-dir state-dir})
            path      (today-path state-dir crew-id)
            existing? (fs/exists? path)
            prefix    (when (and existing? (seq (fs/slurp path))) "\n")]
        (fs/mkdirs (fs/parent path))
        (fs/spit path (str prefix (str/join "\n" entries)) :append existing?)
        {:result (str "wrote " path)})
      {:isError true :error "content must be a string or vector of strings"})))

(defn- parse-date [s]
  (java.time.LocalDate/parse s))

(defn- date-range [start end]
  (loop [current start out []]
    (if (.isAfter current end)
      out
      (recur (.plusDays current 1) (conj out current)))))

(defn memory-get-tool
  [{:keys [end_time start_time state-dir session-key]}]
  (if-not state-dir
    {:isError true :error "state-dir is required"}
    (let [start   (parse-date start_time)
          end     (parse-date end_time)
          crew-id (crew-id {:session-key session-key :state-dir state-dir})
          result  (->> (date-range start end)
                       (map #(str (memory-dir state-dir crew-id) "/" % ".md"))
                       (filter fs/exists?)
                       (map fs/slurp)
                       (str/join "\n"))]
      {:result result})))

(defn- matching-lines [query path]
  (let [pattern (re-pattern query)]
    (->> (str/split-lines (or (fs/slurp path) ""))
         (keep-indexed (fn [idx line]
                         (when (re-find pattern line)
                           (str (.getName (java.io.File. path)) ":" (inc idx) ":" line)))))))

(defn memory-search-tool
  [{:keys [query state-dir session-key]}]
  (if-not state-dir
    {:isError true :error "state-dir is required"}
    (let [crew-id  (crew-id {:session-key session-key :state-dir state-dir})
          dir      (memory-dir state-dir crew-id)
          matches  (->> (or (fs/children dir) [])
                        sort
                        (mapcat #(matching-lines query (str dir "/" %))))]
      {:result (if (seq matches)
                 (str/join "\n" matches)
                 "no matches")})))
