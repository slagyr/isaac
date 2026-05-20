(ns isaac.util.jsonrpc
  "Generic JSON-RPC 2.0 message builders, predicates, and stream I/O.
   Use this for both clients and servers; no transport or dispatch
   assumptions baked in. Server-side handler dispatch (envelope
   normalization, ArityException-tolerant invocation) lives in
   isaac.util.jsonrpc.dispatch."
  (:require
    [cheshire.core :as json]))

(def VERSION "2.0")

(def PARSE_ERROR -32700)
(def INVALID_REQUEST -32600)
(def METHOD_NOT_FOUND -32601)
(def INVALID_PARAMS -32602)
(def INTERNAL_ERROR -32603)

;; region ----- Message constructors -----

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

;; endregion

;; region ----- Newline-delimited line builders -----

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

;; endregion

;; region ----- Predicates on parsed messages -----

(defn notification? [message]
  (not (contains? message :id)))

(defn result? [message]
  (and (map? message)
       (contains? message :id)
       (contains? message :result)))

(defn error? [message]
  (and (map? message)
       (contains? message :jsonrpc)
       (contains? message :error)))

;; endregion

;; region ----- Stream I/O -----

(def ^:private parse-error-marker ::parse-error)

(defn parse-message
  "Parse a single JSON-RPC line. Returns the message map on success or
   ::parse-error on failure."
  [line]
  (try
    (json/parse-string line true)
    (catch Exception _
      parse-error-marker)))

(defn parse-error? [message]
  (= parse-error-marker message))

(defn write-message!
  "Write a message as a single newline-terminated JSON line. `writer`
   may be either a java.io.Writer or a function (str -> any) for
   non-blocking transports like websockets."
  [writer message]
  (let [line (json/generate-string message)]
    (if (ifn? writer)
      (writer line)
      (do
        (.write writer line)
        (.write writer "\n")
        (.flush writer)))))

;; endregion
