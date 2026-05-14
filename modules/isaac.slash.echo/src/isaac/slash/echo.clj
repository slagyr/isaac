(ns isaac.slash.echo
  (:require
    [clojure.string :as str]))

(def ^:private command-id "echo")

(declare handle-echo)

(defn- parse-args [input]
  (second (str/split (str/trim input) #"\s+" 2)))

(defn echo-command [cfg]
  {:command-name (or (:command-name cfg) command-id)
   :description  "Echo the input back unchanged"
   :handler      handle-echo})

(defn handle-echo [_session-key input _ctx]
  {:type    :command
   :command :echo
   :message (or (parse-args input) "")})
