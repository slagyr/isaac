(ns isaac.acp.rpc
  (:require
    [cheshire.core :as json]))

(defn- parse-message [line]
  (try
    (json/parse-string line true)
    (catch Exception e
      (throw (ex-info "Parse error" {:code -32700} e)))))

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
                          (when-not notify?
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
    (catch clojure.lang.ArityException _
      (handler params))))

(defn dispatch [handlers message]
  (when-not (map? message)
    (throw (ex-info "Invalid Request" {:code -32600})))
  (let [id           (:id message)
        notify?      (notification? message)
        method       (:method message)
        params       (:params message)
        method-fn    (get handlers method)]
    (cond
      (nil? method-fn)
      (when-not notify?
        (error-response id -32601 "Method not found"))

      :else
      (try
        (let [result (invoke-handler method-fn params message)]
          (if (envelope? result)
            (normalize-envelope id notify? result)
            (when-not notify?
              (success-response id result))))
        (catch clojure.lang.ExceptionInfo e
          (let [{:keys [code message]} (ex-data e)]
            (if (= -32602 code)
              (when-not notify?
                (error-response id -32602 (or message (.getMessage e) "Invalid params")))
              (throw e))))
        (catch IllegalArgumentException e
          (when-not notify?
            (error-response id -32602 (or (.getMessage e) "Invalid params"))))))))

(defn handle-line [handlers line]
  (try
    (dispatch handlers (parse-message line))
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [code]} (ex-data e)]
        (cond
          (= -32700 code) (error-response nil -32700 "Parse error")
          (= -32600 code) (error-response nil -32600 "Invalid Request")
          :else           (throw e))))
    (catch Exception e
      (error-response nil -32603 "Internal error"))))
