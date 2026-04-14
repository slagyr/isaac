(ns isaac.acp.rpc
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc])
  (:import (clojure.lang ArityException ExceptionInfo)))

(defn- parse-message [line]
  (try
    (json/parse-string line true)
    (catch Exception e
      (throw (ex-info "Parse error" {:code jrpc/PARSE_ERROR} e)))))

(defn read-message [reader]
  (when-let [line (.readLine reader)]
    (parse-message line)))

(defn write-message! [writer message]
  (let [line (json/generate-string message)]
    (if (ifn? writer)
      (writer line)
      (do
        (.write writer line)
        (.write writer "\n")
        (.flush writer)))))

(defn- notification? [message]
  (not (contains? message :id)))

(defn- error-response [id code message]
  {:jsonrpc "2.0"
   :id      id
   :error   {:code code :message message}})

(defn- success-response [id result]
  {:jsonrpc "2.0"
   :id      id
   :result  result})

(defn- envelope? [result]
  (and (map? result)
       (or (contains? result :response)
           (contains? result :notifications))))

(defn- normalize-envelope [id notify? result]
  (let [response      (or (:response result)
                          (when (and (not notify?)
                                     (contains? result :result))
                            (success-response id (:result result))))
        notifications (vec (or (:notifications result) []))]
    (cond
      (and response (seq notifications)) {:response response :notifications notifications}
      response response
      (seq notifications) {:notifications notifications}
      :else nil)))

(defn- invoke-handler [handler params message]
  (try
    (handler params message)
    (catch ArityException _
      (handler params))))

(defn dispatch [handlers message]
  (when-not (map? message)
    (throw (ex-info "Invalid Request" {:code jrpc/INVALID_REQUEST})))
  (let [id           (:id message)
        notify?      (notification? message)
        method       (:method message)
        params       (:params message)
        method-fn    (get handlers method)]
    (cond
      (nil? method-fn)
      (when-not notify?
        (error-response id jrpc/METHOD_NOT_FOUND "Method not found"))

      :else
      (try
        (let [result (invoke-handler method-fn params message)]
          (if (envelope? result)
            (normalize-envelope id notify? result)
            (when-not notify?
              (success-response id result))))
        (catch ExceptionInfo e
          (let [{:keys [code message]} (ex-data e)]
            (if (= jrpc/INVALID_PARAMS code)
              (when-not notify?
                (error-response id jrpc/INVALID_PARAMS (or message (.getMessage e) "Invalid params")))
              (throw e))))
        (catch IllegalArgumentException e
          (when-not notify?
            (error-response id jrpc/INVALID_PARAMS (or (.getMessage e) "Invalid params"))))))))

(defn handle-line [handlers line]
  (try
    (dispatch handlers (parse-message line))
    (catch ExceptionInfo e
      (let [{:keys [code]} (ex-data e)]
        (cond
          (= jrpc/PARSE_ERROR code) (error-response nil jrpc/PARSE_ERROR "Parse error")
          (= jrpc/INVALID_REQUEST code) (error-response nil jrpc/INVALID_REQUEST "Invalid Request")
          :else           (throw e))))
    (catch Exception _e
      (error-response nil jrpc/INTERNAL_ERROR "Internal error"))))
