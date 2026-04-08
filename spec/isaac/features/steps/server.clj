(ns isaac.features.steps.server
  (:require
    [cheshire.core :as json]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.server.app :as app]
    [org.httpkit.client :as http]))

(defgiven server-running "the Isaac server is running"
  []
  (let [{:keys [port]} (app/start! {:port 0})]
    (g/assoc! :server-port port)))

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
