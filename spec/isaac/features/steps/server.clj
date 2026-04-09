(ns isaac.features.steps.server
  (:require
    [cheshire.core :as json]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.cli.serve :as serve]
    [isaac.config.resolution :as config]
    [isaac.features.matchers :as match]
    [isaac.logger :as log]
    [isaac.main :as main]
    [isaac.server.app :as app]
    [org.httpkit.client :as http]))

;; region ----- Setup -----

(defn stop-server! []
  (app/stop!))

(g/after-scenario stop-server!)

(defgiven configure "config:"
  [table]
  (doseq [[k v] (:rows table)]
    (case k
      "log.output"   (case v
                       "memory" (do (log/set-output! :memory)
                                    (log/clear-entries!))
                       (log/set-log-file! v))
      "gateway.port" (g/update! :server-config #(assoc-in (or % {}) [:gateway :port] (parse-long v)))
      "gateway.host" (g/update! :server-config #(assoc-in (or % {}) [:gateway :host] v))
      nil)))

(defgiven server-running "the Isaac server is running"
  []
  (app/stop!)
  (let [{:keys [port]} (app/start! {:port 0})]
    (g/assoc! :server-port port)))

;; endregion ^^^^^ Setup ^^^^^

;; region ----- Server Commands -----

(defwhen server-command-run "the server command is run on port {port:int}"
  [port]
  (with-redefs [serve/block!         (fn [] nil)
                config/load-config   (fn [& _] {})]
    (with-out-str
      (app/stop!)
      (main/run ["server" "--port" (str port)])))
  (app/stop!))

(defwhen server-command-run-no-port "the server command is run without a port flag"
  []
  (let [cfg (or (g/get :server-config) {})]
    (with-redefs [serve/block!       (fn [] nil)
                  config/load-config (fn [& _] cfg)]
      (with-out-str
        (app/stop!)
        (main/run ["server"])))
    (app/stop!)))

(defwhen gateway-command-run "the gateway command is run on port {port:int}"
  [port]
  (with-redefs [serve/block!       (fn [] nil)
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
  (let [entries (log/get-entries)
        result  (match/match-entries table entries)]
    (g/should (:pass? result))))

;; endregion ^^^^^ Log Assertions ^^^^^
