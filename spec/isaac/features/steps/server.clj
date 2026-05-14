(ns isaac.features.steps.server
  (:require
    [babashka.http-client :as bb-http]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen helper!]]
    [isaac.server.cli :as server]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.module.loader :as module-loader]
    [isaac.cron.scheduler :as scheduler]
    [isaac.comm.delivery.worker :as worker]
    [isaac.comm.discord :as discord]
    [isaac.comm.registry :as comm-registry]
    [isaac.bridge.status :as bridge-status]
    [isaac.home :as home]
    [isaac.system :as system]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.main :as main]
    [isaac.spec-helper :as helper]
    [isaac.server.app :as app]
    [isaac.server.http :as server-http]
    [org.httpkit.client :as http]
    [org.httpkit.server :as httpkit]
    [taoensso.timbre :as timbre])
  (:import
    (java.time ZonedDateTime)
    (java.time.format DateTimeFormatter)))

(helper! isaac.features.steps.server)

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
    (= "bind-server-port" value) false
    :else value))

(defn- config-path [path]
  (mapv keyword (str/split path #"\.")))

(defn- delete-sentinel? [value]
  (= "#delete" (str/trim (str value))))

(defn- dissoc-in [m path]
  (cond
    (empty? path)      m
    (= 1 (count path)) (dissoc m (first path))
    :else              (let [parent-path (vec (butlast path))
                             leaf        (last path)
                             parent      (get-in m parent-path)]
                         (if (map? parent)
                           (assoc-in m parent-path (dissoc parent leaf))
                           m))))

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

(defn- parse-isaac-state-value [_file-path _path value]
  (parse-state-value value))

(defn- isaac-file-path [path]
  (if (str/starts-with? path "/") path (str (g/get :state-dir) "/" path)))

(defn- isaac-root-path []
  (str (or (g/get :state-dir) (g/get :isaac-home)) "/.isaac"))

(defn- runtime-state-dir []
  (or (g/get :runtime-state-dir)
      (str (or (g/get :state-dir) (g/get :isaac-home)) "/.isaac")))

(defn- config-path? [path]
  (str/starts-with? path "config/"))

(defn- isaac-file-path [path]
  (cond
    (str/starts-with? path "/") path
    (= path "isaac.edn")         (str (isaac-root-path) "/config/isaac.edn")
    (config-path? path)          (str (isaac-root-path) "/" path)
    :else                        (str (runtime-state-dir) "/" path)))

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
              updated (if (delete-sentinel? v)
                        (dissoc-in current (config-path k))
                        (assoc-in current (config-path k) (parse-config-value v)))]
          (fs/mkdirs (fs/parent path))
          (fs/spit path (pr-str updated)))))))

;; region ----- Setup -----

(defn- deep-merge [a b]
  (if (and (map? a) (map? b))
    (merge-with deep-merge a b)
    b))

(defn stop-server! []
  (app/stop!))

(g/after-scenario stop-server!)

(defn configure [table]
  (doseq [[k v] (config-rows table)]
    (if (= "log.output" k)
      (case v
         "memory" (do (log/set-output! :memory)
                      (log/clear-entries!))
         (do (log/set-log-file! v)
             (log/set-output! :file)))
      (if (= "bind-server-port" k)
        (g/assoc! :bind-server-port? (parse-config-value v))
        (do
          (g/update! :server-config #(if (delete-sentinel? v)
                                       (dissoc-in (or % {}) (config-path k))
                                       (assoc-in (or % {}) (config-path k) (parse-config-value v))))
          (persist-config-entry! k v))))))

