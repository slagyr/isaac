(ns isaac.tool.memory
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]))

(def ^:dynamic *now* nil)

(defn now []
  (or *now* (java.time.Instant/now)))

(defn- string-key-map [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v]) m)))

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (.getName (java.io.File. state-dir)))
    (.getParent (java.io.File. state-dir))
    state-dir))

(defn- crew-id [args]
  (let [args        (string-key-map args)
        session-key (get args "session_key")
        state-dir   (system/get :state-dir)]
    (or (some->> session-key (store/get-session (or (system/get :session-store) (file-store/create-store state-dir))) :crew)
        (get-in (config/load-config {:home (state-dir->home state-dir)}) [:defaults :crew])
        "main")))

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
  [args]
  (let [args        (string-key-map args)
        content     (get args "content")
        session-key (get args "session_key")
        state-dir   (system/get :state-dir)]
    (if-let [entries (lines content)]
      (let [crew-id   (crew-id {"session_key" session-key})
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
  [args]
  (let [args        (string-key-map args)
        end-time    (get args "end_time")
        start-time  (get args "start_time")
        session-key (get args "session_key")
        state-dir   (system/get :state-dir)
        start       (parse-date start-time)
        end         (parse-date end-time)
        crew-id     (crew-id {"session_key" session-key})
        result      (->> (date-range start end)
                         (map #(str (memory-dir state-dir crew-id) "/" % ".md"))
                         (filter fs/exists?)
                         (map fs/slurp)
                         (str/join "\n"))]
    {:result result}))

(defn- matching-lines [query path]
  (let [pattern (re-pattern (str "(?i)" query))]
    (->> (str/split-lines (or (fs/slurp path) ""))
         (keep-indexed (fn [idx line]
                         (when (re-find pattern line)
                           (str (.getName (java.io.File. path)) ":" (inc idx) ":" line)))))))

(defn memory-search-tool
  [args]
  (let [args        (string-key-map args)
        query       (get args "query")
        session-key (get args "session_key")
        state-dir   (system/get :state-dir)
        crew-id     (crew-id {"session_key" session-key})
        dir         (memory-dir state-dir crew-id)
        matches     (->> (or (fs/children dir) [])
                         sort
                         (mapcat #(matching-lines query (str dir "/" %))))]
    {:result (if (seq matches)
               (str/join "\n" matches)
               "no matches")}))
