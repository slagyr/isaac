(ns isaac.acp.rpc
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc])
  (:import (clojure.lang ArityException ExceptionInfo)))

(defn- parse-message [line]
  (try
    (json/parse-string line true)
    (catch Exception _
      ::parse-error)))

(defn write-message! [writer message]
  (let [line (json/generate-string message)]
    (if (ifn? writer)
      (writer line)
      (do
        (.write writer line)
        (.write writer "\n")
        (.flush writer)))))

(defn- envelope? [result]
  (and (map? result)
       (or (contains? result :response)
           (contains? result :notifications))))

(defn- normalize-envelope [id notify? result]
  (let [response      (when-not notify?
                        (or (:response result)
                            (when (contains? result :result)
                              (jrpc/result id (:result result)))))
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

(defn- invalid-params [message _e]
  (jrpc/invalid-params (:id message)))

(defn- invalid-params-exception? [error]
  (= :invalid-params (:type (ex-data error))))

(defn- maybe-normalize-envelope [result message]
  (when (envelope? result)
    (normalize-envelope (:id message) (jrpc/notification? message) result)))

(defn maybe-result [message result]
  (when-not (jrpc/notification? message)
    (jrpc/result (:id message) result)))

(defn dispatch [handlers message]
  (let [handler (get handlers (:method message))]
    (cond
      (not (map? message))
      (jrpc/invalid-request nil)

      (nil? handler)
      (when-not (jrpc/notification? message)
        (jrpc/method-not-found (:id message)))

      :else
      (let [result (try
                     (invoke-handler handler (:params message) message)
                     (catch IllegalArgumentException e
                       (invalid-params message e)))]
        (or (maybe-normalize-envelope result message)
            (when (jrpc/result? result) result)
            (when (jrpc/error? result) result)
            (maybe-result message result))))))

(defn handle-line [handlers line]
  (let [message (parse-message line)]
    (if (= ::parse-error message)
      (jrpc/parse-error)
      (try
        (dispatch handlers message)
        (catch ExceptionInfo e
          (if (invalid-params-exception? e)
            {:jsonrpc "2.0"
             :id      (:id message)
             :error   {:code    -32602
                       :message (or (ex-message e) "Invalid params")}}
            (jrpc/internal-error (:id message))))
        (catch Exception _
          (jrpc/internal-error (:id message)))))))
