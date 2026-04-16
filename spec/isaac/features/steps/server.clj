(ns isaac.features.steps.server
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.cli.server :as server]
    [isaac.config.resolution :as config]
    [isaac.features.matchers :as match]
    [isaac.logger :as log]
    [isaac.main :as main]
    [isaac.server.app :as app]
    [org.httpkit.client :as http]))

(defn- parse-config-value [value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    (= "true" (str/lower-case value)) true
    (= "false" (str/lower-case value)) false
    :else value))

(defn- config-path [path]
  (mapv keyword (str/split path #"\.")))

;; region ----- Setup -----

(defn stop-server! []
  (app/stop!))

(g/after-scenario stop-server!)

(defgiven configure "config:"
  [table]
  (doseq [[k v] (:rows table)]
    (if (= "log.output" k)
      (case v
        "memory" (do (log/set-output! :memory)
                     (log/clear-entries!))
        (log/set-log-file! v))
      (g/update! :server-config #(assoc-in (or % {}) (config-path k) (parse-config-value v))))))

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

;; endregion ^^^^^ Request / Response ^^^^^

;; region ----- Log Assertions -----

(defthen log-entries-match "the log has entries matching:"
  [table]
  (when-let [turn-future (g/get :turn-future)]
    (deref turn-future 30000 nil))
  (let [entries (log/get-entries)
        result  (match/match-entries table entries)]
    (g/should= [] (:failures result))))

(defthen log-entries-dont-match "the log has no entries matching:"
  [table]
  (let [entries (log/get-entries)
        headers (:headers table)]
    (doseq [row (:rows table)]
      (let [result (match/match-entries {:headers headers :rows [row]} entries)]
        (g/should-not (:pass? result))))))

;; endregion ^^^^^ Log Assertions ^^^^^