(defn isaac-edn-file-exists [path table]
  (with-server-fs
    (fn []
      (let [file-path (isaac-file-path path)
            data      (reduce (fn [acc row]
                                (let [row-map (zipmap (:headers table) row)
                                      p       (get row-map "path")
                                      value   (get row-map "value")
                                      keys    (mapv keyword (str/split p #"\."))]
                                  (if (delete-sentinel? value)
                                    (dissoc-in acc keys)
                                    (assoc-in acc keys (parse-isaac-value file-path p value)))))
                              (if (some #(delete-sentinel? (get (zipmap (:headers table) %) "value"))
                                        (:rows table))
                                (or (isaac-file-data path) {})
                                {})
                              (:rows table))]
        (maybe-prune-root-entity! path)
        (fs/mkdirs (fs/parent file-path))
        (fs/spit file-path (pr-str data))
        (notify-config-change! file-path)))))

(defn isaac-file-exists-with-content [path content]
  (with-server-fs
    (fn []
      (let [file-path (isaac-file-path path)]
        (fs/mkdirs (fs/parent file-path))
        (fs/spit file-path content)
        (notify-config-change! file-path)))))

(defn isaac-file-with-log-entries [path n]
  (let [n     (parse-long n)
        lines (->> (range 1 (inc n))
                   (map #(format "{:ts \"2026-05-12T00:%02d:%02dZ\" :level :info :event :e%02d}"
                                 (quot % 60) (mod % 60) %))
                   (str/join "\n"))]
    (with-server-fs
      (fn []
        (let [file-path (isaac-file-path path)]
          (fs/mkdirs (fs/parent file-path))
          (fs/spit file-path lines))))))

(defn- copy-mem-fs-to-disk! [mem virtual-root real-root]
  "Recursively copies all files from mem at virtual-root to real-root on disk."
  (binding [fs/*fs* mem]
    (letfn [(copy! [vpath]
              (cond
                (fs/file? vpath)
                (let [rpath (str real-root (subs vpath (count virtual-root)))]
                  (.mkdirs (.getParentFile (java.io.File. rpath)))
                  (spit rpath (fs/slurp vpath)))
                (fs/dir? vpath)
                (doseq [child (fs/children vpath)]
                  (copy! (str vpath "/" child)))))]
      (copy! virtual-root))))

(defn- clean-real-dir! [path]
  (let [dir (java.io.File. path)]
    (when (.exists dir)
      (doseq [f (-> dir file-seq reverse)]
        (.delete f)))))

(defn- default-server-home []
  (str (System/getProperty "user.dir") "/target/test-state/server-default-home"))

(defn server-running []
  (app/stop!)
  (let [explicit-home? (or (g/get :state-dir) (g/get :isaac-home))
        virtual-home   (or explicit-home?
                           (default-server-home))
        mem            (g/get :mem-fs)
        ;; HTTP handler threads have no fs/*fs* binding. For mem-fs test setups,
        ;; materialize the virtual fs to a real on-disk path so all server threads
        ;; can safely read and write without any dynamic binding required.
        home           (if mem
                          (let [real (str (System/getProperty "user.dir") virtual-home)]
                            (clean-real-dir! real)
                            (copy-mem-fs-to-disk! mem virtual-home real)
                            (g/assoc! :state-dir real)
                             (g/dissoc! :mem-fs)
                             real)
                         (do
                           (when-not explicit-home?
                             (clean-real-dir! virtual-home)
                             (g/assoc! :state-dir virtual-home))
                           virtual-home))
        runtime-state  (str home "/.isaac")
        server-config  (let [base    (binding [fs/*fs* (fs/real-fs)]
                                        (config/load-config {:home home}))
                                  merged  (deep-merge base
                                                      (merge (or (g/get :server-config) {})
                                                             (when-let [providers (g/get :provider-configs)]
                                                               {:providers providers})))
                                  disc    (module-loader/discover! merged {:state-dir runtime-state
                                                                            :cwd       (System/getProperty "user.dir")})]
                              (assoc merged :module-index (:index disc)))
        cfg            (config/server-config server-config)
        ;; For synthetic default homes, feature steps notify config changes
        ;; explicitly, so a memory-backed source is deterministic and cheap.
        ;; Real state-dir scenarios keep the real watcher path when hot reload
        ;; is enabled; no watcher is needed for pure startup-only scenarios.
        config-source  (when (:hot-reload cfg)
                         (if (or mem (not explicit-home?))
                           (change-source/memory-source home)
                           (change-source/watch-service-source home)))
        _              (g/assoc! :config-change-source config-source)
        run-server?    (not (false? (g/get :bind-server-port?)))
        start-opts     (cond-> {:cfg                  server-config
                                 :config-change-source config-source
                                 :dev                  (:dev cfg)
                                 :host                 (:host cfg)
                                 :port                 (if run-server? (:port cfg) 0)
                                  :state-dir            runtime-state
                                  :start-http-server?   run-server?}
                            (g/get :discord-connect-ws!) (assoc :connect-ws! (g/get :discord-connect-ws!)))]
    (g/assoc! :runtime-state-dir runtime-state)
    (g/assoc! :server-handler-opts {:cfg-fn    (fn [] (or (some-> app/state deref :cfg deref) server-config))
                                    :state-dir runtime-state
                                    :home      home})
    (when-let [{:keys [port]} (app/start! start-opts)]
      (g/assoc! :server-port port))))

;; endregion ^^^^^ Setup ^^^^^

;; region ----- Server Commands -----

(defn server-command-run [port]
  (let [cfg (or (g/get :server-config) {})]
    (with-redefs [server/block!         (fn [] nil)
                  config/load-config   (fn [& _] cfg)]
      (with-out-str
        (app/stop!)
        (main/run ["server" "--port" (str port)]))))
  (app/stop!))

(defn server-command-run-no-port []
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

(defn server-command-run-with-args [args]
  (let [cfg       (or (g/get :server-config) {})
        arg-parts (remove str/blank? (str/split args #"\s+" 2))]
    (with-redefs [server/block!       (fn [] nil)
                  config/load-config (fn [& _] cfg)]
      (with-out-str
        (app/stop!)
        (main/run (into ["server"] arg-parts))))
    (app/stop!)))

(defn gateway-command-run [port]
  (let [cfg (or (g/get :server-config) {})]
    (with-redefs [server/block!       (fn [] nil)
                  config/load-config (fn [& _] cfg)]
      (with-out-str
        (app/stop!)
        (main/run ["gateway" "--port" (str port)]))))
  (app/stop!))

;; endregion ^^^^^ Server Commands ^^^^^

;; region ----- Request / Response -----

(defn- extract-headers [rows]
  (into {} (keep (fn [[k v]]
                   (when (str/starts-with? k "header.")
                     [(subs k 7) v]))
                 rows)))

(defn- direct-headers [headers]
  (into {} (map (fn [[k v]] [(str/lower-case k) v])) headers))

(defn- extract-body [rows]
  (some (fn [[k v]] (when (= "body" k) v)) rows))

(defn- table->kv-rows [table]
  (let [rows (cond-> (:rows table)
               (seq (:headers table)) (conj (:headers table)))]
    (mapv (fn [row] (mapv identity row)) rows)))

(defn get-request [path]
  (let [port (g/get :server-port)
        resp (if (pos? (long (or port 0)))
               @(http/get (str "http://localhost:" port path))
               ((server-http/create-handler (g/get :server-handler-opts))
                {:request-method :get
                 :uri            path
                 :headers        {}}))]
    (g/assoc! :http-response resp)))

(defn get-request-with-headers [path table]
  (let [port    (g/get :server-port)
        rows    (table->kv-rows table)
        headers (extract-headers rows)
        resp    (if (pos? (long (or port 0)))
                  @(http/get (str "http://localhost:" port path) {:headers headers})
                  ((server-http/create-handler (g/get :server-handler-opts))
                   {:request-method :get
                    :uri            path
                    :headers        (direct-headers headers)}))]
    (g/assoc! :http-response resp)))

(defn post-request [path table]
  (let [port     (g/get :server-port)
        rows     (table->kv-rows table)
        headers  (extract-headers rows)
        body     (extract-body rows)
        headers  (if (and body (not (contains? headers "Content-Type")))
                   (assoc headers "Content-Type" "application/json")
                   headers)
        resp     (if (pos? (long (or port 0)))
                   @(http/post (str "http://localhost:" port path)
                               (cond-> {:headers headers :as :text}
                                 body (assoc :body body)))
                   ((server-http/create-handler (g/get :server-handler-opts))
                    {:request-method :post
                     :uri            path
                     :headers        (direct-headers headers)
                     :body           body}))]
    (g/assoc! :http-response resp)
    ;; Store hook turn future so session-transcript-matching can await it
    (when-let [hook-ns (find-ns 'isaac.hooks)]
      (when-let [fut-fn (ns-resolve hook-ns 'last-turn-future)]
        (when-let [fut (fut-fn)]
          (g/assoc! :turn-future fut))))))

(defn scheduler-ticks-at [iso]
  (g/assoc! :isaac-file-phase :assert)
  (g/assoc! :runtime-state-dir (str (g/get :state-dir) "/.isaac"))
  (with-server-fs
    (fn []
      (scheduler/tick! {:cfg       (merge (config/load-config {:home (g/get :state-dir)})
                                          (when-let [providers (g/get :provider-configs)] {:providers providers}))
                           :now       (ZonedDateTime/parse iso offset-formatter)
                           :state-dir (runtime-state-dir)}))))

(defn- with-discord-comm [state-dir f]
  (let [cfg  (config/load-config {:home (fs/parent state-dir)})
        dcfg (get-in cfg [:comms :discord])
        di   (discord/->DiscordIntegration state-dir nil (atom dcfg) (atom nil))
        reg  (assoc (comm-registry/fresh-registry) :instances {"discord" di})]
    (binding [comm-registry/*registry* (atom reg)
              home/*state-dir*         state-dir]
      (f))))

(defn delivery-worker-ticks []
  (g/assoc! :isaac-file-phase :assert)
  (g/assoc! :runtime-state-dir (str (g/get :state-dir) "/.isaac"))
  (with-http-post-stub
    (fn []
      (with-server-fs
        (fn []
          (with-discord-comm (runtime-state-dir)
            (fn []
              (system/with-system {:state-dir (runtime-state-dir)}
                (worker/tick! {})))))))))

(defn delivery-worker-ticks-at [iso]
  (g/assoc! :isaac-file-phase :assert)
  (g/assoc! :runtime-state-dir (str (g/get :state-dir) "/.isaac"))
  (with-http-post-stub
    (fn []
      (with-server-fs
        (fn []
          (with-discord-comm (runtime-state-dir)
            (fn []
              (system/with-system {:state-dir (runtime-state-dir)}
                (worker/tick! {:now (java.time.Instant/parse iso)})))))))))

(defn response-status [code]
  (let [resp   (g/get :http-response)
        status (:status resp)]
    (g/should= code status)))

(defn response-body-key-equals [key value]
  (let [resp (g/get :http-response)
        body (json/parse-string (:body resp) true)
        k    (keyword key)]
    (g/should= value (get body k))))

(defn response-body-has-key [key]
  (let [resp (g/get :http-response)
        body (json/parse-string (:body resp) true)
        k    (keyword key)]
    (g/should-not-be-nil (get body k))))

(defn edn-isaac-file-contains [path table]
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
                                (if (delete-sentinel? (get row-map "value"))
                                  (dissoc-in acc (str/split (get row-map "path") #"\."))
                                  (assoc-in acc
                                            (str/split (get row-map "path") #"\.")
                                            (parse-isaac-state-value path (get row-map "path") (get row-map "value"))))))
                           (if (some #(delete-sentinel? (get (zipmap (:headers table) %) "value"))
                                     (:rows table))
                             (or (isaac-file-data path) {})
                             {})
                           (:rows table))
              path (isaac-file-path path)]
          (fs/mkdirs (fs/parent path))
          (fs/spit path (pr-str data)))))))

(defn edn-isaac-file-does-not-exist [path]
  (g/should-not (with-server-fs #(fs/exists? (isaac-file-path path)))))

(defn isaac-edn-file-removed [path]
  (with-server-fs
    (fn []
      (let [file-path (isaac-file-path path)]
        (when (fs/exists? file-path)
          (fs/delete file-path))
        (notify-config-change! file-path)))))

(defn isaac-file-removed [path]
  (with-server-fs
    (fn []
      (let [file-path (isaac-file-path path)]
        (when (fs/exists? file-path)
          (fs/delete file-path))
        (notify-config-change! file-path)))))

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

(defn log-entries-match [table]
  (when-let [turn-future (g/get :turn-future)]
    (deref turn-future 30000 nil))
  (let [result* (atom nil)]
    (helper/await-condition
      (fn []
        (let [result (log-match-result table (log/get-entries))]
          (reset! result* result)
          (empty? (:failures result))))
      2000)
    (g/should= [] (:failures @result*))))

(defn log-entries-dont-match [table]
  (let [entries (log/get-entries)
        headers (:headers table)]
    (doseq [row (:rows table)]
      (let [result (match/match-entries {:headers headers :rows [row]} entries)]
        (g/should-not (:pass? result))))))

(defn config-reloaded []
  (helper/await-condition
    #(some (fn [entry] (= :config/reloaded (:event entry))) (log/get-entries))
    2000)
  (g/should (some (fn [entry] (= :config/reloaded (:event entry))) (log/get-entries))))

(defn available-slash-commands-include [table]
  (let [commands (bridge-status/available-commands)
        headers  (:headers table)]
    (doseq [row (:rows table)]
      (let [expected (zipmap headers row)
            matched? (some (fn [entry]
                             (every? (fn [[k v]] (= v (get entry (keyword k)))) expected))
                           commands)]
        (g/should matched?)))))

;; endregion ^^^^^ Log Assertions ^^^^^

;; region ----- Routing -----

(defgiven "config:" server/configure
  "Sets server-config entries via dot-path keys (e.g. server.port,
    acp.proxy-transport). Special-cases log.output: 'memory' routes
    log output to the captured-entries atom; anything else becomes a
    log file. Also persists entries to <state-dir>/.isaac/config/isaac.edn.
    A value of '#delete' removes that key instead of writing the string.")

(defwhen "the isaac EDN file {path:string} is removed" server/isaac-edn-file-removed
  "Deletes the EDN file at <state-dir>/.isaac/<path> and fires a config-change
   notification so a running server's hot-reload processes the removal.")

(defwhen "the isaac file {path:string} is removed" server/isaac-file-removed
  "Deletes any file at <state-dir>/.isaac/<path> and fires a config-change
   notification so a running server's hot-reload processes the removal.")

(defgiven "the isaac EDN file {path:string} exists with:" server/isaac-edn-file-exists
  "Writes structured EDN to <state-dir>/.isaac/<path>. Table rows are
    {path, value}; dot-separated path column creates nested keyword maps
    (e.g. 'server.port' → {:server {:port ...}}). Fires a config-change
    notification so a running server's hot-reload picks it up. A value of
    '#delete' removes that dotted path from the current file before write.")

(defgiven "the isaac file {path:string} exists with:" server/isaac-file-exists-with-content
  "Writes heredoc content (not EDN) to <state-dir>/.isaac/<path>. Use
   for markdown companions (.md), raw text files, etc. EDN files should
   use 'the isaac EDN file X exists with:' instead.")

(defgiven #"the isaac file \"([^\"]+)\" exists with (\d+) log entries" server/isaac-file-with-log-entries
  "Writes N EDN log lines to <state-dir>/.isaac/<path>. Each line has a
   distinct two-digit-padded :event keyword (:e01..:eNN) so substring
   assertions don't collide across IDs.")

(defgiven "the Isaac server is started" server/server-running
  "Stops any prior server, then starts one against :state-dir / :isaac-home.
   Merges in-memory :server-config and :provider-configs over whatever
   config/load-config returns from disk. When mem-fs is active, wires a
   synchronous memory change-source so hot-reload scenarios fire
   deterministically from test writes.")

(defwhen "the Isaac process is started" server/server-running
  "Alias for 'the Isaac server is started' as a When step. Starts the full
   Isaac process (including comm activation) against the configured state dir.")

(defwhen "the server command is run on port {port:int}" server/server-command-run
  "Runs 'isaac server --port N' with server/block! stubbed to no-op and
   config/load-config stubbed to {}. Immediately stops the server after
   the run returns — use for testing startup flags/logging only.")

(defwhen "the server command is run without a port flag" server/server-command-run-no-port)

(defwhen "the server command is run with args {args:string}" server/server-command-run-with-args)

(defwhen "the isaac config is reloaded" server/config-reloaded)

(defwhen "the gateway command is run on port {port:int}" server/gateway-command-run)

(defwhen #"a GET request is made to \"([^\"]+)\"$" server/get-request)

(defwhen #"a GET request is made to \"([^\"]+)\":" server/get-request-with-headers)

(defwhen #"a POST request is made to \"([^\"]+)\":" server/post-request)

(defwhen #"the scheduler ticks at \"([^\"]+)\"" server/scheduler-ticks-at
  "Invokes scheduler/tick! once with the given ISO timestamp as virtual
   'now'. Flips :isaac-file-phase to :assert so subsequent
   'the EDN isaac file X contains:' steps read/assert instead of write.")

(defwhen "the delivery worker ticks" server/delivery-worker-ticks
  "Invokes worker/tick! once with HTTP-post stubbed. Flips
   :isaac-file-phase to :assert so subsequent file-contains steps
   read/assert. For time-sensitive scheduling, use 'ticks at' variant.")

(defwhen #"the delivery worker ticks at \"([^\"]+)\"" server/delivery-worker-ticks-at)

(defthen "the response status is {code:int}" server/response-status)

(defthen "the response body has {key:string} equal to {value:string}" server/response-body-key-equals)

(defthen "the response body has a {key:string} key" server/response-body-has-key)

(defthen "the available slash commands include:" server/available-slash-commands-include)

(defgiven "the EDN isaac file \"{path}\" contains:" server/edn-isaac-file-contains
  "Dual-mode: when :isaac-file-phase is :assert (after a scheduler or
    worker tick), reads the on-disk EDN and asserts the table rows match.
    Otherwise writes the table as EDN to the path. Same phrase, different
    behavior depending on where it appears in the scenario. In write mode,
    '#delete' removes that path from the current file before writing.")

(defthen "the EDN isaac file \"{path}\" does not exist" server/edn-isaac-file-does-not-exist)
(defthen "the isaac file \"{path}\" does not exist" server/edn-isaac-file-does-not-exist)

(defthen "the log has entries matching:" server/log-entries-match
  "Polls the captured-logs atom up to 2s. Tries a direct match against
   all entries first; on failure, tries sliding-window matches (useful
   when other entries surround the expected ones). Also awaits
   :turn-future up to 30s if set. REQUIRES log.output=memory in config.")

(defthen "the log has no entries matching:" server/log-entries-dont-match
  "Checks the captured logs once (no polling). Each row must NOT match
   any current entry. Use after a step that should NOT have logged —
   don't use for race-y absence; 'never logged' is a stronger claim
   than this step can prove.")

;; endregion ^^^^^ Routing ^^^^^
