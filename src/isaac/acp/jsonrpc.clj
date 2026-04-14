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
   (request id method nil))
  ([id method params]
   (cond-> {:jsonrpc VERSION
            :id id
            :method method}
     (some? params) (assoc :params params))))

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

(defn request-line
  ([id method]
   (request-line id method nil))
  ([id method params]
   (str (json/generate-string (request id method params)) "\n")))