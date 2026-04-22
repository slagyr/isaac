(ns isaac.features.steps.server
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.cli.server :as server]
    [isaac.config.loader :as config]
    [isaac.cron.scheduler :as scheduler]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.main :as main]
    [isaac.server.app :as app]
    [org.httpkit.client :as http]
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

(defn- parse-state-value [value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    (= "true" (str/lower-case value)) true
    (= "false" (str/lower-case value)) false
    (re-matches #"[a-z][a-z0-9-]*" value) (keyword value)
    :else value))

(defn- state-file-path [path]
  (if (str/starts-with? path "/") path (str (g/get :state-dir) "/" path)))

(defn- state-file-data [path]
  (let [path (state-file-path path)]
    (when (fs/exists? path)
      (edn/read-string (fs/slurp path)))))

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

(defgiven server-running "the Isaac server is running"
  []
  (app/stop!)
  (let [server-config  (or (g/get :server-config) {})
        cfg            (config/server-config server-config)
        home           (or (g/get :isaac-home) (System/getProperty "user.home"))
        state-dir      (or (g/get :state-dir) (str home "/.isaac"))
        {:keys [port]} (app/start! {:cfg       server-config
                                    :dev       (:dev cfg)
                                    :home      home
                                    :host      (:host cfg)
                                    :port      (:port cfg)
                                    :state-dir state-dir})]
    (g/assoc! :server-port port)))

;; endregion ^^^^^ Setup ^^^^^

;; region ----- Server Commands -----

(defwhen server-command-run "the server command is run on port {port:int}"
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
    (with-redefs [server/block!       (fn [] nil)
                  config/load-config (fn [& _] cfg)]
      (with-out-str
        (app/stop!)
        (main/run ["server"])))
    (app/stop!)))

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
  [iso]
  (with-server-fs
    (fn []
      (scheduler/tick! {:cfg       (merge (config/load-config {:home (g/get :state-dir)})
                                          (when-let [crew (g/get :crew)] {:crew crew})
                                          (when-let [models (g/get :models)] {:models models})
                                          (when-let [providers (g/get :provider-configs)] {:providers providers}))
                        :now       (ZonedDateTime/parse iso offset-formatter)
                        :state-dir (g/get :state-dir)}))))

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

(defthen edn-state-file-contains "the EDN state file \"{path}\" contains:"
  [path table]
  (let [data (with-server-fs #(state-file-data path))]
    (doseq [row (:rows table)]
      (let [row-map   (zipmap (:headers table) row)
            actual    (get-path data (get row-map "path"))
            expected  (parse-state-value (get row-map "value"))]
        (g/should= expected actual)))))

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
  [table]
  (when-let [turn-future (g/get :turn-future)]
    (deref turn-future 30000 nil))
  (let [entries (log/get-entries)
        result  (log-match-result table entries)]
    (g/should= [] (:failures result))))

(defthen log-entries-dont-match "the log has no entries matching:"
  [table]
  (let [entries (log/get-entries)
        headers (:headers table)]
    (doseq [row (:rows table)]
      (let [result (match/match-entries {:headers headers :rows [row]} entries)]
        (g/should-not (:pass? result))))))

;; endregion ^^^^^ Log Assertions ^^^^^
