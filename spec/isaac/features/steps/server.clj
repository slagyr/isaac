(ns isaac.features.steps.server
  (:require
    [babashka.http-client :as bb-http]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.cli.server :as server]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.cron.scheduler :as scheduler]
    [isaac.delivery.worker :as worker]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.main :as main]
    [isaac.server.app :as app]
    [org.httpkit.client :as http]
    [org.httpkit.server :as httpkit]
    [taoensso.timbre :as timbre])
  (:import
    (java.time ZonedDateTime)
    (java.time.format DateTimeFormatter)))

;; c3kit.apron.refresh logs via timbre and forces :info level, bypassing
;; isaac.logger. Disable timbre's default println appender at step-namespace
;; load time so c3kit's internal logs (">>>>> Stopping App", etc.) don't
;; pollute feature test output. Gherclj loads isaac.features.steps.* for
;; every run, so this silences timbre for the whole feature suite.
(timbre/merge-config! {:appenders {:println {:enabled? false}}})

(defn- parse-config-value [value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    (= "true" (str/lower-case value)) true
    (= "false" (str/lower-case value)) false
    :else value))

(defn- config-path [path]
  (mapv keyword (str/split path #"\.")))

(def ^:private offset-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssZ"))

(defn- config-rows [table]
  (cond-> (:rows table)
    (seq (:headers table)) (conj (:headers table))))

(defn- with-server-fs [f]
  (if-let [mem (g/get :mem-fs)]
    (binding [fs/*fs* mem] (f))
    (f)))

(defn- notify-config-change! [path]
  (when-let [source (g/get :config-change-source)]
    (change-source/notify-path! source path)))

(defn- parse-state-value [value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    (= "true" (str/lower-case value)) true
    (= "false" (str/lower-case value)) false
    (re-matches #"[a-z][a-z-]*" value) (keyword value)
    :else value))

(defn- parse-isaac-state-value [file-path path value]
  (cond
    (= file-path "comm/discord/routing.edn") value
    :else (parse-state-value value)))

(defn- isaac-file-path [path]
  (if (str/starts-with? path "/") path (str (g/get :state-dir) "/" path)))

(defn- isaac-root-path []
  (str (or (g/get :state-dir) (g/get :isaac-home)) "/.isaac"))

(defn- config-path? [path]
  (str/starts-with? path "config/"))

(defn- isaac-file-path [path]
  (cond
    (str/starts-with? path "/") path
    (config-path? path)          (str (isaac-root-path) "/" path)
    :else                        (str (g/get :state-dir) "/" path)))

(defn- parse-isaac-value [file-path path value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    (= "true" (str/lower-case value)) true
    (= "false" (str/lower-case value)) false
    (= path "tools.allow")
    (->> (str/split value #",")
         (map str/trim)
         (remove str/blank?)
         (mapv keyword))

    (or (str/starts-with? value "[")
        (str/starts-with? value "{")
        (str/starts-with? value ":")
        (str/starts-with? value "\""))
    (edn/read-string value)

    (or (contains? #{"defaults.crew" "defaults.model"} path)
        (and (= path "model") (re-find #"/config/crew/" file-path))
        (and (= path "crew") (re-find #"/config/cron/" file-path))
    (and (= path "api") (re-find #"/config/providers/" file-path)))
    (keyword value)

    :else value))

(defn- maybe-prune-root-entity! [path]
  (when-let [[_ kind id] (re-matches #"config/(crew|models|providers)/([^/]+)\.edn" path)]
    (let [root-path (isaac-file-path "config/isaac.edn")]
      (when (fs/exists? root-path)
        (let [data (edn/read-string (fs/slurp root-path))
              data (update data (keyword kind) dissoc id)]
          (fs/spit root-path (pr-str data)))))))

(defn- isaac-file-data [path]
  (let [path (isaac-file-path path)]
    (when (fs/exists? path)
      (edn/read-string (fs/slurp path)))))

(defn- copy-state-tree! [source-fs source-path target-fs target-path]
  (when (fs/exists?- source-fs source-path)
    (if (fs/file?- source-fs source-path)
      (do
        (fs/mkdirs- target-fs (fs/parent target-path))
        (fs/spit- target-fs target-path (fs/slurp- source-fs source-path)))
      (do
        (fs/mkdirs- target-fs target-path)
        (doseq [child (or (fs/children- source-fs source-path) [])]
          (copy-state-tree! source-fs
                            (str source-path "/" child)
                            target-fs
                            (str target-path "/" child)))))))

(defn- parse-json-body [body]
  (try
    (json/parse-string body true)
    (catch Exception _
      body)))

(defn- record-request! [method url opts]
  (let [request {:body    (some-> (:body opts) parse-json-body)
                 :headers (:headers opts)
                 :method  method
                 :url     url}]
    (g/assoc! :outbound-http-request request)
    (g/update! :outbound-http-requests #(conj (or % []) request))))

(defn- stubbed-response [url]
  (when-let [stub (get (g/get :url-stubs) url)]
    {:body    (:body stub "")
     :headers (:headers stub {})
     :status  (:status stub 200)}))

(defn- with-http-post-stub [f]
  (with-redefs [bb-http/post (fn [url opts]
                               (record-request! "POST" url opts)
                               (or (stubbed-response url)
                                   {:status 200 :headers {} :body "{}"}))]
    (f)))

(defn- get-path [data path]
  (reduce (fn [current segment]
            (cond
              (nil? current) nil
              (map? current) (or (get current (keyword segment))
                                 (get current segment))
              :else nil))
          data
          (str/split path #"\.")))

(defn- config-file-path []
  (str (g/get :state-dir) "/.isaac/config/isaac.edn"))

(defn- persist-config-entry! [k v]
  (when-let [_ (g/get :state-dir)]
    (with-server-fs
      (fn []
        (let [path    (config-file-path)
              current (if (fs/exists? path) (edn/read-string (fs/slurp path)) {})
              updated (assoc-in current (config-path k) (parse-config-value v))]
          (fs/mkdirs (fs/parent path))
          (fs/spit path (pr-str updated)))))))

;; region ----- Setup -----

(defn stop-server! []
  (app/stop!))

(g/after-scenario stop-server!)

(defgiven configure "config:"
  "Sets server-config entries via dot-path keys (e.g. server.port,
   acp.proxy-transport). Special-cases log.output: 'memory' routes
   log output to the captured-entries atom; anything else becomes a
   log file. Also persists entries to <state-dir>/.isaac/config/isaac.edn."
  [table]
  (doseq [[k v] (config-rows table)]
    (if (= "log.output" k)
      (case v
         "memory" (do (log/set-output! :memory)
                      (log/clear-entries!))
         (log/set-log-file! v))
      (do
        (g/update! :server-config #(assoc-in (or % {}) (config-path k) (parse-config-value v)))
        (persist-config-entry! k v)))))

(defgiven isaac-edn-file-exists "the isaac EDN file {path:string} exists with:"
  "Writes structured EDN to <state-dir>/.isaac/<path>. Table rows are
   {path, value}; dot-separated path column creates nested keyword maps
   (e.g. 'server.port' → {:server {:port ...}}). Fires a config-change
   notification so a running server's hot-reload picks it up."
  [path table]
  (with-server-fs
    (fn []
      (let [file-path (isaac-file-path path)
            data      (reduce (fn [acc row]
                                (let [row-map (zipmap (:headers table) row)
                                      p       (get row-map "path")
                                      value   (get row-map "value")]
                                  (assoc-in acc
                                            (mapv keyword (str/split p #"\."))
                                            (parse-isaac-value file-path p value))))
                              {}
                              (:rows table))]
        (maybe-prune-root-entity! path)
        (fs/mkdirs (fs/parent file-path))
        (fs/spit file-path (pr-str data))
        (notify-config-change! file-path)))))

(defgiven isaac-file-exists-with-content "the isaac file {path:string} exists with:"
  "Writes heredoc content (not EDN) to <state-dir>/.isaac/<path>. Use
   for markdown companions (.md), raw text files, etc. EDN files should
   use 'the isaac EDN file X exists with:' instead."
  [path content]
  (with-server-fs
    (fn []
      (let [file-path (isaac-file-path path)]
        (fs/mkdirs (fs/parent file-path))
        (fs/spit file-path content)
        (notify-config-change! file-path)))))

(defgiven server-running "the Isaac server is running"
  "Stops any prior server, then starts one against :state-dir / :isaac-home.
   Merges in-memory :server-config and :provider-configs over whatever
   config/load-config returns from disk. When mem-fs is active, wires a
   synchronous memory change-source so hot-reload scenarios fire
   deterministically from test writes."
  []
  (app/stop!)
  (let [home           (or (g/get :state-dir)
                           (g/get :isaac-home)
                           (System/getProperty "user.home"))
        state-dir      (or (g/get :state-dir) home)
        server-config  (merge (with-server-fs #(config/load-config {:home home}))
                              (or (g/get :server-config) {})
                              (when-let [providers (g/get :provider-configs)] {:providers providers}))
        cfg            (config/server-config server-config)
        config-source  (when (g/get :mem-fs)
                         (let [source (change-source/memory-source home)]
                           (g/assoc! :config-change-source source)
                           source))
        {:keys [port]} (with-server-fs
                         #(app/start! {:cfg                  server-config
                                       :config-change-source config-source
                                       :dev                  (:dev cfg)
                                       :home                 home
                                       :host                 (:host cfg)
                                       :port                 (:port cfg)
                                       :state-dir            state-dir}))]
    (g/assoc! :server-port port)))

;; endregion ^^^^^ Setup ^^^^^

;; region ----- Server Commands -----

(defwhen server-command-run "the server command is run on port {port:int}"
  "Runs 'isaac server --port N' with server/block! stubbed to no-op and
   config/load-config stubbed to {}. Immediately stops the server after
   the run returns — use for testing startup flags/logging only."
  [port]
  (with-redefs [server/block!         (fn [] nil)
                config/load-config   (fn [& _] {})]
    (with-out-str
      (app/stop!)
      (main/run ["server" "--port" (str port)])))
  (app/stop!))

(defwhen server-command-run-no-port "the server command is run without a port flag"
  []
  (let [cfg (or (g/get :server-config) {})]
    (with-redefs [server/block!           (fn [] nil)
                  config/load-config      (fn [& _] cfg)
                  httpkit/run-server      (fn [_handler opts] (atom (:port opts)))
                  httpkit/server-port     (fn [s] (or @s 0))
                  httpkit/server-stop!    (fn [_s] nil)]
      (with-out-str
        (app/stop!)
        (main/run ["server"]))
      (app/stop!))))

(defwhen server-command-run-with-args "the server command is run with args {args:string}"
  [args]
  (let [cfg       (or (g/get :server-config) {})
        arg-parts (remove str/blank? (str/split args #"\s+"))]
    (with-redefs [server/block!       (fn [] nil)
                  config/load-config (fn [& _] cfg)]
      (with-out-str
        (app/stop!)
        (main/run (into ["server"] arg-parts))))
    (app/stop!)))

(defwhen gateway-command-run "the gateway command is run on port {port:int}"
  [port]
  (with-redefs [server/block!       (fn [] nil)
                config/load-config (fn [& _] {})]
    (with-out-str
      (app/stop!)
      (main/run ["gateway" "--port" (str port)])))
  (app/stop!))

;; endregion ^^^^^ Server Commands ^^^^^

;; region ----- Request / Response -----

(defwhen get-request "a GET request is made to {path:string}"
  [path]
  (let [port (g/get :server-port)
        url  (str "http://localhost:" port path)
        resp @(http/get url)]
    (g/assoc! :http-response resp)))

(defwhen scheduler-ticks-at #"the scheduler ticks at \"([^\"]+)\""
  "Invokes scheduler/tick! once with the given ISO timestamp as virtual
   'now'. Flips :isaac-file-phase to :assert so subsequent
   'the EDN isaac file X contains:' steps read/assert instead of write."
  [iso]
  (g/assoc! :isaac-file-phase :assert)
  (with-server-fs
    (fn []
      (scheduler/tick! {:cfg       (merge (config/load-config {:home (g/get :state-dir)})
                                          (when-let [providers (g/get :provider-configs)] {:providers providers}))
                          :now       (ZonedDateTime/parse iso offset-formatter)
                          :state-dir (g/get :state-dir)}))))

(defwhen delivery-worker-ticks "the delivery worker ticks"
  "Invokes worker/tick! once with HTTP-post stubbed. Flips
   :isaac-file-phase to :assert so subsequent file-contains steps
   read/assert. For time-sensitive scheduling, use 'ticks at' variant."
  []
  (g/assoc! :isaac-file-phase :assert)
  (with-http-post-stub
    (fn []
      (with-server-fs
        (fn []
          (worker/tick! {:state-dir (g/get :state-dir)}))))))

(defwhen delivery-worker-ticks-at #"the delivery worker ticks at \"([^\"]+)\""
  [iso]
  (g/assoc! :isaac-file-phase :assert)
  (with-http-post-stub
    (fn []
      (with-server-fs
        (fn []
          (worker/tick! {:now       (java.time.Instant/parse iso)
                         :state-dir (g/get :state-dir)}))))))

(defthen response-status "the response status is {code:int}"
  [code]
  (let [resp   (g/get :http-response)
        status (:status resp)]
    (g/should= code status)))

(defthen response-body-key-equals "the response body has {key:string} equal to {value:string}"
  [key value]
  (let [resp (g/get :http-response)
        body (json/parse-string (:body resp) true)
        k    (keyword key)]
    (g/should= value (get body k))))

(defthen response-body-has-key "the response body has a {key:string} key"
  [key]
  (let [resp (g/get :http-response)
        body (json/parse-string (:body resp) true)
        k    (keyword key)]
    (g/should-not-be-nil (get body k))))

(defgiven edn-isaac-file-contains "the EDN isaac file \"{path}\" contains:"
  "Dual-mode: when :isaac-file-phase is :assert (after a scheduler or
   worker tick), reads the on-disk EDN and asserts the table rows match.
   Otherwise writes the table as EDN to the path. Same phrase, different
   behavior depending on where it appears in the scenario."
  [path table]
  (if (= :assert (g/get :isaac-file-phase))
    (let [data (with-server-fs #(isaac-file-data path))]
      (doseq [row (:rows table)]
        (let [row-map   (zipmap (:headers table) row)
              actual    (get-path data (get row-map "path"))
              expected  (parse-isaac-state-value path (get row-map "path") (get row-map "value"))]
          (g/should= expected actual))))
    (with-server-fs
      (fn []
        (let [data (reduce (fn [acc row]
                              (let [row-map (zipmap (:headers table) row)]
                                (assoc-in acc
                                          (str/split (get row-map "path") #"\.")
                                          (parse-isaac-state-value path (get row-map "path") (get row-map "value")))))
                           {}
                           (:rows table))
              path (isaac-file-path path)]
          (fs/mkdirs (fs/parent path))
          (fs/spit path (pr-str data)))))))

(defthen edn-isaac-file-does-not-exist "the EDN isaac file \"{path}\" does not exist"
  [path]
  (g/should-not (with-server-fs #(fs/exists? (isaac-file-path path)))))

;; endregion ^^^^^ Request / Response ^^^^^

;; region ----- Log Assertions -----

(defn- log-match-result [table entries]
  (let [expected-count (count (:rows table))
        direct         (match/match-entries table entries)]
    (if (empty? (:failures direct))
      direct
      (or (some (fn [start]
                  (let [window (subvec (vec entries) start (min (count entries) (+ start expected-count)))
                        result (match/match-entries table window)]
                    (when (empty? (:failures result)) result)))
                (range (count entries)))
          direct))))

(defthen log-entries-match "the log has entries matching:"
  "Polls the captured-logs atom up to 2s. Tries a direct match against
   all entries first; on failure, tries sliding-window matches (useful
   when other entries surround the expected ones). Also awaits
   :turn-future up to 30s if set. REQUIRES log.output=memory in config."
  [table]
  (when-let [turn-future (g/get :turn-future)]
    (deref turn-future 30000 nil))
  (let [deadline (+ (System/currentTimeMillis) 2000)]
    (loop []
      (let [entries (log/get-entries)
            result  (log-match-result table entries)]
        (if (or (empty? (:failures result)) (<= deadline (System/currentTimeMillis)))
          (g/should= [] (:failures result))
          (do
            (Thread/sleep 10)
            (recur)))))))

(defthen log-entries-dont-match "the log has no entries matching:"
  "Checks the captured logs once (no polling). Each row must NOT match
   any current entry. Use after a step that should NOT have logged —
   don't use for race-y absence; 'never logged' is a stronger claim
   than this step can prove."
  [table]
  (let [entries (log/get-entries)
        headers (:headers table)]
    (doseq [row (:rows table)]
      (let [result (match/match-entries {:headers headers :rows [row]} entries)]
        (g/should-not (:pass? result))))))

;; endregion ^^^^^ Log Assertions ^^^^^
