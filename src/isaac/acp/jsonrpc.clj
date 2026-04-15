(ns isaac.acp.jsonrpc
  (:require
    [cheshire.core :as json]))

(def VERSION "2.0")

(def PARSE_ERROR -32700)
(def INVALID_REQUEST -32600)
(def METHOD_NOT_FOUND -32601)
(def INVALID_PARAMS -32602)
(def INTERNAL_ERROR -32603)

(defn request
  ([id method]
   (request id method nil nil))
  ([id method params]
   (request id method params nil))
  ([id method params prompt]
   (cond-> {:jsonrpc VERSION
            :id id
            :method method}
     (some? params) (assoc :params params)
     (some? prompt) (assoc-in [:params :prompt] prompt))))

(defn notification
  ([method]
   (notification method nil))
  ([method params]
   (cond-> {:jsonrpc VERSION
            :method method}
     (some? params) (assoc :params params))))

(defn result [id value]
  {:jsonrpc VERSION
   :id id
   :result value})

(defn method-not-found [id]
  {:jsonrpc VERSION
   :id id
   :error {:code METHOD_NOT_FOUND
           :message "Method not found"}})

(defn invalid-params [id]
  {:jsonrpc VERSION
   :id id
   :error {:code INVALID_PARAMS
           :message "Invalid params"}})

(defn request-line
  ([id method]
   (request-line id method nil nil))
  ([id method params]
   (request-line id method params nil))
  ([id method params prompt]
   (str (json/generate-string (request id method params prompt)) "\n")))

(defn result-line [id value]
  (str (json/generate-string (result id value)) "\n"))

(defn notification-line
  ([method]
   (notification-line method nil))
  ([method params]
   (str (json/generate-string (notification method params)) "\n")))

(defn notification? [message]
  (not (contains? message :id)))

(defn result? [message]
  (and (map? message)
       (contains? message :id)
       (contains? message :result)))

(defn error? [message]
  (and (map? message)
       (contains? message :error)))

(defn parse-error []
  {:jsonrpc VERSION
   :id nil
   :error {:code PARSE_ERROR
           :message "Parse error"}})

(defn invalid-request [id]
  {:jsonrpc VERSION
   :id id
   :error {:code INVALID_REQUEST
           :message "Invalid Request"}})

(defn internal-error [id]
  {:jsonrpc VERSION
   :id id
   :error {:code INTERNAL_ERROR
           :message "Internal error"}})