(ns isaac.acp.rpc
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc])
  (:import (clojure.lang ArityException ExceptionInfo)))

(defn- parse-message [line]
  (try
    (json/parse-string line true)
    (catch Exception e
      (throw (ex-info "Parse error" {:code jrpc/PARSE_ERROR :rpc-message "Parse error"} e)))))

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
  {:jsonrpc jrpc/VERSION
   :id      id
   :error   {:code code :message message}})

(defn- success-response [id result]
  {:jsonrpc jrpc/VERSION
   :id      id
   :result  result})

(defn- exception->response [id notify? e]
  (let [{:keys [code type rpc-message]} (ex-data e)
         response-message (or rpc-message (.getMessage e))]
    (cond
      (= jrpc/INVALID_PARAMS code)
      (when-not notify?
        (error-response id jrpc/INVALID_PARAMS (or response-message "Invalid params")))

      (or (= :invalid-params type)
          (instance? IllegalArgumentException e))
      (when-not notify?
        (error-response id jrpc/INVALID_PARAMS "Invalid params"))

      (= jrpc/PARSE_ERROR code)
      (error-response nil jrpc/PARSE_ERROR (or response-message "Parse error"))

      (= jrpc/INVALID_REQUEST code)
      (error-response nil jrpc/INVALID_REQUEST (or response-message "Invalid Request"))

      :else nil)))

(defn- envelope? [result]
  (and (map? result)
       (or (contains? result :response)
            (contains? result :notifications))))

(defn- response? [result]
  (and (map? result)
       (= jrpc/VERSION (:jsonrpc result))
       (contains? result :id)
       (or (contains? result :result)
           (contains? result :error))))

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

(defn- invalid-params [message e]
  (throw (ex-info (or (.getMessage e) "Invalid params")
                  {:code        jrpc/INVALID_PARAMS
                   :rpc-message "Invalid params"
                   :rpc/request message}
                  e)))

(defn dispatch [handlers message]
  (when-not (map? message)
    (throw (ex-info "Invalid Request" {:code jrpc/INVALID_REQUEST :rpc-message "Invalid Request"})))
  (let [id        (:id message)
        notify?   (notification? message)
        method    (:method message)
        params    (:params message)
        method-fn (get handlers method)]
    (cond
      (nil? method-fn)
      (when-not notify?
        (error-response id jrpc/METHOD_NOT_FOUND "Method not found"))

      :else
      (let [result (try
                     (invoke-handler method-fn params message)
                     (catch IllegalArgumentException e
                       (invalid-params message e)))]
        (if (envelope? result)
          (normalize-envelope id notify? result)
          (if (response? result)
            result
            (when-not notify?
              (success-response id result))))))))

(defn handle-line [handlers line]
  (try
    (let [message (parse-message line)]
      (try
        (dispatch handlers message)
        (catch ExceptionInfo e
          (let [data    (ex-data e)
                request (or (:rpc/request data) message)
                id      (:id request)
                notify? (boolean (and request (notification? request)))]
            (or (exception->response id notify? e)
                (error-response id jrpc/INTERNAL_ERROR "Internal error"))))
        (catch IllegalArgumentException _e
          (error-response (:id message) jrpc/INVALID_PARAMS "Invalid params"))
        (catch Exception _e
          (error-response (:id message) jrpc/INTERNAL_ERROR "Internal error"))))
    (catch ExceptionInfo e
      (or (exception->response nil false e)
          (error-response nil jrpc/INTERNAL_ERROR "Internal error")))))
