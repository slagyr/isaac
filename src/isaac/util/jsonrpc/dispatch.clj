(ns isaac.util.jsonrpc.dispatch
  "Server-side dispatch for incoming JSON-RPC messages.

   `dispatch` looks up the message's :method in a handlers map, invokes
   the handler with the params (and the raw message as a 2nd arg if the
   handler arity allows it), and shapes the return value into a
   JSON-RPC response.

   Handlers may return an envelope `{:response <resp> :notifications [...]}`
   to emit notifications alongside a response — useful for streaming
   server patterns. Or they may return a plain value; for requests
   (non-notifications) the value is wrapped into a result automatically."
  (:require
    [isaac.util.jsonrpc :as jrpc])
  (:import (clojure.lang ArityException ExceptionInfo)))

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
                     (catch IllegalArgumentException _
                       (jrpc/invalid-params (:id message))))]
        (or (maybe-normalize-envelope result message)
            (when (jrpc/result? result) result)
            (when (jrpc/error? result) result)
            (maybe-result message result))))))

(defn handle-line [handlers line]
  (let [message (jrpc/parse-message line)]
    (if (jrpc/parse-error? message)
      (jrpc/parse-error)
      (try
        (dispatch handlers message)
        (catch ExceptionInfo e
          (if (invalid-params-exception? e)
            {:jsonrpc jrpc/VERSION
             :id      (:id message)
             :error   {:code    jrpc/INVALID_PARAMS
                       :message (or (ex-message e) "Invalid params")}}
            (jrpc/internal-error (:id message))))
        (catch Exception _
          (jrpc/internal-error (:id message)))))))
